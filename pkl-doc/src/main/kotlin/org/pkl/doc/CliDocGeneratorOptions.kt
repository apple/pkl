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
package org.pkl.doc

import java.nio.file.Path
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.resolveSafely

/** Configuration options for [CliDocGenerator]. */
data class CliDocGeneratorOptions
@JvmOverloads
constructor(
  /** Base options shared between CLI commands. */
  val base: CliBaseOptions,

  /** The directory where generated documentation is placed. */
  val outputDir: Path,

  /**
   * Internal option only used for testing.
   *
   * Generates source URLs with fixed line numbers `#L123-L456` to avoid churn in expected output
   * files (e.g., when stdlib line numbers change).
   */
  val isTestMode: Boolean = false,

  /**
   * Determines how to create the "current" directory which contains documentation for the latest
   * version of the package.
   *
   * [DocGenerator.CurrentDirectoryMode.SYMLINK] will make the current directory into a symlink to
   * the actual version directory. [DocGenerator.CurrentDirectoryMode.COPY], however, will create a
   * full copy instead.
   */
  var currentDirectoryMode: DocGenerator.CurrentDirectoryMode =
    DocGenerator.CurrentDirectoryMode.SYMLINK
) {
  /** [outputDir] after undergoing normalization. */
  val normalizedOutputDir: Path = base.normalizedWorkingDir.resolveSafely(outputDir)
}
