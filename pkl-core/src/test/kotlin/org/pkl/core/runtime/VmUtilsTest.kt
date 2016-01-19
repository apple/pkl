package org.pkl.core.runtime

import org.pkl.core.SecurityManagers
import org.pkl.core.module.ModuleKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI

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
