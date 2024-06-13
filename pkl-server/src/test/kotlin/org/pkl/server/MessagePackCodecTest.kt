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
package org.pkl.server

import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.util.regex.Pattern
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.msgpack.core.MessagePack
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings
import org.pkl.core.module.PathElement
import org.pkl.core.packages.Checksums

class MessagePackCodecTest {
  private val encoder: MessageEncoder
  private val decoder: MessageDecoder

  init {
    val inputStream = PipedInputStream()
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
  fun `round-trip CreateEvaluatorRequest`() {
    val resourceReader1 =
      ResourceReaderSpec(
        scheme = "resourceReader1",
        hasHierarchicalUris = true,
        isGlobbable = true,
      )
    val resourceReader2 =
      ResourceReaderSpec(
        scheme = "resourceReader2",
        hasHierarchicalUris = true,
        isGlobbable = false,
      )
    val moduleReader1 =
      ModuleReaderSpec(
        scheme = "moduleReader1",
        hasHierarchicalUris = true,
        isGlobbable = true,
        isLocal = true
      )
    val moduleReader2 =
      ModuleReaderSpec(
        scheme = "moduleReader2",
        hasHierarchicalUris = true,
        isGlobbable = false,
        isLocal = false
      )
    @Suppress("HttpUrlsUsage")
    roundtrip(
      CreateEvaluatorRequest(
        requestId = 123,
        allowedModules = listOf("pkl", "file", "https").map(Pattern::compile),
        allowedResources =
          listOf("pkl", "file", "https", "resourceReader1", "resourceReader2")
            .map(Pattern::compile),
        clientResourceReaders = listOf(resourceReader1, resourceReader2),
        clientModuleReaders = listOf(moduleReader1, moduleReader2),
        modulePaths = listOf(Path.of("some/path.zip"), Path.of("other/path.zip")),
        env = mapOf("KEY1" to "VALUE1", "KEY2" to "VALUE2"),
        properties = mapOf("property1" to "value1", "property2" to "value2"),
        timeout = Duration.ofSeconds(10),
        rootDir = Path.of("root/dir"),
        cacheDir = Path.of("cache/dir"),
        outputFormat = "pcf",
        project =
          Project(
            projectFileUri = URI("file:///dummy/PklProject"),
            packageUri = null,
            dependencies =
              mapOf(
                "foo" to
                  Project(
                    projectFileUri = URI("file:///foo"),
                    packageUri = URI("package://localhost:0/foo@1.0.0"),
                    dependencies =
                      mapOf(
                        "bar" to
                          Project(
                            projectFileUri = URI("file:///bar"),
                            packageUri = URI("package://localhost:0/bar@1.1.0"),
                            dependencies = emptyMap()
                          )
                      )
                  ),
                "baz" to
                  RemoteDependency(URI("package://localhost:0/baz@1.1.0"), Checksums("abc123"))
              )
          ),
        http =
          Http(
            proxy = PklEvaluatorSettings.Proxy(URI("http://foo.com:1234"), listOf("bar", "baz")),
            caCertificates = byteArrayOf(1, 2, 3, 4)
          )
      )
    )
  }

  @Test
  fun `round-trip CreateEvaluatorResponse`() {
    roundtrip(CreateEvaluatorResponse(requestId = 123, evaluatorId = 456, error = null))
  }

  @Test
  fun `round-trip CloseEvaluator`() {
    roundtrip(CloseEvaluator(evaluatorId = 123))
  }

  @Test
  fun `round-trip EvaluateRequest`() {
    roundtrip(
      EvaluateRequest(
        requestId = 123,
        evaluatorId = 456,
        moduleUri = URI("some/module.pkl"),
        moduleText = null,
        expr = "some + expression"
      )
    )
  }

  @Test
  fun `round-trip EvaluateResponse`() {
    roundtrip(
      EvaluateResponse(
        requestId = 123,
        evaluatorId = 456,
        result = byteArrayOf(1, 2, 3, 4, 5),
        error = null
      )
    )
  }

  @Test
  fun `round-trip LogMessage`() {
    roundtrip(
      LogMessage(
        evaluatorId = 123,
        level = 0,
        message = "Hello, world!",
        frameUri = "file:///some/module.pkl"
      )
    )
  }

  @Test
  fun `round-trip ReadResourceRequest`() {
    roundtrip(
      ReadResourceRequest(requestId = 123, evaluatorId = 456, uri = URI("some/resource.json"))
    )
  }

  @Test
  fun `round-trip ReadResourceResponse`() {
    roundtrip(
      ReadResourceResponse(
        requestId = 123,
        evaluatorId = 456,
        contents = byteArrayOf(1, 2, 3, 4, 5),
        error = null
      )
    )
  }

  @Test
  fun `round-trip ReadModuleRequest`() {
    roundtrip(ReadModuleRequest(requestId = 123, evaluatorId = 456, uri = URI("some/module.pkl")))
  }

  @Test
  fun `round-trip ReadModuleResponse`() {
    roundtrip(
      ReadModuleResponse(requestId = 123, evaluatorId = 456, contents = "x = 42", error = null)
    )
  }

  @Test
  fun `round-trip ListModulesRequest`() {
    roundtrip(ListModulesRequest(requestId = 135, evaluatorId = 246, uri = URI("foo:/bar/baz/biz")))
  }

  @Test
  fun `round-trip ListModulesResponse`() {
    roundtrip(
      ListModulesResponse(
        requestId = 123,
        evaluatorId = 234,
        pathElements = listOf(PathElement("foo", true), PathElement("bar", false)),
        error = null
      )
    )
    roundtrip(
      ListModulesResponse(
        requestId = 123,
        evaluatorId = 234,
        pathElements = null,
        error = "Something dun went wrong"
      )
    )
  }

  @Test
  fun `round-trip ListResourcesRequest`() {
    roundtrip(ListResourcesRequest(requestId = 987, evaluatorId = 1359, uri = URI("bar:/bazzy")))
  }

  @Test
  fun `round-trip ListResourcesResponse`() {
    roundtrip(
      ListResourcesResponse(
        requestId = 3851,
        evaluatorId = 3019,
        pathElements = listOf(PathElement("foo", true), PathElement("bar", false)),
        error = null
      )
    )
    roundtrip(
      ListResourcesResponse(
        requestId = 3851,
        evaluatorId = 3019,
        pathElements = null,
        error = "something went wrong"
      )
    )
  }

  @Test
  fun `decode request with missing request ID`() {
    val bytes =
      MessagePack.newDefaultBufferPacker()
        .apply {
          packArrayHeader(2)
          packInt(MessageType.CREATE_EVALUATOR_REQUEST.code)
          packMapHeader(1)
          packString("clientResourceSchemes")
          packArrayHeader(0)
        }
        .toByteArray()

    val decoder = MessagePackDecoder(MessagePack.newDefaultUnpacker(bytes))
    val exception = assertThrows<DecodeException> { decoder.decode() }
    assertThat(exception.message).contains("requestId")
  }

  @Test
  fun `decode invalid message header`() {
    val bytes = MessagePack.newDefaultBufferPacker().apply { packInt(2) }.toByteArray()

    val decoder = MessagePackDecoder(MessagePack.newDefaultUnpacker(bytes))
    val exception = assertThrows<DecodeException> { decoder.decode() }
    assertThat(exception).hasMessage("Malformed message header.")
    assertThat(exception).hasRootCauseMessage("Expected Array, but got Integer (02)")
  }
}
