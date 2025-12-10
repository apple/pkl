/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.subcommands
import java.io.OutputStream
import java.nio.file.Path
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.writeBytes
import kotlin.use
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliCommand
import org.pkl.commons.cli.CliException
import org.pkl.commons.currentWorkingDir
import org.pkl.core.Closeables
import org.pkl.core.CommandSpec
import org.pkl.core.EvaluatorBuilder
import org.pkl.core.FileOutput
import org.pkl.core.ModuleSource.uri
import org.pkl.core.util.IoUtils

class CliCommandRunner
@JvmOverloads
constructor(
  private val options: CliBaseOptions,
  private val args: List<String>,
  private val outputStream: OutputStream = System.out,
  private val errStream: OutputStream = System.err,
) : CliCommand(options) {

  private val normalizedSourceModule = options.normalizedSourceModules.first()

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
      val spec = evaluator.evaluateCommand(uri(normalizedSourceModule))
      val root = SynthesizedRunCommand(spec, this)
      root.main(args)
    }
  }
  
  class SynthesizedRunCommand(private val spec: CommandSpec, private val runner: CliCommandRunner): CliktCommand(name = spec.name) {
    init {
//      TODO("init flags")
//      TODO("init args")
      
      subcommands(spec.subcommands.map { SynthesizedRunCommand(it, runner) })
    }

    override val invokeWithoutSubcommand = true
    
    override fun help(context: Context): String = spec.description ?: ""

    override fun run() {
      // TODO("collect options")
      val state = spec.apply.apply(mapOf(), currentContext.obj as CommandSpec.State?)
      currentContext.obj = state
      
      if (spec.noOp) {
        if (currentContext.invokedSubcommand == null) {
          throw PrintHelpMessage(currentContext, true, 1)
        }
        return
      }
      
      val result = state.evaluate()
      runner.writeOutput(result.outputBytes)
      runner.writeMultipleFileOutput(result.outputFiles)
    }
  }

  /**
   * Renders the comand's `output.bytes`, writing it to the standard output stream.
   *
   * Unlike CliEvaluator, there is no need to handle
   */
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
      errStream.writeText(
        IoUtils.relativize(resolvedPath, currentWorkingDir).toString() + IoUtils.getLineSeparator()
      )
      errStream.flush()
    }
  }
}
