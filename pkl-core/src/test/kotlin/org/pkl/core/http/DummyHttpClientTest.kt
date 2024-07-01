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
package org.pkl.core.http

import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class DummyHttpClientTest {
  @Test
  fun `refuses to send messages`() {
    val client = HttpClient.dummyClient()
    val request = HttpRequest.newBuilder(URI("https://example.com")).build()

    assertThrows<AssertionError> { client.send(request, HttpResponse.BodyHandlers.discarding()) }

    assertThrows<AssertionError> { client.send(request, HttpResponse.BodyHandlers.discarding()) }
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
