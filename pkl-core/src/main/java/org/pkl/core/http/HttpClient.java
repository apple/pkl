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
package org.pkl.core.http;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import javax.net.ssl.SSLContext;

/**
 * An HTTP client.
 *
 * <p>To create a new HTTP client, use a {@linkplain #builder() builder}. To send {@linkplain
 * HttpRequest requests} and retrieve their {@linkplain HttpResponse responses}, use {@link #send}.
 * To release resources held by the client in a timely manner, use {@link #close}.
 *
 * <p>HTTP clients are thread-safe. Each client maintains its own connection pool and {@link
 * SSLContext}. For efficiency reasons, clients should be reused whenever possible.
 */
public interface HttpClient extends AutoCloseable {

  /** A builder of {@linkplain HttpClient HTTP clients}. */
  interface Builder {
    /**
     * Sets the {@code User-Agent} header.
     *
     * <p>Defaults to {@code "Pkl/$version ($os $flavor)"}.
     */
    Builder setUserAgent(String userAgent);

    /**
     * Sets the timeout for connecting to a server.
     *
     * <p>Defaults to 60 seconds.
     */
    Builder setConnectTimeout(java.time.Duration timeout);

    /**
     * Sets the timeout for the interval between sending a request and receiving response headers.
     *
     * <p>Defaults to 60 seconds. To set a timeout for a specific request, use {@link
     * HttpRequest.Builder#timeout}.
     */
    Builder setRequestTimeout(java.time.Duration timeout);

    /**
     * Adds a CA certificate file to the client's trust store.
     *
     * <p>The given file must contain <a href="https://en.wikipedia.org/wiki/X.509">X.509</a>
     * certificates in PEM format.
     */
    Builder addCertificates(Path file);

    /**
     * Adds a CA certificate file to the client's trust store.
     *
     * <p>The given file must contain <a href="https://en.wikipedia.org/wiki/X.509">X.509</a>
     * certificates in PEM format.
     *
     * <p>This method can be used to add certificate files located on the JVM class path. To add
     * certificate files located on the file system, use {@link #addCertificates(Path)}.
     */
    Builder addCertificates(URL file);

    /**
     * Adds the CA certificate files in {@code ~/.pkl/cacerts/} to the client's trust store.
     *
     * <p>Each file must contain <a href="https://en.wikipedia.org/wiki/X.509">X.509</a>
     * certificates in PEM format. If {@code ~/.pkl/cacerts/} does not exist or is empty, Pkl's
     * {@link #addBuiltInCertificates() built-in certificates} are added instead.
     *
     * <p>This method implements the default behavior of Pkl CLIs.
     */
    Builder addDefaultCliCertificates() throws IOException;

    /** Adds Pkl's built-in CA certificates to the client's trust store. */
    Builder addBuiltInCertificates();

    /**
     * Creates a new {@code HttpClient} from the current state of this builder.
     *
     * @throws UncheckedIOException if a certificate file cannot be read
     * @throws RuntimeException if a certificate related problem occurs
     */
    HttpClient build();

    /**
     * Returns an {@code HTTPClient} wrapper that defers building the actual HTTP client until the
     * wrapper's {@link HttpClient#send} method is called.
     *
     * <p>Note: When using this method, any exception thrown when building the actual HTTP client is
     * equally deferred.
     */
    HttpClient buildLazily();
  }

  /**
   * Creates a new {@code HTTPClient} builder with default settings.
   *
   * <p>The default settings are:
   *
   * <ul>
   *   <li>Connect timeout: 60 seconds
   *   <li>Request timeout: 60 seconds
   *   <li>CA certificates: none (falls back to {@link Builder#addBuiltInCertificates})
   * </ul>
   */
  static Builder builder() {
    return new HttpClientBuilder();
  }

  /** Returns a client that throws {@link AssertionError} on every attempt to send a request. */
  static HttpClient dummyClient() {
    return new DummyHttpClient();
  }

  /**
   * Sends an HTTP request. The response body is processed by the given body handler.
   *
   * <p>If the request does not specify a {@linkplain HttpRequest#timeout timeout}, the client's
   * {@linkplain Builder#setRequestTimeout request timeout} is used. If the request does not specify
   * a preferred {@linkplain HttpRequest#version() HTTP version}, HTTP/2 is used. The request's
   * {@code User-Agent} header is set to the client's {@link Builder#setUserAgent User-Agent}
   * header.
   *
   * <p>Depending on the given body handler, this method blocks until response headers or the entire
   * response body has been received. If response headers are not received within the request
   * timeout, {@link HttpTimeoutException} is thrown.
   *
   * <p>For additional information on how to use this method, see {@link
   * java.net.http.HttpClient#send}.
   */
  <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
      throws IOException;

  /**
   * Closes this client.
   *
   * <p>This method makes a best effort to release the resources held by this client in a timely
   * manner. This may involve waiting for pending requests to complete.
   *
   * <p>Subsequent calls to this method have no effect. Subsequent calls to any other method throw
   * {@link IllegalStateException}.
   */
  void close();
}
