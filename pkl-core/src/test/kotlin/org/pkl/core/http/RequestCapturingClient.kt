package org.pkl.core.http

import org.pkl.commons.test.FakeHttpResponse
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class RequestCapturingClient : HttpClient {
  lateinit var request: HttpRequest

  override fun <T : Any> send(
    request: HttpRequest,
    responseBodyHandler: HttpResponse.BodyHandler<T>
  ): HttpResponse<T> {
    this.request = request
    return FakeHttpResponse()
  }

  override fun close() {}
}
