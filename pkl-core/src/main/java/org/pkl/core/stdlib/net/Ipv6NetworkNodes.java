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

import static org.pkl.core.stdlib.net.IpMacFactories.getFirstAddress;
import static org.pkl.core.stdlib.net.IpMacFactories.getIpv6Base;
import static org.pkl.core.stdlib.net.IpMacFactories.getIpv6Prefix;
import static org.pkl.core.stdlib.net.IpMacFactories.getIpv6Repr;
import static org.pkl.core.stdlib.net.IpMacFactories.getIpv6ScopeId;
import static org.pkl.core.stdlib.net.IpMacFactories.getLastAddress;
import static org.pkl.core.stdlib.net.IpMacFactories.makeIpv6Address;
import static org.pkl.core.stdlib.net.IpMacFactories.makeIpv6Network;

import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.Objects;
import org.pkl.core.ast.internal.ToStringNode;
import org.pkl.core.ast.internal.ToStringNodeGen;
import org.pkl.core.runtime.NetModule;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.util.UInt128;

public class Ipv6NetworkNodes {

  // firstAddress and lastAddress are externals instead of object factory properties
  // because constructing new IPv6 addresses requires passing a node (for eval errors)

  public abstract static class firstAddress extends ExternalPropertyNode {
    @Specialization
    protected VmTyped eval(VmTyped self) {
      var base = getIpv6Base(self);
      var repr = getIpv6Repr(base);
      var scopeId = getIpv6ScopeId(base);
      var prefix = getIpv6Prefix(self);
      return makeIpv6Address(getFirstAddress(repr, prefix), scopeId, this);
    }
  }

  public abstract static class lastAddress extends ExternalPropertyNode {
    @Specialization
    protected VmTyped eval(VmTyped self) {
      var base = getIpv6Base(self);
      var repr = getIpv6Repr(base);
      var scopeId = getIpv6ScopeId(base);
      var prefix = getIpv6Prefix(self);
      return makeIpv6Address(getLastAddress(repr, prefix), scopeId, this);
    }
  }

  @ImportStatic(NetModule.class)
  public abstract static class contains extends ExternalMethod1Node {
    @Specialization(guards = "addr.getVmClass() == getIpv6AddressClass()")
    protected boolean eval(VmTyped self, VmTyped addr) {
      var base = getIpv6Base(self);
      var repr = getIpv6Repr(base);
      var scopeId = getIpv6ScopeId(base);

      var otherRepr = getIpv6Repr(addr);
      var otherScopeId = getIpv6ScopeId(addr);

      if (scopeId != null && otherScopeId != null && !Objects.equals(scopeId, otherScopeId))
        return false;

      var prefix = getIpv6Prefix(self);
      return getFirstAddress(repr, prefix).le(otherRepr)
          && otherRepr.le(getLastAddress(repr, prefix));
    }

    @Specialization(replaces = "eval")
    protected boolean evalIpv6(
        @SuppressWarnings("unused") VmTyped self, @SuppressWarnings("unused") VmTyped ip) {
      return false;
    }
  }

  public abstract static class containsNetwork extends ExternalMethod1Node {
    @Specialization
    protected boolean eval(VmTyped self, VmTyped net) {
      var base = getIpv6Base(self);
      var repr = getIpv6Repr(base);
      var scopeId = getIpv6ScopeId(base);

      var netBase = getIpv6Base(net);
      var netRepr = getIpv6Repr(netBase);
      var netScopeId = getIpv6ScopeId(netBase);

      if (scopeId != null && netScopeId != null && !Objects.equals(scopeId, netScopeId))
        return false;

      var prefix = getIpv6Prefix(self);
      var netPrefix = getIpv6Prefix(net);
      return prefix <= netPrefix
          && getFirstAddress(repr, prefix).equals(getFirstAddress(netRepr, prefix));
    }
  }

  public abstract static class overlaps extends ExternalMethod1Node {
    @Specialization
    protected boolean eval(VmTyped self, VmTyped net) {
      var base = getIpv6Base(self);
      var repr = getIpv6Repr(base);
      var scopeId = getIpv6ScopeId(base);

      var netBase = getIpv6Base(net);
      var netRepr = getIpv6Repr(netBase);
      var netScopeId = getIpv6ScopeId(netBase);

      if (scopeId != null && netScopeId != null && !Objects.equals(scopeId, netScopeId))
        return false;

      var prefix = getIpv6Prefix(self);
      var netPrefix = getIpv6Prefix(net);

      return getFirstAddress(repr, prefix).equals(getFirstAddress(netRepr, prefix))
          || getFirstAddress(repr, netPrefix).equals(getFirstAddress(netRepr, netPrefix));
    }
  }

  public abstract static class reverse extends ExternalPropertyNode {
    private static final byte REVERSE_RESOLUTION = 4;
    private static final char[] HEX_LUT =
        new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    @Specialization
    protected Object eval(VmTyped self) {
      var prefix = getIpv6Prefix(self);
      if (prefix < REVERSE_RESOLUTION) return VmNull.withoutDefault();

      var repr = getIpv6Repr(getIpv6Base(self)).getBytes();
      var sb = new StringBuilder();
      // i is the prefix len for each hostname segment
      // a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p.q.r.s.t.u.v.w.x.y.z.0.1.2.3.4.5.ip6.arpa
      // ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^
      // 128 120 112 104 96| 88| 80| 72| 64| 56| 48| 40| 32| 24| 16| 8 |
      //   124 116 108 100 92  84  76  68  60  52  44  36  28  20  12  4
      for (var i = (int) (prefix - (prefix % REVERSE_RESOLUTION));
          i >= 0;
          i -= REVERSE_RESOLUTION) {
        var b = repr[i / 2];
        // if even use top 4 bits, if odd use the bottom 4
        sb.append(HEX_LUT[i % 2 == 0 ? (b >>> 4) : (b & 0xf)]);
        sb.append('.');
      }
      sb.append("ip6.arpa");
      return sb.toString();
    }
  }

  public abstract static class subdivideTo extends ExternalMethod1Node {
    @Specialization
    protected VmList eval(VmTyped self, long target) {
      var prefix = getIpv6Prefix(self);
      if (prefix == target) return VmList.of(this);
      assert target > prefix && target <= UInt128.SIZE;
      var count = 1 << (target - prefix);
      var step = 1 << (UInt128.SIZE - target);
      var base = getIpv6Base(self);
      var scopeId = getIpv6ScopeId(base);
      var repr = getIpv6Repr(base);
      var addr = getFirstAddress(repr, prefix);
      var builder = VmList.EMPTY.builder();
      for (var i = 0; i < count; i++, addr = addr.add(step)) {
        builder.add(makeIpv6Network(makeIpv6Address(addr, scopeId, this), target));
      }
      return builder.build();
    }
  }

  public abstract static class subnet extends ExternalMethod2Node {
    @Specialization
    protected VmTyped eval(VmTyped self, long target, long n) {
      var prefix = getIpv6Prefix(self);
      if (prefix == target) return self;
      assert target > prefix && target <= UInt128.SIZE;
      var step = UInt128.mask(UInt128.SIZE - target);
      var base = getIpv6Base(self);
      try {
        var offset = step.mult(n);
        var init = getFirstAddress(getIpv6Repr(base), prefix);
        return makeIpv6Network(
            makeIpv6Address(init.add(offset), getIpv6ScopeId(base), this), target);
      } catch (ArithmeticException e) {
        throw exceptionBuilder().evalError("integerOverflow").build();
      }
    }
  }

  public abstract static class toString extends ExternalMethod0Node {
    @Child ToStringNode toStringNode = ToStringNodeGen.create(sourceSection, null);

    @Specialization
    protected String eval(VirtualFrame frame, VmTyped self) {
      var base = getIpv6Base(self);
      var prefix = getIpv6Prefix(self);
      return String.format("%s/%d", toStringNode.executeWith(frame, base), prefix);
    }
  }
}
