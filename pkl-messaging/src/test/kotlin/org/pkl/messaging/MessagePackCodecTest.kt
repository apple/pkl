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
package org.pkl.messaging

import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.msgpack.core.MessagePack
import org.pkl.messaging.Messages.*
import org.pkl.messaging.externalreader.ExternalReader
import org.pkl.messaging.externalreader.ModuleReaderSpec
import org.pkl.messaging.externalreader.ResourceReaderSpec

class MessagePackCodecTest {
  private val encoder: MessageEncoder
  private val decoder: MessageDecoder

  init {
    // The encoder and decoder run on the same thread, so the pipe must be able to buffer an
    // entire encoded message. The default buffer is only 1024 bytes.
    val inputStream = PipedInputStream(64 * 1024)
    val outputStream = PipedOutputStream(inputStream)
    encoder = MessagePackEncoder(MessagePack.newDefaultPacker(outputStream))
    decoder = MessagePackDecoder(MessagePack.newDefaultUnpacker(inputStream))
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
    roundtrip(InitializeModuleReaderResponse(123, ModuleReaderSpec("my-scheme", true, true, true)))
  }

  @Test
  fun `round-trip InitializeResourceReaderResponse`() {
    roundtrip(InitializeResourceReaderResponse(123, null))
    roundtrip(InitializeResourceReaderResponse(123, ResourceReaderSpec("my-scheme", true, true)))
  }

  @Test
  fun `round-trip CloseExternalProcess`() {
    roundtrip(CloseExternalProcess())
  }

  @Test
  fun `round-trip CreateEvaluatorRequest`() {
    val resourceReader1 = ResourceReaderSpec("resourceReader1", true, true)
    val resourceReader2 = ResourceReaderSpec("resourceReader2", true, false)
    val moduleReader1 = ModuleReaderSpec("moduleReader1", true, true, true)
    val moduleReader2 = ModuleReaderSpec("moduleReader2", true, false, false)
    val externalReader = ExternalReader("external-cmd", listOf("arg1", "arg2"), "/foo/bar")
    roundtrip(
      CreateEvaluatorRequest(
        123,
        listOf("pkl", "file", "https"),
        listOf("pkl", "file", "https", "resourceReader1", "resourceReader2"),
        listOf(moduleReader1, moduleReader2),
        listOf(resourceReader1, resourceReader2),
        listOf(Path.of("some/path.zip"), Path.of("other/path.zip")),
        mapOf("KEY1" to "VALUE1", "KEY2" to "VALUE2"),
        mapOf("property1" to "value1", "property2" to "value2"),
        Duration.ofSeconds(10),
        Path.of("root/dir"),
        Path.of("cache/dir"),
        "pcf",
        Project(
          URI("file:///dummy/PklProject"),
          null,
          mapOf(
            "foo" to
              Project(
                URI("file:///foo"),
                URI("package://localhost:0/foo@1.0.0"),
                mapOf(
                  "bar" to
                    Project(URI("file:///bar"), URI("package://localhost:0/bar@1.1.0"), emptyMap())
                ),
              ),
            "baz" to RemoteDependency(URI("package://localhost:0/baz@1.1.0"), Checksums("abc123")),
          ),
        ),
        Http(
          byteArrayOf(1, 2, 3, 4),
          Proxy(URI("http://foo.com:1234"), listOf("bar", "baz")),
          mapOf(URI("https://foo.com/") to URI("https://bar.com/")),
          mapOf("**" to mapOf("X-Foo" to listOf("Foo", "Bar"))),
        ),
        mapOf("external" to externalReader, "external2" to externalReader),
        mapOf("external" to externalReader),
        "pretty",
      )
    )
  }

  @Test
  fun `round-trip CreateEvaluatorResponse`() {
    roundtrip(CreateEvaluatorResponse(123, 456, null))
  }

  @Test
  fun `round-trip CloseEvaluator`() {
    roundtrip(CloseEvaluator(123))
  }

  @Test
  fun `round-trip EvaluateRequest`() {
    roundtrip(EvaluateRequest(123, 456, URI("some/module.pkl"), null, "some + expression"))
  }

  @Test
  fun `round-trip EvaluateResponse`() {
    roundtrip(EvaluateResponse(123, 456, byteArrayOf(1, 2, 3, 4, 5), null))
  }

  @Test
  fun `round-trip LogMessage`() {
    roundtrip(LogMessage(123, 0, "Hello, world!", "file:///some/module.pkl"))
  }

  @Test
  fun `round-trip ReadResourceRequest`() {
    roundtrip(ReadResourceRequest(123, 456, URI("foo:/bar.pkl")))
  }

  @Test
  fun `round-trip ReadResourceResponse`() {
    roundtrip(ReadResourceResponse(123, 456, byteArrayOf(1, 2, 3, 4, 5), null))
    roundtrip(ReadResourceResponse(123, 456, byteArrayOf(), null))
    roundtrip(ReadResourceResponse(123, 456, null, "something went wrong"))
  }

  @Test
  fun `round-trip ReadModuleRequest`() {
    roundtrip(ReadModuleRequest(123, 456, URI("foo:/bar.pkl")))
  }

  @Test
  fun `round-trip ReadModuleResponse`() {
    roundtrip(ReadModuleResponse(123, 456, "foo = 1", null))
    roundtrip(ReadModuleResponse(123, 456, null, "something went wrong"))
  }

  @Test
  fun `round-trip ListResourcesRequest`() {
    roundtrip(ListResourcesRequest(123, 456, URI("foo:/bar/")))
  }

  @Test
  fun `round-trip ListResourcesResponse`() {
    roundtrip(
      ListResourcesResponse(
        123,
        456,
        listOf(PathElement("foo.txt", false), PathElement("bar", true)),
        null,
      )
    )
    roundtrip(ListResourcesResponse(123, 456, null, "something went wrong"))
  }

  @Test
  fun `round-trip ListModulesRequest`() {
    roundtrip(ListModulesRequest(123, 456, URI("foo:/bar/")))
  }

  @Test
  fun `round-trip ListModulesResponse`() {
    roundtrip(
      ListModulesResponse(
        123,
        456,
        listOf(PathElement("foo.pkl", false), PathElement("bar", true)),
        null,
      )
    )
    roundtrip(ListModulesResponse(123, 456, null, "something went wrong"))
  }
}
