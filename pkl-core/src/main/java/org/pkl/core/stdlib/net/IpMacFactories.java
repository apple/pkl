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

import com.oracle.truffle.api.nodes.Node;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.pkl.core.PklBugException;
import org.pkl.core.runtime.NetModule;
import org.pkl.core.runtime.VmBytes;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.VmObjectFactory;
import org.pkl.core.util.UInt128;

public class IpMacFactories {

  private static final int IPV4_LOOPBACK_MASK = 0xff000000;
  private static final int IPV4_LOOPBACK = 0x7f000000;
  private static final byte IPV4_REVERSE_RESOLUTION = 8;

  private static final byte IPV6_REVERSE_RESOLUTION = 4;
  private static final Pattern IPV4_IN_IPV6 =
      Pattern.compile(
          ":((25[0-5]|2[0-4]\\d|[01]?\\d{1,2})\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d{1,2})$");

  private static final char[] HEX_LUT =
      new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

  private static final VmObjectFactory<Integer> ipv4AddressFactory =
      new VmObjectFactory<Integer>(NetModule::getIpv4AddressClass)
          .addProperty(
              "bytes",
              repr ->
                  new VmBytes(
                      new byte[] {
                        (byte) (repr >>> 24),
                        (byte) ((repr >>> 16) & 0xff),
                        (byte) ((repr >>> 8) & 0xff),
                        (byte) (repr & 0xff),
                      }))
          .addBooleanProperty("isUnspecified", repr -> repr == 0)
          .addBooleanProperty("isLoopback", repr -> (repr & IPV4_LOOPBACK_MASK) == IPV4_LOOPBACK)
          .addStringProperty(
              "reverseDnsHostname",
              repr ->
                  String.join(
                          ".",
                          Integer.toString(repr & 0xff),
                          Integer.toString((repr >>> 8) & 0xff),
                          Integer.toString((repr >>> 16) & 0xff),
                          Integer.toString((repr >>> 24) & 0xff))
                      + ".in-addr.arpa");

  private record Ipv6Address(UInt128 repr, @Nullable String scopeId) {}

  private static final VmObjectFactory<Ipv6Address> ipv6AddressFactory =
      new VmObjectFactory<Ipv6Address>(NetModule::getIpv6AddressClass)
          .addProperty("bytes", addr -> new VmBytes(addr.repr.getBytes()))
          .addBooleanProperty("isUnspecified", addr -> addr.repr.equals(UInt128.ZERO))
          .addBooleanProperty("isLoopback", addr -> addr.repr.equals(UInt128.ONE))
          .addStringProperty(
              "reverseDnsHostname",
              addr -> {
                var bytes = addr.repr.getBytes();
                return String.format(
                    "%x.%x.%x.%x.%x.%x.%x.%x.%x.%x.%x.%x.%x.%x.%x.%x.%x.%x.%x.%x.%x.%x.%x.%x.%x.%x.%x..%x.%x.%x.%x.%x.ip6.arpa",
                    bytes[15] & 0xf,
                    (bytes[15] >>> 4) & 0xf,
                    bytes[14] & 0xf,
                    (bytes[14] >>> 4) & 0xf,
                    bytes[13] & 0xf,
                    (bytes[13] >>> 4) & 0xf,
                    bytes[12] & 0xf,
                    (bytes[12] >>> 4) & 0xf,
                    bytes[11] & 0xf,
                    (bytes[11] >>> 4) & 0xf,
                    bytes[10] & 0xf,
                    (bytes[10] >>> 4) & 0xf,
                    bytes[9] & 0xf,
                    (bytes[9] >>> 4) & 0xf,
                    bytes[8] & 0xf,
                    (bytes[8] >>> 4) & 0xf,
                    bytes[7] & 0xf,
                    (bytes[7] >>> 4) & 0xf,
                    bytes[6] & 0xf,
                    (bytes[6] >>> 4) & 0xf,
                    bytes[5] & 0xf,
                    (bytes[5] >>> 4) & 0xf,
                    bytes[4] & 0xf,
                    (bytes[4] >>> 4) & 0xf,
                    bytes[3] & 0xf,
                    (bytes[3] >>> 4) & 0xf,
                    bytes[2] & 0xf,
                    (bytes[2] >>> 4) & 0xf,
                    bytes[1] & 0xf,
                    (bytes[1] >>> 4) & 0xf,
                    bytes[0] & 0xf,
                    (bytes[0] >>> 4) & 0xf);
              })
          .addProperty("scopeId", addr -> VmNull.lift(addr.scopeId))
          .addBooleanProperty("isIpv4Mapped", addr -> isIpv4Mapped(addr.repr))
          .addBooleanProperty("isIpv4Embedded", addr -> isIpv4Embedded(addr.repr))
          .addBooleanProperty("isNonGlobalScope", addr -> isNonGlobalScope(addr.repr));

  private record IpNetwork(VmTyped base, long prefix) {}

  private static final VmObjectFactory<IpNetwork> ipv4NetworkFactory =
      new VmObjectFactory<IpNetwork>(NetModule::getIpv4NetworkClass)
          .addProperty("base", net -> net.base)
          .addIntProperty("prefix", net -> net.prefix)
          .addProperty(
              "firstAddress",
              net -> makeIpv4Address(getFirstAddress(getIpv4Repr(net.base), net.prefix)))
          .addProperty(
              "lastAddress",
              net -> makeIpv4Address(getLastAddress(getIpv4Repr(net.base), net.prefix)))
          .addProperty(
              "reverseDnsZone",
              net -> {
                if (net.prefix < IPV4_REVERSE_RESOLUTION) return VmNull.withoutDefault();

                var repr = getIpv4Repr(net.base);
                var sb = new StringBuilder();
                // i is the prefix len for each hostname segment
                // aaa.bbb.ccc.ddd.in-addr.arpa
                // ^^^ ^^^ ^^^ ^^^
                // 32  24  16  8
                for (var i = net.prefix - (net.prefix % IPV4_REVERSE_RESOLUTION);
                    i >= 0;
                    i -= IPV4_REVERSE_RESOLUTION) {
                  sb.append((repr >>> i) & 0xff);
                  sb.append('.');
                }
                sb.append("in-addr.arpa");
                return sb.toString();
              })
          .addProperty("subnetMask", net -> makeIpv4Address(maskHi(net.prefix)));

  private static final VmObjectFactory<IpNetwork> ipv6NetworkFactory =
      new VmObjectFactory<IpNetwork>(NetModule::getIpv6NetworkClass)
          .addProperty("base", net -> net.base)
          .addIntProperty("prefix", net -> net.prefix)
          .addProperty(
              "reverseDnsZone",
              net -> {
                if (net.prefix < IPV6_REVERSE_RESOLUTION) return VmNull.withoutDefault();

                var repr = getIpv6Repr(net.base).getBytes();
                var sb = new StringBuilder();
                // i is the prefix len for each hostname segment
                // a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p.q.r.s.t.u.v.w.x.y.z.0.1.2.3.4.5.ip6.arpa
                // ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^
                // 128 120 112 104 96| 88| 80| 72| 64| 56| 48| 40| 32| 24| 16| 8 |
                //   124 116 108 100 92  84  76  68  60  52  44  36  28  20  12  4
                for (var i = (int) (net.prefix - (net.prefix % IPV6_REVERSE_RESOLUTION));
                    i >= 0;
                    i -= IPV6_REVERSE_RESOLUTION) {
                  var b = repr[i >> 1];
                  // if even, use top 4 bits, if odd use the bottom 4
                  sb.append(HEX_LUT[i % 2 == 0 ? (b >>> 4) : (b & 0xf)]);
                  sb.append('.');
                }
                sb.append("ip6.arpa");
                return sb.toString();
              });

  private static final VmObjectFactory<byte[]> macAddressFactory =
      new VmObjectFactory<byte[]>(NetModule::getMacAddressClass).addProperty("bytes", VmBytes::new);

  public static VmTyped parseIpv4Address(String addr) {
    var parts = addr.split("\\.", 4);
    assert parts.length == 4;
    var repr =
        (Integer.parseInt(parts[0]) << 24)
            | (Integer.parseInt(parts[1]) << 16)
            | (Integer.parseInt(parts[2]) << 8)
            | Integer.parseInt(parts[3]);
    return makeIpv4Address(repr);
  }

  public static VmTyped makeIpv4Address(int repr) {
    return ipv4AddressFactory.create(repr);
  }

  public static VmTyped parseIpv6Address(String addr, @Nullable Node node) {
    var pctIdx = addr.indexOf('%');
    String scopeId = null;
    if (pctIdx >= 0) {
      scopeId = addr.substring(pctIdx + 1);
      addr = addr.substring(0, pctIdx);
    }

    var ipv4Matcher = IPV4_IN_IPV6.matcher(addr);
    String[] ipv4 = null;
    if (ipv4Matcher.find()) {
      var ipv4Str = addr.substring(ipv4Matcher.start() + 1);
      ipv4 = ipv4Str.split("\\.");
      assert ipv4.length == 4;
      addr = addr.substring(0, ipv4Matcher.start() + 1) + "0:0";
    }

    var bytes = new byte[16];
    var dcolonIdx = addr.indexOf("::");
    if (dcolonIdx < 0) {
      var parts = addr.split(":", 8);
      assert parts.length == 8;
      var part = Integer.parseInt(parts[0], 16);
      bytes[0] = (byte) (part >>> 8);
      bytes[1] = (byte) (part & 0xff);
      part = Integer.parseInt(parts[1], 16);
      bytes[2] = (byte) (part >>> 8);
      bytes[3] = (byte) (part & 0xff);
      part = Integer.parseInt(parts[2], 16);
      bytes[4] = (byte) (part >>> 8);
      bytes[5] = (byte) (part & 0xff);
      part = Integer.parseInt(parts[3], 16);
      bytes[6] = (byte) (part >>> 8);
      bytes[7] = (byte) (part & 0xff);
      part = Integer.parseInt(parts[4], 16);
      bytes[8] = (byte) (part >>> 8);
      bytes[9] = (byte) (part & 0xff);
      part = Integer.parseInt(parts[5], 16);
      bytes[10] = (byte) (part >>> 8);
      bytes[11] = (byte) (part & 0xff);
      part = Integer.parseInt(parts[6], 16);
      bytes[12] = (byte) (part >>> 8);
      bytes[13] = (byte) (part & 0xff);
      part = Integer.parseInt(parts[7], 16);
      bytes[14] = (byte) (part >>> 8);
      bytes[15] = (byte) (part & 0xff);
    } else {
      var pre = addr.substring(0, dcolonIdx).split(":");
      var post = addr.substring(dcolonIdx + 2).split(":");
      var j = 0;
      if (dcolonIdx > 0) {
        for (var s : pre) {
          assert s.length() <= 4;
          var part = Integer.parseInt(s, 16);
          bytes[j++] = (byte) (part >>> 8);
          bytes[j++] = (byte) (part & 0xff);
        }
      }
      if (dcolonIdx < addr.length() - 2) {
        j = 16 - post.length - post.length;
        for (String s : post) {
          assert s.length() <= 4;
          var part = Integer.parseInt(s, 16);
          bytes[j++] = (byte) (part >>> 8);
          bytes[j++] = (byte) (part & 0xff);
        }
      }
    }

    if (ipv4 != null) {
      try {
        // parse as int then cast because Byte.parseByte() can't parse 255
        bytes[12] = (byte) Integer.parseInt(ipv4[0]);
        bytes[13] = (byte) Integer.parseInt(ipv4[1]);
        bytes[14] = (byte) Integer.parseInt(ipv4[2]);
        bytes[15] = (byte) Integer.parseInt(ipv4[3]);
      } catch (NumberFormatException e) {
        // input string known valid already
        throw PklBugException.unreachableCode();
      }
    }

    return makeIpv6Address(UInt128.fromBytes(bytes), scopeId, node);
  }

  public static VmTyped makeIpv6Address(
      UInt128 repr, @Nullable String scopeId, @Nullable Node node) {
    if (scopeId != null && !isNonGlobalScope(repr)) {
      throw new VmExceptionBuilder()
          .evalError("ipv6GlobalAddressWithScopeId", Ipv6AddressNodes.toAddressString(repr, false))
          .withOptionalLocation(node)
          .build();
    }

    return ipv6AddressFactory.create(new Ipv6Address(repr, scopeId));
  }

  public static VmTyped parseIpv4Network(String cidr) {
    var parts = cidr.split("/", 2);
    assert parts.length == 2;
    var base = parseIpv4Address(parts[0]);
    try {
      var prefix = Long.parseLong(parts[1]);
      return makeIpv4Network(base, prefix);
    } catch (NumberFormatException e) {
      throw PklBugException.unreachableCode();
    }
  }

  public static VmTyped makeIpv4Network(VmTyped base, long prefix) {
    assert prefix <= 32 && prefix >= 0;
    return ipv4NetworkFactory.create(new IpNetwork(base, prefix));
  }

  public static VmTyped parseIpv6Network(String cidr, @Nullable Node node) {
    var parts = cidr.split("/", 2);
    assert parts.length == 2;
    var base = parseIpv6Address(parts[0], node);
    try {
      var prefix = Integer.parseInt(parts[1]);
      return makeIpv6Network(base, prefix);
    } catch (NumberFormatException e) {
      throw PklBugException.unreachableCode();
    }
  }

  public static VmTyped makeIpv6Network(VmTyped base, long prefix) {
    assert prefix <= 128 && prefix >= 0;
    return ipv6NetworkFactory.create(new IpNetwork(base, prefix));
  }

  public static VmTyped parseMacAddress(String mac) {
    var parts = mac.split("[.:-]");
    assert parts.length == 6;
    return macAddressFactory.create(
        new byte[] {
          (byte) Integer.parseInt(parts[0], 16),
          (byte) Integer.parseInt(parts[1], 16),
          (byte) Integer.parseInt(parts[2], 16),
          (byte) Integer.parseInt(parts[3], 16),
          (byte) Integer.parseInt(parts[4], 16),
          (byte) Integer.parseInt(parts[5], 16),
        });
  }

  private static final UInt128 IPV6_LINK_LOCAL_MASK = new UInt128(0xffffffffffffffffL, 0L);
  private static final UInt128 IPV6_LINK_LOCAL = new UInt128(0xfe80000000000000L, 0);

  private static boolean isNonGlobalScope(UInt128 repr) {
    // true if addr is link-local
    // https://www.rfc-editor.org/info/rfc4291/#section-2.5.6
    if (repr.and(IPV6_LINK_LOCAL_MASK).equals(IPV6_LINK_LOCAL)) return true;
    // true if multicast with scop < global (0xe)
    // https://www.rfc-editor.org/info/rfc4291/#section-2.7
    var hi = repr.hi();
    return (hi & 0xff000000_00000000L) == 0xff000000_00000000L
        && (hi & 0x000f0000_00000000L) < 0x000e0000_00000000L;
  }

  private static final UInt128 IPV4_MAPPED_MASK =
      new UInt128(0xffffffffffffffffL, 0xffffffff00000000L);
  private static final UInt128 IPV4_MAPPED = new UInt128(0L, 0x0000ffff00000000L);

  public static boolean isIpv4Mapped(UInt128 repr) {
    return repr.and(IPV4_MAPPED_MASK).equals(IPV4_MAPPED);
  }

  private static final UInt128 IPV4_EMBEDDED_MASK =
      new UInt128(0xffffffffffffffffL, 0xffffffff00000000L);
  private static final UInt128 IPV4_EMBEDDED = new UInt128(0x0064ff9b00000000L, 0L);

  public static boolean isIpv4Embedded(UInt128 repr) {
    return repr.and(IPV4_EMBEDDED_MASK).equals(IPV4_EMBEDDED);
  }

  public static int getIpv4Repr(VmTyped addr) {
    assert addr.getVmClass() == NetModule.getIpv4AddressClass();
    return (int) addr.getExtraStorage();
  }

  public static VmTyped getIpv4Base(VmTyped addr) {
    assert addr.getVmClass() == NetModule.getIpv4NetworkClass();
    return ((IpNetwork) addr.getExtraStorage()).base;
  }

  public static long getIpv4Prefix(VmTyped addr) {
    assert addr.getVmClass() == NetModule.getIpv4NetworkClass();
    return ((IpNetwork) addr.getExtraStorage()).prefix;
  }

  public static UInt128 getIpv6Repr(VmTyped addr) {
    assert addr.getVmClass() == NetModule.getIpv6AddressClass();
    return ((Ipv6Address) addr.getExtraStorage()).repr;
  }

  public static @Nullable String getIpv6ScopeId(VmTyped addr) {
    assert addr.getVmClass() == NetModule.getIpv6AddressClass();
    return ((Ipv6Address) addr.getExtraStorage()).scopeId;
  }

  public static VmTyped getIpv6Base(VmTyped addr) {
    assert addr.getVmClass() == NetModule.getIpv6NetworkClass();
    return ((IpNetwork) addr.getExtraStorage()).base;
  }

  public static long getIpv6Prefix(VmTyped addr) {
    assert addr.getVmClass() == NetModule.getIpv6NetworkClass();
    return ((IpNetwork) addr.getExtraStorage()).prefix;
  }

  public static int maskHi(long prefix) {
    if (prefix == 0) return 0;
    assert prefix <= 32 && prefix > 0;
    var shift = 32 - prefix;
    return (-1 >>> shift) << shift;
  }

  public static int maskLo(long suffix) {
    if (suffix == 0) return 0;
    assert suffix <= 32 && suffix > 0;
    var shift = 32 - suffix;
    return -1 >>> shift;
  }

  public static int getFirstAddress(int repr, long prefix) {
    return repr & maskHi(prefix);
  }

  public static int getLastAddress(int repr, long prefix) {
    return repr | maskLo(Integer.SIZE - prefix);
  }

  public static UInt128 getFirstAddress(UInt128 repr, long prefix) {
    return repr.and(UInt128.maskHi(prefix));
  }

  public static UInt128 getLastAddress(UInt128 repr, long prefix) {
    return repr.or(UInt128.maskLo(UInt128.SIZE - prefix));
  }
}
