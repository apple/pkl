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

package org.pkl.codegen.java

import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.cliMain
import org.pkl.commons.cli.commands.ModulesCommand
import org.pkl.commons.toPath
import org.pkl.core.Release

/** Main method for the Java code generator CLI. */
internal fun main(args: Array<String>) {
  cliMain { PklJavaCodegenCommand.main(args) }
}

object PklJavaCodegenCommand :
  ModulesCommand(
    name = "pkl-codegen-java",
    helpLink = Release.current().documentation().homepage(),
  ) {

  private val defaults = CliJavaCodeGeneratorOptions(CliBaseOptions(), "".toPath())

  private val outputDir: Path by
    option(
        names = arrayOf("-o", "--output-dir"),
        metavar = "<path>",
        help = "The directory where generated source code is placed.",
      )
      .path()
      .default(defaults.outputDir)

  private val indent: String by
    option(
        names = arrayOf("--indent"),
        metavar = "<chars>",
        help = "The characters to use for indenting generated source code.",
      )
      .default(defaults.indent)

  private val generateGetters: Boolean by
    option(
        names = arrayOf("--generate-getters"),
        help =
          "Whether to generate public getter methods and " +
            "private final fields instead of public final fields.",
      )
      .flag()

  private val generateJavadoc: Boolean by
    option(
        names = arrayOf("--generate-javadoc"),
        help = "Whether to preserve Pkl doc comments by generating corresponding Javadoc comments.",
      )
      .flag()

  private val generateSpringBoot: Boolean by
    option(
        names = arrayOf("--generate-spring-boot"),
        help = "Whether to generate config classes for use with Spring Boot.",
      )
      .flag()

  private val paramsAnnotation: String by
    option(
        names = arrayOf("--params-annotation"),
        help =
          "Fully qualified name of the annotation type to use for annotating constructor parameters with their name.",
      )
      .defaultLazy(
        "`none` if `--generate-spring-boot` is set, `org.pkl.config.java.mapper.Named` otherwise"
      ) {
        if (generateSpringBoot) "none" else "org.pkl.config.java.mapper.Named"
      }

  private val nonNullAnnotation: String? by
    option(
      names = arrayOf("--non-null-annotation"),
      help =
        """
      Fully qualified name of the annotation type to use for annotating non-null types.
      The specified annotation type must be annotated with `@java.lang.annotation.Target(ElementType.TYPE_USE)`
      or the generated code may not compile.
    """
          .trimIndent(),
    )

  private val implementSerializable: Boolean by
    option(
        names = arrayOf("--implement-serializable"),
        help = "Whether to generate classes that implement java.io.Serializable.",
      )
      .flag()

  private val renames: Map<String, String> by
    option(
        names = arrayOf("--rename"),
        metavar = "<old_name=new_name>",
        help =
          """
            Replace a prefix in the names of the generated Java classes (repeatable).
            By default, the names of generated classes are derived from the Pkl module names.
            With this option, you can override or modify the default names, renaming entire
            classes or just their packages.
          """
            .trimIndent(),
      )
      .associate()

  override fun run() {
    val options =
      CliJavaCodeGeneratorOptions(
        base = baseOptions.baseOptions(modules, projectOptions),
        outputDir = outputDir,
        indent = indent,
        generateGetters = generateGetters,
        generateJavadoc = generateJavadoc,
        generateSpringBootConfig = generateSpringBoot,
        paramsAnnotation = if (paramsAnnotation == "none") null else paramsAnnotation,
        nonNullAnnotation = nonNullAnnotation,
        implementSerializable = implementSerializable,
        renames = renames,
      )
    CliJavaCodeGenerator(options).run()
  }
}
