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
package org.pkl.codegen.java

import java.io.IOException
import org.pkl.commons.cli.CliCommand
import org.pkl.commons.cli.CliException
import org.pkl.commons.createParentDirectories
import org.pkl.commons.writeString
import org.pkl.core.ModuleSource
import org.pkl.core.module.ModuleKeyFactories

/** API for the Java code generator CLI. */
class CliJavaCodeGenerator(private val options: CliJavaCodeGeneratorOptions) :
  CliCommand(options.base) {

  override fun doRun() {
    val builder = evaluatorBuilder()
    try {
      builder.build().use { evaluator ->
        for (moduleUri in options.base.normalizedSourceModules) {
          val schema = evaluator.evaluateSchema(ModuleSource.uri(moduleUri))
          val codeGenerator = JavaCodeGenerator(schema, options.toJavaCodegenOptions())
          try {
            for ((fileName, fileContents) in codeGenerator.output) {
              val outputFile = options.outputDir.resolve(fileName)
              try {
                outputFile.createParentDirectories().writeString(fileContents)
              } catch (e: IOException) {
                throw CliException("I/O error writing file `$outputFile`.\nCause: ${e.message}")
              }
            }
          } catch (e: JavaCodeGeneratorException) {
            throw CliException(e.message!!)
          }
        }
      }
    } finally {
      ModuleKeyFactories.closeQuietly(builder.moduleKeyFactories)
    }
  }
}
