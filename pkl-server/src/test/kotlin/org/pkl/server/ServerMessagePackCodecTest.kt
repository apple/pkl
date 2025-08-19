/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.msgpack.core.MessagePack
import org.pkl.core.messaging.Message
import org.pkl.core.messaging.MessageDecoder
import org.pkl.core.messaging.MessageEncoder
import org.pkl.core.messaging.Messages
import org.pkl.core.packages.Checksums

class ServerMessagePackCodecTest {
  private val encoder: MessageEncoder
  private val decoder: MessageDecoder

  init {
    val inputStream =
      PipedInputStream(10240) // use larger pipe size since large messages can be >1024 bytes
    val outputStream = PipedOutputStream(inputStream)
    encoder = ServerMessagePackEncoder(MessagePack.newDefaultPacker(outputStream))
    decoder = ServerMessagePackDecoder(MessagePack.newDefaultUnpacker(inputStream))
  }

  private fun roundtrip(message: Message) {
    encoder.encode(message)
    val decoded = decoder.decode()
    assertThat(decoded).isEqualTo(message)
  }

  @Test
  fun `round-trip CreateEvaluatorRequest`() {
    val resourceReader1 = Messages.ResourceReaderSpec("resourceReader1", true, true)
    val resourceReader2 = Messages.ResourceReaderSpec("resourceReader2", true, false)
    val moduleReader1 = Messages.ModuleReaderSpec("moduleReader1", true, true, true)
    val moduleReader2 = Messages.ModuleReaderSpec("moduleReader2", true, false, false)
    val externalReader = ExternalReader("external-cmd", listOf("arg1", "arg2"))
    roundtrip(
      CreateEvaluatorRequest(
        requestId = 123,
        allowedModules = listOf("pkl", "file", "https"),
        allowedResources = listOf("pkl", "file", "https", "resourceReader1", "resourceReader2"),
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
                            dependencies = emptyMap(),
                          )
                      ),
                  ),
                "baz" to
                  RemoteDependency(URI("package://localhost:0/baz@1.1.0"), Checksums("abc123")),
              ),
          ),
        http =
          Http(
            proxy = Proxy(URI("http://foo.com:1234"), listOf("bar", "baz")),
            caCertificates = byteArrayOf(1, 2, 3, 4),
            rewrites = mapOf(URI("https://foo.com/") to URI("https://bar.com/")),
          ),
        externalModuleReaders = mapOf("external" to externalReader, "external2" to externalReader),
        externalResourceReaders = mapOf("external" to externalReader),
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
        expr = "some + expression",
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
        error = null,
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
        frameUri = "file:///some/module.pkl",
      )
    )
  }
}
