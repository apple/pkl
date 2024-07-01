/**
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
package org.pkl.core.util

import java.io.IOException
import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.pkl.commons.test.FakeHttpResponse

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
    val response = FakeHttpResponse.withoutBody { statusCode = 200 }
    assertDoesNotThrow { HttpUtils.checkHasStatusCode200(response) }

    val response2 = FakeHttpResponse.withoutBody { statusCode = 404 }
    assertThrows<IOException> { HttpUtils.checkHasStatusCode200(response2) }
  }

  @Test
  fun setPort() {
    assertThrows<IllegalArgumentException> { HttpUtils.setPort(URI("https://example.com"), -1) }
    assertThrows<IllegalArgumentException> { HttpUtils.setPort(URI("https://example.com"), 65536) }
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
