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

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;

public final class TypedNodes {
  private TypedNodes() {}

  public abstract static class hasProperty extends ExternalMethod1Node {
    @Specialization
    protected Object eval(VmTyped self, String name) {
      return self.getVmClass().hasProperty(Identifier.get(name));
    }
  }

  public abstract static class getProperty extends ExternalMethod1Node {
    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    @Specialization
    protected Object eval(VmTyped self, String name) {
      return VmUtils.readMember(self, Identifier.get(name), callNode);
    }
  }

  public abstract static class getPropertyOrNull extends ExternalMethod1Node {
    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    @Specialization
    protected Object eval(VmTyped self, String name) {
      return VmNull.lift(VmUtils.readMemberOrNull(self, Identifier.get(name), callNode));
    }
  }

  public abstract static class toMap extends ExternalMethod0Node {
    @Specialization
    protected VmMap eval(VmTyped self) {
      var builder = VmMap.builder();
      self.forceAndIterateMemberValues(
          (memberKey, memberDef, memberValue) -> {
            // exclude type definitions
            if (memberDef.isClass() || memberDef.isTypeAlias()) return true;
            builder.add(memberKey.toString(), memberValue);
            return true;
          });
      return builder.build();
    }
  }

  public abstract static class toDynamic extends ExternalMethod0Node {
    @Specialization
    protected VmObjectLike eval(VmTyped self) {
      var result =
          new VmDynamic(
              VmUtils.createEmptyMaterializedFrame(),
              BaseModule.getDynamicClass().getPrototype(),
              self.getVmClass().getTypedToDynamicMembers(),
              0);
      result.setExtraStorage(self);
      return result;
    }
  }
}
