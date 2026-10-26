/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TypeArgumentCandidatesTest {
  private fun candidates(source: String): List<Int> =
    TypeArgumentCandidates.scan(Lexer(source)).stream().toArray().toList()

  @Test
  fun `empty input`() {
    assertThat(candidates("")).isEmpty()
  }

  @Test
  fun `simple method call with type arguments`() {
    assertThat(candidates("x = foo<Int>(5)")).isEqualTo(listOf(7))
  }

  @Test
  fun `comparison without closing angle bracket`() {
    assertThat(candidates("x = a < b")).isEmpty()
  }

  @Test
  fun `closing angle bracket not followed by open paren`() {
    assertThat(candidates("x = foo<Int> bar")).isEmpty()
  }

  @Test
  fun `less-than-or-equal and greater-than-or-equal are not angle brackets`() {
    assertThat(candidates("x = a <= b > (c)")).isEmpty()
    assertThat(candidates("x = a < b >= (c)")).isEmpty()
  }

  @Test
  fun `stray greater-than is ignored`() {
    assertThat(candidates("x = a > (b)")).isEmpty()
    // the first `>` has no matching `<`; the second one matches
    assertThat(candidates("x = a > b < c > (d)")).isEqualTo(listOf(10))
  }

  @Test
  fun `nested generics - only the outermost is followed by open paren`() {
    val source = "x = foo<Map<String, List<Int>>>(5)"
    assertThat(candidates(source)).isEqualTo(listOf(7))
  }

  @Test
  fun `multiple candidates`() {
    assertThat(candidates("x = foo<Int>(bar<String>(1))")).isEqualTo(listOf(7, 16))
    assertThat(candidates("x = foo<Int>(1)\ny = bar<String>(2)")).isEqualTo(listOf(7, 23))
  }

  @Test
  fun `function types - arrow is not a closing angle bracket`() {
    assertThat(candidates("x = foo<(Int) -> String>(f)")).isEqualTo(listOf(7))
  }

  @Test
  fun `union, nullable and default types`() {
    assertThat(candidates("x = foo<*Int|String?>(f)")).isEqualTo(listOf(7))
  }

  // constrained types

  @Test
  fun `greater-than inside a constraint does not close the type argument list`() {
    assertThat(candidates("x = foo<Int(this > 3)>(5)")).isEqualTo(listOf(7))
  }

  @Test
  fun `less-than inside a constraint is not a candidate`() {
    // only the outer `<` is
    assertThat(candidates("x = foo<Int(this < 3)>(5)")).isEqualTo(listOf(7))
  }

  @Test
  fun `comparisons in both directions inside a constraint`() {
    assertThat(candidates("x = foo<Int(this < 3 && this > 1)>(5)")).isEqualTo(listOf(7))
  }

  @Test
  fun `constraint with a call followed by open paren`() {
    val source = "x = foo<Int(bar<Int>(this))>(5)"
    assertThat(candidates(source)).isEqualTo(listOf(7, 15))
  }

  @Test
  fun `constraint in a nested type argument`() {
    assertThat(candidates("x = foo<List<Int(this > 3)>>(5)")).isEqualTo(listOf(7))
  }

  @Test
  fun `multiple constrained type arguments`() {
    assertThat(candidates("x = foo<Int(this > 3), String(length < 5)>(5)")).isEqualTo(listOf(7))
  }

  @Test
  fun `comparison inside parens does not match a greater-than outside`() {
    // the `>` after `)` is at a different nesting level than the `<`
    assertThat(candidates("x = (a < b) > (c)")).isEmpty()
  }

  // brackets and braces

  @Test
  fun `comparison inside subscript does not match a greater-than outside`() {
    assertThat(candidates("x = list[a < b] > (c)")).isEmpty()
  }

  @Test
  fun `comparison inside object body does not match a greater-than outside`() {
    assertThat(candidates("x = new Foo { y = a < b } > (c)")).isEmpty()
  }

  @Test
  fun `greater-than inside object body does not close an outer less-than`() {
    assertThat(candidates("x = foo<Foo(new Listing { 1 > 0 }.length > 0)>(5)")).isEqualTo(listOf(7))
  }

  @Test
  fun `unmatched less-than inside parens is discarded when the paren closes`() {
    // `<` pushed after `(` must not be left on the stack for later `>`
    assertThat(candidates("x = (a < b) c > (d)")).isEmpty()
  }

  @Test
  fun `stray closing bracket does not break the scan`() {
    assertThat(candidates(") x = foo<Int>(5)")).isEqualTo(listOf(9))
  }

  // string interpolation

  @Test
  fun `comparison inside string interpolation is not a candidate`() {
    assertThat(candidates("""x = "\(a < b)" > (c)""")).isEmpty()
  }

  @Test
  fun `method call inside string interpolation`() {
    assertThat(candidates("""x = "hello \(foo<Int>(1)) world"""")).isEqualTo(listOf(16))
  }

  @Test
  fun `greater-than inside string interpolation does not close an outer less-than`() {
    assertThat(candidates("""x = foo<"\(a > b)">(5)""")).isEqualTo(listOf(7))
  }

  @Test
  fun `nested string interpolation`() {
    assertThat(candidates("""x = "\("\(foo<Int>(1))")"""")).isEqualTo(listOf(13))
  }

  @Test
  fun `multiline string with interpolation`() {
    val source = "x = \"\"\"\n  \\(foo<Int>(1))\n  \"\"\""
    assertThat(candidates(source)).isEqualTo(listOf(15))
  }

  @Test
  fun `angle brackets in string literal text are ignored`() {
    assertThat(candidates("""x = "a < b > (c)"""")).isEmpty()
  }

  // affixes between the closing angle bracket and open paren

  @Test
  fun `comments between closing angle bracket and open paren`() {
    assertThat(candidates("x = foo<Int> /* c */ (5)")).isEqualTo(listOf(7))
    assertThat(candidates("x = foo<Int> // c\n(5)")).isEqualTo(listOf(7))
  }

  @Test
  fun `newline and semicolon before open paren - parser re-checks these`() {
    // these are intentionally lenient; `ParserImpl` and `GenericParserImpl` reject them.
    assertThat(candidates("x = foo<Int>\n(5)")).isEqualTo(listOf(7))
    assertThat(candidates("x = foo<Int>; (5)")).isEqualTo(listOf(7))
  }

  // robustness

  @Test
  fun `lexing error ends scan without throwing`() {
    // candidates found before the error are retained
    assertThat(candidates("x = foo<Int>(1)\ny = \u0001")).isEqualTo(listOf(7))
    // `<` still open when the error occurs is not a candidate
    assertThat(candidates("x = foo<Int \u0001 >(1)")).isEmpty()
  }

  @Test
  fun `deeply nested angle brackets`() {
    val depth = 1000
    val source = "x = foo" + "<A".repeat(depth) + ">".repeat(depth) + "(1)"
    // only the outermost `<` has a `>` that is followed by `(`
    assertThat(candidates(source)).isEqualTo(listOf(7))
  }

  @Test
  fun `many nested brackets`() {
    val depth = 1000
    val source = "x = " + "(".repeat(depth) + "a < b" + ")".repeat(depth) + " > (c)"
    assertThat(candidates(source)).isEmpty()
  }
}
