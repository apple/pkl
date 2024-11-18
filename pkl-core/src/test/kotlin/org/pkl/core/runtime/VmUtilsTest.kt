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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VmUtilsTest {
  @Test
  fun `codePointOffsetToCharOffset - ascii`() {
    val str = "0123"

    assertThat(VmUtils.codePointOffsetToCharOffset(str, -1)).isEqualTo(-1)
    assertThat(VmUtils.codePointOffsetToCharOffset(str, -999)).isEqualTo(-1)

    assertThat(VmUtils.codePointOffsetToCharOffset(str, 0)).isEqualTo(0)
    assertThat(VmUtils.codePointOffsetToCharOffset(str, 1)).isEqualTo(1)
    assertThat(VmUtils.codePointOffsetToCharOffset(str, 2)).isEqualTo(2)
    assertThat(VmUtils.codePointOffsetToCharOffset(str, 3)).isEqualTo(3)
    assertThat(VmUtils.codePointOffsetToCharOffset(str, 4)).isEqualTo(4)

    assertThat(VmUtils.codePointOffsetToCharOffset(str, 5)).isEqualTo(-1)
    assertThat(VmUtils.codePointOffsetToCharOffset("012345", 999)).isEqualTo(-1)
  }

  @Test
  fun `codePointOffsetToCharOffset - unicode`() {
    val str = "0\uD83D\uDE002\uD83D\uDE00"

    assertThat(VmUtils.codePointOffsetToCharOffset(str, -1)).isEqualTo(-1)
    assertThat(VmUtils.codePointOffsetToCharOffset(str, -999)).isEqualTo(-1)

    assertThat(VmUtils.codePointOffsetToCharOffset(str, 0)).isEqualTo(0)
    assertThat(VmUtils.codePointOffsetToCharOffset(str, 1)).isEqualTo(1)
    assertThat(VmUtils.codePointOffsetToCharOffset(str, 2)).isEqualTo(3)
    assertThat(VmUtils.codePointOffsetToCharOffset(str, 3)).isEqualTo(4)
    assertThat(VmUtils.codePointOffsetToCharOffset(str, 4)).isEqualTo(6)

    assertThat(VmUtils.codePointOffsetToCharOffset(str, 5)).isEqualTo(-1)
    assertThat(VmUtils.codePointOffsetToCharOffset(str, 999)).isEqualTo(-1)
  }

  @Test
  fun `codePointOffsetToCharOffset - unicode with startIndex`() {
    val str = "0\uD83D\uDE002\uD83D\uDE00"

    assertThat(VmUtils.codePointOffsetToCharOffset(str, -1, 3)).isEqualTo(-1)
    assertThat(VmUtils.codePointOffsetToCharOffset(str, -999, 3)).isEqualTo(-1)

    assertThat(VmUtils.codePointOffsetToCharOffset(str, 0, 3)).isEqualTo(3)
    assertThat(VmUtils.codePointOffsetToCharOffset(str, 1, 3)).isEqualTo(4)
    assertThat(VmUtils.codePointOffsetToCharOffset(str, 2, 3)).isEqualTo(6)

    assertThat(VmUtils.codePointOffsetToCharOffset(str, 3, 3)).isEqualTo(-1)
    assertThat(VmUtils.codePointOffsetToCharOffset(str, 5)).isEqualTo(-1)
    assertThat(VmUtils.codePointOffsetToCharOffset(str, 999)).isEqualTo(-1)
  }

  @Test
  fun `codePointOffsetFromEndToCharOffset - ascii`() {
    val str = "0123"

    assertThat(VmUtils.codePointOffsetFromEndToCharOffset(str, -1)).isEqualTo(-1)
    assertThat(VmUtils.codePointOffsetFromEndToCharOffset(str, -999)).isEqualTo(-1)

    assertThat(VmUtils.codePointOffsetFromEndToCharOffset(str, 0)).isEqualTo(4)
    assertThat(VmUtils.codePointOffsetFromEndToCharOffset(str, 1)).isEqualTo(3)
    assertThat(VmUtils.codePointOffsetFromEndToCharOffset(str, 2)).isEqualTo(2)
    assertThat(VmUtils.codePointOffsetFromEndToCharOffset(str, 3)).isEqualTo(1)
    assertThat(VmUtils.codePointOffsetFromEndToCharOffset(str, 4)).isEqualTo(0)

    assertThat(VmUtils.codePointOffsetFromEndToCharOffset(str, 5)).isEqualTo(-1)
    assertThat(VmUtils.codePointOffsetFromEndToCharOffset("012345", 999)).isEqualTo(-1)
  }

  @Test
  fun `codePointOffsetFromEndToCharOffset - unicode`() {
    val str = "0\uD83D\uDE002\uD83D\uDE00"

    assertThat(VmUtils.codePointOffsetFromEndToCharOffset(str, -1)).isEqualTo(-1)
    assertThat(VmUtils.codePointOffsetFromEndToCharOffset(str, -999)).isEqualTo(-1)

    assertThat(VmUtils.codePointOffsetFromEndToCharOffset(str, 0)).isEqualTo(6)
    assertThat(VmUtils.codePointOffsetFromEndToCharOffset(str, 1)).isEqualTo(4)
    assertThat(VmUtils.codePointOffsetFromEndToCharOffset(str, 2)).isEqualTo(3)
    assertThat(VmUtils.codePointOffsetFromEndToCharOffset(str, 3)).isEqualTo(1)
    assertThat(VmUtils.codePointOffsetFromEndToCharOffset(str, 4)).isEqualTo(0)

    assertThat(VmUtils.codePointOffsetFromEndToCharOffset(str, 5)).isEqualTo(-1)
    assertThat(VmUtils.codePointOffsetFromEndToCharOffset(str, 999)).isEqualTo(-1)
  }
}
