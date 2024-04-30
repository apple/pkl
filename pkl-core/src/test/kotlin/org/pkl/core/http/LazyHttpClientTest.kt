package org.pkl.core.http

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.createTempFile
import org.pkl.commons.writeString
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path

class LazyHttpClientTest {
  @Test
  fun `builds underlying client on first send`(@TempDir tempDir: Path) {
    val certFile = tempDir.resolve("cert.pem").apply { writeString("broken") }
    val client = HttpClient.builder()
      .addCertificates(certFile)
      .buildLazily()
    val request = HttpRequest.newBuilder(URI("https://example.com")).build()
    
    assertThrows<HttpClientInitException> {
      client.send(request, BodyHandlers.discarding())
    }
  }
  
  @Test
  fun `does not build underlying client unnecessarily`(@TempDir tempDir: Path) {
    val certFile = tempDir.createTempFile().apply { writeString("broken") }
    val client = HttpClient.builder()
      .addCertificates(certFile)
      .buildLazily()
    
    assertDoesNotThrow {
      client.close()
      client.close()
    }
  }
}
