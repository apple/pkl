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
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createFile
import kotlin.io.path.readBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.test.FileTestUtils
import org.pkl.core.Release

class HttpClientTest {
  @Test
  fun `can build default client`() {
    val client = assertDoesNotThrow { HttpClient.builder().build() }

    assertThat(client).isInstanceOf(RequestRewritingClient::class.java)
    client as RequestRewritingClient

    val release = Release.current()
    assertThat(client.userAgent)
      .isEqualTo("Pkl/${release.version()} (${release.os()}; ${release.flavor()})")
    assertThat(client.requestTimeout).isEqualTo(Duration.ofSeconds(60))

    assertThat(client.delegate).isInstanceOf(JdkHttpClient::class.java)
    val delegate = client.delegate as JdkHttpClient

    assertThat(delegate.underlying.connectTimeout()).hasValue(Duration.ofSeconds(60))
  }

  @Test
  fun `can build custom client`() {
    val client =
      HttpClient.builder()
        .setUserAgent("Agent 1")
        .setRequestTimeout(Duration.ofHours(86))
        .setConnectTimeout(Duration.ofMinutes(42))
        .build() as RequestRewritingClient

    assertThat(client.userAgent).isEqualTo("Agent 1")
    assertThat(client.requestTimeout).isEqualTo(Duration.ofHours(86))

    val delegate = client.delegate as JdkHttpClient
    assertThat(delegate.underlying.connectTimeout()).hasValue(Duration.ofMinutes(42))
  }

  @Test
  fun `can load certificates from regular file`() {
    assertDoesNotThrow {
      HttpClient.builder().addCertificates(FileTestUtils.selfSignedCertificate).build()
    }
  }

  @Test
  fun `can load certificates from a byte array`() {
    assertDoesNotThrow {
      HttpClient.builder().addCertificates(FileTestUtils.selfSignedCertificate.readBytes()).build()
    }
  }

  @Test
  fun `certificate file cannot be empty`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("certs.pem").createFile()

    val e =
      assertThrows<HttpClientInitException> { HttpClient.builder().addCertificates(file).build() }

    assertThat(e).hasMessageContaining("empty")
  }

  @Test
  fun `can load built-in certificates`() {
    assertDoesNotThrow { HttpClient.builder().build() }
  }

  @Test
  fun `can be closed multiple times`() {
    val client = HttpClient.builder().build()

    assertDoesNotThrow {
      client.close()
      client.close()
    }
  }

  @Test
  fun `refuses to send messages once closed`() {
    val client = HttpClient.builder().build()
    val request = HttpRequest.newBuilder(URI("https://example.com")).build()

    client.close()

    assertThrows<IllegalStateException> {
      client.send(request, HttpResponse.BodyHandlers.discarding())
    }
    assertThrows<IllegalStateException> {
      client.send(request, HttpResponse.BodyHandlers.discarding())
    }
  }
}
