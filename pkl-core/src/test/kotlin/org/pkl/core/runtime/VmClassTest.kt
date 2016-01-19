package org.pkl.core.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VmClassTest {
  @Test
  fun `class pkl_base_Container has one hidden property named 'default'`() {
    assertThat(BaseModule.getMappingClass().isHiddenProperty(Identifier.DEFAULT)).isTrue
  }
}
