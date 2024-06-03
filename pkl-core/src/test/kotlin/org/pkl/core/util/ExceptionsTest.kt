package org.pkl.core.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException
import java.lang.Error

class ExceptionsTest {
  @Test
  fun `get root cause of simple exception`() {
    val e = IOException("io")
    assertThat(Exceptions.getRootCause(e)).isSameAs(e)
  }

  @Test
  fun `get root cause of nested exception`() {
    val e = IOException("io")
    val e2 = RuntimeException("runtime")
    val e3 = Error("error")
    e.initCause(e2)
    e2.initCause(e3)
    assertThat(Exceptions.getRootCause(e)).isSameAs(e3)
  }

  @Test
  fun `get root reason`() {
    val e = IOException("io")
    val e2 = RuntimeException("the root reason")
    e.initCause(e2)
    assertThat(Exceptions.getRootReason(e)).isEqualTo("the root reason")
  }

  @Test
  fun `get root reason if null`() {
    val e = IOException("io")
    val e2 = RuntimeException(null as String?)
    e.initCause(e2)
    assertThat(Exceptions.getRootReason(e)).isEqualTo("(unknown reason)")
  }

  @Test
  fun `get root reason if empty`() {
    val e = IOException("io")
    val e2 = RuntimeException("")
    e.initCause(e2)
    assertThat(Exceptions.getRootReason(e)).isEqualTo("(unknown reason)")
  }
}
