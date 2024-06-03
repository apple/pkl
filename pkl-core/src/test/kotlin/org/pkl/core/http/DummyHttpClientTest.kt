package org.pkl.core.http

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class DummyHttpClientTest {
  @Test
  fun `refuses to send messages`() {
    val client = HttpClient.dummyClient()
    val request = HttpRequest.newBuilder(URI("https://example.com")).build()

    assertThrows<AssertionError> {
      client.send(request, HttpResponse.BodyHandlers.discarding())
    }

    assertThrows<AssertionError> {
      client.send(request, HttpResponse.BodyHandlers.discarding())
    }
  }

  @Test
  fun `can be closed`() {
    val client = HttpClient.dummyClient()

    assertDoesNotThrow {
      client.close()
      client.close()
    }
  }
}
