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
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess
import org.pkl.commons.cli.CliException
import org.pkl.formatter.Formatter

class CliFormatterRunner(
  private val check: Boolean,
  private val list: Boolean,
  private val overwrite: Boolean,
  private val paths: List<Path>,
) {
  private val writer = System.out.writer()

  fun run() {
    when {
      list -> {
        for (path in paths) {
          val contents = path.readText()
          val formatted = Formatter().format(contents)
          var status = 0
          if (contents != formatted) {
            writer.appendLine(path.toAbsolutePath().toString())
            status = 1
          }
          exitProcess(status)
        }
      }
      overwrite -> {
        for (path in paths) {
          val formatted = Formatter().format(path)
          try {
            path.writeText(formatted, Charsets.UTF_8)
          } catch (e: IOException) {
            throw CliException("Could not overwrite `$path`: ${e.message}")
          }
        }
      }
      check -> throw CliException("`check` option not yet supported.")
    }
  }
}
