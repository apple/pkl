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
package org.pkl.core.runtime

import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.runtime.AnsiCodingStringBuilder.AnsiCode

class AnsiCodingStringBuilderTest {
  @Test
  fun `no formatting`() {
    val result = AnsiCodingStringBuilder(false).append(AnsiCode.RED, "hello").toString()
    assertThat(result).isEqualTo("hello")
  }

  private val red = "\u001b[31m"
  private val redBold = "\u001b[1;31m"
  private val reset = "\u001b[0m"
  private val bold = "\u001b[1m"

  // make test failures easier to debug
  private val String.escaped
    get() = replace("\u001b", "[ESC]")

  @Test
  fun `don't emit same color code`() {
    val result =
      AnsiCodingStringBuilder(true).append(AnsiCode.RED, "hi").append(AnsiCode.RED, "hi").toString()
    assertThat(result.escaped).isEqualTo("${red}hihi${reset}".escaped)
  }

  @Test
  fun `only add needed codes`() {
    val result =
      AnsiCodingStringBuilder(true)
        .append(AnsiCode.RED, "hi")
        .append(EnumSet.of(AnsiCode.RED, AnsiCode.BOLD), "hi")
        .toString()
    assertThat(result.escaped).isEqualTo("${red}hi${bold}hi${reset}".escaped)
  }

  @Test
  fun `reset if need to subtract`() {
    val result =
      AnsiCodingStringBuilder(true)
        .append(EnumSet.of(AnsiCode.RED, AnsiCode.BOLD), "hi")
        .append(AnsiCode.RED, "hi")
        .toString()
    assertThat(result.escaped).isEqualTo("${redBold}hi${reset}${red}hi${reset}".escaped)
  }

  @Test
  fun `plain text in between`() {
    val result =
      AnsiCodingStringBuilder(true)
        .append(AnsiCode.RED, "hi")
        .append("hi")
        .append(AnsiCode.RED, "hi")
        .toString()
    assertThat(result.escaped).isEqualTo("${red}hi${reset}hi${red}hi${reset}".escaped)
  }
}
