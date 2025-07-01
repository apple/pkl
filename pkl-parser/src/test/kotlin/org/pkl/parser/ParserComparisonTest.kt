/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.parser

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension
import org.junit.jupiter.api.Test
import org.pkl.commons.walk

class ParserComparisonTest : ParserComparisonTestInterface {

  @Test
  fun testAsPrecedence() {
    compare("prop = 3 + bar as Int * 5")
  }

  @Test
  fun testStringInterpolation() {
    compare(
      """
      prop = "\(bar)"
      prop2 = "foo \(bar)"
      prop3 = "\(bar) foo"
      prop4 = "foo \(bar + baz) foo"
      """
        .trimIndent()
    )

    compare(
      """
      prop = ""${'"'}
        \(bar)
        ""${'"'}
      prop2 = ""${'"'}
        foo \(bar)
        ""${'"'}
      prop3 = ""${'"'}
        \(bar) foo
        ""${'"'}
      prop4 = ""${'"'}
        foo \(bar + baz) foo
        ""${'"'}
      """
        .trimIndent()
    )
  }

  override fun getSnippets(): List<Path> {
    return Path("../pkl-core/src/test/files/LanguageSnippetTests/input")
      .walk()
      .filter { path ->
        val pathStr = path.toString().replace("\\", "/")
        path.extension == "pkl" &&
          !exceptions.any { pathStr.endsWith(it) } &&
          !regexExceptions.any { it.matches(pathStr) }
      }
      .toList()
  }

  companion object {
    // tests that are not syntactically valid Pkl
    private val exceptions =
      setOf(
        "stringError1.pkl",
        "annotationIsNotExpression2.pkl",
        "amendsRequiresParens.pkl",
        "errors/parser18.pkl",
        "errors/nested1.pkl",
        "errors/invalidCharacterEscape.pkl",
        "errors/invalidUnicodeEscape.pkl",
        "errors/unterminatedUnicodeEscape.pkl",
        "errors/keywordNotAllowedHere1.pkl",
        "errors/keywordNotAllowedHere2.pkl",
        "errors/keywordNotAllowedHere3.pkl",
        "errors/keywordNotAllowedHere4.pkl",
        "errors/moduleWithHighMinPklVersionAndParseErrors.pkl",
        "errors/underscore.pkl",
        "notAUnionDefault.pkl",
        "multipleDefaults.pkl",
        "modules/invalidModule1.pkl",
      )

    private val regexExceptions =
      setOf(
        Regex(".*/errors/delimiters/.*"),
        Regex(".*/errors/parser\\d+\\.pkl"),
        Regex(".*/parser/.*"),
      )
  }
}
