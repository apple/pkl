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
package org.pkl.core.messaging

import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.msgpack.core.MessagePack
import org.pkl.core.messaging.Messages.*
import org.pkl.core.module.PathElement

class MessagePackCodecTest {
  private val encoder: MessageEncoder
  private val decoder: MessageDecoder

  init {
    val inputStream = PipedInputStream()
    val outputStream = PipedOutputStream(inputStream)
    encoder = BaseMessagePackEncoder(MessagePack.newDefaultPacker(outputStream))
    decoder = BaseMessagePackDecoder(MessagePack.newDefaultUnpacker(inputStream))
  }

  private fun roundtrip(message: Message) {
    encoder.encode(message)
    val decoded = decoder.decode()
    assertThat(decoded).isEqualTo(message)
  }

  @Test
  fun `round-trip ReadResourceRequest`() {
    roundtrip(ReadResourceRequest(123, 456, URI("some/resource.json")))
  }

  @Test
  fun `round-trip ReadResourceResponse`() {
    roundtrip(ReadResourceResponse(123, 456, byteArrayOf(1, 2, 3, 4, 5), null))
  }

  @Test
  fun `round-trip ReadModuleRequest`() {
    roundtrip(ReadModuleRequest(123, 456, URI("some/module.pkl")))
  }

  @Test
  fun `round-trip ReadModuleResponse`() {
    roundtrip(ReadModuleResponse(123, 456, "x = 42", null))
  }

  @Test
  fun `round-trip ListModulesRequest`() {
    roundtrip(ListModulesRequest(135, 246, URI("foo:/bar/baz/biz")))
  }

  @Test
  fun `round-trip ListModulesResponse`() {
    roundtrip(
      ListModulesResponse(
        123,
        234,
        listOf(PathElement("foo", true), PathElement("bar", false)),
        null
      )
    )
    roundtrip(ListModulesResponse(123, 234, null, "Something dun went wrong"))
  }

  @Test
  fun `round-trip ListResourcesRequest`() {
    roundtrip(ListResourcesRequest(987, 1359, URI("bar:/bazzy")))
  }

  @Test
  fun `round-trip ListResourcesResponse`() {
    roundtrip(
      ListResourcesResponse(
        3851,
        3019,
        listOf(PathElement("foo", true), PathElement("bar", false)),
        null
      )
    )
    roundtrip(ListResourcesResponse(3851, 3019, null, "something went wrong"))
  }

  @Test
  fun `decode request with missing request ID`() {
    val bytes =
      MessagePack.newDefaultBufferPacker()
        .apply {
          packArrayHeader(2)
          packInt(Message.Type.LIST_RESOURCES_REQUEST.code)
          packMapHeader(1)
          packString("uri")
          packString("file:/test")
        }
        .toByteArray()

    val decoder = BaseMessagePackDecoder(MessagePack.newDefaultUnpacker(bytes))
    val exception = assertThrows<DecodeException> { decoder.decode() }
    assertThat(exception.message).contains("requestId")
  }

  @Test
  fun `decode invalid message header`() {
    val bytes = MessagePack.newDefaultBufferPacker().apply { packInt(2) }.toByteArray()

    val decoder = BaseMessagePackDecoder(MessagePack.newDefaultUnpacker(bytes))
    val exception = assertThrows<DecodeException> { decoder.decode() }
    assertThat(exception).hasMessage("Malformed message header.")
    assertThat(exception).hasRootCauseMessage("Expected Array, but got Integer (02)")
  }
}
