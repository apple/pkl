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

import java.util.List;
import java.util.function.IntPredicate;
import org.jspecify.annotations.Nullable;

/**
 * Assembles a URL string from decoded parts, for {@code Builder}.
 *
 * <p>Every free-form part is percent-encoded with the percent-encode set for its component before
 * assembly.
 */
final class UrlBuilder {
  private UrlBuilder() {}

  private static final IntPredicate SEGMENT =
      c -> PercentEncoder.PATH.test(c) || c == '/' || c == '\\' || c == '%';

  private static final IntPredicate USERINFO = c -> PercentEncoder.USERINFO.test(c) || c == '%';

  private static final IntPredicate FRAGMENT = c -> PercentEncoder.FRAGMENT.test(c) || c == '%';

  /**
   * Returns the URL string for these parts, to be parsed leniently.
   *
   * <p>{@code query} is expected to already be an {@code application/x-www-form-urlencoded} string
   * (see {@link FormUrlEncoder}, whose set does encode {@code %}).
   */
  static String assemble(
      String scheme,
      String username,
      String password,
      String host,
      @Nullable Integer port,
      List<String> segments,
      String query,
      @Nullable String fragment) {
    var sb = new StringBuilder();
    sb.append(scheme).append("://");
    if (!username.isEmpty() || !password.isEmpty()) {
      encode(sb, username, USERINFO);
      if (!password.isEmpty()) {
        sb.append(':');
        encode(sb, password, USERINFO);
      }
      sb.append('@');
    }
    sb.append(host);
    if (port != null) {
      sb.append(':').append(port.intValue());
    }
    for (var segment : segments) {
      sb.append('/');
      encode(sb, segment, SEGMENT);
    }
    if (!query.isEmpty()) {
      sb.append('?').append(query);
    }
    if (fragment != null) {
      sb.append('#');
      encode(sb, fragment, FRAGMENT);
    }
    return sb.toString();
  }

  /**
   * Whether {@code host} can be assembled into a URL string without changing the URL's structure.
   */
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  static boolean isAssemblableHost(String host) {
    if (!host.startsWith("[")) {
      return host.codePoints().noneMatch(UrlParser::isForbiddenHostCodePoint);
    }
    if (host.length() < 3 || !host.endsWith("]")) {
      return false;
    }
    for (var i = 1; i < host.length() - 1; i++) {
      var c = host.charAt(i);
      if (!PercentEncoder.isHexDigit(c) && c != ':' && c != '.') {
        return false;
      }
    }
    return true;
  }

  private static void encode(StringBuilder out, String value, IntPredicate set) {
    value.codePoints().forEach(cp -> PercentEncoder.encode(cp, set, out));
  }
}
