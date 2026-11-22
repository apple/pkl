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

import static org.pkl.core.stdlib.net.IpMacFactories.getIpv6Repr;
import static org.pkl.core.stdlib.net.IpMacFactories.getIpv6ScopeId;
import static org.pkl.core.stdlib.net.IpMacFactories.isIpv4Embedded;
import static org.pkl.core.stdlib.net.IpMacFactories.isIpv4Mapped;
import static org.pkl.core.stdlib.net.IpMacFactories.makeIpv6Address;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import java.util.Objects;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.util.UInt128;

public class Ipv6AddressNodes {

  public abstract static class add extends ExternalMethod1Node {
    @Specialization
    protected VmTyped eval(VmTyped self, long n) {
      return makeIpv6Address(getIpv6Repr(self).add(n), getIpv6ScopeId(self), this);
    }
  }

  public abstract static class rangeTo extends ExternalMethod1Node {
    @Specialization
    protected VmList eval(VmTyped self, VmTyped other) {
      var repr = getIpv6Repr(self);
      var max = getIpv6Repr(other);
      var selfScopeId = getIpv6ScopeId(self);
      var maxScopeId = getIpv6ScopeId(other);
      var scopeId = Objects.equals(selfScopeId, maxScopeId) ? selfScopeId : null;
      var builder = VmList.EMPTY.builder();
      for (; repr.le(max); repr = repr.add(1)) {
        builder.add(makeIpv6Address(repr, scopeId, this));
      }
      return builder.build();
    }
  }

  public abstract static class toString extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self) {
      var repr = getIpv6Repr(self);
      var scopeId = getIpv6ScopeId(self);
      return scopeId == null
          ? toAddressString(repr, false)
          : toAddressString(repr, false) + "%" + scopeId;
    }
  }

  public abstract static class withScopeId extends ExternalMethod1Node {
    @Specialization
    protected VmTyped eval(VmTyped self, Object scopeId) {
      return makeIpv6Address(getIpv6Repr(self), (String) VmNull.unwrap(scopeId), this);
    }
  }

  public abstract static class toExpandedString extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self) {
      var repr = getIpv6Repr(self);
      var scopeId = getIpv6ScopeId(self);
      return scopeId == null
          ? toAddressString(repr, true)
          : toAddressString(repr, true) + "%" + scopeId;
    }
  }

  @TruffleBoundary
  public static String toAddressString(UInt128 repr, boolean expand) {
    var isIpv4 = isIpv4Mapped(repr) || isIpv4Embedded(repr);
    var dcolonIdx = -1;
    var dcolonLen = -1;
    var bytes = repr.getBytes();

    // if applicable find the segment index and length of the ::
    // this is the longest stretch of 0 segments
    if (!expand) {
      var zeroStart = -1;
      var j = 0;
      for (var i = 0; i < 8; i++, j += 2) {
        if (bytes[j] == 0 && bytes[j + 1] == 0) {
          if (zeroStart < 0) {
            zeroStart = i;
          }
        } else {
          if (zeroStart >= 0) {
            var zeroLen = i - zeroStart;
            if (zeroLen > dcolonLen) {
              dcolonIdx = zeroStart;
              dcolonLen = zeroLen;
            }
          }
          zeroStart = -1;
        }
      }
      if (zeroStart >= 0) {
        var zeroLen = 8 - zeroStart;
        if (zeroLen > dcolonLen) {
          dcolonIdx = zeroStart;
          dcolonLen = zeroLen;
        }
      }
    }

    var sb = new StringBuilder();
    var j = 0;
    for (var i = 0; i < 8; i++) {
      if (i == dcolonIdx) {
        i += dcolonLen - 1; // 1 offsets i++ in loop
        j += dcolonLen + dcolonLen;
        sb.append(':');
        // if the last segment, insert both ::
        // otherwise the next segment adds its own :
        if (i + 1 == 8 || dcolonLen == 8) {
          sb.append(':');
        }
      } else {
        if (i > 0) {
          sb.append(':');
        }
        if (isIpv4 && i == 6) {
          // break the loop and append the v4 segment after
          break;
        }
        var val = Byte.toUnsignedInt(bytes[j++]) << 8 | Byte.toUnsignedInt(bytes[j++]);
        sb.append(String.format(expand ? "%04x" : "%x", val));
      }
    }

    if (isIpv4) {
      sb.append(
          String.format(
              "%d.%d.%d.%d",
              Byte.toUnsignedInt(bytes[12]),
              Byte.toUnsignedInt(bytes[13]),
              Byte.toUnsignedInt(bytes[14]),
              Byte.toUnsignedInt(bytes[15])));
    }

    return sb.toString();
  }
}
