package org.pkl.core.http

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers

class LazyHttpClientTest {
  @Test
  fun `builds underlying client on first send`() {
    val client = HttpClient.builder()
      .addCertificates(javaClass.getResource("brokenCerts.pem")!!.toURI())
      .buildLazily()
    val request = HttpRequest.newBuilder(URI("https://example.com")).build()
    
    assertThrows<HttpClientInitException> {
      client.send(request, BodyHandlers.discarding())
    }
  }
  
  @Test
  fun `does not build underlying client unnecessarily`() {
    val client = HttpClient.builder()
      .addCertificates(javaClass.getResource("brokenCerts.pem")!!.toURI())
      .buildLazily()
    
    assertDoesNotThrow {
      client.close()
      client.close()
    }
  }
}
