/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.util.EnumSet;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.ast.member.ListingOrMappingTypeCastNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.Nullable;

public abstract class VmListingOrMapping extends VmObject {
  // reified type of listing elements and mapping values
  private final @Nullable ListingOrMappingTypeCastNode typeCastNode;
  private final @Nullable Object typeCheckReceiver;
  private final @Nullable VmObjectLike typeCheckOwner;

  public VmListingOrMapping(
      MaterializedFrame enclosingFrame,
      @Nullable VmObject parent,
      UnmodifiableEconomicMap<Object, ObjectMember> members) {
    super(enclosingFrame, parent, members);
    typeCastNode = null;
    typeCheckReceiver = null;
    typeCheckOwner = null;
  }

  public VmListingOrMapping(
      MaterializedFrame enclosingFrame,
      @Nullable VmObject parent,
      UnmodifiableEconomicMap<Object, ObjectMember> members,
      ListingOrMappingTypeCastNode typeCastNode,
      Object typeCheckReceiver,
      VmObjectLike typeCheckOwner) {
    super(enclosingFrame, parent, members);
    this.typeCastNode = typeCastNode;
    this.typeCheckReceiver = typeCheckReceiver;
    this.typeCheckOwner = typeCheckOwner;
  }

  // Recursively executes type casts between `owner` and `this` and returns the resulting value.
  public final Object executeTypeCasts(
      Object value,
      VmObjectLike owner,
      EnumSet<FrameMarker> frameMarkers,
      IndirectCallNode callNode,
      // if non-null, a stack frame for this member is inserted if a type cast fails
      @Nullable ObjectMember member,
      // Next type cast to be performed by the caller.
      // Avoids repeating the same type cast in some cases.
      @Nullable ListingOrMappingTypeCastNode nextTypeCastNode) {
    var newNextTypeCastNode = typeCastNode != null ? typeCastNode : nextTypeCastNode;
    @SuppressWarnings("DataFlowIssue")
    var result =
        this == owner
            ? value
            : ((VmListingOrMapping) parent)
                .executeTypeCasts(
                    value, owner, frameMarkers, callNode, member, newNextTypeCastNode);
    if (typeCastNode == null || typeCastNode == nextTypeCastNode) return result;
    var callTarget = typeCastNode.getCallTarget();
    try {
      return callNode.call(callTarget, frameMarkers, typeCheckReceiver, typeCheckOwner, result);
    } catch (VmException e) {
      CompilerDirectives.transferToInterpreter();
      if (member != null) {
        VmUtils.insertStackFrame(member, callTarget, e);
      }
      throw e;
    }
  }

  @Override
  @TruffleBoundary
  public final @Nullable Object getCachedValue(Object key) {
    var result = EconomicMaps.get(cachedValues, key);
    // if this object has members, `this[key]` may differ from `parent[key]`, so stop the search
    if (result != null || !members.isEmpty()) return result;

    // Optimization: Recursively steal value from parent cache to avoid computing it multiple times.
    // The current implementation has the following limitations and drawbacks:
    // * It only works if a parent has, coincidentally, already cached `key`.
    // * It turns getCachedValue() into an operation that isn't guaranteed to be fast and fail-safe.
    // * It requires making VmObject.getCachedValue() non-final,
    //   which is unfavorable for Truffle partial evaluation and JVM inlining.
    // * It may not be worth its cost for constant members and members that are cheap to compute.

    assert parent != null; // VmListingOrMapping always has a parent
    result = parent.getCachedValue(key);
    if (result == null) return null;

    if (typeCastNode != null && !(key instanceof Identifier)) {
      var callNode = IndirectCallNode.getUncached();
      var callTarget = typeCastNode.getCallTarget();
      try {
        result =
            callNode.call(callTarget, FrameMarkers.NONE, typeCheckReceiver, typeCheckOwner, result);
      } catch (VmException e) {
        var member = VmUtils.findMember(parent, key);
        assert member != null; // already found the member's cached value
        VmUtils.insertStackFrame(member, callTarget, e);
        throw e;
      }
    }
    setCachedValue(key, result);
    return result;
  }

  /**
   * Tells whether the value type of this listing/mapping is known to be a subtype of {@code
   * typeNode}. If {@code true}, type checks of individual values can be elided because
   * listings/mappings are covariant in their value type.
   */
  public final boolean isValueTypeKnownSubtypeOf(TypeNode typeNode) {
    if (typeNode.isNoopTypeCheck()) {
      return true;
    }
    if (typeCastNode == null) {
      return false;
    }
    return typeCastNode.getTypeNode().isEquivalentTo(typeNode);
  }
}
