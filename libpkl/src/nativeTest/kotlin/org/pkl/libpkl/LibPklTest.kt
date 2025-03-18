/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.libpkl

import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.Release

class LibPklTest {
  @Test
  fun testMalformedMessage() {
    val messageResponseHandler =
      object : LibPklJNA.PklMessageResponseHandler {
        override fun invoke(length: Int, message: Pointer, userData: Pointer?) {}
      }
    val execRef = PointerByReference()
    val error = LibPklJNA.PklError()
    val result = LibPklJNA.INSTANCE.pkl_init(messageResponseHandler, Pointer.NULL, execRef, error)
    assertThat(result).`as` { "Failed to call pkl_init: ${error.message}" }.isEqualTo(0)
    val result2 =
      LibPklJNA.INSTANCE.pkl_send_message(
        execRef.value,
        10,
        byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
        error,
      )
    assertThat(result2).isNotEqualTo(0)
    assertThat(error.message).isEqualTo("Invalid encoding: Malformed message header.")
    assertThat(LibPklJNA.INSTANCE.pkl_close(execRef.value, error)).isEqualTo(0)
  }

  @Test
  fun testVersionString() {
    val currentVersion = Release.current().version.toString()
    assertThat(LibPklJNA.INSTANCE.pkl_version()).isEqualTo(currentVersion)
  }
}
