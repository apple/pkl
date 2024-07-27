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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.PklBugException;
import org.pkl.core.ast.member.ListingOrMappingTypeCheckNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.Nullable;

public abstract class VmListingOrMapping<SELF extends VmListingOrMapping<SELF>> extends VmObject {

  /** The original that this listing/mapping is a surrogate of. It might have its own surrogatee. */
  private final @Nullable SELF surrogatee;

  private final ListingOrMappingTypeCheckNode typeCheckNode;
  private final MaterializedFrame typeNodeFrame;
  private final EconomicMap<Object, ObjectMember> cachedMembers = EconomicMaps.create();

  public VmListingOrMapping(
      MaterializedFrame enclosingFrame,
      @Nullable VmObject parent,
      UnmodifiableEconomicMap<Object, ObjectMember> members,
      @Nullable SELF surrogatee,
      @Nullable ListingOrMappingTypeCheckNode typeCheckNode,
      @Nullable MaterializedFrame typeNodeFrame) {
    super(enclosingFrame, parent, members);
    this.surrogatee = surrogatee;
    this.typeCheckNode = typeCheckNode;
    this.typeNodeFrame = typeNodeFrame;
  }

  ObjectMember findMember(Object key) {
    var member = EconomicMaps.get(cachedMembers, key);
    if (member != null) {
      return member;
    }
    if (surrogatee != null) {
      return surrogatee.findMember(key);
    }
    // member is guaranteed to exist; this is only called if `getCachedValue()` returns non-null
    // and `setCachedValue` will record the object member in `cachedMembers`.
    throw PklBugException.unreachableCode();
  }

  @Override
  public void setCachedValue(Object key, Object value, ObjectMember objectMember) {
    super.setCachedValue(key, value, objectMember);
    EconomicMaps.put(cachedMembers, key, objectMember);
  }

  // If a cached value already exists on the surrogatee, use it, and check its type.
  @Override
  public @Nullable Object getCachedValue(Object key) {
    var myCachedValue = super.getCachedValue(key);
    if (myCachedValue != null || surrogatee == null) {
      return myCachedValue;
    }
    var memberValue = surrogatee.getCachedValue(key);
    if (memberValue == null) {
      return null;
    }
    // optimization: don't use VmUtils.findMember to avoid iterating over all members
    var objectMember = findMember(key);
    var ret = checkMemberType(objectMember, memberValue, IndirectCallNode.getUncached());
    EconomicMaps.put(cachedValues, key, ret);
    return ret;
  }

  @Override
  public Object getExtraStorage() {
    if (surrogatee != null) {
      return surrogatee.getExtraStorage();
    }
    assert extraStorage != null;
    return extraStorage;
  }

  /** Perform a typecheck on this member, */
  public Object checkMemberType(
      ObjectMember member, Object memberValue, IndirectCallNode callNode) {
    if (!(member.isEntry() || member.isElement()) || typeCheckNode == null) {
      return memberValue;
    }
    assert typeNodeFrame != null;
    var ret = memberValue;
    if (surrogatee != null) {
      ret = surrogatee.checkMemberType(member, ret, callNode);
    }
    var callTarget = typeCheckNode.getCallTarget();
    try {
      return callNode.call(
          callTarget, VmUtils.getReceiver(typeNodeFrame), VmUtils.getOwner(typeNodeFrame), ret);
    } catch (VmException vmException) {
      CompilerDirectives.transferToInterpreter();
      // treat typecheck as part of the call stack to read the original member if there is a
      // source section for it.
      var sourceSection = member.getBodySection();
      if (!sourceSection.isAvailable()) {
        sourceSection = member.getSourceSection();
      }
      if (sourceSection.isAvailable()) {
        vmException
            .getInsertedStackFrames()
            .put(callTarget, VmUtils.createStackFrame(sourceSection, member.getQualifiedName()));
      }
      throw vmException;
    }
  }

  public abstract SELF createSurrogate(
      ListingOrMappingTypeCheckNode typeCheckNode, MaterializedFrame typeNodeFrame);
}
