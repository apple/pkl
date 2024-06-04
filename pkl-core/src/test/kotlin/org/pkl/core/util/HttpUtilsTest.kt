package org.pkl.core.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.pkl.commons.test.FakeHttpResponse
import java.io.IOException
import java.net.URI

class HttpUtilsTest {
  @Test
  fun isHttpUrl() {
    assertThat(HttpUtils.isHttpUrl(URI("http://example.com"))).isTrue
    assertThat(HttpUtils.isHttpUrl(URI("https://example.com"))).isTrue
    assertThat(HttpUtils.isHttpUrl(URI("HtTpS://example.com"))).isTrue
    assertThat(HttpUtils.isHttpUrl(URI("file://example.com"))).isFalse

    assertThat(HttpUtils.isHttpUrl(URI("http://example.com").toURL())).isTrue
    assertThat(HttpUtils.isHttpUrl(URI("https://example.com").toURL())).isTrue
    assertThat(HttpUtils.isHttpUrl(URI("HtTpS://example.com").toURL())).isTrue
    assertThat(HttpUtils.isHttpUrl(URI("file://example.com").toURL())).isFalse
  }
  
  @Test
  fun checkHasStatusCode200() {
    val response = FakeHttpResponse.withoutBody { 
      statusCode = 200 
    }
    assertDoesNotThrow {  
      HttpUtils.checkHasStatusCode200(response)
    }

    val response2 = FakeHttpResponse.withoutBody { 
      statusCode = 404 
    }
    assertThrows<IOException> {
      HttpUtils.checkHasStatusCode200(response2)
    }
  }
  
  @Test
  fun setPort() {
    assertThrows<IllegalArgumentException> {
      HttpUtils.setPort(URI("https://example.com"), -1)
    }
    assertThrows<IllegalArgumentException> {
      HttpUtils.setPort(URI("https://example.com"), 65536)
    }
    assertThat(HttpUtils.setPort(URI("http://example.com"), 123))
      .isEqualTo(URI("http://example.com:123"))
    assertThat(HttpUtils.setPort(URI("http://example.com:456"), 123))
      .isEqualTo(URI("http://example.com:123"))
    assertThat(HttpUtils.setPort(URI("https://example.com/foo/bar.baz?query=1#fragment"), 123))
      .isEqualTo(URI("https://example.com:123/foo/bar.baz?query=1#fragment"))
    assertThat(HttpUtils.setPort(URI("https://example.com:456/foo/bar.baz?query=1#fragment"), 123))
      .isEqualTo(URI("https://example.com:123/foo/bar.baz?query=1#fragment"))
  }
}
