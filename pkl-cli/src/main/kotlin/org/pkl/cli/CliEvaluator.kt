/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.writeBytes
import org.pkl.commons.cli.CliCommand
import org.pkl.commons.cli.CliException
import org.pkl.commons.currentWorkingDir
import org.pkl.commons.writeString
import org.pkl.core.Closeables
import org.pkl.core.Evaluator
import org.pkl.core.EvaluatorBuilder
import org.pkl.core.ModuleSource
import org.pkl.core.PklException
import org.pkl.core.module.ModulePathResolver
import org.pkl.core.runtime.ModuleResolver
import org.pkl.core.runtime.VmException
import org.pkl.core.runtime.VmUtils
import org.pkl.core.util.IoUtils

private data class OutputFile(val pathSpec: String, val moduleUri: URI)

/** API equivalent of the Pkl command-line evaluator. */
class CliEvaluator
@JvmOverloads
constructor(
  private val options: CliEvaluatorOptions,
  // use System.{in,out}() rather than System.console()
  // because the latter returns null when output is sent through a unix pipe
  private val inputStream: InputStream = System.`in`,
  private val outputStream: OutputStream = System.out,
) : CliCommand(options.base) {
  /**
   * Output files for the modules to be evaluated. Returns `null` if `options.outputPath` is `null`
   * or if `options.multipleFileOutputPath` is not `null`. Multiple modules may be mapped to the
   * same output file, in which case their outputs are concatenated with
   * [CliEvaluatorOptions.moduleOutputSeparator].
   */
  @Suppress("MemberVisibilityCanBePrivate")
  val outputFiles: Set<File>? by lazy {
    fileOutputPaths?.values?.mapTo(mutableSetOf(), Path::toFile)
  }

  /**
   * Output directories for the modules to be evaluated. Returns `null` if
   * `options.multipleFileOutputPath` is `null`.
   */
  @Suppress("MemberVisibilityCanBePrivate")
  val outputDirectories: Set<File>? by lazy {
    directoryOutputPaths?.values?.mapTo(mutableSetOf(), Path::toFile)
  }

  /** The file output path */
  val fileOutputPaths: Map<URI, Path>? by lazy {
    if (options.multipleFileOutputPath != null) return@lazy null
    options.outputPath?.let { resolveOutputPaths(it) }
  }

  private val directoryOutputPaths: Map<URI, Path>? by lazy {
    options.multipleFileOutputPath?.let { resolveOutputPaths(it) }
  }

  /**
   * Evaluates source modules according to [options].
   *
   * If [CliEvaluatorOptions.outputPath] is set, each module's `output.bytes` is written to the
   * module's [output file][outputFiles].
   *
   * If [CliEvaluatorOptions.multipleFileOutputPath] is set, each module's `output.files` are
   * written to the module's [output directory][outputDirectories]. Otherwise, each module's
   * `output.bytes` is written to [outputStream] (which defaults to standard out).
   *
   * Throws [CliException] in case of an error.
   */
  override fun doRun() {
    val builder = evaluatorBuilder()
    try {
      if (options.multipleFileOutputPath != null) {
        writeMultipleFileOutput(builder)
      } else {
        writeOutput(builder)
      }
    } finally {
      Closeables.closeQuietly(builder.moduleKeyFactories)
      Closeables.closeQuietly(builder.resourceReaders)
    }
  }

  private fun resolveOutputPaths(pathStr: String): Map<URI, Path> {
    val moduleUris = options.base.normalizedSourceModules
    val workingDir = options.base.normalizedWorkingDir
    // used just to resolve the `%{moduleName}` placeholder
    val moduleResolver = ModuleResolver(moduleKeyFactories(ModulePathResolver.empty()))

    return moduleUris.associateWith { uri ->
      val moduleDir: String? =
        IoUtils.toPath(uri)?.let {
          IoUtils.relativize(it.parent, workingDir).toString().ifEmpty { "." }
        }
      val moduleKey =
        try {
          moduleResolver.resolve(uri)
        } catch (e: VmException) {
          throw e.toPklException(stackFrameTransformer, options.base.color?.hasColor() ?: false)
        }
      val substituted =
        pathStr
          .replace("%{moduleName}", IoUtils.inferModuleName(moduleKey))
          .replace("%{outputFormat}", options.outputFormat ?: "%{outputFormat}")
          .replace("%{moduleDir}", moduleDir ?: "%{moduleDir}")
      if (substituted.contains("%{moduleDir}")) {
        throw PklException(
          "Cannot substitute output path placeholder `%{moduleDir}` " +
            "because module `$uri` does not have a file system path."
        )
      }
      val absolutePath = workingDir.resolve(substituted).normalize()
      absolutePath
    }
  }

  private fun Evaluator.evalOutput(moduleSource: ModuleSource): ByteArray {
    if (options.expression == null) {
      return evaluateOutputBytes(moduleSource)
    }
    return evaluateExpressionString(moduleSource, options.expression)
      .toByteArray(StandardCharsets.UTF_8)
  }

  /** Renders each module's `output.bytes`, writing it to the specified output file. */
  private fun writeOutput(builder: EvaluatorBuilder) {
    val evaluator = builder.setOutputFormat(options.outputFormat).build()
    evaluator.use { ev ->
      val outputFiles = fileOutputPaths
      if (outputFiles != null) {
        // files that we've written non-empty output to
        // YamlRenderer produces empty output if `isStream` is true and `output.value` is empty
        // collection
        val writtenFiles = mutableSetOf<Path>()

        for ((moduleUri, outputFile) in outputFiles) {
          val moduleSource = toModuleSource(moduleUri, inputStream)
          if (Files.isDirectory(outputFile)) {
            throw CliException(
              "Output file `$outputFile` is a directory. " +
                "Did you mean `--multiple-file-output-path`?"
            )
          }
          val output = ev.evalOutput(moduleSource)
          outputFile.createParentDirectories()
          if (!writtenFiles.contains(outputFile)) {
            // write file even if output is empty to overwrite output from previous runs
            outputFile.writeBytes(output)
            if (output.isNotEmpty()) {
              writtenFiles.add(outputFile)
            }
          } else {
            if (output.isNotEmpty()) {
              outputFile.writeString(
                options.moduleOutputSeparator + '\n',
                Charsets.UTF_8,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND,
              )
              outputFile.writeBytes(output, StandardOpenOption.WRITE, StandardOpenOption.APPEND)
            }
          }
        }
      } else {
        var outputWritten = false
        for (moduleUri in options.base.normalizedSourceModules) {
          val moduleSource = toModuleSource(moduleUri, inputStream)
          if (options.expression != null) {
            val output = evaluator.evaluateExpressionString(moduleSource, options.expression)
            if (output.isNotEmpty()) {
              if (outputWritten) outputStream.writeLine(options.moduleOutputSeparator)
              outputStream.writeText(output)
              outputStream.flush()
              outputWritten = true
            }
          } else {
            val outputBytes = evaluator.evaluateOutputBytes(moduleSource)
            if (outputBytes.isNotEmpty()) {
              if (outputWritten) outputStream.writeLine(options.moduleOutputSeparator)
              outputStream.write(outputBytes)
              outputStream.flush()
              outputWritten = true
            }
          }
        }
      }
    }
  }

  private fun toModuleSource(uri: URI, reader: InputStream) =
    if (uri == VmUtils.REPL_TEXT_URI) {
      ModuleSource.create(uri, reader.readAllBytes().toString(StandardCharsets.UTF_8))
    } else {
      ModuleSource.uri(uri)
    }

  /**
   * Renders each module's `output.files`, writing each entry as a file into the specified output
   * directory.
   */
  private fun writeMultipleFileOutput(builder: EvaluatorBuilder) {
    val outputDirs = directoryOutputPaths!!
    val writtenFiles = mutableMapOf<Path, OutputFile>()
    for ((moduleUri, outputDir) in outputDirs) {
      val evaluator = builder.setOutputFormat(options.outputFormat).build()
      if (outputDir.exists() && !outputDir.isDirectory()) {
        throw CliException("Output path `$outputDir` exists and is not a directory.")
      }
      val moduleSource = toModuleSource(moduleUri, inputStream)
      val output = evaluator.evaluateOutputFiles(moduleSource)
      val realOutputDir = if (outputDir.exists()) outputDir.toRealPath() else outputDir

      for ((pathSpec, fileOutput) in output) {
        checkPathSpec(pathSpec)
        val resolvedPath = realOutputDir.resolve(pathSpec).normalize()
        val realPath = if (resolvedPath.exists()) resolvedPath.toRealPath() else resolvedPath
        if (!realPath.startsWith(realOutputDir)) {
          throw CliException(
            "Output file conflict: `output.files` entry `\"$pathSpec\"` in module `$moduleUri` resolves to file path `$realPath`, which is outside output directory `$realOutputDir`."
          )
        }
        val previousOutput = writtenFiles[realPath]
        if (previousOutput != null) {
          throw CliException(
            "Output file conflict: `output.files` entries `\"${previousOutput.pathSpec}\"` in module `${previousOutput.moduleUri}` and `\"$pathSpec\"` in module `$moduleUri` resolve to the same file path `$realPath`."
          )
        }
        if (realPath.isDirectory()) {
          throw CliException(
            "Output file conflict: `output.files` entry `\"$pathSpec\"` in module `$moduleUri` resolves to file path `$realPath`, which is a directory."
          )
        }
        writtenFiles[realPath] = OutputFile(pathSpec, moduleUri)
        realPath.createParentDirectories()
        realPath.writeBytes(fileOutput.bytes)
        outputStream.writeText(
          IoUtils.relativize(resolvedPath, currentWorkingDir).toString() +
            IoUtils.getLineSeparator()
        )
        outputStream.flush()
      }
    }
  }
}
