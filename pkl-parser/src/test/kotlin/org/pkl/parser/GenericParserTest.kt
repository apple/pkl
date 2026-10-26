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

import java.nio.charset.StandardCharsets
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.assertTimeoutPreemptively

class GenericParserTest {
  @Test
  fun `parser fails if exceeds max backtracking count`() {
    val code =
      javaClass
        .getResourceAsStream("large_backtracking.pkl.txt")!!
        .readAllBytes()
        .toString(StandardCharsets.UTF_8)
    val err = assertThrows<GenericParserError> { GenericParser().parseModule(code) }
    assertThat(err).hasMessageContaining("Too much backtracking occurred during parsing.")
  }

  // this is kind of a smoke test to make sure that we parse these cases reasonably quickly.
  @Test
  fun `parser succeeds if nested backtracking stays within max depth`() {
    val code =
      """
      result = a(a < a(a < a(a < a(a < a(a < a(a < a(a < a(a < a(a < 1)))))))))
      """
        .trimIndent()
    assertTimeoutPreemptively(Duration.ofMillis(10)) { GenericParser().parseModule(code) }
  }
}
