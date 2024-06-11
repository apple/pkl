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

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.regex.Pattern;
import org.pkl.core.util.Nullable;

/**
 * Represents a noproxy entry.
 *
 * <p>Follows the rules described in <a
 * href="https://about.gitlab.com/blog/2021/01/27/we-need-to-talk-no-proxy/#standardizing-no_proxy">Standardizing
 * {@code no_proxy}</a>
 */
final class NoProxyRule {
  private static final String portString = "(?::(?<port>\\d{1,5}))?";
  private static final String cidrString = "(?:/(?<cidr>\\d{1,3}))?";
  private static final String ipv4AddressString =
      "(?<host>[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})";
  private static final Pattern ipv4Address = Pattern.compile("^" + ipv4AddressString + "$");
  private static final Pattern ipv4AddressOrCidr =
      Pattern.compile("^" + ipv4AddressString + cidrString + portString + "$");
  private static final String ipv6AddressString =
      "(?<host>(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|(?:[0-9a-fA-F]{1,4}:){1,7}:|(?:[0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|(?:[0-9a-fA-F]{1,4}:){1,5}(?::[0-9a-fA-F]{1,4}){1,2}|(?:[0-9a-fA-F]{1,4}:){1,4}(?::[0-9a-fA-F]{1,4}){1,3}|(?:[0-9a-fA-F]{1,4}:){1,3}(?::[0-9a-fA-F]{1,4}){1,4}|(?:[0-9a-fA-F]{1,4}:){1,2}(?::[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:(?::[0-9a-fA-F]{1,4}){1,6}|:(?:(?::[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(?::[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]+|::(?:ffff(:0{1,4})?:)?(?:(?:25[0-5]|(2[0-4]|1?[0-9])?[0-9])\\.){3}(?:25[0-5]|(?:2[0-4]|1?[0-9])?[0-9])|(?:[0-9a-fA-F]{1,4}:){1,4}:(?:(?:25[0-5]|(?:2[0-4]|1?[0-9])?[0-9])\\.){3}(?:25[0-5]|(?:2[0-4]|1?[0-9])?[0-9]))";
  private static final Pattern ipv6AddressOrCidr =
      Pattern.compile(
          "^(?<open>\\[)?" + ipv6AddressString + cidrString + "(?<close>])?" + portString + "$");
  private static final Pattern hostnamePattern =
      Pattern.compile("^\\.?(?<host>[^:]+)" + portString + "$");

  private @Nullable Integer ipv4 = null;
  private @Nullable Integer ipv4Mask = null;
  private @Nullable BigInteger ipv6 = null;
  private @Nullable BigInteger ipv6Mask = null;
  private @Nullable String hostname = null;
  private int port = 0;
  private boolean allNoProxy = false;

  public NoProxyRule(String repr) {
    if (repr.equals("*")) {
      allNoProxy = true;
      return;
    }
    var ipv4Matcher = ipv4AddressOrCidr.matcher(repr);
    if (ipv4Matcher.matches()) {
      var ipAddress = ipv4Matcher.group("host");
      ipv4 = parseIpv4(ipAddress);
      if (ipv4Matcher.group("cidr") != null) {
        var prefixLength = Integer.parseInt(ipv4Matcher.group("cidr"));
        if (prefixLength > 32) {
          // best-effort (don't fail on invalid cidrs).
          hostname = repr;
        }
        ipv4Mask = 0xffffffff << (32 - prefixLength);
      }
      if (ipv4Matcher.group("port") != null) {
        port = Integer.parseInt(ipv4Matcher.group("port"));
      }
      return;
    }
    var ipv6Matcher = ipv6AddressOrCidr.matcher(repr);
    if (ipv6Matcher.matches()) {
      var ipAddress = ipv6Matcher.group("host");
      ipv6 = parseIpv6(ipAddress);
      if (ipv6Matcher.group("cidr") != null) {
        var maskBuffer = ByteBuffer.allocate(16).putLong(-1L).putLong(-1L);
        var prefixLength = Integer.parseInt(ipv6Matcher.group("cidr"));
        if (prefixLength > 128) {
          // best-effort (don't fail on invalid cidrs).
          hostname = repr;
          return;
        }
        ipv6Mask = new BigInteger(1, maskBuffer.array()).not().shiftRight(prefixLength);
      }
      if (ipv6Matcher.group("port") != null) {
        port = Integer.parseInt(ipv6Matcher.group("port"));
      }
      return;
    }
    var hostnameMatcher = hostnamePattern.matcher(repr);
    if (hostnameMatcher.matches()) {
      hostname = hostnameMatcher.group("host");
      if (hostnameMatcher.group("port") != null) {
        port = Integer.parseInt(hostnameMatcher.group("port"));
      }
      return;
    }
    throw new RuntimeException("Failed to parse hostname in no-proxy rule: " + repr);
  }

  public boolean matches(URI uri) {
    if (allNoProxy) {
      return true;
    }
    if (!hostMatches(uri)) {
      return false;
    }
    if (port == 0) {
      return true;
    }
    var thatPort = uri.getPort();
    if (thatPort == -1) {
      thatPort =
          switch (uri.getScheme()) {
            case "http" -> 80;
            case "https" -> 443;
            default -> -1;
          };
    }
    return port == thatPort;
  }

  /** Tells if the provided URI should not be proxied according to the rules described. */
  public boolean hostMatches(URI uri) {
    if (allNoProxy) {
      return true;
    }
    var host = uri.getHost();
    if (host == null) {
      return false;
    }
    if (host.equalsIgnoreCase(hostname)) {
      return true;
    }
    if (hostname != null && endsWithIgnoreCase(host, "." + hostname)) {
      return true;
    }
    return ipV6Matches(uri.getHost()) || ipV4Matches(uri.getHost());
  }

  private boolean endsWithIgnoreCase(String str, String suffix) {
    var len = suffix.length();
    return str.regionMatches(true, str.length() - len, suffix, 0, len);
  }

  private boolean ipV4Matches(String hostname) {
    if (ipv4 == null) {
      return false;
    }
    if (!ipv4Address.matcher(hostname).matches()) {
      return false;
    }
    var address = parseIpv4(hostname);
    if (ipv4.equals(address)) {
      return true;
    }
    if (ipv4Mask != null) {
      return (ipv4 & ipv4Mask) == (address & ipv4Mask);
    }
    return false;
  }

  private boolean ipV6Matches(String hostname) {
    if (ipv6 == null) {
      return false;
    }
    if (!hostname.startsWith("[") && !hostname.endsWith("]")) {
      return false;
    }
    var ipv6Repr = hostname.substring(1, hostname.length() - 1);
    // According to RFC3986, square brackets can _only_ surround IPV6 addresses, so it should be
    // safe to straight up parse it.
    // <https://www.ietf.org/rfc/rfc3986.txt>
    var address = parseIpv6(ipv6Repr);
    if (ipv6.equals(address)) {
      return true;
    }
    if (ipv6Mask != null) {
      return ipv6.and(ipv6Mask).equals(address.and(ipv6Mask));
    }
    return false;
  }

  private BigInteger parseIpv6(String repr) {
    try {
      var inet = Inet6Address.getByName(repr);
      var byteArr = inet.getAddress();
      return new BigInteger(1, byteArr);
    } catch (UnknownHostException e) {
      // should never happen; `repr` is an IPV6 literal.
      throw new RuntimeException(
          "Received unexpected UnknownHostException during parsing IPV6 literal", e);
    }
  }

  private int parseIpv4(String repr) {
    try {
      var inet = Inet4Address.getByName(repr);
      return ByteBuffer.wrap(inet.getAddress()).getInt();
    } catch (UnknownHostException e) {
      // should never happen; `repr` is an IPV4 literal.
      throw new RuntimeException(
          "Received unexpected UnknownHostException during parsing IPV4 literal", e);
    }
  }
}
