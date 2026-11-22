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
import static org.pkl.core.stdlib.net.IpMacFactories.getIpv4Base;
import static org.pkl.core.stdlib.net.IpMacFactories.getIpv4Prefix;
import static org.pkl.core.stdlib.net.IpMacFactories.getIpv4Repr;
import static org.pkl.core.stdlib.net.IpMacFactories.getLastAddress;
import static org.pkl.core.stdlib.net.IpMacFactories.makeIpv4Address;
import static org.pkl.core.stdlib.net.IpMacFactories.makeIpv4Network;
import static org.pkl.core.stdlib.net.IpMacFactories.maskHi;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.pkl.core.ast.internal.ToStringNode;
import org.pkl.core.ast.internal.ToStringNodeGen;
import org.pkl.core.runtime.NetModule;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.ExternalPropertyNode;

public class Ipv4NetworkNodes {

  public abstract static class subnetMask extends ExternalPropertyNode {
    @Specialization
    protected VmTyped eval(VmTyped self) {
      var prefix = getIpv4Prefix(self);
      return makeIpv4Address(maskHi(prefix));
    }
  }

  @ImportStatic(NetModule.class)
  public abstract static class contains extends ExternalMethod1Node {
    @Specialization(guards = "addr.getVmClass() == getIpv4AddressClass()")
    protected boolean eval(VmTyped self, VmTyped addr) {
      var repr = getIpv4Repr(getIpv4Base(self));
      var prefix = getIpv4Prefix(self);
      var ip = getIpv4Repr(getIpv4Base(addr));
      return getFirstAddress(repr, prefix) <= ip && ip <= getLastAddress(repr, prefix);
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
      var repr = getIpv4Repr(getIpv4Base(self));
      var prefix = getIpv4Prefix(self);
      var netRepr = getIpv4Repr(getIpv4Base(net));
      var netPrefix = getIpv4Prefix(net);

      return prefix <= netPrefix
          && getFirstAddress(repr, prefix) == getFirstAddress(netRepr, prefix);
    }
  }

  public abstract static class overlaps extends ExternalMethod1Node {
    @Specialization
    protected boolean eval(VmTyped self, VmTyped net) {
      var repr = getIpv4Repr(getIpv4Base(self));
      var prefix = getIpv4Prefix(self);
      var netRepr = getIpv4Repr(getIpv4Base(net));
      var netPrefix = getIpv4Prefix(net);

      return getFirstAddress(repr, prefix) == getFirstAddress(netRepr, prefix)
          || getFirstAddress(repr, netPrefix) == getFirstAddress(netRepr, netPrefix);
    }
  }

  public abstract static class subdivideTo extends ExternalMethod1Node {
    @Specialization
    protected VmList eval(VmTyped self, long target) {
      var prefix = getIpv4Prefix(self);
      if (prefix == target) return VmList.of(this);
      assert target > prefix && target <= Integer.SIZE;
      var count = 1 << (target - prefix);
      var step = 1 << (Integer.SIZE - target);
      var repr = getIpv4Repr(getIpv4Base(self));
      var addr = getFirstAddress(repr, prefix);
      var builder = VmList.EMPTY.builder();
      for (var i = 0; i < count; i++, addr += step) {
        builder.add(makeIpv4Network(makeIpv4Address(addr), target));
      }
      return builder.build();
    }
  }

  public abstract static class subnet extends ExternalMethod2Node {
    @Specialization
    protected VmTyped eval(VmTyped self, long target, long n) {
      var prefix = getIpv4Prefix(self);
      if (prefix == target) return self;
      assert target > prefix && target <= Integer.SIZE;
      var step = 1 << (Integer.SIZE - target);
      var base = getIpv4Base(self);
      try {
        var offset = StrictMath.multiplyExact(step, n);
        var init = getFirstAddress(getIpv4Repr(base), prefix);
        return makeIpv4Network(makeIpv4Address((int) (init + offset)), target);
      } catch (ArithmeticException e) {
        throw exceptionBuilder().evalError("integerOverflow").build();
      }
    }
  }

  public abstract static class toString extends ExternalMethod0Node {
    @Child ToStringNode toStringNode = ToStringNodeGen.create(sourceSection, null);

    @Specialization
    protected String eval(VirtualFrame frame, VmTyped self) {
      var base = getIpv4Base(self);
      var prefix = getIpv4Prefix(self);
      return doEval(toStringNode.executeWith(frame, base), prefix);
    }

    @TruffleBoundary
    private String doEval(String ip, long prefix) {
      return String.format("%s/%d", ip, prefix);
    }
  }
}
