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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * An {@code HttpClient} decorator that defers creating the underlying HTTP client until the first
 * send.
 */
@ThreadSafe
final class LazyHttpClient implements HttpClient {
  private final Supplier<HttpClient> supplier;
  private final Object lock = new Object();

  @GuardedBy("lock")
  private HttpClient client;

  @GuardedBy("lock")
  private RuntimeException exception;

  LazyHttpClient(Supplier<HttpClient> supplier) {
    this.supplier = supplier;
  }

  @Override
  public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> responseBodyHandler)
      throws IOException {
    return getOrCreateClient().send(request, responseBodyHandler);
  }

  @Override
  public void close() {
    getClient().ifPresent(HttpClient::close);
  }

  private HttpClient getOrCreateClient() {
    synchronized (lock) {
      // only try to create client once
      if (exception != null) {
        throw exception;
      }

      if (client == null) {
        try {
          client = supplier.get();
        } catch (RuntimeException t) {
          exception = t;
          throw t;
        }
      }
      return client;
    }
  }

  private Optional<HttpClient> getClient() {
    synchronized (lock) {
      return Optional.ofNullable(client);
    }
  }
}
