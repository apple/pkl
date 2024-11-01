/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.pkl.core.ast.expression.binary.EqualNode;
import org.pkl.core.ast.expression.binary.EqualNodeGen;
import org.pkl.core.ast.internal.ReadCursorValueNode;
import org.pkl.core.ast.lambda.ApplyVmFunction1Node;
import org.pkl.core.ast.lambda.ApplyVmFunction2Node;
import org.pkl.core.ast.lambda.ApplyVmFunction2NodeGen;
import org.pkl.core.ast.lambda.ApplyVmFunction3Node;
import org.pkl.core.ast.lambda.ApplyVmFunction3NodeGen;
import org.pkl.core.runtime.*;
import org.pkl.core.runtime.VmObjectCursor.CursorOption;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.ExternalPropertyNode;

public final class MappingNodes {
  private MappingNodes() {}

  public abstract static class isEmpty extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(VmMapping self) {
      return self.isEmpty();
    }
  }

  public abstract static class isNotEmpty extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(VmMapping self) {
      return !self.isEmpty();
    }
  }

  public abstract static class length extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmMapping self) {
      return self.getLength();
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

      for (VmObject curr = self; curr != null; curr = curr.getParent()) {
        if (curr.hasMember(key)) return true;
      }

      return false;
    }
  }

  public abstract static class containsValue extends ExternalMethod1Node {
    @Child
    EqualNode equalNode = EqualNodeGen.create(VmUtils.unavailableSourceSection(), true, null, null);

    @Specialization
    protected boolean eval(
        VirtualFrame frame,
        VmMapping self,
        Object value,
        @Cached("create()") ReadCursorValueNode readCursorValueNode) {
      for (var cursor = self.entries(CursorOption.ANY_ORDER); cursor.advance(); ) {
        var cursorValue = readCursorValueNode.execute(frame, cursor);
        if (equalNode.executeWith(frame, value, cursorValue)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public boolean isInstrumentable() {
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

  public abstract static class getOrDefault extends ExternalMethod1Node {
    @Child private IndirectCallNode callNode = IndirectCallNode.create();
    @Child private ApplyVmFunction1Node applyNode = ApplyVmFunction1Node.create();

    @Specialization
    protected Object eval(VmMapping self, Object key) {
      var value = VmUtils.readMemberOrNull(self, key, callNode);
      if (value != null) {
        return value;
      }

      var defaultFunction = (VmFunction) VmUtils.readMember(self, Identifier.DEFAULT, callNode);
      return applyNode.execute(defaultFunction, key);
    }
  }

  public abstract static class fold extends ExternalMethod2Node {
    @Child private ApplyVmFunction3Node applyLambdaNode = ApplyVmFunction3NodeGen.create();

    @Specialization
    protected Object eval(
        VirtualFrame frame,
        VmMapping self,
        Object initial,
        VmFunction function,
        @Cached("create()") ReadCursorValueNode readCursorValueNode) {
      var result = initial;
      for (var cursor = self.entries(); cursor.advance(); ) {
        result =
            applyLambdaNode.execute(
                function, result, cursor.key(), readCursorValueNode.execute(frame, cursor));
      }
      return result;
    }
  }

  public abstract static class every extends ExternalMethod1Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected boolean eval(
        VirtualFrame frame,
        VmMapping self,
        VmFunction function,
        @Cached("create()") ReadCursorValueNode readCursorValueNode) {
      for (var cursor = self.entries(CursorOption.ANY_ORDER); cursor.advance(); ) {
        if (!applyLambdaNode.executeBoolean(
            function, cursor.key(), readCursorValueNode.execute(frame, cursor))) return false;
      }
      return true;
    }
  }

  public abstract static class any extends ExternalMethod1Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected boolean eval(
        VirtualFrame frame,
        VmMapping self,
        VmFunction function,
        @Cached("create()") ReadCursorValueNode readCursorValueNode) {
      for (var cursor = self.entries(CursorOption.ANY_ORDER); cursor.advance(); ) {
        if (applyLambdaNode.executeBoolean(
            function, cursor.key(), readCursorValueNode.execute(frame, cursor))) {
          return true;
        }
      }
      return false;
    }
  }

  public abstract static class toMap extends ExternalMethod0Node {
    @Specialization
    protected VmMap eval(
        VirtualFrame frame,
        VmMapping self,
        @Cached("create()") ReadCursorValueNode readCursorValueNode) {
      var builder = VmMap.builder();
      for (var cursor = self.entries(); cursor.advance(); ) {
        builder.add(cursor.key(), readCursorValueNode.execute(frame, cursor));
      }
      return builder.build();
    }
  }
}
