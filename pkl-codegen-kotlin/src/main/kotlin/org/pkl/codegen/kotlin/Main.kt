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
@file:JvmName("Main")

package org.pkl.codegen.kotlin

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.cliMain
import org.pkl.commons.cli.commands.ModulesCommand
import org.pkl.commons.cli.commands.installCommonOptions
import org.pkl.commons.toPath
import org.pkl.core.Release

/** Main method for the Kotlin code generator CLI. */
internal fun main(args: Array<String>) {
  cliMain { PklKotlinCodegenCommand().main(args) }
}

val helpLink = "${Release.current().documentation.homepage}kotlin-binding/codegen.html#cli"

class PklKotlinCodegenCommand : ModulesCommand(name = "pkl-codegen-kotlin", helpLink = helpLink) {
  private val defaults = CliKotlinCodeGeneratorOptions(CliBaseOptions(), "".toPath())

  private val outputDir: Path by
    option(
        names = arrayOf("-o", "--output-dir"),
        metavar = "path",
        help = "The directory where generated source code is placed.",
      )
      .path()
      .default(defaults.outputDir)

  private val indent: String by
    option(
        names = arrayOf("--indent"),
        metavar = "chars",
        help = "The characters to use for indenting generated source code.",
      )
      .default(defaults.indent)

  private val generateKdoc: Boolean by
    option(
        names = arrayOf("--generate-kdoc"),
        help = "Whether to preserve Pkl doc comments by generating corresponding KDoc comments.",
      )
      .flag()

  private val generateSpringboot: Boolean by
    option(
        names = arrayOf("--generate-spring-boot"),
        help = "Whether to generate config classes for use with Spring Boot.",
      )
      .flag()

  private val implementSerializable: Boolean by
    option(
        names = arrayOf("--implement-serializable"),
        help = "Whether to generate classes that implement java.io.Serializable.",
      )
      .flag()

  private val renames: Map<String, String> by
    option(
        names = arrayOf("--rename"),
        metavar = "old_name=new_name",
        help =
          """
            Replace a prefix in the names of the generated Kotlin classes (repeatable).
            By default, the names of generated classes are derived from the Pkl module names.
            With this option, you can override or modify the default names, renaming entire
            classes or just their packages.
          """
            .trimIndent(),
      )
      .associate()

  override val helpString: String = "Generate Kotlin classes and interfaces from Pkl module(s)"

  override fun run() {
    val options =
      CliKotlinCodeGeneratorOptions(
        base = baseOptions.baseOptions(modules, projectOptions),
        outputDir = outputDir,
        indent = indent,
        generateKdoc = generateKdoc,
        generateSpringBootConfig = generateSpringboot,
        implementSerializable = implementSerializable,
        renames = renames,
      )
    CliKotlinCodeGenerator(options).run()
  }

  init {
    installCommonOptions()
  }
}
