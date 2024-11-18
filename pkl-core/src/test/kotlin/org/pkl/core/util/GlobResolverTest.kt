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
package org.pkl.core.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GlobResolverTest {
  @Test
  fun `basic match`() {
    val pattern = GlobResolver.toRegexPattern("foobar")
    assertTrue(pattern.matcher("foobar").matches())
    assertFalse(pattern.matcher("oobar").matches())
    assertFalse(pattern.matcher("fooba").matches())
    assertFalse(pattern.matcher("").matches())
  }

  @Test
  fun `basic match 2`() {
    val pattern = GlobResolver.toRegexPattern("foo+bar.pkl")
    assertTrue(pattern.matcher("foo+bar.pkl").matches())
    assertFalse(pattern.matcher("foooooobar.pkl").matches())
  }

  @ParameterizedTest
  @ValueSource(
    strings =
      [
        "foo.pkl",
        "bar.pkl",
        "baz.pkl",
        "buzzy...baz.pkl",
        "ted_lasso.min.pkl",
        "ted_lasso.pkl.min.pkl"
      ]
  )
  fun `glob match`(input: String) {
    val pattern = GlobResolver.toRegexPattern("*.pkl")
    assertTrue(pattern.matcher(input).matches())
  }

  @ParameterizedTest
  @ValueSource(
    strings =
      [
        "bar.pcf",
        "bar.yml",
        "bar.pkl.bcl",
        "bar.pklpkl",
        "pkl",
        // crosses directory boundaries
        "/bar/baz.pkl",
        "/baz.pkl"
      ]
  )
  fun `glob non-match`(input: String) {
    val pattern = GlobResolver.toRegexPattern("*.pkl")
    assertFalse(pattern.matcher(input).matches())
  }

  @ParameterizedTest
  @ValueSource(
    strings = ["/foo.pkl/bar/baz.pkl", "//fo///ba.pkl", ".pkl", "buz.pkl", "pkl.pkl.pkl.pkl"]
  )
  fun `globstar match`(input: String) {
    val pattern = GlobResolver.toRegexPattern("**.pkl")
    assertTrue(pattern.matcher(input).matches())
  }

  @ParameterizedTest
  @ValueSource(
    strings = ["/foo.pkl/bar/baz.pkl", "//fo///ba.pkl", ".pkl", "buz.pkl", "pkl.pkl.pkl.pkl"]
  )
  fun `globstar non-match`(input: String) {
    val pattern = GlobResolver.toRegexPattern("**.pkl")
    assertTrue(pattern.matcher(input).matches())
  }

  @ParameterizedTest
  @ValueSource(
    strings =
      [
        "/foo.pkl/bar/baz.pkl",
        "//fo///ba.pkl",
      ]
  )
  fun `globstar match 2`(input: String) {
    val pattern = GlobResolver.toRegexPattern("/**/*.pkl")
    assertTrue(pattern.matcher(input).matches())
  }

  @ParameterizedTest
  @ValueSource(strings = ["bar.pkl", ".pkl", "/foo.pkl"])
  fun `globstar non-match 2`(input: String) {
    val pattern = GlobResolver.toRegexPattern("/**/*.pkl")
    assertFalse(pattern.matcher(input).matches())
  }

  @Test
  fun `sub-patterns`() {
    val pattern = GlobResolver.toRegexPattern("{foo,bar}")
    assertTrue(pattern.matcher("foo").matches())
    assertTrue(pattern.matcher("bar").matches())
    assertFalse(pattern.matcher("barr").matches())
    assertFalse(pattern.matcher("fooo").matches())
    assertFalse(pattern.matcher("zebra").matches())
    assertFalse(pattern.matcher("").matches())

    val pattern2 = GlobResolver.toRegexPattern("{,,,a,}")
    assertTrue(pattern2.matcher("a").matches())
    assertTrue(pattern2.matcher("").matches())
    assertFalse(pattern2.matcher("z").matches())

    val pattern3 = GlobResolver.toRegexPattern("*.y{a,}ml")
    assertTrue(pattern3.matcher("foo.yml").matches())
    assertTrue(pattern3.matcher("foo.yaml").matches())
    assertFalse(pattern3.matcher("foo.yaaaaaml").matches())
  }

  @Test
  fun `sub-patterns with wildcards`() {
    val pattern = GlobResolver.toRegexPattern("{*.foo,*.bar}")
    assertTrue(pattern.matcher("thing.foo").matches())
    assertTrue(pattern.matcher("thing.bar").matches())
    assertTrue(pattern.matcher("zing.bar").matches())
    assertTrue(pattern.matcher(".bar").matches())
    assertTrue(pattern.matcher(".foo").matches())
  }

  @Test
  fun `invalid sub-patterns`() {
    assertThrows<GlobResolver.InvalidGlobPatternException> {
      GlobResolver.toRegexPattern("{foo{bar}}")
    }
    assertThrows<GlobResolver.InvalidGlobPatternException> { GlobResolver.toRegexPattern("{foo") }
    // no leading open curly means closing curly is treated verbatim
    assertDoesNotThrow { GlobResolver.toRegexPattern("foo}") }
  }

  @Test
  fun `character classes`() {
    // leading `^` is the verbatim caret character.
    val pattern = GlobResolver.toRegexPattern("thing[^0-9]")
    assertTrue(pattern.matcher("thing^").matches())
    assertTrue(pattern.matcher("thing0").matches())
    assertTrue(pattern.matcher("thing1").matches())
    assertTrue(pattern.matcher("thing2").matches())
    assertTrue(pattern.matcher("thing3").matches())
    assertTrue(pattern.matcher("thing4").matches())
    assertTrue(pattern.matcher("thing5").matches())
  }

  @Test
  fun `character classes don't cross directory boundaries`() {
    val pattern = GlobResolver.toRegexPattern("[.-z]")
    assertTrue(pattern.matcher("f").matches())
    assertFalse(pattern.matcher("/").matches())
  }

  @Test
  fun `invalid character classes`() {
    assertThrows<GlobResolver.InvalidGlobPatternException> { GlobResolver.toRegexPattern("thing[") }
    assertThrows<GlobResolver.InvalidGlobPatternException> {
      GlobResolver.toRegexPattern("thing[foo/bar]")
    }
    assertThrows<GlobResolver.InvalidGlobPatternException> {
      GlobResolver.toRegexPattern("[[=a=]]")
    }
    assertThrows<GlobResolver.InvalidGlobPatternException> {
      GlobResolver.toRegexPattern("[[:alnum:]]")
    }
    assertThrows<GlobResolver.InvalidGlobPatternException> {
      GlobResolver.toRegexPattern("[[.a-acute.]]")
    }
    // no leading open square bracket means closing square bracket is verbatim
    assertDoesNotThrow { GlobResolver.toRegexPattern("]") }
  }

  @Test
  fun `invalid extglob`() {
    assertThrows<GlobResolver.InvalidGlobPatternException> {
      GlobResolver.toRegexPattern("!(foo|bar)")
    }
    assertThrows<GlobResolver.InvalidGlobPatternException> {
      GlobResolver.toRegexPattern("+(foo|bar)")
    }
    assertThrows<GlobResolver.InvalidGlobPatternException> {
      GlobResolver.toRegexPattern("?(foo|bar)")
    }
    assertThrows<GlobResolver.InvalidGlobPatternException> {
      GlobResolver.toRegexPattern("@(foo|bar)")
    }
    assertThrows<GlobResolver.InvalidGlobPatternException> {
      GlobResolver.toRegexPattern("*(foo|bar)")
    }
  }

  @Test
  fun `wildcard character`() {
    val pattern = GlobResolver.toRegexPattern("ae?ou")
    assertTrue(pattern.matcher("aeiou").matches())
    assertTrue(pattern.matcher("aeeou").matches())
    assertTrue(pattern.matcher("aelou").matches())
    assertTrue(pattern.matcher("aejou").matches())
    assertFalse(pattern.matcher("aeou").matches())
    assertFalse(pattern.matcher("aou").matches())
  }

  @Test
  fun `character classes - negation`() {
    val pattern = GlobResolver.toRegexPattern("thing[!0-5]")
    assertFalse(pattern.matcher("thing1").matches())
    assertFalse(pattern.matcher("thing2").matches())
    assertFalse(pattern.matcher("thing3").matches())
    assertFalse(pattern.matcher("thing4").matches())
    assertFalse(pattern.matcher("thing5").matches())
    assertTrue(pattern.matcher("thing6").matches())
    assertTrue(pattern.matcher("thing7").matches())
  }

  @Test
  fun escapes() {
    val pattern1 = GlobResolver.toRegexPattern("\\\\foo")
    assertTrue(pattern1.matcher("\\foo").matches())

    val pattern2 = GlobResolver.toRegexPattern("\\{foo-bar.pkl")
    assertTrue(pattern2.matcher("{foo-bar.pkl").matches())

    val pattern3 = GlobResolver.toRegexPattern("\\[foo-bar.pkl")
    assertTrue(pattern3.matcher("[foo-bar.pkl").matches())
  }
}
