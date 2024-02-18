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
package org.pkl.codegen.kotlin

import java.nio.file.Path
import org.pkl.commons.cli.CliBaseOptions

/** Configuration options for [CliKotlinCodeGenerator]. */
data class CliKotlinCodeGeneratorOptions(
  /** Base options shared between CLI commands. */
  val base: CliBaseOptions,

  /** The directory where generated source code is placed. */
  val outputDir: Path,

  /** The characters to use for indenting generated source code. */
  val indent: String = "  ",

  /** Kotlin package to use for generated code; if none is provided, the root package is used. */
  val kotlinPackage: String = "",

  /** Whether to generate Kdoc based on doc comments for Pkl modules, classes, and properties. */
  val generateKdoc: Boolean = false,

  /** Whether to generate config classes for use with Spring Boot. */
  val generateSpringBootConfig: Boolean = false,

  /** Whether to make generated classes implement [java.io.Serializable] */
  val implementSerializable: Boolean = false,

  /** Whether to annotate generated data classes with [kotlinx.serialization.Serializable] */
  val implementKSerializable: Boolean = false
) {
  fun toKotlinCodegenOptions(): KotlinCodegenOptions =
    KotlinCodegenOptions(
      indent = indent,
      generateKdoc = generateKdoc,
      generateSpringBootConfig = generateSpringBootConfig,
      implementSerializable = implementSerializable,
      kotlinPackage = kotlinPackage,
      implementKSerializable = implementKSerializable,
    )
}
