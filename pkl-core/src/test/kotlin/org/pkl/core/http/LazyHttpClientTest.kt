/*
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
package org.pkl.core.http

import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.createTempFile
import org.pkl.commons.writeString

class LazyHttpClientTest {
  @Test
  fun `builds underlying client on first send`(@TempDir tempDir: Path) {
    val certFile = tempDir.resolve("cert.pem").apply { writeString("broken") }
    val client = HttpClient.builder().addCertificates(certFile).buildLazily()
    val request = HttpRequest.newBuilder(URI("https://example.com")).build()

    assertThrows<HttpClientInitException> { client.send(request, BodyHandlers.discarding()) }
  }

  @Test
  fun `does not build underlying client unnecessarily`(@TempDir tempDir: Path) {
    val certFile = tempDir.createTempFile().apply { writeString("broken") }
    val client = HttpClient.builder().addCertificates(certFile).buildLazily()

    assertDoesNotThrow {
      client.close()
      client.close()
    }
  }
}
