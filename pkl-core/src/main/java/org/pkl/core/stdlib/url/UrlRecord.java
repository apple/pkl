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
package org.pkl.core.stdlib.url;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An immutable WHATWG <a href="https://url.spec.whatwg.org/#concept-url">URL record</a>.
 *
 * <p>All string components are stored in their serialized (percent-encoded, canonicalized) form, so
 * component accessors and {@link #serialize()} are simple reads. This is the value carried in a
 * {@code Url}'s extra storage.
 */
@SuppressWarnings("JavadocLinkAsPlainText")
public record UrlRecord(
    String scheme,
    String username,
    String password,
    @Nullable String host,
    @Nullable Integer port,
    List<String> path,
    boolean hasOpaquePath,
    @Nullable String query,
    @Nullable String fragment) {

  /** https://url.spec.whatwg.org/#is-special */
  boolean isSpecial() {
    return isSpecialScheme(scheme);
  }

  /** https://url.spec.whatwg.org/#include-credentials */
  boolean includesCredentials() {
    return !username.isEmpty() || !password.isEmpty();
  }

  static boolean isSpecialScheme(String scheme) {
    return switch (scheme) {
      case "ftp", "file", "http", "https", "ws", "wss" -> true;
      default -> false;
    };
  }

  /** The default port for {@code scheme}, or {@code null} if it has none. */
  static @Nullable Integer defaultPort(String scheme) {
    return switch (scheme) {
      case "ftp" -> 21;
      case "http", "ws" -> 80;
      case "https", "wss" -> 443;
      default -> null;
    };
  }

  /**
   * https://url.spec.whatwg.org/#concept-url-origin — the serialization of this URL's origin, or
   * {@code null} if it is an <a href="https://html.spec.whatwg.org/#concept-origin-opaque">opaque
   * origin</a>.
   */
  public @Nullable String origin() {
    return switch (scheme) {
      case "blob" -> blobOrigin();
      case "ftp", "http", "https", "ws", "wss" -> tupleOrigin();
      default -> null;
    };
  }

  private @Nullable String blobOrigin() {
    var pathUrl = UrlParser.parse(serializedPath(), false);
    if (pathUrl == null) {
      return null;
    }
    return switch (pathUrl.scheme) {
      case "http", "https", "file" -> pathUrl.origin();
      default -> null;
    };
  }

  private String tupleOrigin() {
    var sb = new StringBuilder(scheme).append("://");
    if (host != null) {
      sb.append(host);
    }
    if (port != null) {
      sb.append(':').append(port);
    }
    return sb.toString();
  }

  /** The percent-decoded path segments, or an empty list if this URL has an opaque path. */
  public List<String> segments() {
    if (hasOpaquePath) {
      return List.of();
    }
    var result = new ArrayList<String>(path.size());
    for (var segment : path) {
      result.add(PercentEncoder.percentDecode(segment));
    }
    return result;
  }

  /** the query parsed as {@code application/x-www-form-urlencoded}. */
  public QueryParam[] queryParams() {
    if (query == null || query.isEmpty()) {
      return EMPTY_PARAMS;
    }
    var result = new ArrayList<QueryParam>();
    for (var sequence : query.split("&", -1)) {
      if (sequence.isEmpty()) {
        continue;
      }
      var separator = sequence.indexOf('=');
      var name = separator == -1 ? sequence : sequence.substring(0, separator);
      var value = separator == -1 ? "" : sequence.substring(separator + 1);
      result.add(new QueryParam(decodeFormValue(name), decodeFormValue(value)));
    }
    return result.toArray(EMPTY_PARAMS);
  }

  public record QueryParam(String name, String value) {}

  private static final QueryParam[] EMPTY_PARAMS = new QueryParam[0];

  /** {@code +} means a space in {@code application/x-www-form-urlencoded}, and is not encoded. */
  private static String decodeFormValue(String input) {
    return PercentEncoder.percentDecode(input.replace('+', ' '));
  }

  /** https://url.spec.whatwg.org/#url-serializing */
  public String serialize() {
    return serialize(false);
  }

  public String serialize(boolean excludeFragment) {
    var sb = new StringBuilder();
    sb.append(scheme).append(':');
    if (host != null) {
      sb.append("//");
      if (includesCredentials()) {
        sb.append(username);
        if (!password.isEmpty()) {
          sb.append(':').append(password);
        }
        sb.append('@');
      }
      sb.append(host);
      if (port != null) {
        sb.append(':').append(port);
      }
    }
    // Guard against a path starting with "//" being reparsed as an authority.
    if (host == null && !hasOpaquePath && path.size() > 1 && path.get(0).isEmpty()) {
      sb.append("/.");
    }
    serializePath(sb);
    if (query != null) {
      sb.append('?').append(query);
    }
    if (!excludeFragment && fragment != null) {
      sb.append('#').append(fragment);
    }
    return sb.toString();
  }

  /** https://url.spec.whatwg.org/#url-path-serializer */
  public String serializedPath() {
    var sb = new StringBuilder();
    serializePath(sb);
    return sb.toString();
  }

  /** https://url.spec.whatwg.org/#url-path-serializer */
  private void serializePath(StringBuilder sb) {
    if (hasOpaquePath) {
      sb.append(path.isEmpty() ? "" : path.get(0));
      return;
    }
    for (var segment : path) {
      sb.append('/').append(segment);
    }
  }
}
