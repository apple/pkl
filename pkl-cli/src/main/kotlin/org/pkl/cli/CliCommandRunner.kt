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
import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.transform.TransformContext
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.restrictTo
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.writeBytes
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliCommand
import org.pkl.commons.cli.CliException
import org.pkl.commons.currentWorkingDir
import org.pkl.core.Closeables
import org.pkl.core.CommandSpec
import org.pkl.core.EvaluatorBuilder
import org.pkl.core.FileOutput
import org.pkl.core.ModuleSource.uri
import org.pkl.core.PklBugException
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
        val root = SynthesizedRunCommand(spec, this)
        root.main(args)
      }
    }
  }

  class SynthesizedRunCommand(private val spec: CommandSpec, private val runner: CliCommandRunner) :
    CliktCommand(name = spec.name) {
    init {
      for (flag in spec.flags) {
        val names =
          if (flag.shortName == null) arrayOf("--${flag.name}")
          else arrayOf("--${flag.name}", "-${flag.shortName}")
        var opt = option(names = names, help = spec.description ?: "", hidden = flag.hide)
        val type = flag.type
        val opt1 =
          if (flag.parse != null) {
            when (type) {
              is CommandSpec.OptionType.Primitive,
              is CommandSpec.OptionType.Enum ->
                opt.parse(flag.parse!!).frobnicate(type.isRequired, flag.defaultValue)
              is CommandSpec.OptionType.Collection ->
                opt
                  .parse(flag.parse!!)
                  .frobnicate(
                    type.isRequired,
                    flag.defaultValue,
                    true,
                    type.type == CommandSpec.OptionType.Collection.Type.SET,
                  )
              is CommandSpec.OptionType.Map -> opt.associate { it } // TODO
            }
          } else {
            when (type) {
              is CommandSpec.OptionType.Primitive ->
                opt.primitive(type, flag.name, flag.defaultValue)
              is CommandSpec.OptionType.Collection ->
                when (val valueType = type.valueType) {
                  is CommandSpec.OptionType.Primitive ->
                    opt.primitive(
                      valueType,
                      flag.name,
                      flag.defaultValue,
                      true,
                      type.type == CommandSpec.OptionType.Collection.Type.SET,
                      type.isRequired,
                    )
                  is CommandSpec.OptionType.Enum ->
                    opt
                      .choice(valueType.choices.associateBy { it })
                      .frobnicate(
                        type.isRequired,
                        flag.defaultValue,
                        true,
                        type.type == CommandSpec.OptionType.Collection.Type.SET,
                      )
                  else -> throw RuntimeException("unexpected collection flag value type $valueType")
                }
              is CommandSpec.OptionType.Enum ->
                opt
                  .choice(type.choices.associateBy { it })
                  .frobnicate(type.isRequired, flag.defaultValue)
              is CommandSpec.OptionType.Map -> TODO()
            }
          }
        registerOption(opt1)
      }

      for (arg in spec.arguments) {
        val arg0 = argument(arg.name, arg.description ?: "")
        val arg1 =
          when (val type = arg.type) {
            is CommandSpec.OptionType.Primitive -> arg0.primitive(type)
            is CommandSpec.OptionType.Enum ->
              arg0.choice(type.choices.associateBy { it }).frobnicate(type.isRequired)
            is CommandSpec.OptionType.Collection ->
              when (val valueType = type.valueType) {
                is CommandSpec.OptionType.Primitive ->
                  arg0.primitive(
                    valueType,
                    true,
                    type.type == CommandSpec.OptionType.Collection.Type.SET,
                    type.isRequired,
                  )
                is CommandSpec.OptionType.Enum ->
                  arg0
                    .choice(valueType.choices.associateBy { it })
                    .frobnicate(
                      type.isRequired,
                      true,
                      type.type == CommandSpec.OptionType.Collection.Type.SET,
                    )
                else ->
                  throw RuntimeException("unexpected collection argument value type $valueType")
              }
            else -> throw RuntimeException("unexpected argument type $type")
          }
        registerArgument(arg1)
      }

      subcommands(spec.subcommands.map { SynthesizedRunCommand(it, runner) })
    }

    private fun <InT> NullableOption<InT, InT>.required(
      required: Boolean
    ): NullableOption<InT, InT> =
      if (required)
        transformAll(showAsRequired = true) { it.lastOrNull() ?: throw MissingOption(option) }
      else this

    private fun <InT> NullableOption<InT, InT>.frobnicate(
      required: Boolean,
      default: Any?,
      multiple: Boolean = false,
      multipleSet: Boolean = false,
    ) =
      transformAll(defaultForHelp = default?.toString(), showAsRequired = required) {
        return@transformAll when {
          multiple || multipleSet ->
            when {
              it.isEmpty() && required -> throw MissingOption(option)
              it.isEmpty() && !required -> default
              else -> if (multipleSet) HashSet(it) else it
            }
          default != null -> it.lastOrNull() ?: default
          required -> it.lastOrNull() ?: throw MissingOption(option)
          else -> it
        }
      }

    private fun <InT> ProcessedArgument<InT, InT>.frobnicate(
      required: Boolean,
      multiple: Boolean = false,
      multipleSet: Boolean = false,
    ) =
      if (multiple && !multipleSet) transformAll(nvalues = -1, required = required) { it }
      else if (multipleSet) transformAll(nvalues = -1, required = required) { it.toSet() } else this

    fun NullableOption<String, String>.parse(
      parseFunction: CommandSpec.ParseOptionFunction,
      metavar: Context.() -> String = { localization.defaultMetavar() },
      completionCandidates: CompletionCandidates? = null,
    ): NullableOption<Any, Any> = convert(metavar, completionCandidates) { parseFunction.parse(it) }

    private fun RawOption.primitive(
      type: CommandSpec.OptionType.Primitive,
      name: String,
      default: Any?,
      multiple: Boolean = false,
      multipleSet: Boolean = false,
      required: Boolean = type.isRequired,
    ): GroupableOption =
      when (type.type) {
        CommandSpec.OptionType.Primitive.Type.NUMBER ->
          number().frobnicate(required, default, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.FLOAT ->
          double()
            .copy(metavarGetter = { "float" })
            .frobnicate(required, default, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.INT ->
          long()
            .copy(metavarGetter = { "int" })
            .frobnicate(required, default, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.INT8 ->
          long()
            .restrictTo(-128L..<128)
            .copy(metavarGetter = { "int8" })
            .frobnicate(required, default, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.INT16 ->
          long()
            .restrictTo(Short.MIN_VALUE.toLong()..Short.MAX_VALUE)
            .copy(metavarGetter = { "int16" })
            .frobnicate(required, default, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.INT32 ->
          long()
            .restrictTo(Integer.MIN_VALUE.toLong()..Integer.MAX_VALUE)
            .copy(metavarGetter = { "int32" })
            .frobnicate(required, default, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.UINT ->
          long()
            .restrictTo(0L..Long.MAX_VALUE)
            .copy(metavarGetter = { "uint" })
            .frobnicate(required, default, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.UINT8 ->
          long()
            .restrictTo(0L..<255)
            .copy(metavarGetter = { "uint8" })
            .frobnicate(required, default, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.UINT16 ->
          long()
            .restrictTo(0L..<256L * 256)
            .copy(metavarGetter = { "uint16" })
            .frobnicate(required, default, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.UINT32 ->
          long()
            .restrictTo(0L..<256L * 256 * 256 * 256)
            .copy(metavarGetter = { "uint32" })
            .frobnicate(required, default, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.BOOLEAN ->
          if (
            multiple || multipleSet
          ) // FIXME do we always want to use flag when not multiple? this is a little inconsistent
           boolean().frobnicate(required, default, multiple, multipleSet)
          else if (default == null) nullableFlag("--no-${name}").required(required)
          else flag("--no-${name}", default = default as Boolean)
        CommandSpec.OptionType.Primitive.Type.STRING ->
          frobnicate(required, default, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.CHAR ->
          copy(metavarGetter = { "char" })
            .frobnicate(required, default, multiple, multipleSet)
            .validate {
              when (it) {
                is String -> if (it.length == 1) Unit else fail("expected one character")
                is List<*> ->
                  if (it.all { char -> (char as String).length == 1 }) Unit
                  else fail("expected one character")
                else -> throw PklBugException.unreachableCode()
              }
            }
      }

    private fun RawArgument.primitive(
      type: CommandSpec.OptionType.Primitive,
      multiple: Boolean = false,
      multipleSet: Boolean = false,
      required: Boolean = type.isRequired,
    ): Argument =
      when (type.type) {
        CommandSpec.OptionType.Primitive.Type.NUMBER ->
          number().frobnicate(required, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.FLOAT ->
          double().frobnicate(required, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.INT ->
          long().frobnicate(required, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.INT8 ->
          long().restrictTo(-128L..<128).frobnicate(required, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.INT16 ->
          long()
            .restrictTo(Short.MIN_VALUE.toLong()..Short.MAX_VALUE)
            .frobnicate(required, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.INT32 ->
          long()
            .restrictTo(Integer.MIN_VALUE.toLong()..Integer.MAX_VALUE)
            .frobnicate(required, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.UINT ->
          long().restrictTo(0L..Long.MAX_VALUE).frobnicate(required, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.UINT8 ->
          long().restrictTo(0L..<255).frobnicate(required, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.UINT16 ->
          long().restrictTo(0L..<256L * 256).frobnicate(required, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.UINT32 ->
          long().restrictTo(0L..<256L * 256 * 256 * 256).frobnicate(required, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.BOOLEAN ->
          boolean().frobnicate(required, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.STRING -> frobnicate(required, multiple, multipleSet)
        CommandSpec.OptionType.Primitive.Type.CHAR ->
          frobnicate(required, multiple, multipleSet).validate { (it as String).length == 1 }
      }

    private val convertPrimitive:
      TransformContext.(value: String, type: CommandSpec.OptionType.Primitive) -> Any =
      { value, type ->
        when (type.type) {
          CommandSpec.OptionType.Primitive.Type.NUMBER ->
            value.toIntOrNull() ?: value.toFloatOrNull() ?: fail("$value is not a valid number")

          CommandSpec.OptionType.Primitive.Type.FLOAT -> TODO()
          CommandSpec.OptionType.Primitive.Type.INT -> TODO()
          CommandSpec.OptionType.Primitive.Type.INT8 -> TODO()
          CommandSpec.OptionType.Primitive.Type.INT16 -> TODO()
          CommandSpec.OptionType.Primitive.Type.INT32 -> TODO()
          CommandSpec.OptionType.Primitive.Type.UINT -> TODO()
          CommandSpec.OptionType.Primitive.Type.UINT8 -> TODO()
          CommandSpec.OptionType.Primitive.Type.UINT16 -> TODO()
          CommandSpec.OptionType.Primitive.Type.UINT32 -> TODO()
          CommandSpec.OptionType.Primitive.Type.BOOLEAN -> TODO()
          CommandSpec.OptionType.Primitive.Type.STRING -> TODO()
          CommandSpec.OptionType.Primitive.Type.CHAR -> TODO()
        }
      }

    override val invokeWithoutSubcommand = true

    override fun help(context: Context): String = spec.description ?: ""

    override fun run() {
      val opts =
        registeredOptions()
          .mapNotNull {
            if (it.names.contains("--help")) return@mapNotNull null
            val opt = it as? OptionWithValues<*, *, *> ?: return@mapNotNull null
            if ((opt.value as? List<*>)?.isEmpty() ?: false) return@mapNotNull  null
            return@mapNotNull  it.names.first().trimStart('-') to opt.value
          }
          .toMap() + registeredArguments()
            .mapNotNull { it as? ArgumentDelegate<*> }
            .associateBy({ it.name }, { it.value })

      val state = spec.apply.apply(opts, currentContext.obj as CommandSpec.State?)
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

private val numberConversion: TransformContext.(String) -> Number = {
  it.toLongOrNull() ?: it.toFloatOrNull() ?: fail("$it is not a valid number")
}

fun RawOption.number(acceptsValueWithoutName: Boolean = false): NullableOption<Number, Number> =
  convert({ "number" }, conversion = numberConversion)
    .copy(acceptsNumberValueWithoutName = acceptsValueWithoutName)

fun RawArgument.number(): ProcessedArgument<Number, Number> = convert(conversion = numberConversion)
