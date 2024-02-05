/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pkl.cli.repl

import java.io.IOException
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import org.fusesource.jansi.Ansi
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader.Option
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.reader.impl.completer.AggregateCompleter
import org.jline.reader.impl.history.DefaultHistory
import org.jline.terminal.TerminalBuilder
import org.jline.utils.InfoCmp
import org.pkl.core.repl.ReplRequest
import org.pkl.core.repl.ReplResponse
import org.pkl.core.repl.ReplServer
import org.pkl.core.util.IoUtils

internal class Repl(workingDir: Path, private val server: ReplServer) {
    private val terminal = TerminalBuilder.builder().apply { jansi(true) }.build()
    private val history = DefaultHistory()
    private val reader =
        LineReaderBuilder.builder()
            .apply {
                history(history)
                terminal(terminal)
                completer(AggregateCompleter(CommandCompleter, FileCompleter(workingDir)))
                option(Option.DISABLE_EVENT_EXPANSION, true)
                variable(
                    org.jline.reader.LineReader.HISTORY_FILE,
                    (IoUtils.getPklHomeDir().resolve("repl-history"))
                )
            }
            .build()

    private var continuation = false
    private var quit = false
    private var nextRequestId = 0

    fun run() {
        // JLine 2 history file is incompatible with JLine 3
        IoUtils.getPklHomeDir().resolve("repl-history.bin").deleteIfExists()

        println(ReplMessages.welcome)
        println()

        var inputBuffer = ""

        try {
            while (!quit) {
                val line =
                    try {
                        if (continuation) {
                            nextRequestId -= 1
                            reader.readLine(" ".repeat("pkl$nextRequestId> ".length))
                        } else {
                            reader.readLine("pkl$nextRequestId> ")
                        }
                    } catch (e: UserInterruptException) {
                        ":quit"
                    } catch (e: EndOfFileException) {
                        ":quit"
                    }

                val input = line.trim()
                if (input.isEmpty()) continue

                if (continuation) {
                    inputBuffer = (inputBuffer + "\n" + input).trim()
                    continuation = false
                } else {
                    inputBuffer = input
                }

                if (inputBuffer.startsWith(":")) {
                    executeCommand(inputBuffer)
                } else {
                    evaluate(inputBuffer)
                }
            }
        } finally {
            try {
                history.save()
            } catch (ignored: IOException) {}
            try {
                terminal.close()
            } catch (ignored: IOException) {}
        }
    }

    private fun executeCommand(inputBuffer: String) {
        val candidates = getMatchingCommands(inputBuffer)
        when {
            candidates.isEmpty() -> {
                println("Unknown command: `${inputBuffer.drop(1)}`")
            }
            candidates.size > 1 -> {
                print("Which of the following did you mean?  ")
                println(candidates.joinToString(separator = "  ") { "`:${it.type}`" })
            }
            else -> {
                doExecuteCommand(candidates.single())
            }
        }
    }

    private fun doExecuteCommand(command: ParsedCommand) {
        when (command.type) {
            Command.Clear -> clear()
            Command.Examples -> examples()
            Command.Force -> force(command)
            Command.Help -> help()
            Command.Load -> load(command)
            Command.Quit -> quit()
            Command.Reset -> reset()
        }
    }

    private fun clear() {
        terminal.puts(InfoCmp.Capability.clear_screen)
        terminal.flush()
    }

    private fun examples() {
        println(ReplMessages.examples)
    }

    private fun help() {
        println(ReplMessages.help)
    }

    private fun quit() {
        quit = true
    }

    private fun reset() {
        server.handleRequest(ReplRequest.Reset(nextRequestId()))
        clear()
        nextRequestId = 0
    }

    private fun evaluate(inputBuffer: String) {
        handleEvalRequest(ReplRequest.Eval(nextRequestId(), inputBuffer, false, false))
    }

    private fun loadModule(uri: URI) {
        handleEvalRequest(ReplRequest.Load(nextRequestId(), uri))
    }

    private fun force(command: ParsedCommand) {
        handleEvalRequest(ReplRequest.Eval(nextRequestId(), command.arg, false, true))
    }

    private fun load(command: ParsedCommand) {
        loadModule(IoUtils.toUri(command.arg))
    }

    private fun handleEvalRequest(request: ReplRequest) {
        val responses = server.handleRequest(request)

        for (response in responses) {
            when (response) {
                is ReplResponse.EvalSuccess -> {
                    println(response.result)
                }
                is ReplResponse.EvalError -> {
                    println(response.message)
                }
                is ReplResponse.InternalError -> {
                    throw response.cause
                }
                is ReplResponse.IncompleteInput -> {
                    assert(responses.size == 1)
                    continuation = true
                }
                else -> throw IllegalStateException("Unexpected response: $response")
            }
        }
    }

    private fun nextRequestId(): String = "pkl$nextRequestId".apply { nextRequestId += 1 }

    private fun print(msg: String) {
        terminal.writer().print(highlight(msg))
    }

    private fun println(msg: String = "") {
        terminal.writer().println(highlight(msg))
    }

    private fun highlight(str: String): String {
        val ansi = Ansi.ansi()
        var normal = true
        for (part in str.split("`", "```")) {
            ansi.a(part)
            normal = !normal
            if (!normal) ansi.bold() else ansi.boldOff()
        }
        ansi.reset()
        return ansi.toString()
    }
}
