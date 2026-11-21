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

import static org.pkl.core.stdlib.net.IpMacFactories.getIpv4Repr;
import static org.pkl.core.stdlib.net.IpMacFactories.makeIpv4Address;

import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;

public class Ipv4AddressNodes {

  public abstract static class add extends ExternalMethod1Node {
    private static final long MASK = 0xffffffffL;

    @Specialization
    protected VmTyped eval(VmTyped self, long n) {
      return makeIpv4Address((int) ((getIpv4Repr(self) + n) & MASK));
    }
  }

  public abstract static class rangeTo extends ExternalMethod1Node {
    @Specialization
    protected VmList eval(VmTyped self, VmTyped other) {
      var repr = getIpv4Repr(self);
      var max = getIpv4Repr(other);
      var builder = VmList.EMPTY.builder();
      for (; repr <= max; repr++) {
        builder.add(makeIpv4Address(repr));
      }
      return builder.build();
    }
  }

  public abstract static class toString extends ExternalMethod0Node {
    @Specialization
    protected String eval(VmTyped self) {
      var repr = getIpv4Repr(self);
      return String.format(
          "%d.%d.%d.%d",
          (repr >>> 24) & 0xff, (repr >>> 16) & 0xff, (repr >>> 8) & 0xff, repr & 0xff);
    }
  }
}
