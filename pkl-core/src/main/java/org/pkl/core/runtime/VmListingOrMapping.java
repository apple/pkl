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
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.PklBugException;
import org.pkl.core.ast.member.ListingOrMappingTypeCastNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.EconomicSets;
import org.pkl.core.util.Nullable;

public abstract class VmListingOrMapping<SELF extends VmListingOrMapping<SELF>> extends VmObject {

  /**
   * A Listing or Mapping typecast creates a new object that contains a new typecheck node, and
   * delegates member lookups to this delegate.
   */
  private final @Nullable SELF delegate;

  private final @Nullable ListingOrMappingTypeCastNode typeCastNode;
  private final MaterializedFrame typeNodeFrame;
  private final EconomicMap<Object, ObjectMember> cachedMembers = EconomicMaps.create();
  private final EconomicSet<Object> checkedMembers = EconomicSets.create();

  public VmListingOrMapping(
      MaterializedFrame enclosingFrame,
      @Nullable VmObject parent,
      UnmodifiableEconomicMap<Object, ObjectMember> members,
      @Nullable SELF delegate,
      @Nullable ListingOrMappingTypeCastNode typeCastNode,
      @Nullable MaterializedFrame typeNodeFrame) {
    super(enclosingFrame, parent, members);
    this.delegate = delegate;
    this.typeCastNode = typeCastNode;
    this.typeNodeFrame = typeNodeFrame;
  }

  ObjectMember findMember(Object key) {
    var member = EconomicMaps.get(cachedMembers, key);
    if (member != null) {
      return member;
    }
    if (delegate != null) {
      return delegate.findMember(key);
    }
    // member is guaranteed to exist; this is only called if `getCachedValue()` returns non-null
    // and `setCachedValue` will record the object member in `cachedMembers`.
    throw PklBugException.unreachableCode();
  }

  public @Nullable ListingOrMappingTypeCastNode getTypeCastNode() {
    return typeCastNode;
  }

  @Override
  public void setCachedValue(Object key, Object value, ObjectMember objectMember) {
    super.setCachedValue(key, value, objectMember);
    EconomicMaps.put(cachedMembers, key, objectMember);
  }

  @Override
  public @Nullable Object getCachedValue(Object key) {
    var myCachedValue = super.getCachedValue(key);
    if (myCachedValue != null || delegate == null) {
      return myCachedValue;
    }
    var memberValue = delegate.getCachedValue(key);
    // if this object member appears inside `checkedMembers`, we have already checked its type
    // and can safely return it.
    if (EconomicSets.contains(checkedMembers, key)) {
      return memberValue;
    }
    if (memberValue == null) {
      return null;
    }
    // If a cached value already exists on the delegate, run a typecast on it.
    // optimization: don't use `VmUtils.findMember` to avoid iterating over all members
    var objectMember = findMember(key);
    var ret = typecastObjectMember(objectMember, memberValue, IndirectCallNode.getUncached());
    if (ret != memberValue) {
      EconomicMaps.put(cachedValues, key, ret);
    } else {
      // optimization: don't add to own cached values if typecast results in the same value
      EconomicSets.add(checkedMembers, key);
    }
    return ret;
  }

  @Override
  public Object getExtraStorage() {
    if (delegate != null) {
      return delegate.getExtraStorage();
    }
    assert extraStorage != null;
    return extraStorage;
  }

  /** Perform a typecast on this member, */
  public Object typecastObjectMember(
      ObjectMember member, Object memberValue, IndirectCallNode callNode) {
    if (!(member.isEntry() || member.isElement()) || typeCastNode == null) {
      return memberValue;
    }
    assert typeNodeFrame != null;
    var ret = memberValue;
    if (delegate != null) {
      ret = delegate.typecastObjectMember(member, ret, callNode);
    }
    var callTarget = typeCastNode.getCallTarget();
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

  public abstract SELF withCheckedMembers(
      ListingOrMappingTypeCastNode typeCastNode, MaterializedFrame typeNodeFrame);

  /** Tells if this mapping/listing runs the same typechecks as {@code typeNode}. */
  public boolean hasSameChecksAs(TypeNode typeNode) {
    if (typeCastNode == null) {
      return false;
    }
    if (typeCastNode.getTypeNode().isEquivalentTo(typeNode)) {
      return true;
    }
    // we can say the check is the same if the delegate has this check.
    // when `Listing<Any>` delegates to `Listing<UInt>`, it has the same checks as a `UInt`
    // typenode.
    if (delegate != null) {
      return delegate.hasSameChecksAs(typeNode);
    }
    return false;
  }
}
