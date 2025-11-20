/*
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
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.ExternalMethod0Node;

public final class ObjectNodes {
  private ObjectNodes() {}

  public abstract static class toMixin extends ExternalMethod0Node {
    @Specialization
    protected VmFunction eval(VmObject self) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      var rootNode = new ObjectToMixinRootNode(self, new FrameDescriptor());
      return new VmFunction(
          VmUtils.createEmptyMaterializedFrame(),
          null,
          1,
          rootNode,
          null);
    }
  }

  private static final class ObjectToMixinRootNode extends org.pkl.core.ast.PklRootNode {
    private final VmObject sourceObject;

    public ObjectToMixinRootNode(VmObject sourceObject, FrameDescriptor descriptor) {
      super(null, descriptor);
      this.sourceObject = sourceObject;
    }

    @Override
    public com.oracle.truffle.api.source.SourceSection getSourceSection() {
      return VmUtils.unavailableSourceSection();
    }

    @Override
    public String getName() {
      return "toMixin";
    }

    @Override
    protected Object executeImpl(VirtualFrame frame) {
      var arguments = frame.getArguments();
      if (arguments.length != 3) {
        CompilerDirectives.transferToInterpreter();
        throw new VmExceptionBuilder()
            .evalError("wrongFunctionArgumentCount", 1, arguments.length - 2)
            .build();
      }

      var targetObject = arguments[2];

      if (!(targetObject instanceof VmObject)) {
        CompilerDirectives.transferToInterpreter();
        throw new VmExceptionBuilder()
            .typeMismatch(targetObject, BaseModule.getDynamicClass())
            .build();
      }

      var parent = (VmObject) targetObject;
      var parentLength = (parent instanceof VmDynamic) ? ((VmDynamic) parent).getLength() : 0;
      var sourceLength = (sourceObject instanceof VmDynamic) ? ((VmDynamic) sourceObject).getLength() : 0;
      var adjustedMembers = adjustMemberIndices(sourceObject.getMembers(), parentLength);

      return new VmDynamic(
          sourceObject.getEnclosingFrame(),
          parent,
          adjustedMembers,
          parentLength + sourceLength);
    }

    // Adjust element indices in the members map by offsetting them by parentLength
    @CompilerDirectives.TruffleBoundary
    private static org.graalvm.collections.UnmodifiableEconomicMap<Object, ObjectMember> adjustMemberIndices(
        org.graalvm.collections.UnmodifiableEconomicMap<Object, ObjectMember> members,
        long parentLength) {
      if (parentLength == 0) {
        return members;
      }

      var result = org.pkl.core.util.EconomicMaps.<Object, ObjectMember>create(
          org.pkl.core.util.EconomicMaps.size(members));

      var cursor = members.getEntries();
      while (cursor.advance()) {
        var key = cursor.getKey();
        var member = cursor.getValue();

        // If this is an element (not an entry with an Int key), offset the index
        if (member.isElement()) {
          // Elements always have Long keys
          var newKey = (Long) key + parentLength;
          org.pkl.core.util.EconomicMaps.put(result, newKey, member);
        } else {
          // Properties and entries are not offset
          org.pkl.core.util.EconomicMaps.put(result, key, member);
        }
      }

      return result;
    }
  }
}
