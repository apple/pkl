/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.cli

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.completion.CompletionCommand
import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import java.io.OutputStream
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.writeBytes
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliCommand
import org.pkl.commons.cli.CliException
import org.pkl.commons.cli.commands.installCommonOptions
import org.pkl.commons.currentWorkingDir
import org.pkl.core.Closeables
import org.pkl.core.CommandSpec
import org.pkl.core.EvaluatorBuilder
import org.pkl.core.FileOutput
import org.pkl.core.ModuleSource.uri
import org.pkl.core.PklBugException
import org.pkl.core.PklException
import org.pkl.core.util.IoUtils

class CliCommandRunner
@JvmOverloads
constructor(
  private val options: CliBaseOptions,
  private val reservedFlagNames: Set<String>,
  private val reservedFlagShortNames: Set<String>,
  private val args: List<String>,
  private val outputStream: OutputStream = System.out,
  private val errStream: OutputStream = System.err,
) : CliCommand(options) {

  override fun doRun() {
    val builder = evaluatorBuilder()
    try {
      evalCmd(builder)
    } finally {
      Closeables.closeQuietly(builder.moduleKeyFactories)
      Closeables.closeQuietly(builder.resourceReaders)
    }
  }

  private fun evalCmd(builder: EvaluatorBuilder) {
    val evaluator = builder.build()
    evaluator.use {
      evaluator.evaluateCommand(
        uri(resolvedSourceModules.first()),
        reservedFlagNames,
        reservedFlagShortNames,
      ) { spec ->
        try {
          val root = SynthesizedRunCommand(spec, this, options.sourceModules.first().toString())
          root.installCommonOptions(includeVersion = false)
          root.subcommands(
            CompletionCommand(
              name = "shell-completion",
              help = "Generate a completion script for the given shell",
            )
          )
          root.parse(args)
        } catch (e: PklException) {
          throw e
        } catch (e: Exception) {
          throw e.message?.let { PklException(it, e) } ?: PklException(e)
        }
      }
    }
  }

  /** Renders the comand's `output.bytes`, writing it to the standard output stream. */
  fun writeOutput(outputBytes: ByteArray) {
    if (outputBytes.isEmpty()) return
    outputStream.write(outputBytes)
    outputStream.flush()
  }

  /**
   * Renders the command's `output.files`, writing each entry as a file.
   *
   * File paths are written to the standard error stream.
   *
   * Unlike CliEvaluator, command outputs write relative to --working-dir and may write files
   * anywhere in the filesystem. This is intentionally less sandboxed than `pkl eval` and directly
   * targets the capabilities of CLI tools written in general purpose languages. Pkl commands should
   * therefore be treated as untrusted code the way that any other CLI tool would be.
   */
  fun writeMultipleFileOutput(outputFiles: Map<String, FileOutput>) {
    if (outputFiles.isEmpty()) return

    val writtenFiles = mutableMapOf<Path, String>()
    val outputDir = options.normalizedWorkingDir
    if (outputDir.exists() && !outputDir.isDirectory()) {
      throw CliException("Output path `$outputDir` exists and is not a directory.")
    }
    for ((pathSpec, fileOutput) in outputFiles) {
      checkPathSpec(pathSpec)
      val resolvedPath = outputDir.resolve(pathSpec).normalize()
      val realPath = if (resolvedPath.exists()) resolvedPath.toRealPath() else resolvedPath
      val previousOutput = writtenFiles[realPath]
      if (previousOutput != null) {
        throw CliException(
          "Output file conflict: `output.files` entries `\"${previousOutput}\"` and `\"$pathSpec\"` resolve to the same file path `$realPath`."
        )
      }
      if (realPath.isDirectory()) {
        throw CliException(
          "Output file conflict: `output.files` entry `\"$pathSpec\"` resolves to file path `$realPath`, which is a directory."
        )
      }
      writtenFiles[realPath] = pathSpec
      realPath.createParentDirectories()
      realPath.writeBytes(fileOutput.bytes)
      val displayPath =
        if (Path.of(pathSpec).isAbsolute) pathSpec
        else IoUtils.relativize(resolvedPath, currentWorkingDir).toString()
      errStream.writeText(displayPath + IoUtils.getLineSeparator())
      errStream.flush()
    }
  }

  class SynthesizedRunCommand(
    private val spec: CommandSpec,
    private val runner: CliCommandRunner,
    name: String? = null,
  ) : CliktCommand(name = name ?: spec.name) {
    init {
      spec.options.forEach { opt ->
        when (opt) {
          is CommandSpec.Flag ->
            registerOption(
              option(
                  names = opt.names,
                  help = opt.helpText ?: "",
                  metavar = opt.metavar,
                  hidden = opt.hidden,
                  completionCandidates = opt.completionCandidates?.toClikt(),
                )
                .convert {
                  try {
                    opt.transformEach.apply(it, workingDirUri)
                  } catch (e: CommandSpec.Option.BadValue) {
                    fail(e.message!!)
                  } catch (_: CommandSpec.Option.MissingOption) {
                    throw MissingOption(option)
                  }
                }
                .transformAll(opt.defaultValue, opt.showAsRequired) {
                  try {
                    opt.transformAll.apply(it, workingDirUri)
                  } catch (e: CommandSpec.Option.BadValue) {
                    fail(e.message!!)
                  } catch (_: CommandSpec.Option.MissingOption) {
                    throw MissingOption(option)
                  }
                }
            )
          is CommandSpec.BooleanFlag ->
            registerOption(
              if (opt.defaultValue != null)
                option(names = opt.names, help = opt.helpText ?: "", hidden = opt.hidden)
                  .flag("--no-${opt.name}", default = opt.defaultValue!!)
              else
                option(names = opt.names, help = opt.helpText ?: "", hidden = opt.hidden)
                  .nullableFlag("--no-${opt.name}")
            )
          is CommandSpec.CountedFlag ->
            registerOption(
              option(names = opt.names, help = opt.helpText ?: "", hidden = opt.hidden)
                .int()
                .transformValues(0..0) { it.lastOrNull() ?: 1 }
                .transformAll { it.sum().toLong() }
            )
          is CommandSpec.Argument ->
            registerArgument(
              argument(
                  opt.name,
                  opt.helpText ?: "",
                  completionCandidates = opt.completionCandidates?.toClikt(),
                )
                .convert {
                  try {
                    opt.transformEach.apply(it, workingDirUri)
                  } catch (e: CommandSpec.Option.BadValue) {
                    fail(e.message!!)
                  } catch (_: CommandSpec.Option.MissingOption) {
                    throw MissingArgument(argument)
                  }
                }
                .transformAll(if (opt.repeated) -1 else 1, !opt.repeated) {
                  try {
                    opt.transformAll.apply(it, workingDirUri)
                  } catch (e: CommandSpec.Option.BadValue) {
                    fail(e.message!!)
                  } catch (_: CommandSpec.Option.MissingOption) {
                    throw MissingArgument(argument)
                  }
                }
            )
        }
      }
      spec.subcommands.forEach { subcommands(SynthesizedRunCommand(it, runner)) }
    }

    val workingDirUri: URI by lazy { runner.options.normalizedWorkingDir.toUri() }

    override val invokeWithoutSubcommand = true

    override val hiddenFromHelp: Boolean = spec.hidden

    override fun help(context: Context): String = spec.helpText ?: ""

    override fun run() {
      if (currentContext.invokedSubcommand is CompletionCommand) return

      val opts =
        registeredOptions()
          .mapNotNull {
            val opt = it as? OptionWithValues<*, *, *> ?: return@mapNotNull null
            return@mapNotNull if (it.names.contains("--help")) null
            else it.names.last().trimStart('-') to opt.value
          }
          .toMap() +
          registeredArguments()
            .mapNotNull { it as? ArgumentDelegate<*> }
            .associateBy({ it.name }, { it.value })

      val state = spec.apply.apply(opts, currentContext.obj as CommandSpec.State?)
      currentContext.obj = state

      if (currentContext.invokedSubcommand != null) return
      if (spec.subcommands.isNotEmpty() && spec.noOp) {
        throw PrintHelpMessage(currentContext, true, 1)
      }

      val result = state.evaluate()
      runner.writeOutput(result.outputBytes)
      runner.writeMultipleFileOutput(result.outputFiles)
    }
  }
}

fun CommandSpec.CompletionCandidates.toClikt(): CompletionCandidates =
  when (this) {
    CommandSpec.CompletionCandidates.PATH -> CompletionCandidates.Path
    is CommandSpec.CompletionCandidates.Fixed -> CompletionCandidates.Fixed(values)
    else -> throw PklBugException.unreachableCode()
  }
