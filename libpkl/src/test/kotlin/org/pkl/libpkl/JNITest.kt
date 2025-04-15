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
import java.io.ByteArrayOutputStream
import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.msgpack.core.MessagePack
import org.pkl.core.messaging.Message
import org.pkl.server.*

class JNITest {
  @Test
  fun `init and close`() {
    val handlerFn =
      object : LibPklLibrary.PklMessageResponseHandler {
        override fun invoke(length: Int, message: Pointer?) {
          when {
            message == null || length == 0 -> {
              println("null message")
            }
            else -> {
              val receivedBytes: ByteArray = message.getByteArray(0, length)
              val message = decode(receivedBytes)
              println("length: $length, message: $message")
            }
          }
        }
      }

    assertThat(LibPklLibrary.INSTANCE.pkl_init(handlerFn)).isEqualTo(0)
    assertThat(LibPklLibrary.INSTANCE.pkl_close()).isEqualTo(0)
  }

  @Test
  fun `create evaluator and receive message`() {
    val received = mutableListOf<Message?>()

    val handlerFn =
      object : LibPklLibrary.PklMessageResponseHandler {
        override fun invoke(length: Int, message: Pointer?) {
          when {
            message == null || length == 0 -> {
              println("null message")
            }
            else -> {
              val receivedBytes: ByteArray = message.getByteArray(0, length)
              val message = decode(receivedBytes)
              received.add(message)
              println("length: $length, message: $message")
            }
          }
        }
      }

    assertThat(LibPklLibrary.INSTANCE.pkl_init(handlerFn)).isEqualTo(0)

    encode(
        CreateEvaluatorRequest(
          123,
          listOf(".*"),
          listOf(".*"),
          listOf(),
          listOf(),
          listOf(),
          mapOf(),
          mapOf(),
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
        )
      )
      .let { message ->
        assertThat(LibPklLibrary.INSTANCE.pkl_send_message(message.size, message)).isEqualTo(0)
      }

    assertThat(received).hasSize(1)
    assertThat(received.first()).isInstanceOf(CreateEvaluatorResponse::class.java)

    received.clear()

    encode(EvaluateRequest(1, 123, URI("repl:text"), """foo = 1""", null)).let { message ->
      assertThat(LibPklLibrary.INSTANCE.pkl_send_message(message.size, message)).isEqualTo(0)
    }

    assertThat(received).hasSize(1)
    assertThat(received.first()).isInstanceOf(EvaluateResponse::class.java)
    val response = received.removeFirst() as EvaluateResponse
    assertThat(response.evaluatorId).isEqualTo(123)
    received.clear()

    encode(CloseEvaluator(123)).let { message ->
      assertThat(LibPklLibrary.INSTANCE.pkl_send_message(message.size, message)).isEqualTo(0)
    }

    assertThat(received).hasSize(0)
    assertThat(LibPklLibrary.INSTANCE.pkl_close()).isEqualTo(0)
  }

  interface LibPklLibrary : Library {
    companion object {
      val INSTANCE: LibPklLibrary = Native.load("pkl", LibPklLibrary::class.java)
    }

    interface PklMessageResponseHandler : Callback {
      fun invoke(length: Int, message: Pointer?)
    }

    fun pkl_init(handler: PklMessageResponseHandler?): Int

    fun pkl_send_message(length: Int, message: ByteArray?): Int

    fun pkl_close(): Int
  }

  private fun encode(message: Message): ByteArray {
    ByteArrayOutputStream().use { os ->
      val packer = MessagePack.newDefaultPacker(os)
      val encoder = ServerMessagePackEncoder(packer)
      encoder.encode(message)
      return os.toByteArray()
    }
  }

  private fun decode(receivedBytes: ByteArray): Message? {
    val unpacker = MessagePack.newDefaultUnpacker(receivedBytes)
    return ServerMessagePackDecoder(unpacker).decode()
  }
}
