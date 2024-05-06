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
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.concurrent.ThreadSafe;
import org.pkl.core.util.HttpUtils;

/**
 * An {@code HttpClient} decorator that
 *
 * <ul>
 *   <li>overrides the {@code User-Agent} header of {@code HttpRequest}s
 *   <li>sets a request timeout if none is present
 *   <li>ensures that {@link #close()} is idempotent.
 * </ul>
 *
 * <p>Both {@code User-Agent} header and default request timeout are configurable through {@link
 * HttpClient.Builder}.
 */
@ThreadSafe
final class RequestRewritingClient implements HttpClient {
  // non-private for testing
  final String userAgent;
  final Duration requestTimeout;
  final int testPort;
  final HttpClient delegate;

  private final AtomicBoolean closed = new AtomicBoolean();

  RequestRewritingClient(
      String userAgent, Duration requestTimeout, int testPort, HttpClient delegate) {
    this.userAgent = userAgent;
    this.requestTimeout = requestTimeout;
    this.testPort = testPort;
    this.delegate = delegate;
  }

  @Override
  public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> responseBodyHandler)
      throws IOException {
    checkNotClosed(request);
    return delegate.send(rewriteRequest(request), responseBodyHandler);
  }

  @Override
  public void close() {
    if (!closed.getAndSet(true)) delegate.close();
  }

  // Based on JDK 17's implementation of HttpRequest.newBuilder(HttpRequest, filter).
  private HttpRequest rewriteRequest(HttpRequest original) {
    HttpRequest.Builder builder = HttpRequest.newBuilder();

    builder
        .uri(rewriteUri(original.uri()))
        .expectContinue(original.expectContinue())
        .timeout(original.timeout().orElse(requestTimeout))
        .version(original.version().orElse(java.net.http.HttpClient.Version.HTTP_2));

    original
        .headers()
        .map()
        .forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
    builder.setHeader("User-Agent", userAgent);

    var method = original.method();
    original
        .bodyPublisher()
        .ifPresentOrElse(
            publisher -> builder.method(method, publisher),
            () -> {
              switch (method) {
                case "GET" -> builder.GET();
                case "DELETE" -> builder.DELETE();
                default -> builder.method(method, HttpRequest.BodyPublishers.noBody());
              }
            });

    return builder.build();
  }

  private URI rewriteUri(URI uri) {
    if (testPort != -1 && uri.getPort() == 0) {
      return HttpUtils.setPort(uri, testPort);
    }
    return uri;
  }

  private void checkNotClosed(HttpRequest request) {
    if (closed.get()) {
      throw new IllegalStateException(
          "Cannot send request " + request + " because this client has already been closed.");
    }
  }
}
