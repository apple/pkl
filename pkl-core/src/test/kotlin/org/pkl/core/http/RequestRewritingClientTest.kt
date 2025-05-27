/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import java.net.http.HttpClient as JdkHttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatList
import org.junit.jupiter.api.Test
import org.pkl.core.util.IoUtils

class RequestRewritingClientTest {
  private val captured = RequestCapturingClient()
  private val client =
    RequestRewritingClient(
      "Pkl",
      Duration.ofSeconds(42),
      -1,
      captured,
      mapOf(URI("https://foo/") to URI("https://bar/")),
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
    val request =
      HttpRequest.newBuilder(exampleUri)
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
    val request = HttpRequest.newBuilder(exampleUri).timeout(Duration.ofMinutes(33)).build()

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
    val request = HttpRequest.newBuilder(exampleUri).version(JdkHttpClient.Version.HTTP_1_1).build()

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
    val request = HttpRequest.newBuilder(exampleUri).DELETE().build()

    client.send(request, BodyHandlers.discarding())

    assertThat(captured.request.method()).isEqualTo("DELETE")
  }

  @Test
  fun `leaves body publisher intact`() {
    val publisher = BodyPublishers.ofString("body")
    val request = HttpRequest.newBuilder(exampleUri).PUT(publisher).build()

    client.send(request, BodyHandlers.discarding())

    assertThat(captured.request.bodyPublisher().get()).isSameAs(publisher)
  }

  @Test
  fun `rewrites port 0 if test port is set`() {
    val captured = RequestCapturingClient()
    val client = RequestRewritingClient("Pkl", Duration.ofSeconds(42), 5000, captured, mapOf())
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

  @Test
  fun `matches rewrite rule`() {
    fun matchesRewriteRule(uri: String, rule: String) =
      assertThat(RequestRewritingClient.matchesRewriteRule(URI(uri), URI(rule))).`as`("$uri matches $rule")

    matchesRewriteRule("https://www.foo.com/path/to/qux.html", "https://www.foo.com/").isTrue
    matchesRewriteRule("HTTPS://www.foo.com/path/to/qux.html", "https://www.foo.com/").isTrue
    matchesRewriteRule("HTTPS://WWW.FOO.COM/path/to/qux.html", "https://www.foo.com/").isTrue

    matchesRewriteRule("https://www.foo.com/path/to/qux.html", "https://www.foo.com/path/").isTrue
    matchesRewriteRule("https://www.foo.com/path/to/qux.html", "https://www.foo.com/PATH/").isFalse
    matchesRewriteRule("https://www.foo.com", "https://www.foo.com/").isFalse

    matchesRewriteRule("https://www.foo.com/path/to/qux.html?foo&bar", "https://www.foo.com/path/to/qux.html?foo&bar").isTrue
    matchesRewriteRule("https://www.foo.com/path/to/qux.html?foo&baz", "https://www.foo.com/path/to/qux.html?foo&bar").isFalse

    matchesRewriteRule("https://www.foo.com/path/to/qux.html?foo&bar#qux", "https://www.foo.com/path/to/qux.html?foo&bar#q").isTrue
    matchesRewriteRule("https://www.foo.com/path/to/qux.html?foo&bar#qux", "https://www.foo.com/path/to/qux.html?foo&bar#w").isFalse
    matchesRewriteRule("https://www.foo.com/path/to/qux.html?foo&bar", "https://www.foo.com/path/to/qux.html?foo&bar#w").isFalse

    matchesRewriteRule("https:///", "https:///").isTrue
    matchesRewriteRule("https:///", "http:///").isFalse

    // userinfo
    matchesRewriteRule("https://foo@foo.com/", "http://foo.com/").isFalse
    matchesRewriteRule("https://foo@foo.com/", "http://foo@foo.com/").isFalse
    matchesRewriteRule("https://foo@foo.com/", "http://FOO@foo.com/").isFalse
  }

  @Test
  fun `rewrites URIs`() {
    fun rewrittenRequest(uri: String, rule: Pair<String, String>): String {
      val captured = RequestCapturingClient()
      val client = RequestRewritingClient("Pkl", Duration.ofSeconds(42), -1, captured, mapOf(URI(rule.first) to URI(rule.second)))
      val request = HttpRequest.newBuilder(URI(uri)).build()
      client.send(request, BodyHandlers.discarding())
      return captured.request.uri().toString()
    }

    assertThat(rewrittenRequest("https://foo.com/bar/baz", "https://foo.com/" to "https://bar.com/"))
      .isEqualTo("https://bar.com/bar/baz")

    assertThat(rewrittenRequest("https://FOO.COM/bar/baz", "https://foo.com/" to "https://bar.com/"))
      .isEqualTo("https://bar.com/bar/baz")

    assertThat(rewrittenRequest("https://foo.com/bar/baz", "https://foo.com/" to "https://bar.com/qux/baz/"))
      .isEqualTo("https://bar.com/qux/baz/bar/baz")

    assertThat(rewrittenRequest("https://foo.com/bar/baz", "https://foo.com/" to "https://bar.com/qux/baz/"))
      .isEqualTo("https://bar.com/qux/baz/bar/baz")

    assertThat(rewrittenRequest("https://foo.com/bar/baz?qux=foo", "https://foo.com/" to "https://bar.com/?qux=foo"))
      .isEqualTo("https://foo.com/bar/baz?qux=foo")
  }
}
