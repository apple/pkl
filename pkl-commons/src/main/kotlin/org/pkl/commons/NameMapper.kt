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
package org.pkl.commons

/**
 * A helper class for translating names of Pkl modules to different names of classes and/or objects
 * in the target language of a code generation execution.
 *
 * The `mapping` parameter is expected to contain valid prefixes of Pkl module names, with an
 * optional dot at the end, and values should be valid class names in the language for which code
 * generation is performed.
 *
 * If the rename patterns do not explicitly rename the class, the class name is capitalized.
 *
 * When computing the appropriate target name, the longest matching prefix is used.
 *
 * Prefix replacements are literal, and therefore dots are important. When renaming packages, in
 * most cases, you must ensure that you have an ending dot on both sides of a mapping (except for
 * the empty mapping, if you use it), otherwise you may get unexpected results:
 * ```kotlin
 * val mapper = NameMapper(
 *   mapOf(
 *     "com.foo." to "x", // Dot on the left only
 *     "org.bar" to "y.", // Dot on the right only
 *     "net.baz" to "z" // No dots
 *   )
 * )
 *
 * assertThat(mapper.map("com.foo.bar")).isEqualTo("" to "xbar") // Target prefix merged into the suffix
 * assertThat(mapper.map("org.bar.baz")).isEqualTo("y." to "Baz") // Double dot, invalid package name
 * assertThat(mapper.map("net.baz.qux")).isEqualTo("z" to "Qux") // Looks okay, but...
 * assertThat(mapper.map("net.bazqux")).isEqualTo("" to "zqux") // ...may cut the package name in the middle.
 * ```
 */
class NameMapper(mapping: Map<String, String>) {
  private val sortedMapping = mapping.toList().sortedBy { -it.first.length }

  private fun doMap(sourceName: String): Pair<String, Boolean> {
    for ((sourcePrefix, targetPrefix) in sortedMapping) {
      if (sourceName.startsWith(sourcePrefix)) {
        val rest = sourceName.substring(sourcePrefix.length)
        val mapped = targetPrefix + rest
        val wasClassRenamed =
          !targetPrefix.endsWith('.') && (sourcePrefix.length - 1) >= sourceName.lastIndexOf('.')
        return mapped to wasClassRenamed
      }
    }
    return sourceName to false
  }

  fun map(sourceName: String): Pair<String, String> {
    val (mappedName, wasClassRenamed) = doMap(sourceName)
    val packageName = mappedName.substringBeforeLast(".", "")
    val mappedClassName = mappedName.substringAfterLast(".")
    val className =
      if (wasClassRenamed) mappedClassName
      else mappedClassName.replaceFirstChar { it.titlecaseChar() }
    return packageName to className
  }
}
