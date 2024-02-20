package org.pkl.core.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.pkl.commons.test.FakeHttpResponse
import java.io.IOException
import java.net.URI
import java.net.URL

class HttpUtilsTest {
  @Test
  fun isHttpUrl() {
    assertThat(HttpUtils.isHttpUrl(URI("http://example.com"))).isTrue
    assertThat(HttpUtils.isHttpUrl(URI("https://example.com"))).isTrue
    assertThat(HttpUtils.isHttpUrl(URI("HtTpS://example.com"))).isTrue
    assertThat(HttpUtils.isHttpUrl(URI("file://example.com"))).isFalse

    assertThat(HttpUtils.isHttpUrl(URL("http://example.com"))).isTrue
    assertThat(HttpUtils.isHttpUrl(URL("https://example.com"))).isTrue
    assertThat(HttpUtils.isHttpUrl(URL("HtTpS://example.com"))).isTrue
    assertThat(HttpUtils.isHttpUrl(URL("file://example.com"))).isFalse
  }
  
  @Test
  fun require200StatusCode() {
    val response = FakeHttpResponse.withoutBody { 
      statusCode = 200 
    }
    assertDoesNotThrow {  
      HttpUtils.requireStatusCode200(response)
    }

    val response2 = FakeHttpResponse.withoutBody { 
      statusCode = 404 
    }
    assertThrows<IOException> {
      HttpUtils.requireStatusCode200(response2)
    }
  }
}
