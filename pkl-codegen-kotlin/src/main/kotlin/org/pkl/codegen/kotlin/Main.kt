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
@file:JvmName("Main")

package org.pkl.codegen.kotlin

import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.cliMain
import org.pkl.commons.cli.commands.ModulesCommand
import org.pkl.commons.toPath
import org.pkl.core.Release

/** Main method for the Kotlin code generator CLI. */
internal fun main(args: Array<String>) {
    cliMain { PklKotlinCodegenCommand().main(args) }
}

class PklKotlinCodegenCommand :
    ModulesCommand(
        name = "pkl-codegen-kotlin",
        helpLink = Release.current().documentation().homepage(),
    ) {

    private val defaults = CliKotlinCodeGeneratorOptions(CliBaseOptions(), "".toPath())

    private val outputDir: Path by
        option(
                names = arrayOf("-o", "--output-dir"),
                metavar = "<path>",
                help = "The directory where generated source code is placed."
            )
            .path()
            .default(defaults.outputDir)

    private val indent: String by
        option(
                names = arrayOf("--indent"),
                metavar = "<chars>",
                help = "The characters to use for indenting generated source code."
            )
            .default(defaults.indent)

    private val generateKdoc: Boolean by
        option(
                names = arrayOf("--generate-kdoc"),
                help =
                    "Whether to generate Kdoc based on doc comments " +
                        "for Pkl modules, classes, and properties."
            )
            .flag()

    private val generateSpringboot: Boolean by
        option(
                names = arrayOf("--generate-spring-boot"),
                help = "Whether to generate config classes for use with Spring boot."
            )
            .flag()

    private val implementSerializable: Boolean by
        option(
                names = arrayOf("--implement-serializable"),
                help = "Whether to make generated classes implement java.io.Serializable"
            )
            .flag()

    override fun run() {
        val options =
            CliKotlinCodeGeneratorOptions(
                base = baseOptions.baseOptions(modules, projectOptions),
                outputDir = outputDir,
                indent = indent,
                generateKdoc = generateKdoc,
                generateSpringBootConfig = generateSpringboot,
                implementSerializable = implementSerializable
            )
        CliKotlinCodeGenerator(options).run()
    }
}
