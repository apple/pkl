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
package org.pkl.core.stdlib.net;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import org.jspecify.annotations.Nullable;

/**
 * A parser for <a href="https://www.rfc-editor.org/rfc/rfc3986">RFC 3986</a> URI references.
 *
 * <p>Both shapes of {@code URI-reference} (section 4.1) are accepted: a {@code URI}, which states a
 * scheme, and a {@code relative-ref}, which does not.
 *
 * <p>Reference resolution is not part of parsing; see {@link #resolve}.
 *
 * <p>An IPv6 address may carry the zone identifier of <a
 * href="https://www.rfc-editor.org/rfc/rfc6874">RFC 6874</a>, as in {@code [fe80::1%25eth0]}..
 */
@SuppressWarnings("JavadocLinkAsPlainText")
public final class UrlParser {
  private UrlParser() {}

  /**
   * The components of a URI reference, each holding the raw text that was written for it.
   *
   * <p>{@code scheme} is {@code null} for a relative reference. {@code host} is {@code null} when
   * the reference has no authority. {@code userInfo} and {@code port} are only ever set alongside a
   * host.
   */
  record Parsed(
      @Nullable String scheme,
      @Nullable String userInfo,
      @Nullable String host,
      @Nullable Integer port,
      String path,
      @Nullable String query,
      @Nullable String fragment) {

    /**
     * Serializes these components (section 5.3), percent-encoding whatever cannot appear literally
     * in its component.
     */
    String serialize() {
      var sb = new StringBuilder();
      if (scheme != null) {
        sb.append(scheme).append(':');
      }
      if (host != null) {
        sb.append("//");
        if (userInfo != null) {
          PercentEncoder.encode(sb, userInfo, PercentEncoder.USERINFO);
          sb.append('@');
        }
        if (isIpLiteral(host)) {
          // already validated, and none of its characters may be encoded
          sb.append(host);
        } else {
          PercentEncoder.encode(sb, host, PercentEncoder.REG_NAME);
        }
        if (port != null) {
          sb.append(':').append(port.intValue());
        }
      }
      if (scheme == null && host == null && startsWithColonSegment(path)) {
        // a relative reference whose first segment holds a ":" would be read back as a scheme, so
        // it has to be preceded by a dot-segment (section 4.2)
        sb.append("./");
      }
      PercentEncoder.encode(sb, path, PercentEncoder.PATH);
      if (query != null) {
        sb.append('?');
        PercentEncoder.encode(sb, query, PercentEncoder.QUERY_OR_FRAGMENT);
      }
      if (fragment != null) {
        sb.append('#');
        PercentEncoder.encode(sb, fragment, PercentEncoder.QUERY_OR_FRAGMENT);
      }
      return sb.toString();
    }
  }

  // Parsing (https://www.rfc-editor.org/rfc/rfc3986#section-3)

  /** Parses {@code input} as a URI reference. Returns {@code null} if it is not one. */
  static @Nullable Parsed parse(String input) {
    var length = input.length();

    // scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ) ":"
    // Absent a scheme, this is a relative reference and any ":" belongs to the path.
    String scheme = null;
    var pointer = 0;
    if (length > 0 && PercentEncoder.isAlpha(input.charAt(0))) {
      var end = 1;
      while (end < length && isSchemeChar(input.charAt(end))) {
        end++;
      }
      if (end < length && input.charAt(end) == ':') {
        scheme = toLowerAscii(input.substring(0, end));
        pointer = end + 1;
      }
    }

    // authority = [ userinfo "@" ] host [ ":" port ]
    String userInfo = null;
    String host = null;
    Integer port = null;
    if (input.startsWith("//", pointer)) {
      pointer += 2;
      var end = pointer;
      while (end < length && !isAuthorityTerminator(input.charAt(end))) {
        end++;
      }
      var authority = input.substring(pointer, end);
      pointer = end;

      // the userinfo runs to the last "@", because "@" may appear literally in a host
      var at = authority.lastIndexOf('@');
      if (at >= 0) {
        userInfo = authority.substring(0, at);
        if (!hasValidPercentEncoding(userInfo)) {
          return null;
        }
      }
      host = authority.substring(at + 1);

      var colon = portSeparator(host);
      if (colon >= 0) {
        var rawPort = host.substring(colon + 1);
        host = host.substring(0, colon);
        if (!rawPort.isEmpty()) {
          port = parsePort(rawPort);
          if (port == null) {
            return null;
          }
        }
      }
      if (!isValidHost(host)) {
        return null;
      }
    }

    var pathEnd = pointer;
    while (pathEnd < length && input.charAt(pathEnd) != '?' && input.charAt(pathEnd) != '#') {
      pathEnd++;
    }
    var path = input.substring(pointer, pathEnd);
    if (!isValidPath(path, host != null)) {
      return null;
    }
    pointer = pathEnd;

    String query = null;
    if (pointer < length && input.charAt(pointer) == '?') {
      var end = input.indexOf('#', pointer + 1);
      if (end < 0) {
        end = length;
      }
      query = input.substring(pointer + 1, end);
      if (!hasValidPercentEncoding(query)) {
        return null;
      }
      pointer = end;
    }

    String fragment = null;
    if (pointer < length && input.charAt(pointer) == '#') {
      fragment = input.substring(pointer + 1);
      if (!hasValidPercentEncoding(fragment)) {
        return null;
      }
    }

    return new Parsed(scheme, userInfo, host, port, path, query, fragment);
  }

  // Reference resolution (https://www.rfc-editor.org/rfc/rfc3986#section-5.2.2)

  /**
   * Resolves {@code ref} against {@code base}.
   *
   * <p>This is the strict form of the transform, which never reinterprets a reference that repeats
   * the base's scheme as a relative one.
   *
   * <p>{@code base} is expected to state a scheme, as section 5.1 requires. Resolving against one
   * that does not is not defined, and would only produce another relative reference.
   */
  static Parsed resolve(Parsed base, Parsed ref) {
    if (ref.scheme() != null) {
      return new Parsed(
          ref.scheme(),
          ref.userInfo(),
          ref.host(),
          ref.port(),
          removeDotSegments(ref.path()),
          ref.query(),
          ref.fragment());
    }
    if (ref.host() != null) {
      return new Parsed(
          base.scheme(),
          ref.userInfo(),
          ref.host(),
          ref.port(),
          removeDotSegments(ref.path()),
          ref.query(),
          ref.fragment());
    }
    String path;
    String query;
    if (ref.path().isEmpty()) {
      path = base.path();
      query = ref.query() != null ? ref.query() : base.query();
    } else {
      path =
          ref.path().charAt(0) == '/'
              ? removeDotSegments(ref.path())
              : removeDotSegments(merge(base, ref.path()));
      query = ref.query();
    }
    return new Parsed(
        base.scheme(), base.userInfo(), base.host(), base.port(), path, query, ref.fragment());
  }

  /** https://www.rfc-editor.org/rfc/rfc3986#section-5.2.3 */
  private static String merge(Parsed base, String path) {
    if (base.host() != null && base.path().isEmpty()) {
      return "/" + path;
    }
    var lastSlash = base.path().lastIndexOf('/');
    return lastSlash < 0 ? path : base.path().substring(0, lastSlash + 1) + path;
  }

  /** https://www.rfc-editor.org/rfc/rfc3986#section-5.2.4 */
  static String removeDotSegments(String path) {
    var out = new StringBuilder(path.length());
    var length = path.length();
    var pointer = 0;
    while (pointer < length) {
      if (path.startsWith("../", pointer)) {
        pointer += 3;
      } else if (path.startsWith("./", pointer)) {
        pointer += 2;
      } else if (path.startsWith("/./", pointer)) {
        pointer += 2;
      } else if (pointer + 2 == length && path.startsWith("/.", pointer)) {
        out.append('/');
        pointer = length;
      } else if (path.startsWith("/../", pointer)) {
        removeLastSegment(out);
        pointer += 3;
      } else if (pointer + 3 == length && path.startsWith("/..", pointer)) {
        removeLastSegment(out);
        out.append('/');
        pointer = length;
      } else if (pointer + 1 == length && path.charAt(pointer) == '.') {
        pointer = length;
      } else if (pointer + 2 == length && path.startsWith("..", pointer)) {
        pointer = length;
      } else {
        // move the first segment, along with any leading "/", to the output
        var end = path.indexOf('/', path.charAt(pointer) == '/' ? pointer + 1 : pointer);
        if (end < 0) {
          end = length;
        }
        out.append(path, pointer, end);
        pointer = end;
      }
    }
    return out.toString();
  }

  private static void removeLastSegment(StringBuilder out) {
    var lastSlash = out.lastIndexOf("/");
    out.setLength(Math.max(lastSlash, 0));
  }

  // Derived views

  /** The percent-decoded segments of {@code path}. */
  static List<String> segments(String path) {
    if (path.isEmpty()) {
      return List.of();
    }
    var segments = new ArrayList<String>();
    var start = path.charAt(0) == '/' ? 1 : 0;
    while (true) {
      var slash = path.indexOf('/', start);
      if (slash < 0) {
        segments.add(PercentEncoder.decode(path.substring(start)));
        return segments;
      }
      segments.add(PercentEncoder.decode(path.substring(start, slash)));
      start = slash + 1;
    }
  }

  /**
   * The percent-decoded parameters of {@code query}, which is read as {@code
   * application/x-www-form-urlencoded}.
   *
   * <p>A parameter that states no {@code =} has a {@code null} value. A name that repeats keeps the
   * value of its first occurrence.
   */
  static Map<String, @Nullable String> queryParameters(@Nullable String query) {
    if (query == null || query.isEmpty()) {
      return Map.of();
    }
    var parameters = new LinkedHashMap<String, @Nullable String>();
    var start = 0;
    while (start < query.length()) {
      var end = query.indexOf('&', start);
      if (end < 0) {
        end = query.length();
      }
      // an empty pair ("a=1&&b=2") holds no parameters
      if (end > start) {
        var separator = query.indexOf('=', start);
        var hasValue = separator >= 0 && separator < end;
        var name = PercentEncoder.decodeForm(query.substring(start, hasValue ? separator : end));
        var value =
            hasValue ? PercentEncoder.decodeForm(query.substring(separator + 1, end)) : null;
        // repeats are dropped
        if (!parameters.containsKey(name)) {
          parameters.put(name, value);
        }
      }
      start = end + 1;
    }
    return parameters;
  }

  /**
   * Whether {@code left} and {@code right} identify the same resource
   * (https://www.rfc-editor.org/rfc/rfc3986#section-6).
   *
   * <p>Both are put through the syntax-based normalization of section 6.2.2 first, so that two ways
   * of writing the same URL compare equal. A default port is kept.
   */
  static boolean isEquivalent(Parsed left, Parsed right) {
    return normalize(left).equals(normalize(right));
  }

  private static Parsed normalize(Parsed url) {
    return new Parsed(
        url.scheme() == null ? null : toLowerAscii(url.scheme()),
        normalizeOptional(url.userInfo(), PercentEncoder.USERINFO),
        url.host() == null ? null : normalizeHost(url.host()),
        url.port(),
        normalizePath(url),
        normalizeOptional(url.query(), PercentEncoder.QUERY_OR_FRAGMENT),
        normalizeOptional(url.fragment(), PercentEncoder.QUERY_OR_FRAGMENT));
  }

  private static String normalizeComponent(String component, IntPredicate allowed) {
    var out = new StringBuilder(component.length());
    PercentEncoder.normalize(out, component, allowed);
    return out.toString();
  }

  private static @Nullable String normalizeOptional(
      @Nullable String component, IntPredicate allowed) {
    return component == null ? null : normalizeComponent(component, allowed);
  }

  private static String normalizeHost(String host) {
    if (!isIpLiteral(host)) {
      return toLowerAscii(normalizeComponent(host, PercentEncoder.REG_NAME));
    }
    // an IP literal holds nothing that may be encoded, and its zone identifier, unlike the address
    // in front of it, names an interface and is case-sensitive
    var zone = host.indexOf("%25");
    return zone < 0
        ? toLowerAscii(host)
        : toLowerAscii(host.substring(0, zone)) + host.substring(zone);
  }

  private static String normalizePath(Parsed url) {
    var path = normalizeComponent(url.path(), PercentEncoder.PATH);
    if (path.isEmpty()) {
      // a URL with an authority and no path names the same resource as one whose path is "/"
      // (section 6.2.3)
      return url.host() == null ? path : "/";
    }
    // dot segments are only removable from an absolute path
    return path.charAt(0) == '/' ? removeDotSegments(path) : path;
  }

  // Validation. These back the type constraints of `pkl:net`'s `Url`, so that a URL written or
  // amended by hand is held to the same standard as one the parser produced.

  /** Whether every {@code %} in {@code input} begins a percent-encoded octet. */
  static boolean hasValidPercentEncoding(String input) {
    for (var i = input.indexOf('%'); i >= 0; i = input.indexOf('%', i + 3)) {
      if (i + 2 >= input.length()
          || !PercentEncoder.isHexDigit(input.charAt(i + 1))
          || !PercentEncoder.isHexDigit(input.charAt(i + 2))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Whether {@code input} is an absolute URL that is already written the way RFC 3986 spells one
   * out. Backs {@code String.isValidUrl}.
   *
   * <p>This is stricter than {@link #parse}, which is lenient in the two ways text taken from the
   * outside world usually needs it to be:
   *
   * <ul>
   *   <li>a relative reference, such as {@code ./foo}, parses but is not a URL
   *   <li>nothing is encoded here, so a character that its component cannot hold literally has to
   *       already be percent-encoded: {@code http://example.com/some path} is not a URL, but {@code
   *       http://example.com/some%20path} is
   * </ul>
   */
  public static boolean isValidUrl(String input) {
    var parsed = parse(input);
    if (parsed == null || parsed.scheme() == null) {
      return false;
    }
    var host = parsed.host();
    if (host != null && !isIpLiteral(host) && !isEncoded(host, PercentEncoder.REG_NAME)) {
      return false;
    }
    return isEncoded(parsed.userInfo(), PercentEncoder.USERINFO)
        && isEncoded(parsed.path(), PercentEncoder.PATH)
        && isEncoded(parsed.query(), PercentEncoder.QUERY_OR_FRAGMENT)
        && isEncoded(parsed.fragment(), PercentEncoder.QUERY_OR_FRAGMENT);
  }

  private static boolean isEncoded(@Nullable String component, IntPredicate allowed) {
    return component == null || component.codePoints().allMatch(c -> c == '%' || allowed.test(c));
  }

  /** Whether {@code input} is a scheme. Unlike parsing, the trailing {@code :} is not accepted. */
  static boolean isValidScheme(String input) {
    if (input.isEmpty() || !PercentEncoder.isAlpha(input.charAt(0))) {
      return false;
    }
    for (var i = 1; i < input.length(); i++) {
      if (!isSchemeChar(input.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /** Whether {@code host} can be serialized back out as a host. */
  static boolean isValidHost(String host) {
    if (isIpLiteral(host)) {
      if (host.length() < 3) {
        return false;
      }
      var address = host.substring(1, host.length() - 1);
      // "v" cannot begin an IPv6address, as it is not a hex digit, so it tells the two forms apart
      if (address.charAt(0) == 'v' || address.charAt(0) == 'V') {
        return isIpvFuture(address);
      }
      // IPv6addrz = IPv6address "%25" ZoneID (https://www.rfc-editor.org/rfc/rfc6874#section-2)
      var zone = address.indexOf("%25");
      if (zone < 0) {
        return hasIpv6Characters(address);
      }
      return zone > 0
          && hasIpv6Characters(address.substring(0, zone))
          && isZoneId(address.substring(zone + 3));
    }
    return host.indexOf('[') < 0 && host.indexOf(']') < 0 && hasValidPercentEncoding(host);
  }

  /** Whether every character of {@code address} is one an {@code IPv6address} is built from. */
  private static boolean hasIpv6Characters(String address) {
    for (var i = 0; i < address.length(); i++) {
      var c = address.charAt(i);
      if (!PercentEncoder.isHexDigit(c) && c != ':' && c != '.') {
        return false;
      }
    }
    return true;
  }

  /** Whether {@code zoneId} is a {@code ZoneID}: {@code 1*( unreserved / pct-encoded )}. */
  private static boolean isZoneId(String zoneId) {
    if (zoneId.isEmpty()) {
      return false;
    }
    for (var i = 0; i < zoneId.length(); i++) {
      var c = zoneId.charAt(i);
      if (c == '%') {
        if (i + 2 >= zoneId.length()
            || !PercentEncoder.isHexDigit(zoneId.charAt(i + 1))
            || !PercentEncoder.isHexDigit(zoneId.charAt(i + 2))) {
          return false;
        }
        i += 2;
      } else if (!PercentEncoder.UNRESERVED.test(c)) {
        return false;
      }
    }
    return true;
  }

  /** IPvFuture = "v" 1*HEXDIG "." 1*( unreserved / sub-delims / ":" ) */
  private static boolean isIpvFuture(String address) {
    var dot = address.indexOf('.');
    if (dot < 2 || dot == address.length() - 1) {
      return false;
    }
    for (var i = 1; i < dot; i++) {
      if (!PercentEncoder.isHexDigit(address.charAt(i))) {
        return false;
      }
    }
    for (var i = dot + 1; i < address.length(); i++) {
      // the tail of an IPvFuture is drawn from the same set as a userinfo
      if (!PercentEncoder.USERINFO.test(address.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Whether {@code path} can sit next to an authority, or, when there is none, next to no authority
   * at all.
   */
  static boolean isValidPath(String path, boolean hasAuthority) {
    if (!hasValidPercentEncoding(path)) {
      return false;
    }
    return hasAuthority
        // path-abempty
        ? path.isEmpty() || path.charAt(0) == '/'
        // path-absolute / path-rootless / path-empty
        : !path.startsWith("//");
  }

  private static boolean isIpLiteral(String host) {
    return host.startsWith("[") && host.endsWith("]");
  }

  /** Whether the first segment of {@code path} holds a {@code ":"}. */
  private static boolean startsWithColonSegment(String path) {
    var colon = path.indexOf(':');
    if (colon < 0) {
      return false;
    }
    var slash = path.indexOf('/');
    return slash < 0 || colon < slash;
  }

  /**
   * The index of the {@code ":"} that separates the host from the port in {@code hostAndPort}, or
   * {@code -1} if it states no port.
   */
  private static int portSeparator(String hostAndPort) {
    if (hostAndPort.startsWith("[")) {
      var close = hostAndPort.indexOf(']');
      if (close < 0) {
        // an unterminated IP literal; let host validation reject it
        return -1;
      }
      var next = close + 1;
      return next < hostAndPort.length() && hostAndPort.charAt(next) == ':' ? next : -1;
    }
    return hostAndPort.lastIndexOf(':');
  }

  /** Returns the port, or {@code null} if it is not a number that fits in 16 bits. */
  private static @Nullable Integer parsePort(String input) {
    var port = 0;
    for (var i = 0; i < input.length(); i++) {
      if (!PercentEncoder.isDigit(input.charAt(i))) {
        return null;
      }
      port = port * 10 + (input.charAt(i) - '0');
      if (port > 65535) {
        return null;
      }
    }
    return port;
  }

  private static boolean isSchemeChar(int c) {
    return PercentEncoder.isAlpha(c)
        || PercentEncoder.isDigit(c)
        || c == '+'
        || c == '-'
        || c == '.';
  }

  /** The gen-delims that end an authority and start the component after it. */
  private static boolean isAuthorityTerminator(int c) {
    return c == '/' || c == '?' || c == '#';
  }

  private static String toLowerAscii(String input) {
    var out = new StringBuilder(input.length());
    for (var i = 0; i < input.length(); i++) {
      var c = input.charAt(i);
      out.append((char) ((c >= 'A' && c <= 'Z') ? c + 0x20 : c));
    }
    return out.toString();
  }
}
