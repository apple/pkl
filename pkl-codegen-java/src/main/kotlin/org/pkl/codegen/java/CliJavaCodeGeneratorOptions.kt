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
package org.pkl.codegen.java

import java.nio.file.Path
import org.pkl.commons.cli.CliBaseOptions

/** Configuration options for [CliJavaCodeGenerator]. */
data class CliJavaCodeGeneratorOptions(
  /** Base options shared between CLI commands. */
  val base: CliBaseOptions,

  /** The directory where generated source code is placed. */
  val outputDir: Path,

  /** The characters to use for indenting generated source code. */
  val indent: String = "  ",

  /**
   * Whether to generate public getter methods and private/protected fields instead of public
   * fields.
   */
  val generateGetters: Boolean = false,

  /** Whether to generate Javadoc based on doc comments for Pkl modules, classes, and properties. */
  val generateJavadoc: Boolean = false,

  /** Whether to generate config classes for use with Spring Boot. */
  val generateSpringBootConfig: Boolean = false,

  /**
   * Fully qualified name of the annotation to use on constructor parameters. If this options is not
   * set, [org.pkl.config.java.mapper.Named] will be used.
   */
  val paramsAnnotation: String? = null,

  /**
   * Fully qualified name of the annotation to use on non-null properties. If this option is not
   * set, [org.pkl.config.java.mapper.NonNull] will be used.
   */
  val nonNullAnnotation: String? = null,

  /** Whether to make generated classes implement [java.io.Serializable] */
  val implementSerializable: Boolean = false,
  
  /**
   * A mapping of packages.
   * 
   * When you need to have Java package names different from the default package
   * names derived from Pkl module names, you can define a package mapping, where the key
   * is the original Pkl module name, and the value is its replacement.
   */
  val packageMapping: Map<String, String> = emptyMap()
) {
  fun toJavaCodegenOptions() =
    JavaCodegenOptions(
      indent,
      generateGetters,
      generateJavadoc,
      generateSpringBootConfig,
      paramsAnnotation,
      nonNullAnnotation,
      implementSerializable,
      packageMapping
    )
}
