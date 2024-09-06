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
package org.pkl.core.stdlib.base;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.util.HashSet;
import org.pkl.core.ast.lambda.ApplyVmFunction3Node;
import org.pkl.core.ast.lambda.ApplyVmFunction3NodeGen;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.MutableLong;
import org.pkl.core.util.MutableReference;

public final class MappingNodes {
  private MappingNodes() {}

  public abstract static class isEmpty extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected boolean eval(VmMapping self) {
      for (VmObjectLike curr = self; curr != null; curr = curr.getParent()) {
        var cursor = EconomicMaps.getEntries(curr.getMembers());
        while (cursor.advance()) {
          if (!(cursor.getKey() instanceof Identifier)) return false;
        }
      }
      return true;
    }
  }

  public abstract static class length extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected long eval(VmMapping self) {
      var count = new MutableLong(0);
      var visited = new HashSet<>();
      self.iterateMembers(
          (key, member) -> {
            var alreadyVisited = !visited.add(key);
            // important to record hidden member as visited before skipping it
            // because any overriding member won't carry a `hidden` identifier
            if (alreadyVisited || member.isLocalOrExternalOrHidden()) return true;
            count.getAndIncrement();
            return true;
          });
      return count.get();
    }
  }

  public abstract static class keys extends ExternalPropertyNode {
    @Specialization
    protected VmSet eval(VmMapping self) {
      return self.getAllKeys();
    }
  }

  public abstract static class containsKey extends ExternalMethod1Node {
    @Specialization
    protected boolean eval(VmMapping self, Object key) {
      if (self.hasCachedValue(key)) return true;

      for (VmObjectLike curr = self; curr != null; curr = curr.getParent()) {
        if (curr.hasMember(key)) return true;
      }

      return false;
    }
  }

  public abstract static class getOrNull extends ExternalMethod1Node {
    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    @Specialization
    protected Object eval(VmMapping self, Object key) {
      return VmNull.lift(VmUtils.readMemberOrNull(self, key, callNode));
    }
  }

  public abstract static class fold extends ExternalMethod2Node {
    @Child private ApplyVmFunction3Node applyLambdaNode = ApplyVmFunction3NodeGen.create();

    @Specialization
    protected Object eval(VmMapping self, Object initial, VmFunction function) {
      var result = new MutableReference<>(initial);
      self.forceAndIterateMemberValues(
          (key, def, value) -> {
            result.set(applyLambdaNode.execute(function, result.get(), key, value));
            return true;
          });
      return result.get();
    }
  }

  public abstract static class toMap extends ExternalMethod0Node {
    @Specialization
    protected VmMap eval(VmMapping self) {
      var builder = VmMap.builder();
      self.forceAndIterateMemberValues(
          (key, def, value) -> {
            builder.add(key, value);
            return true;
          });
      return builder.build();
    }
  }
}
