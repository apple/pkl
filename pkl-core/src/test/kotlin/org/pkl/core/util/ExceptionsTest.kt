/**
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
package org.pkl.core.util

import java.io.IOException
import java.lang.Error
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
