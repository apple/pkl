/*
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
package org.pkl.cli

import org.pkl.commons.cli.CliBaseOptions

/** Configuration options for [CliEvaluator]. */
data class CliEvaluatorOptions(
  /** Base options shared between CLI commands. */
  val base: CliBaseOptions,

  /**
   * The file path where the output file is placed. If multiple source modules are given,
   * placeholders can be used to map them to different output files. If multiple modules are mapped
   * to the same output file, their outputs are concatenated. Currently, the only available
   * concatenation strategy is to separate outputs with `---`, as in a YAML stream.
   *
   * The following placeholders are supported:
   * - `%{moduleDir}` The directory path of the module, relative to the working directory. Only
   *   available when evaluating file-based modules.
   * - `%{moduleName}` The simple module name as inferred from the module URI. For hierarchical
   *   URIs, this is the last path segment without file extension.
   * - `%{outputFormat}` The requested output format. Only available if `outputFormat` is non-null.
   *
   * If [CliBaseOptions.workingDir] corresponds to a file system path, relative output paths are
   * resolved against that path. Otherwise, relative output paths are not allowed.
   *
   * If `null`, output is written to the console.
   */
  val outputPath: String? = null,

  /**
   * The output format to generate.
   *
   * The default output renderer for a module supports the following formats:
   * - `"json"`
   * - `"jsonnet"`
   * - `"pcf"` (default)
   * - `"plist"`
   * - `"properties"`
   * - `"textproto"`
   * - `"xml"`
   * - `"yaml"`
   */
  val outputFormat: String? = null,

  /** The separator to use when multiple module outputs are written to the same location. */
  val moduleOutputSeparator: String = "---",

  /**
   * The directory where a module's output files are placed.
   *
   * Setting this option causes Pkl to evaluate `output.files` instead of `output.text`, and write
   * files using each entry's key as the file path relative to [multipleFileOutputPath], and each
   * value's `text` property as the file's contents.
   */
  val multipleFileOutputPath: String? = null,

  /**
   * The expression to evaluate within the module.
   *
   * If set, the said expression is evaluated under the context of the enclosing module.
   *
   * If unset, the module's `output.text` property evaluated.
   */
  val expression: String = "output.text",
) {

  companion object {
    val defaults = CliEvaluatorOptions(CliBaseOptions())
  }
}
