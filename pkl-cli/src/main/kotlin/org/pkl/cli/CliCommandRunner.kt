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

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.completion.CompletionCommand
import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.transform.TransformContext
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.writeBytes
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliCommand
import org.pkl.commons.cli.CliException
import org.pkl.commons.cli.commands.BaseOptions
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
      evaluator.evaluateCommand(uri(normalizedSourceModule)) { spec ->
        val root = SynthesizedRunCommand(spec, this, options.sourceModules.first().toString())
        root.subcommands(
          CompletionCommand(
            name = "shell-completion",
            help = "Generate a completion script for the given shell",
          )
        )
        root.main(args)
      }
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
      // FIXME: should we validate against options.normalizedRootDir?
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
      spec.flags.forEach(this::registerFlag)
      spec.arguments.forEach(this::registerArgument)
      spec.subcommands.forEach { subcommands(SynthesizedRunCommand(it, runner)) }
    }

    override val invokeWithoutSubcommand = true

    override val hiddenFromHelp: Boolean = spec.hide

    override fun help(context: Context): String = spec.description ?: ""

    override fun run() {
      if (currentContext.invokedSubcommand is CompletionCommand) return

      val opts =
        registeredOptions()
          .mapNotNull {
            val opt = it as? OptionWithValues<*, *, *> ?: return@mapNotNull null
            return@mapNotNull if (
              it.names.contains("--help") ||
                (opt.value as? List<*>)?.isEmpty() ?: false ||
                (opt.value as? Map<*, *>)?.isEmpty() ?: false
            )
              null
            else it.names.first().trimStart('-') to opt.value
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

    private fun registerFlag(flag: CommandSpec.Flag) {
      val names =
        if (flag.shortName == null) arrayOf("--${flag.name}")
        else arrayOf("--${flag.name}", "-${flag.shortName}")
      val flag0 = option(names = names, help = spec.description ?: "", hidden = flag.hide)
      val type = flag.type
      registerOption(
        when (type) {
          is CommandSpec.OptionType.Primitive,
          is CommandSpec.OptionType.Enum,
          is CommandSpec.OptionType.Collection ->
            flag0.convert(type, flag.parse, runner.options.normalizedWorkingDir).flag(flag)
          is CommandSpec.OptionType.Map ->
            flag0
              .splitPair("=")
              .convert {
                Pair(
                  convertValue(it.first, type.keyType, "key"),
                  if (flag.parse != null) flag.parse!!.parse(it.second)
                  else convertValue(it.second, type.valueType, "value"),
                )
              }
              .multiple(
                default =
                  (flag.defaultValue?.let { default ->
                    (default as Map<*, *>).entries.toList().map { Pair(it.key!!, it.value!!) }
                  } ?: emptyList())
              )
              .toMap()
        }
      )
    }

    private fun registerArgument(arg: CommandSpec.Argument) {
      val arg0 = argument(arg.name, arg.description ?: "")
      registerArgument(
        when (val type = arg.type) {
          is CommandSpec.OptionType.Primitive,
          is CommandSpec.OptionType.Enum,
          is CommandSpec.OptionType.Collection ->
            arg0.convert(type, arg.parse, runner.options.normalizedWorkingDir).argument(arg)
          else -> throw CliException("Unexpected argument type $type")
        }
      )
    }
  }
}

// handle required, default, multiple (list/set)
private fun <InT> NullableOption<InT, InT>.flag(flag: CommandSpec.Flag) =
  when (val valueType = (flag.type as? CommandSpec.OptionType.Collection)?.valueType) {
    is CommandSpec.OptionType.Primitive,
    is CommandSpec.OptionType.Enum,
    null ->
      transformAll(
        defaultForHelp = flag.defaultValue?.toString(),
        showAsRequired = flag.type.isRequired,
      ) {
        when {
          flag.type is CommandSpec.OptionType.Collection ->
            when {
              it.isEmpty() && flag.type.isRequired -> throw MissingOption(option)
              it.isEmpty() && !flag.type.isRequired -> flag.defaultValue
              else ->
                if (
                  (flag.type as CommandSpec.OptionType.Collection).type ==
                    CommandSpec.OptionType.Collection.Type.SET
                )
                  it.toSet()
                else it
            }
          flag.defaultValue != null -> it.lastOrNull() ?: flag.defaultValue
          flag.type.isRequired -> it.lastOrNull() ?: throw MissingOption(option)
          else -> it
        }
      }
    else -> throw CliException("Unexpected collection flag value type $valueType")
  }

// handle multiple
private fun <InT> ProcessedArgument<InT, InT>.argument(arg: CommandSpec.Argument) =
  if (arg.type is CommandSpec.OptionType.Collection)
    when (val valueType = (arg.type as CommandSpec.OptionType.Collection).valueType) {
      is CommandSpec.OptionType.Primitive,
      is CommandSpec.OptionType.Enum ->
        transformAll(nvalues = -1, required = arg.type.isRequired) {
          if (
            (arg.type as CommandSpec.OptionType.Collection).type ==
              CommandSpec.OptionType.Collection.Type.SET
          )
            it.toSet()
          else it
        }
      else -> throw CliException("Unexpected collection argument value type $valueType")
    }
  else this

// handle parse/import functions

@Suppress("DuplicatedCode")
fun RawOption.convert(
  type: CommandSpec.OptionType,
  parseFunction: CommandSpec.ParseOptionFunction?,
  workingDir: Path,
  completionCandidates: CompletionCandidates? = null,
) =
  if (parseFunction != null)
    convert(completionCandidates = completionCandidates) {
      // if the parse func is executing an import, resolve the value to a normalized source URI
      // this is the same process that CliBaseOptions and its callers use
      val parseArg =
        if (parseFunction.isImport) {
          val uri = BaseOptions.parseModuleName(it)
          if (uri.isAbsolute) uri.toString()
          else IoUtils.resolve(workingDir.toUri(), uri).toString()
        } else it
      return@convert parseFunction.parse(parseArg)
    }
  else convert { convertValue(it, type, null) }.copy(metavarGetter = { type.toString() })

@Suppress("DuplicatedCode")
fun RawArgument.convert(
  type: CommandSpec.OptionType,
  parseFunction: CommandSpec.ParseOptionFunction?,
  workingDir: Path,
  completionCandidates: CompletionCandidates? = null,
) =
  if (parseFunction != null)
    convert(completionCandidates = completionCandidates) {
      // if the parse func is executing an import, resolve the value to a normalized source URI
      // this is the same process that CliBaseOptions and its callers use
      val parseArg =
        if (parseFunction.isImport) {
          val uri = BaseOptions.parseModuleName(it)
          if (uri.isAbsolute) uri.toString()
          else IoUtils.resolve(workingDir.toUri(), uri).toString()
        } else it
      return@convert parseFunction.parse(parseArg)
    }
  else convert { convertValue(it, type, null) }

// helpers for converting primitives/enums

private fun String.toLongOrNull(
  ctx: TransformContext,
  type: CommandSpec.OptionType,
  range: ClosedRange<Long>? = null,
): Long? {
  val long = toLongOrNull() ?: return null
  if (range != null && !range.contains(long)) ctx.fail("$this is out of range for $type")
  return long
}

private val convertValue:
  TransformContext.(value: String, type: CommandSpec.OptionType, mapPosition: String?) -> Any =
  { value, type, mapPosition ->
    when (type) {
      is CommandSpec.OptionType.Primitive ->
        when (type.type) {
          CommandSpec.OptionType.Primitive.Type.NUMBER ->
            value.toIntOrNull() ?: value.toDoubleOrNull()
          CommandSpec.OptionType.Primitive.Type.FLOAT -> value.toDoubleOrNull()
          CommandSpec.OptionType.Primitive.Type.INT -> value.toLongOrNull(this, type)
          // ranges based on org.pkl.core.stdlib.math.MathNodes
          CommandSpec.OptionType.Primitive.Type.INT8 ->
            value.toLongOrNull(this, type, Byte.MIN_VALUE.toLong()..<Byte.MAX_VALUE)
          CommandSpec.OptionType.Primitive.Type.INT16 ->
            value.toLongOrNull(this, type, Short.MIN_VALUE.toLong()..Short.MAX_VALUE)
          CommandSpec.OptionType.Primitive.Type.INT32 ->
            value.toLongOrNull(this, type, Integer.MIN_VALUE.toLong()..Integer.MAX_VALUE)
          CommandSpec.OptionType.Primitive.Type.UINT ->
            value.toLongOrNull(this, type, 0..<Long.MAX_VALUE)
          CommandSpec.OptionType.Primitive.Type.UINT8 -> value.toLongOrNull(this, type, 0..<256L)
          CommandSpec.OptionType.Primitive.Type.UINT16 ->
            value.toLongOrNull(this, type, 0..<65_536L) // 256 * 256
          CommandSpec.OptionType.Primitive.Type.UINT32 ->
            value.toLongOrNull(this, type, 0..<4_294_967_296L) // 256 * 256 * 256 * 256
          CommandSpec.OptionType.Primitive.Type.BOOLEAN ->
            when (value.lowercase()) {
              "true",
              "t",
              "1",
              "yes",
              "y",
              "on" -> true
              "false",
              "f",
              "0",
              "no",
              "n",
              "off" -> false
              else -> null
            }
          CommandSpec.OptionType.Primitive.Type.STRING -> value
          CommandSpec.OptionType.Primitive.Type.CHAR -> if (value.length == 1) value else null
        }
      is CommandSpec.OptionType.Enum ->
        if (type.choices.contains(value)) value
        else fail("invalid choice: $value. (choose from ${type.choices.joinToString()})")
      else -> fail("unsupported ${mapPosition?.let { "map $it " }}type $type")
    } ?: fail("$value is not a valid $type")
  }
