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

import static org.pkl.core.stdlib.net.IpMacFactories.getIpv6Base;
import static org.pkl.core.stdlib.net.IpMacFactories.getIpv6Repr;
import static org.pkl.core.stdlib.net.IpMacFactories.getIpv6ScopeId;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.util.UInt128;

public class MacAddressNodes {

  private static byte[] getRepr(VmTyped self) {
    return (byte[]) self.getExtraStorage();
  }

  public abstract static class toString extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self) {
      var repr = getRepr(self);
      return String.format(
          "%02x:%02x:%02x:%02x:%02x:%02x", repr[0], repr[1], repr[2], repr[3], repr[4], repr[5]);
    }
  }

  public abstract static class toSlaacAddress extends ExternalMethod1Node {
    @Specialization
    protected VmTyped eval(VmTyped self, VmTyped net) {
      var repr = getRepr(self);
      var base = getIpv6Base(net);
      var newRepr = getIpv6Repr(base).getBytes();
      newRepr[8] = (byte) (repr[0] ^ 0x02);
      newRepr[9] = repr[1];
      newRepr[10] = repr[2];
      newRepr[11] = -1;
      newRepr[12] = -2;
      newRepr[13] = repr[3];
      newRepr[14] = repr[4];
      newRepr[15] = repr[5];

      return IpMacFactories.makeIpv6Address(UInt128.fromBytes(newRepr), getIpv6ScopeId(base), this);
    }
  }
}
