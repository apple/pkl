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
package org.pkl.core.ast;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.*;

public final class ObjectToMixinNode extends PklRootNode {
  private final VmObject sourceObject;

  public ObjectToMixinNode(VmObject sourceObject, FrameDescriptor descriptor) {
    super(null, descriptor);
    this.sourceObject = sourceObject;
  }

  @Override
  public SourceSection getSourceSection() {
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
    var parentLength = getObjectLength(parent);
    var sourceLength = getObjectLength(sourceObject);
    var allSourceMembers = collectAllMembers(sourceObject);
    var adjustedMembers = adjustMemberIndices(allSourceMembers, parentLength);

    return new VmDynamic(
        sourceObject.getEnclosingFrame(),
        parent,
        adjustedMembers,
        (int) (parentLength + sourceLength));
  }

  // Get the length of an object (number of elements)
  private static long getObjectLength(VmObject obj) {
    if (obj instanceof VmDynamic) {
      return ((VmDynamic) obj).getLength();
    } else if (obj instanceof VmListing) {
      return ((VmListing) obj).getLength();
    } else if (obj instanceof VmMapping) {
      return ((VmMapping) obj).getLength();
    }
    return 0;
  }

  // Collect all members from the source object and its entire parent chain (including prototypes)
  @CompilerDirectives.TruffleBoundary
  private static org.graalvm.collections.UnmodifiableEconomicMap<Object, ObjectMember> collectAllMembers(
      VmObject sourceObject) {
    var result = org.pkl.core.util.EconomicMaps.<Object, ObjectMember>create();

    // Build list of objects from source to root (including all prototypes)
    var chain = new java.util.ArrayList<VmObject>();
    var current = sourceObject;
    while (current != null) {
      chain.add(current);
      current = current.getParent();
    }

    // Iterate in reverse order (from root down to source)
    // This ensures parent members appear first, but child members override parents
    for (int i = chain.size() - 1; i >= 0; i--) {
      var obj = chain.get(i);
      var entries = obj.getMembers().getEntries();
      while (entries.advance()) {
        var key = entries.getKey();
        var member = entries.getValue();
        if (member.isLocalOrExternalOrHidden()) continue;
        // Skip undefined members (required properties with no default value)
        if (member.isUndefined()) continue;
        // Always put the member - later objects in the chain override earlier ones
        org.pkl.core.util.EconomicMaps.put(result, key, member);
      }
    }

    return result;
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
