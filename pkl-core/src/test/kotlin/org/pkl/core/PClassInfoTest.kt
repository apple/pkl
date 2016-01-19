package org.pkl.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI

class PClassInfoTest {
  @Test
  fun `standard type`() {
    val info = PClassInfo.get("pkl.base", "Duration", URI("pkl:base"))
    assertThat(info.moduleName).isEqualTo("pkl.base")
    assertThat(info.simpleName).isEqualTo("Duration")
    assertThat(info.qualifiedName).isEqualTo("pkl.base#Duration")
    assertThat(info.displayName).isEqualTo("Duration")
    assertThat(info.toString()).isEqualTo("Duration")
    assertThat(info.javaClass).isEqualTo(Duration::class.java)
    assertThat(info.moduleUri).isEqualTo(URI("pkl:base"))
  }

  @Test
  fun `user-defined type`() {
    val uri = URI.create("my:person")
    val info = PClassInfo.get("my", "Person", uri)
    assertThat(info.moduleName).isEqualTo("my")
    assertThat(info.simpleName).isEqualTo("Person")
    assertThat(info.qualifiedName).isEqualTo("my#Person")
    assertThat(info.displayName).isEqualTo("my#Person")
    assertThat(info.toString()).isEqualTo("my#Person")
    assertThat(info.javaClass).isEqualTo(PObject::class.java)
    assertThat(info.moduleUri).isEqualTo(uri)
  }

  @Test
  fun isExactTypeOf() {
    assertThat(PClassInfo.Any.isExactClassOf(Object())).isFalse
    assertThat(PClassInfo.Typed.isExactClassOf(Object())).isFalse
  }
}
