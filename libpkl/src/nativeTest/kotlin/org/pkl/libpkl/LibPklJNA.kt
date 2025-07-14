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

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

@Suppress("FunctionName")
interface LibPklJNA : Library {
  companion object {
    val INSTANCE: LibPklJNA = Native.load("pkl", LibPklJNA::class.java)
  }

  interface PklMessageResponseHandler : Callback {
    fun invoke(length: Int, message: Pointer, userData: Pointer?)
  }

  fun pkl_init(handler: PklMessageResponseHandler, userData: Pointer?): Int

  fun pkl_send_message(length: Int, message: ByteArray): Int

  fun pkl_close(): Int

  fun pkl_version(): String
}
