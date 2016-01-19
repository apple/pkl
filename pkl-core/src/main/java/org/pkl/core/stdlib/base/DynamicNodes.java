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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;

public final class DynamicNodes {
  private DynamicNodes() {}

  public abstract static class length extends ExternalMethod0Node {
    @Specialization
    protected long eval(VmDynamic self) {
      return self.getLength();
    }
  }

  public abstract static class hasProperty extends ExternalMethod1Node {
    @Specialization
    protected Object eval(VmDynamic self, String name) {
      var memberName = Identifier.get(name);
      if (self.hasCachedValue(name)) return true;

      for (VmObjectLike curr = self; curr != null; curr = curr.getParent()) {
        if (curr.hasMember(memberName)) return true;
      }
      return false;
    }
  }

  public abstract static class getProperty extends ExternalMethod1Node {
    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    @Specialization
    protected Object eval(VmDynamic self, String name) {
      return VmUtils.readMember(self, Identifier.get(name), callNode);
    }
  }

  public abstract static class getPropertyOrNull extends ExternalMethod1Node {
    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    @Specialization
    protected Object eval(VmDynamic self, String name) {
      return VmNull.lift(VmUtils.readMemberOrNull(self, Identifier.get(name), callNode));
    }
  }

  public abstract static class toMap extends ExternalMethod0Node {
    @Specialization
    protected VmMap eval(VmDynamic self) {
      var builder = VmMap.builder();
      self.forceAndIterateMemberValues( // could be smarter and skip forcing elements
          (key, member, value) -> {
            if (!member.isElement()) {
              builder.add(key instanceof Identifier ? key.toString() : key, value);
            }
            return true;
          });
      return builder.build();
    }
  }

  public abstract static class toList extends ExternalMethod0Node {
    @Specialization
    protected VmList eval(VmDynamic self) {
      var builder = VmList.EMPTY.builder();
      self.forceAndIterateMemberValues( // could be smarter and only force elements
          (key, member, value) -> {
            if (member.isElement()) {
              builder.add(value);
            }
            return true;
          });
      return builder.build();
    }
  }

  public abstract static class toTyped extends ExternalMethod1Node {
    @Specialization
    protected VmObjectLike eval(VmDynamic self, VmClass clazz) {
      if (!clazz.isSubclassOf(BaseModule.getTypedClass())) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder().evalError("notASubclassOfTyped", clazz).build();
      }

      VmUtils.checkIsInstantiable(clazz, this);

      var result =
          new VmTyped(
              VmUtils.createEmptyMaterializedFrame(),
              clazz.getPrototype(),
              clazz,
              clazz.getDynamicToTypedMembers());
      result.setExtraStorage(self);
      return result;
    }
  }
}
