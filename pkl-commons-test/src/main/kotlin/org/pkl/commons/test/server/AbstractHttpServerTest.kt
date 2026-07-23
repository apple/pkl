/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.commons.test.server

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.pkl.server.EvaluateRequest
import org.pkl.server.EvaluateResponse
import org.pkl.server.Http

/**
 * Extends [AbstractServerTest] with HTTP tests backed by a WireMock server.
 *
 * Some transports can't run these at all (e.g. `LibPklServerTest` - a real outbound HTTP request
 * causes `doClose` to hang for about a minute, suspected to be an unclosed `HttpClient`'s default
 * thread pool blocking native isolate teardown) - those extend [AbstractServerTest] directly
 * instead of this class.
 */
abstract class AbstractHttpServerTest : AbstractServerTest() {
  @Nested
  @WireMockTest
  inner class HttpTests {
    @Test
    fun `http headers`(wwRuntimeInfo: WireMockRuntimeInfo) {
      stubFor(get(urlEqualTo("/foo.pkl")).willReturn(ok("foo = 1")))
      val evaluatorId =
        client.sendCreateEvaluatorRequest(
          http =
            Http(
              caCertificates = null,
              proxy = null,
              rewrites = null,
              headers = mapOf("**" to mapOf("X-Foo" to listOf("Foo"))),
            )
        )
      client.send(
        EvaluateRequest(
          1,
          evaluatorId,
          URI("repl:text"),
          "res = import(\"${wwRuntimeInfo.httpBaseUrl}/foo.pkl\")",
          "output.text",
        )
      )
      val response = client.receive<EvaluateResponse>()
      assertThat(response.error).isNull()
      verify(getRequestedFor(urlEqualTo("/foo.pkl")).withHeader("X-Foo", equalTo("Foo")))
    }
  }
}
