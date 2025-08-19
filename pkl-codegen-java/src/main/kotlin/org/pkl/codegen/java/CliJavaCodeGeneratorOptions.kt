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

  /** Whether to add a `@Generated` annotation to the types to be generated. */
  val addGeneratedAnnotation: Boolean = false,

  /**
   * Whether to generate public getter methods and private/protected fields instead of public
   * fields.
   */
  val generateGetters: Boolean = false,

  /** Whether to preserve Pkl doc comments by generating corresponding Javadoc comments. */
  val generateJavadoc: Boolean = false,

  /** Whether to generate config classes for use with Spring Boot. */
  val generateSpringBootConfig: Boolean = false,

  /**
   * Fully qualified name of the annotation type to use for annotating constructor parameters with
   * their name.
   *
   * The specified annotation type must have a `value` parameter of type [java.lang.String] or the
   * generated code may not compile.
   *
   * If set to `null`, constructor parameters are not annotated. The default value is `null` if
   * [generateSpringBootConfig] is `true` and `"org.pkl.config.java.mapper.Named"` otherwise.
   */
  val paramsAnnotation: String? =
    if (generateSpringBootConfig) null else "org.pkl.config.java.mapper.Named",

  /**
   * Fully qualified name of the annotation type to use for annotating non-null types.
   *
   * The specified annotation type must have a [java.lang.annotation.Target] of
   * [java.lang.annotation.ElementType.TYPE_USE] or the generated code may not compile. If set to
   * `null`, [org.pkl.config.java.mapper.NonNull] will be used.
   */
  val nonNullAnnotation: String? = null,

  /** Whether to generate classes that implement [java.io.Serializable]. */
  val implementSerializable: Boolean = false,

  /**
   * A rename mapping for class names.
   *
   * When you need to have Java class or package names different from the default names derived from
   * Pkl module names, you can define a rename mapping, where the key is a prefix of the original
   * Pkl module name, and the value is the desired replacement.
   */
  val renames: Map<String, String> = emptyMap(),
) {
  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("deprecated without replacement")
  fun toJavaCodegenOptions() = toJavaCodeGeneratorOptions()

  internal fun toJavaCodeGeneratorOptions() =
    JavaCodeGeneratorOptions(
      indent,
      addGeneratedAnnotation,
      generateGetters,
      generateJavadoc,
      generateSpringBootConfig,
      paramsAnnotation,
      nonNullAnnotation,
      implementSerializable,
      renames,
    )
}
