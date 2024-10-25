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
package org.pkl.commons

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ShlexTest {

  @Test
  fun `empty input produces empty output`() {
    assertThat(shlex("")).isEqualTo(emptyList<String>())
  }

  @Test
  fun `whitespace input produces empty output`() {
    assertThat(shlex("  \n \t  ")).isEqualTo(emptyList<String>())
  }

  @Test
  fun `regular token parsing`() {
    assertThat(shlex("\nabc  def\tghi ")).isEqualTo(listOf("abc", "def", "ghi"))
  }

  @Test
  fun `single quoted token parsing`() {
    assertThat(shlex("'this is a single token'")).isEqualTo(listOf("this is a single token"))
  }

  @Test
  fun `double quoted token parsing`() {
    assertThat(shlex("\"this is a single token\"")).isEqualTo(listOf("this is a single token"))
  }

  @Test
  fun `escaping handles double quotes`() {
    assertThat(shlex(""""\"this is a single double quoted token\"""""))
      .isEqualTo(listOf("\"this is a single double quoted token\""))
  }

  @Test
  fun `escaping does not apply within single quotes`() {
    assertThat(shlex("""'this is a single \" token'"""))
      .isEqualTo(listOf("""this is a single \" token"""))
  }

  @Test
  fun `adjacent quoted strings are one token`() {
    assertThat(shlex(""""single"' joined 'token""")).isEqualTo(listOf("single joined token"))
    assertThat(shlex(""""single"' 'token""")).isEqualTo(listOf("single token"))
  }

  @Test
  fun `space escapes do not split tokens`() {
    assertThat(shlex("""single\ token""")).isEqualTo(listOf("single token"))
  }

  @Test
  fun `empty quotes produce a single empty token`() {
    assertThat(shlex("\"\"")).isEqualTo(listOf(""))
    assertThat(shlex("''")).isEqualTo(listOf(""))
    assertThat(shlex("'' ''")).isEqualTo(listOf("", ""))
    assertThat(shlex("''''")).isEqualTo(listOf(""))
  }
}
