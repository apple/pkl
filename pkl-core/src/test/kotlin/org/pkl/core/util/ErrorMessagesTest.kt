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
package org.pkl.core.util

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.pkl.core.runtime.VmClass
import org.pkl.core.runtime.VmValue
import org.pkl.core.runtime.VmValueConverter
import org.pkl.core.runtime.VmValueVisitor

class ErrorMessagesTest {
  private class UnforceableValue : VmValue() {
    var forced = false
      private set

    override fun force(allowUndefinedValues: Boolean) {
      forced = true
      throw RuntimeException("value must not be forced while rendering an error message")
    }

    override fun accept(visitor: VmValueVisitor) {
      visitor.visitString("lazy")
    }

    override fun <T : Any> accept(converter: VmValueConverter<T>, path: Iterable<Any>): T =
      throw UnsupportedOperationException()

    override fun equals(obj: Any?): Boolean = this === obj

    override fun toString(): String {
      force(true)
      return "lazy"
    }

    override fun getVmClass(): VmClass = throw UnsupportedOperationException()

    override fun export(): Any = throw UnsupportedOperationException()

    override fun hashCode(): Int = System.identityHashCode(this)
  }

  @Test
  fun `renders VmValue arguments without forcing them`() {
    val value = UnforceableValue()

    lateinit var message: String
    assertThatCode { message = ErrorMessages.create("cannotIterateOverThisValue", value) }
      .doesNotThrowAnyException()

    assertThat(value.forced).isFalse()
    assertThat(message).contains("lazy")
  }
}
