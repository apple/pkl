package org.pkl.core.http

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatList
import org.junit.jupiter.api.Test
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.net.http.HttpClient as JdkHttpClient

class RequestRewritingClientTest {
  private val captured = RequestCapturingClient()
  private val client = RequestRewritingClient(
      "Pkl",
      Duration.ofSeconds(42),
      -1,
      ProxySelector.getDefault(),
      captured
  )
  private val exampleUri = URI("https://example.com/foo/bar.html")
  private val exampleRequest = HttpRequest.newBuilder(exampleUri).build()

  @Test
  fun `fills in missing User-Agent header`() {
    client.send(exampleRequest, BodyHandlers.discarding())
    
    assertThatList(captured.request.headers().allValues("User-Agent")).containsOnly("Pkl")
  }

  @Test
  fun `overrides existing User-Agent headers`() {
    val request = HttpRequest.newBuilder(exampleUri)
      .header("User-Agent", "Agent 1")
      .header("User-Agent", "Agent 2")
      .build()
    
    client.send(request, BodyHandlers.discarding())

    assertThatList(captured.request.headers().allValues("User-Agent")).containsOnly("Pkl")
  }

  @Test
  fun `fills in missing request timeout`() {
    client.send(exampleRequest, BodyHandlers.discarding())
    
    assertThat(captured.request.timeout()).hasValue(Duration.ofSeconds(42))
  }

  @Test
  fun `leaves existing request timeout intact`() {
    val request = HttpRequest.newBuilder(exampleUri)
      .timeout(Duration.ofMinutes(33))
      .build()
    
    client.send(request, BodyHandlers.discarding())

    assertThat(captured.request.timeout()).hasValue(Duration.ofMinutes(33))
  }

  @Test
  fun `fills in missing HTTP version`() {
    client.send(exampleRequest, BodyHandlers.discarding())

    assertThat(captured.request.version()).hasValue(JdkHttpClient.Version.HTTP_2)
  }

  @Test
  fun `leaves existing HTTP version intact`() {
    val request = HttpRequest.newBuilder(exampleUri)
      .version(JdkHttpClient.Version.HTTP_1_1)
      .build()
    
    client.send(request, BodyHandlers.discarding())
    
    assertThat(captured.request.version()).hasValue(JdkHttpClient.Version.HTTP_1_1)
  }

  @Test
  fun `leaves default method intact`() {
    val request = HttpRequest.newBuilder(exampleUri).build()

    client.send(request, BodyHandlers.discarding())

    assertThat(captured.request.method()).isEqualTo("GET")
  }
  
  @Test
  fun `leaves explicit method intact`() {
    val request = HttpRequest.newBuilder(exampleUri)
      .DELETE()
      .build()
    
    client.send(request, BodyHandlers.discarding())
    
    assertThat(captured.request.method()).isEqualTo("DELETE")
  }
  
  @Test
  fun `leaves body publisher intact`() {
    val publisher = BodyPublishers.ofString("body")
    val request = HttpRequest.newBuilder(exampleUri)
      .PUT(publisher)
      .build()
    
    client.send(request, BodyHandlers.discarding())
    
    assertThat(captured.request.bodyPublisher().get()).isSameAs(publisher)
  }
  
  @Test
  fun `rewrites port 0 if test port is set`() {
    val captured = RequestCapturingClient()
    val client = RequestRewritingClient(
        "Pkl",
        Duration.ofSeconds(42),
        5000,
        ProxySelector.getDefault(),
        captured
    )
    val request = HttpRequest.newBuilder(URI("https://example.com:0")).build()

    client.send(request, BodyHandlers.discarding())

    assertThat(captured.request.uri().port).isEqualTo(5000)
  }
  
  @Test
  fun `leaves port 0 intact if no test port is set`() {
    val request = HttpRequest.newBuilder(URI("https://example.com:0")).build()

    client.send(request, BodyHandlers.discarding())
    
    assertThat(captured.request.uri().port).isEqualTo(0)
  }
}


