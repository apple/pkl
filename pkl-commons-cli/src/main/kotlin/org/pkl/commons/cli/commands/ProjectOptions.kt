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
package org.pkl.commons.cli.commands

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path

/**
 * Options related to projects for CLI commands that are related to normal evaluation (`pkl eval`,
 * `pkl test`).
 */
class ProjectOptions : OptionGroup() {
  val projectDir: Path? by
    option(
        names = arrayOf("--project-dir"),
        metavar = "<path>",
        help =
          "The project directory to use for this command. By default, searches up from the working directory for a PklProject file."
      )
      .single()
      .path()

  val omitProjectSettings: Boolean by
    option(
        names = arrayOf("--omit-project-settings"),
        help = "Ignores evaluator settings set in the PklProject file."
      )
      .single()
      .flag(default = false)

  val noProject: Boolean by
    option(
        names = arrayOf("--no-project"),
        help = "Disables loading settings and dependencies from the PklProject file."
      )
      .single()
      .flag(default = false)
}
