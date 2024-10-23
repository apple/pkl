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
package org.pkl.commons.cli.commands

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import org.pkl.commons.cli.CliTestOptions

class TestOptions : OptionGroup() {
  private val junitReportDir: Path? by
    option(
        names = arrayOf("--junit-reports"),
        metavar = "<dir>",
        help = "Directory where to store JUnit reports."
      )
      .path()

  private val overwrite: Boolean by
    option(names = arrayOf("--overwrite"), help = "Force generation of expected examples.").flag()

  val cliTestOptions: CliTestOptions by lazy { CliTestOptions(junitReportDir, overwrite) }
}
