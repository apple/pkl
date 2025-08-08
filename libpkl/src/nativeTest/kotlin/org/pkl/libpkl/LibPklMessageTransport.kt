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
import org.assertj.core.api.Assertions.assertThat
import org.msgpack.core.MessagePack
import org.pkl.core.messaging.Message
import org.pkl.core.messaging.MessageTransports
import org.pkl.server.ServerMessagePackDecoder
import org.pkl.server.ServerMessagePackEncoder

class LibPklMessageTransport : MessageTransports.AbstractMessageTransport({}) {
  private val messageResponseHandler: LibPklJNA.PklMessageResponseHandler =
    object : LibPklJNA.PklMessageResponseHandler {
      override fun invoke(length: Int, message: Pointer, userData: Pointer?) {
        val message = decode(message.getByteArray(0, length))
        accept(message)
      }
    }

  var pexec: Pointer? = null

  override fun doStart() {
    pexec = LibPklJNA.INSTANCE.pkl_init(messageResponseHandler, Pointer.NULL)
    assertThat(this.pexec).isNotNull()
  }

  override fun doClose() {
    assertThat(LibPklJNA.INSTANCE.pkl_close(pexec!!)).isEqualTo(0)
  }

  override fun doSend(message: Message) {
    val bytes = encode(message)
    LibPklJNA.INSTANCE.pkl_send_message(pexec!!, bytes.size, bytes)
  }

  private fun encode(message: Message): ByteArray {
    val packer = MessagePack.newDefaultBufferPacker()
    val encoder = ServerMessagePackEncoder(packer)
    encoder.encode(message)
    return packer.toByteArray()
  }

  private fun decode(receivedBytes: ByteArray): Message {
    val unpacker = MessagePack.newDefaultUnpacker(receivedBytes)
    return ServerMessagePackDecoder(unpacker).decode()!!
  }
}
