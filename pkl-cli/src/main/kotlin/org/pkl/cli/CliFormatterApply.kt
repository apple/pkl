/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliException

class CliFormatterApply(cliBaseOptions: CliBaseOptions, path: Path, private val silent: Boolean) :
  CliFormatterCommand(cliBaseOptions, path) {

  override fun doRun() {
    var status = 0

    for (path in paths()) {
      val contents = Files.readString(path)
      val (formatted, stat) = format(path, contents)
      status = if (status == 0) stat else status
      if (stat != 0 || contents == formatted) continue
      if (!silent) {
        consoleWriter.write(path.toAbsolutePath().toString())
        consoleWriter.appendLine()
        consoleWriter.flush()
      }
      try {
        path.writeText(formatted, Charsets.UTF_8)
      } catch (e: IOException) {
        consoleWriter.write("Could not overwrite `$path`: ${e.message}")
        consoleWriter.appendLine()
        consoleWriter.flush()
        status = 1
      }
    }
    if (status != 0) {
      throw CliException("Formatting violations found.", status)
    }
  }
}
