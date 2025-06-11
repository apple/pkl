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
package org.pkl.core.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VmValueRendererTest {
  private val renderer = VmValueRenderer.singleLine(80)

  @Test
  fun `render null without default`() {
    val none = VmNull.withoutDefault()
    assertThat(renderer.render(none)).isEqualTo("null")
  }

  @Test
  fun `render null with default`() {
    val none = VmNull.withDefault("default")
    assertThat(renderer.render(none)).isEqualTo("null")
  }

  @Test
  fun `render bytes`() {
    val bytes = VmBytes((-128..127).map { it.toByte() }.toByteArray())
    assertThat(renderer.render(bytes))
      .isEqualTo("Bytes(128, 129, 130, 131, 132, 133, 134, 135, ... <total size: 256.b>)")
  }

  @Test
  fun `render bytes - precisions`() {
    val oneKbExactly = VmBytes(ByteArray(1000))
    assertThat(renderer.render(oneKbExactly))
      .isEqualTo("Bytes(0, 0, 0, 0, 0, 0, 0, 0, ... <total size: 1.kb>)")

    val over1Kb = VmBytes(ByteArray(123467))
    assertThat(renderer.render(over1Kb))
      .isEqualTo("Bytes(0, 0, 0, 0, 0, 0, 0, 0, ... <total size: 123.47.kb>)")

    val oneTenthPrecision = VmBytes(ByteArray(1100))
    assertThat(renderer.render(oneTenthPrecision))
      .isEqualTo("Bytes(0, 0, 0, 0, 0, 0, 0, 0, ... <total size: 1.1.kb>)")
  }
}
