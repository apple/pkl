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
  private static final String ipv4AddressString =
      "[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}";
  private static final Pattern ipv4Address = Pattern.compile("^" + ipv4AddressString + "$");
  private static final Pattern ipv4AddressOrCidr =
      Pattern.compile("^(" + ipv4AddressString + ")(?:/([1-9][0-9]?))?$");
  private static final String ipv6AddressString =
      "(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|(?:[0-9a-fA-F]{1,4}:){1,7}:|(?:[0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|(?:[0-9a-fA-F]{1,4}:){1,5}(?::[0-9a-fA-F]{1,4}){1,2}|(?:[0-9a-fA-F]{1,4}:){1,4}(?::[0-9a-fA-F]{1,4}){1,3}|(?:[0-9a-fA-F]{1,4}:){1,3}(?::[0-9a-fA-F]{1,4}){1,4}|(?:[0-9a-fA-F]{1,4}:){1,2}(?::[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:(?::[0-9a-fA-F]{1,4}){1,6}|:(?:(?::[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(?::[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]+|::(?:ffff(:0{1,4})?:)?(?:(?:25[0-5]|(2[0-4]|1?[0-9])?[0-9])\\.){3}(?:25[0-5]|(?:2[0-4]|1?[0-9])?[0-9])|(?:[0-9a-fA-F]{1,4}:){1,4}:(?:(?:25[0-5]|(?:2[0-4]|1?[0-9])?[0-9])\\.){3}(?:25[0-5]|(?:2[0-4]|1?[0-9])?[0-9])";
  private static final Pattern ipv6AddressOrCidr =
      Pattern.compile("^(" + ipv6AddressString + ")(?:/([1-9][0-9]{0,2}))?$");

  private @Nullable Integer ipv4 = null;
  private @Nullable Integer ipv4Mask = null;
  private @Nullable BigInteger ipv6 = null;
  private @Nullable BigInteger ipv6Mask = null;
  private @Nullable String hostname = null;
  private boolean allNoProxy = false;

  public NoProxyRule(String repr) {
    if (repr.equals("*")) {
      allNoProxy = true;
      return;
    }
    var ipv4Matcher = ipv4AddressOrCidr.matcher(repr);
    if (ipv4Matcher.matches()) {
      var ipAddress = ipv4Matcher.group(1);
      ipv4 = parseIpv4(ipAddress);
      if (ipv4Matcher.groupCount() == 2) {
        var prefixLength = Integer.parseInt(ipv4Matcher.group(2));
        if (prefixLength > 32) {
          // best-effort (don't fail on invalid cidrs).
          hostname = repr;
        }
        ipv4Mask = 0xffffffff << (32 - prefixLength);
      }
      return;
    }
    var ipv6Matcher = ipv6AddressOrCidr.matcher(repr);
    if (ipv6Matcher.matches()) {
      var ipAddress = ipv6Matcher.group(1);
      ipv6 = parseIpv6(ipAddress);
      if (ipv6Matcher.groupCount() == 4) {
        var maskBuffer = ByteBuffer.allocate(16).putLong(-1L).putLong(-1L);
        var prefixLength = Integer.parseInt(ipv6Matcher.group(4));
        if (prefixLength > 128) {
          // best-effort (don't fail on invalid cidrs).
          hostname = repr;
          return;
        }
        ipv6Mask = new BigInteger(1, maskBuffer.array()).not().shiftRight(prefixLength);
      }
      return;
    }
    if (repr.startsWith(".")) {
      hostname = repr.substring(1);
    } else {
      hostname = repr;
    }
  }

  /** Tells if the provided URI should not be proxied according to the rules described. */
  public boolean matches(URI uri) {
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
    if (ipv4 == address) {
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
