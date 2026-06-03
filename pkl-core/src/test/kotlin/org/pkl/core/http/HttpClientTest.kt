/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.permanentRedirect
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.absolutePathString
import kotlin.io.path.createFile
import kotlin.io.path.readBytes
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
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
      HttpClient.builder().addCertificates(FileTestUtils.selfSignedCertificatePem).build()
    }
  }

  @Test
  fun `can load certificates from a byte array`() {
    assertDoesNotThrow {
      HttpClient.builder()
        .addCertificates(FileTestUtils.selfSignedCertificatePem.readBytes())
        .build()
    }
  }

  @Test
  fun `certificate file cannot be empty`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("certs.pem").createFile()

    val e = assertThrows<HttpClientException> { HttpClient.builder().addCertificates(file).build() }

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
      client.send(request, HttpResponse.BodyHandlers.discarding(), NoopChecker)
    }
    assertThrows<IllegalStateException> {
      client.send(request, HttpResponse.BodyHandlers.discarding(), NoopChecker)
    }
  }

  @Nested
  inner class RedirectsTest {
    // incorrect diagnostic
    @Suppress("JUnitMalformedDeclaration")
    @RegisterExtension
    val wireMock: WireMockExtension =
      WireMockExtension.newInstance()
        .configureStaticDsl(true)
        .options(
          wireMockConfig().apply {
            dynamicPort()
            dynamicHttpsPort()
            keystorePath(FileTestUtils.selfSignedCertificateP12.absolutePathString())
            keystorePassword(FileTestUtils.selfSignedCertificatePassword)
            keystoreType("PKCS12")
          }
        )
        .build()

    @Test
    fun `follows redirects`() {
      stubFor(get(urlEqualTo("/foo.pkl")).willReturn(permanentRedirect("/bar.pkl")))
      stubFor(get(urlEqualTo("/bar.pkl")).willReturn(ok("bar = 1")))
      val client = HttpClient.builder().build()
      val request =
        HttpRequest.newBuilder(URI("${wireMock.runtimeInfo.httpBaseUrl}/foo.pkl")).build()
      val response = client.send(request, HttpResponse.BodyHandlers.ofString(), NoopChecker)
      assert(response.body() == "bar = 1")
      verify(getRequestedFor(urlEqualTo("/foo.pkl")))
      verify(getRequestedFor(urlEqualTo("/bar.pkl")))
    }

    @Test
    fun `preserves configured headers across redirects`() {
      stubFor(get(urlEqualTo("/foo.pkl")).willReturn(permanentRedirect("/bar.pkl")))
      stubFor(get(urlEqualTo("/bar.pkl")).willReturn(ok("bar = 1")))

      val client =
        HttpClient.builder().addHeaders("**", mapOf("x-foo" to listOf("foo value"))).build()
      val request =
        HttpRequest.newBuilder(URI("${wireMock.runtimeInfo.httpBaseUrl}/foo.pkl")).build()
      val response = client.send(request, HttpResponse.BodyHandlers.ofString(), NoopChecker)
      assert(response.body() == "bar = 1")
      verify(getRequestedFor(urlEqualTo("/foo.pkl")).withHeader("x-foo", matching("foo value")))
      verify(getRequestedFor(urlEqualTo("/bar.pkl")).withHeader("x-foo", matching("foo value")))
    }

    @Test
    fun `respects configured rewrites across redirects`() {
      stubFor(get(urlEqualTo("/foo.pkl")).willReturn(permanentRedirect("/orig/bar.pkl")))
      stubFor(get(urlEqualTo("/rewritten/bar.pkl")).willReturn(ok()))

      val client =
        HttpClient.builder()
          .addRewrite(
            URI("${wireMock.runtimeInfo.httpBaseUrl}/orig/"),
            URI("${wireMock.runtimeInfo.httpBaseUrl}/rewritten/"),
          )
          .build()
      val request =
        HttpRequest.newBuilder(URI("${wireMock.runtimeInfo.httpBaseUrl}/foo.pkl")).build()
      client.send(request, HttpResponse.BodyHandlers.ofString(), NoopChecker)
      verify(getRequestedFor(urlEqualTo("/foo.pkl")))
      verify(getRequestedFor(urlEqualTo("/rewritten/bar.pkl")))
    }

    @Test
    fun `cannot downgrade HTTPS to HTTP`() {
      stubFor(
        get(urlEqualTo("/foo.pkl"))
          .willReturn(permanentRedirect("${wireMock.runtimeInfo.httpBaseUrl}/bar.pkl"))
      )

      val client =
        HttpClient.builder()
          .addCertificates(FileTestUtils.selfSignedCertificatePem)
          .addHeaders("**", mapOf("x-foo" to listOf("foo value")))
          .build()
      val request =
        HttpRequest.newBuilder(URI("${wireMock.runtimeInfo.httpsBaseUrl}/foo.pkl")).build()
      assertThatCode { client.send(request, HttpResponse.BodyHandlers.ofString(), NoopChecker) }
        .hasMessageContaining("Cannot follow redirect from 'https:' URL to 'http:' URL")
    }

    @Test
    fun `infinite redirects fail with VmException`() {
      stubFor(get(urlEqualTo("/foo.pkl")).willReturn(permanentRedirect("/bar.pkl")))
      stubFor(get(urlEqualTo("/bar.pkl")).willReturn(permanentRedirect("/foo.pkl")))
      val client = HttpClient.builder().build()
      val request =
        HttpRequest.newBuilder(URI("${wireMock.runtimeInfo.httpBaseUrl}/foo.pkl")).build()
      assertThatCode { client.send(request, HttpResponse.BodyHandlers.ofString(), NoopChecker) }
        .hasMessageContaining("Too many redirects")
      verify(getRequestedFor(urlEqualTo("/foo.pkl")))
      verify(getRequestedFor(urlEqualTo("/bar.pkl")))
    }

    @Test
    fun `invalid redirect URI fails with VmException`() {
      stubFor(get(urlEqualTo("/foo.pkl")).willReturn(permanentRedirect("http://not a valid url/")))
      val client = HttpClient.builder().build()
      val request =
        HttpRequest.newBuilder(URI("${wireMock.runtimeInfo.httpBaseUrl}/foo.pkl")).build()
      assertThatCode { client.send(request, HttpResponse.BodyHandlers.ofString(), NoopChecker) }
        .hasMessageContaining(
          """
          Cannot follow HTTP redirect because the response Location header has a malformed URI.
          """
            .trimIndent()
        )
      verify(getRequestedFor(urlEqualTo("/foo.pkl")))
    }

    @Test
    fun `checks each URL before making a request`() {
      stubFor(get(urlEqualTo("/foo.pkl")).willReturn(permanentRedirect("/bar.pkl")))
      stubFor(get(urlEqualTo("/bar.pkl")).willReturn(permanentRedirect("/qux.pkl")))
      stubFor(get(urlEqualTo("/qux.pkl")).willReturn(ok()))
      val checkedUrls = mutableListOf<URI>()
      val checker = HttpClient.HttpRequestChecker { uri -> checkedUrls.add(uri) }
      val client = HttpClient.builder().build()
      val request =
        HttpRequest.newBuilder(URI("${wireMock.runtimeInfo.httpBaseUrl}/foo.pkl")).build()
      client.send(request, HttpResponse.BodyHandlers.ofString(), checker)
      assertThat(checkedUrls).hasSize(3)
      assertThat(checkedUrls)
        .usingRecursiveComparison()
        .isEqualTo(
          listOf(
            URI("${wireMock.runtimeInfo.httpBaseUrl}/foo.pkl"),
            URI("${wireMock.runtimeInfo.httpBaseUrl}/bar.pkl"),
            URI("${wireMock.runtimeInfo.httpBaseUrl}/qux.pkl"),
          )
        )
    }
  }
}
