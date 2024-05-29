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
package org.pkl.codegen.kotlin

import javax.script.ScriptEngineManager
import javax.script.ScriptException
import kotlin.reflect.KClass
import kotlin.text.RegexOption.MULTILINE
import kotlin.text.RegexOption.UNIX_LINES
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback

class CompilationFailedException(msg: String?, cause: Throwable? = null) :
  RuntimeException(msg, cause)

object InMemoryKotlinCompiler {
  init {
    // prevent "Unable to load JNA library" warning
    setIdeaIoUseFallback()
  }

  // Implementation notes:
  // * all [sourceFiles] are currently combined into a single file
  // * implementation makes assumptions about structure of generated source files
  fun compile(sourceFiles: Map<String, String>): Map<String, KClass<*>> {
    fun String.findClasses(
      prefix: String = "",
      nameGroup: Int = 2,
      bodyGroup: Int = 4,
      regex: String =
        "^(data |open |enum )?class\\s+(\\w+) *(\\([^)]*\\))?.*$\\n((^  .*\\n|^$\\n)*)",
      transform: (String, String) -> Sequence<Pair<String, String>> = { name, body ->
        sequenceOf(Pair(name, prefix + name)) + body.findClasses("$prefix$name.")
      }
    ): Sequence<Pair<String, String>> = // (simpleName1, qualifiedName1), ...
    Regex(regex, setOf(MULTILINE, UNIX_LINES)).findAll(this).flatMap {
        transform(it.groupValues[nameGroup], it.groupValues[bodyGroup].trimIndent())
      }

    fun String.findOuterObjects(): Sequence<Pair<String, String>> = // (simpleName, qualifiedName)
    findClasses("", 1, 2, "^object\\s+(\\w+).*$\n((^  .*$\n|^$\n)*)") { name, body ->
        body.findClasses("$name.")
      }

    val (importLines, remainder) =
      sourceFiles.entries
        .filter { (filename, _) -> filename.endsWith(".kt") }
        .flatMap { (_, text) -> text.lines() }
        .partition { it.startsWith("import") }
    val importBlock = importLines.sorted().distinct()
    val (packageLines, code) = remainder.partition { it.startsWith("package") }
    val packageBlock = packageLines.distinct()
    assert(
      packageBlock.size <= 1
    ) // everything is in the same package and/or there is no package line
    val sourceText = listOf(packageBlock, importBlock, code).flatten().joinToString("\n")

    val (simpleNames, qualifiedNames) =
      sourceText.findClasses().plus(sourceText.findOuterObjects()).unzip()
    val instrumentation =
      "listOf<kotlin.reflect.KClass<*>>(${qualifiedNames.joinToString(",") { "$it::class" }})"

    // create new engine for each compilation
    // (otherwise we sometimes get kotlin compiler exceptions)
    val engine = ScriptEngineManager().getEngineByExtension("kts")!!
    val classes =
      try {
        @Suppress("UNCHECKED_CAST")
        engine.eval("$sourceText\n\n$instrumentation") as List<KClass<*>>
      } catch (e: ScriptException) {
        throw CompilationFailedException(e.message, e)
      }

    return simpleNames.zip(classes).toMap()
  }
}
