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
package org.pkl.cli

import java.io.Writer
import org.pkl.commons.cli.CliCommand
import org.pkl.commons.createParentDirectories
import org.pkl.commons.writeString
import org.pkl.core.ModuleSource
import org.pkl.core.module.ModuleKeyFactories

class CliImportAnalyzer(
  private val options: CliImportAnalyzerOptions,
  private val consoleWriter: Writer = System.out.writer()
) : CliCommand(options.base) {

  override fun doRun() {
    val rendered = render()
    if (options.outputPath != null) {
      options.outputPath.createParentDirectories()
      options.outputPath.writeString(rendered)
    } else {
      consoleWriter.write(rendered)
      consoleWriter.flush()
    }
  }

  // language=pkl
  private val sourceModule =
    ModuleSource.text(
      """
    import "pkl:analyze"

    local importStrings = read*("prop:pkl.analyzeImports.**").toMap().values.toSet()

    output {
      value = analyze.importGraph(importStrings)
      renderer {
        converters {
          [Map] = (it) -> it.toMapping()
          [Set] = (it) -> it.toListing()
        }
      }
    }
  """
        .trimIndent()
    )

  private fun render(): String {
    val builder = evaluatorBuilder().setOutputFormat(options.outputFormat)
    try {
      return builder
        .apply {
          for ((idx, sourceModule) in options.base.normalizedSourceModules.withIndex()) {
            addExternalProperty("pkl.analyzeImports.$idx", sourceModule.toString())
          }
        }
        .build()
        .use { it.evaluateOutputText(sourceModule) }
    } finally {
      ModuleKeyFactories.closeQuietly(builder.moduleKeyFactories)
    }
  }
}
