/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import java.nio.file.Files
import java.nio.file.Path
import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine
import org.jline.terminal.Terminal
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.OSUtils
import org.jline.utils.StyleResolver

/**
 * Originally copied from:
 * https://github.com/jline/jline3/blob/jline-parent-3.21.0/builtins/src/main/java/org/jline/builtins/Completers.java
 *
 * Reasons for copying this class instead of adding jline-builtins dependency:
 * - Adding the dependency breaks native-image build (at least when using build-time initialization,
 *   might work with some config).
 * - Completers.FileNameCompleter is the only class we currently use.
 */
internal abstract class JLineFileNameCompleter : Completer {
  override fun complete(
    reader: LineReader,
    commandLine: ParsedLine,
    candidates: MutableList<Candidate>,
  ) {
    val buffer = commandLine.word().substring(0, commandLine.wordCursor())
    val current: Path
    val curBuf: String
    val sep = getSeparator(reader.isSet(LineReader.Option.USE_FORWARD_SLASH))
    val lastSep = buffer.lastIndexOf(sep)
    try {
      if (lastSep >= 0) {
        curBuf = buffer.substring(0, lastSep + 1)
        current =
          if (curBuf.startsWith("~")) {
            if (curBuf.startsWith("~$sep")) {
              userHome.resolve(curBuf.substring(2))
            } else {
              userHome.parent.resolve(curBuf.substring(1))
            }
          } else {
            userDir.resolve(curBuf)
          }
      } else {
        curBuf = ""
        current = userDir
      }
      try {
        Files.newDirectoryStream(current) { accept(it) }
          .use { directory ->
            directory.forEach { path ->
              val value = curBuf + path.fileName.toString()
              if (Files.isDirectory(path)) {
                candidates.add(
                  Candidate(
                    value + if (reader.isSet(LineReader.Option.AUTO_PARAM_SLASH)) sep else "",
                    getDisplay(reader.terminal, path, resolver, sep),
                    null,
                    null,
                    if (reader.isSet(LineReader.Option.AUTO_REMOVE_SLASH)) sep else null,
                    null,
                    false,
                  )
                )
              } else {
                candidates.add(
                  Candidate(
                    value,
                    getDisplay(reader.terminal, path, resolver, sep),
                    null,
                    null,
                    null,
                    null,
                    true,
                  )
                )
              }
            }
          }
      } catch (ignored: IOException) {}
    } catch (ignored: Exception) {}
  }

  protected open fun accept(path: Path): Boolean {
    return try {
      !Files.isHidden(path)
    } catch (e: IOException) {
      false
    }
  }

  protected open val userDir: Path
    get() = Path.of(System.getProperty("user.dir"))

  private val userHome: Path
    get() = Path.of(System.getProperty("user.home"))

  private fun getSeparator(useForwardSlash: Boolean): String {
    return if (useForwardSlash) "/" else userDir.fileSystem.separator
  }

  private fun getDisplay(
    terminal: Terminal,
    path: Path,
    resolver: StyleResolver,
    separator: String,
  ): String {
    val builder = AttributedStringBuilder()
    val name = path.fileName.toString()
    val index = name.lastIndexOf(".")
    val type = if (index != -1) ".*" + name.substring(index) else null
    if (Files.isSymbolicLink(path)) {
      builder.styled(resolver.resolve(".ln"), name).append("@")
    } else if (Files.isDirectory(path)) {
      builder.styled(resolver.resolve(".di"), name).append(separator)
    } else if (Files.isExecutable(path) && !OSUtils.IS_WINDOWS) {
      builder.styled(resolver.resolve(".ex"), name).append("*")
    } else if (type != null && resolver.resolve(type).style != 0L) {
      builder.styled(resolver.resolve(type), name)
    } else if (Files.isRegularFile(path)) {
      builder.styled(resolver.resolve(".fi"), name)
    } else {
      builder.append(name)
    }
    return builder.toAnsi(terminal)
  }

  companion object {
    private val resolver = StyleResolver { name ->
      when (name) {
        // imitate org.jline.builtins.Styles.DEFAULT_LS_COLORS
        "di" -> "1;91"
        "ex" -> "1;92"
        "ln" -> "1;96"
        "fi" -> null
        else -> null
      }
    }
  }
}

internal class FileCompleter(override val userDir: Path) : JLineFileNameCompleter() {
  override fun complete(
    reader: LineReader,
    commandLine: ParsedLine,
    candidates: MutableList<Candidate>,
  ) {
    val loadCmd =
      getMatchingCommands(commandLine.line()).find { it.type == Command.Load && it.ws.isNotEmpty() }
    if (loadCmd != null) {
      super.complete(reader, commandLine, candidates)
    }
  }
}

internal object CommandCompleter : Completer {
  private val commandCandidates: List<Candidate> =
    Command.entries.map { Candidate(":" + it.toString().lowercase()) }

  override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>) {
    if (line.wordIndex() == 0) candidates.addAll(commandCandidates)
  }
}
