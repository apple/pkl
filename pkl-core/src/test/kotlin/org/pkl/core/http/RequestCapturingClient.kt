package org.pkl.core.http

import java.net.URI
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*
import javax.net.ssl.SSLSession

class RequestCapturingClient : HttpClient {
  lateinit var request: HttpRequest

  private fun <T: Any?> dummyResponse(request: HttpRequest) : HttpResponse<T> {
    return object : HttpResponse<T> {
      override fun statusCode(): Int = throw NotImplementedError()

      override fun request(): HttpRequest = request

      override fun previousResponse(): Optional<HttpResponse<T>> = throw NotImplementedError()

      override fun headers(): HttpHeaders = throw NotImplementedError()

      override fun body(): T = throw NotImplementedError()

      override fun sslSession(): Optional<SSLSession> = throw NotImplementedError()

      override fun uri(): URI = throw NotImplementedError()

      override fun version(): java.net.http.HttpClient.Version = throw NotImplementedError()
    }
  }

  override fun <T : Any?> send(
    request: HttpRequest,
    responseBodyHandler: HttpResponse.BodyHandler<T>
  ): HttpResponse<T> {
    this.request = request
    return dummyResponse(request)
  }

  override fun close() {}
}
