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
import org.pkl.commons.cli.*
import org.pkl.core.EvaluatorBuilder
import org.pkl.core.ModuleSource.uri
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.stdlib.test.report.JUnitReport
import org.pkl.core.stdlib.test.report.SimpleReport
import org.pkl.core.util.ErrorMessages

class CliTestRunner
@JvmOverloads
constructor(
  private val options: CliBaseOptions,
  private val testOptions: CliTestOptions,
  private val consoleWriter: Writer = System.out.writer(),
  private val errWriter: Writer = System.err.writer()
) : CliCommand(options) {

  override fun doRun() {
    val builder = evaluatorBuilder()
    try {
      evalTest(builder)
    } finally {
      ModuleKeyFactories.closeQuietly(builder.moduleKeyFactories)
    }
  }

  private fun evalTest(builder: EvaluatorBuilder) {
    val sources =
      options.normalizedSourceModules.ifEmpty { project?.tests?.map { it.toUri() } }
        ?:
        // keep in sync with error message thrown by clikt
        throw CliException(
          """
          Usage: pkl test [OPTIONS] <modules>...
          
          Error: Missing argument "<modules>"
        """
            .trimIndent()
        )

    val evaluator = builder.build()
    evaluator.use {
      var failed = false
      val moduleNames = mutableSetOf<String>()
      for (moduleUri in sources) {
        try {
          val results = evaluator.evaluateTest(uri(moduleUri), testOptions.overwrite)
          if (!failed) {
            failed = results.failed()
          }
          SimpleReport().report(results, consoleWriter)
          consoleWriter.flush()
          val junitDir = testOptions.junitDir
          if (junitDir != null) {
            junitDir.toFile().mkdirs()
            val moduleName = "${results.moduleName}.xml"
            if (moduleName in moduleNames) {
              throw RuntimeException(
                """
                  Cannot generate JUnit report for $moduleUri.
                  A report with the same name was already generated.
                  
                  To fix, provide a different name for this module by adding a module header.
                """
                  .trimIndent()
              )
            }
            moduleNames += moduleName
            JUnitReport().reportToPath(results, junitDir.resolve(moduleName))
          }
        } catch (ex: Exception) {
          errWriter.appendLine("Error evaluating module ${moduleUri.path}:")
          errWriter.write(ex.message ?: "")
          errWriter.write(ex.stackTraceToString())
          if (moduleUri != sources.last()) {
            errWriter.appendLine()
          }
          errWriter.flush()
          failed = true
        }
      }
      if (failed) {
        throw CliTestException(ErrorMessages.create("testsFailed"))
      }
    }
  }
}
