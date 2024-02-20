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
package org.pkl.core.util;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpResponse;

public final class HttpUtils {
  private HttpUtils() {}

  public static boolean isHttpUrl(URL url) {
    var protocol = url.getProtocol();
    return "https".equalsIgnoreCase(protocol) || "http".equalsIgnoreCase(protocol);
  }

  public static boolean isHttpUrl(URI uri) {
    var scheme = uri.getScheme();
    return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
  }

  public static void requireStatusCode200(HttpResponse<?> response) throws IOException {
    if (response.statusCode() == 200) return;

    var body = response.body();
    if (body instanceof AutoCloseable) {
      try {
        ((AutoCloseable) body).close();
      } catch (Exception ignored) {
      }
    }

    throw new IOException(
        ErrorMessages.create("badHttpStatusCode", response.statusCode(), response.uri()));
  }
}
