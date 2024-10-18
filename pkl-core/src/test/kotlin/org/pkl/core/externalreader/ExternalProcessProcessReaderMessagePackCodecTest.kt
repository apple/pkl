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
package org.pkl.core.externalreader

import java.io.PipedInputStream
import java.io.PipedOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.msgpack.core.MessagePack
import org.pkl.core.externalreader.ExternalReaderMessages.*
import org.pkl.core.messaging.*

class ExternalProcessProcessReaderMessagePackCodecTest {
  private val encoder: MessageEncoder
  private val decoder: MessageDecoder

  init {
    val inputStream = PipedInputStream()
    val outputStream = PipedOutputStream(inputStream)
    encoder = ExternalReaderMessagePackEncoder(MessagePack.newDefaultPacker(outputStream))
    decoder = ExternalReaderMessagePackDecoder(MessagePack.newDefaultUnpacker(inputStream))
  }

  private fun roundtrip(message: Message) {
    encoder.encode(message)
    val decoded = decoder.decode()
    assertThat(decoded).isEqualTo(message)
  }

  @Test
  fun `round-trip InitializeModuleReaderRequest`() {
    roundtrip(InitializeModuleReaderRequest(123, "my-scheme"))
  }

  @Test
  fun `round-trip InitializeResourceReaderRequest`() {
    roundtrip(InitializeResourceReaderRequest(123, "my-scheme"))
  }

  @Test
  fun `round-trip InitializeModuleReaderResponse`() {
    roundtrip(InitializeModuleReaderResponse(123, null))
    roundtrip(
      InitializeModuleReaderResponse(123, Messages.ModuleReaderSpec("my-scheme", true, true, true))
    )
  }

  @Test
  fun `round-trip InitializeResourceReaderResponse`() {
    roundtrip(InitializeResourceReaderResponse(123, null))
    roundtrip(
      InitializeResourceReaderResponse(123, Messages.ResourceReaderSpec("my-scheme", true, true))
    )
  }

  @Test
  fun `round-trip CloseExternalProcess`() {
    roundtrip(CloseExternalProcess())
  }
}
