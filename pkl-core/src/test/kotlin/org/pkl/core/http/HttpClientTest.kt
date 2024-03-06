package org.pkl.core.http

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.test.FileTestUtils
import org.pkl.core.Release
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

class HttpClientTest {
  @Test
  fun `can build default client`() {
    val client = assertDoesNotThrow {
      HttpClient.builder().build()
    }
    
    assertThat(client).isInstanceOf(RequestRewritingClient::class.java)
    client as RequestRewritingClient
    
    val release = Release.current()
    assertThat(client.userAgent).isEqualTo("Pkl/${release.version()} (${release.os()}; ${release.flavor()})")
    assertThat(client.requestTimeout).isEqualTo(Duration.ofSeconds(60))
    
    assertThat(client.delegate).isInstanceOf(JdkHttpClient::class.java)
    val delegate = client.delegate as JdkHttpClient
    
    assertThat(delegate.underlying.connectTimeout()).hasValue(Duration.ofSeconds(60))
  }
  
  @Test
  fun `can build custom client`() {
    val client = HttpClient.builder()
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
  fun `can load certificates from file system`() {
    assertDoesNotThrow {
      HttpClient.builder().addCertificates(FileTestUtils.selfSignedCertificate).build()
    }
  }
  
  @Test
  fun `certificate file located on file system cannot be empty`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("certs.pem").createFile()

    val e = assertThrows<HttpClientInitException> {
      HttpClient.builder().addCertificates(file).build()
    }

    assertThat(e).hasMessageContaining("empty")
  }

  @Test
  fun `can load certificates from class path`() {
    assertDoesNotThrow {
      HttpClient.builder().addCertificates(javaClass.getResource("/org/pkl/certs/PklCARoots.pem")!!.toURI()).build()
    }
  }
  
  @Test
  fun `only allows loading jar and file certificate URIs`() {
    assertThrows<HttpClientInitException> {
      HttpClient.builder().addCertificates(URI("https://example.com"))
    }
  }

  @Test
  fun `certificate file located on class path cannot be empty`() {
    val uri = javaClass.getResource("emptyCerts.pem")!!.toURI()

    val e = assertThrows<HttpClientInitException> {
      HttpClient.builder().addCertificates(uri).build()
    }

    assertThat(e).hasMessageContaining("empty")
  }

  @Test
  fun `can load built-in certificates`() {
    assertDoesNotThrow {  
      HttpClient.builder().addBuiltInCertificates().build()
    }
  }
  
  @Test
  fun `can load certificates from Pkl user home cacerts directory`(@TempDir tempDir: Path) {
    val certFile = tempDir.resolve(".pkl")
      .resolve("cacerts")
      .createDirectories()
      .resolve("certs.pem")
    FileTestUtils.selfSignedCertificate.copyTo(certFile)
    
    assertDoesNotThrow {
      HttpClientBuilder(tempDir).addDefaultCliCertificates().build()
    }
  }
  
  @Test
  fun `loading certificates from cacerts directory falls back to built-in certificates`(@TempDir userHome: Path) {
    assertDoesNotThrow {
      HttpClientBuilder(userHome).addDefaultCliCertificates().build()
    }
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
