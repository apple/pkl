/*
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
package org.pkl.commons.test

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*
import javax.net.ssl.SSLSession

class FakeHttpResponse<T : Any> : HttpResponse<T> {
  companion object {
    fun <T : Any> withBody(block: FakeHttpResponse<T>.() -> Unit): FakeHttpResponse<T> =
      FakeHttpResponse<T>().apply(block)

    fun withoutBody(block: FakeHttpResponse<Unit>.() -> Unit): FakeHttpResponse<Unit> =
      FakeHttpResponse<Unit>().apply { body = Unit }.apply(block)
  }

  var statusCode: Int = 200
  var request: HttpRequest = HttpRequest.newBuilder().uri(URI("https://example.com")).build()
  var uri: URI = URI("https://example.com")
  var version: HttpClient.Version = HttpClient.Version.HTTP_2

  lateinit var headers: HttpHeaders
  lateinit var body: T

  override fun statusCode(): Int = statusCode

  override fun request(): HttpRequest = request

  override fun previousResponse(): Optional<HttpResponse<T>> = Optional.empty()

  override fun headers(): HttpHeaders = headers

  override fun body(): T = body

  override fun sslSession(): Optional<SSLSession> = Optional.empty()

  override fun uri(): URI = uri

  override fun version(): HttpClient.Version = version
}
