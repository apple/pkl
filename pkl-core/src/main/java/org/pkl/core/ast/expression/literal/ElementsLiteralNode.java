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
package org.pkl.core.ast.expression.literal;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.Nullable;

/**
 * Object literal that contains elements (and possibly properties) but not entries. Example: `new
 * foo { "pigeon" }`
 */
@ImportStatic(BaseModule.class)
public abstract class ElementsLiteralNode extends SpecializedObjectLiteralNode {
  private final ObjectMember[] elements;

  public ElementsLiteralNode(
      SourceSection sourceSection,
      VmLanguage language,
      String qualifiedScopeName,
      boolean isCustomThisScope,
      @Nullable FrameDescriptor parametersDescriptor,
      UnresolvedTypeNode[] parameterTypes,
      UnmodifiableEconomicMap<Object, ObjectMember> properties,
      ObjectMember[] elements) {

    super(
        sourceSection,
        language,
        qualifiedScopeName,
        isCustomThisScope,
        parametersDescriptor,
        parameterTypes,
        properties);
    this.elements = elements;

    assert elements.length > 0;
  }

  @Override
  protected ElementsLiteralNode copy(ExpressionNode newParentNode) {
    //noinspection ConstantConditions
    return ElementsLiteralNodeGen.create(
        sourceSection,
        language,
        qualifiedScopeName,
        isCustomThisScope,
        null, // copied node no longer has parameters
        new UnresolvedTypeNode[0], // ditto
        members,
        elements,
        newParentNode);
  }

  @Specialization(guards = "parent.getLength() == parentLength")
  protected VmDynamic evalDynamicCached(
      VirtualFrame frame,
      VmDynamic parent,
      @Cached("parent.getLength()") int parentLength,
      @Cached("createMembers(parentLength)")
          UnmodifiableEconomicMap<Object, ObjectMember> members) {

    return new VmDynamic(frame.materialize(), parent, members, parentLength + elements.length);
  }

  @Specialization
  protected VmDynamic evalDynamicUncached(VirtualFrame frame, VmDynamic parent) {
    return new VmDynamic(
        frame.materialize(),
        parent,
        createMembers(parent.getLength()),
        parent.getLength() + elements.length);
  }

  @Specialization
  protected Object evalNull(VirtualFrame frame, VmNull parent) {
    // assumes that Graal PE can handle recursive call to same node
    return executeWithParent(frame, parent.getDefaultValue());
  }

  @Specialization(guards = "checkIsValidFunctionAmendment(parent)")
  protected VmFunction evalFunction(
      VirtualFrame frame,
      VmFunction parent,
      @Cached(value = "createAmendFunctionNode(frame)", neverDefault = true)
          AmendFunctionNode amendFunctionNode) {

    return amendFunctionNode.execute(frame, parent);
  }

  @Specialization(
      guards = {
        "parent == getListingClass()",
        "checkIsValidListingAmendment()",
        "checkMaxListingMemberIndex(0)"
      })
  protected VmListing evalListingClass(
      VirtualFrame frame,
      @SuppressWarnings("unused") VmClass parent,
      @Cached(value = "createMembers(0)", neverDefault = true)
          UnmodifiableEconomicMap<Object, ObjectMember> members) {

    return new VmListing(
        frame.materialize(), BaseModule.getListingClass().getPrototype(), members, elements.length);
  }

  @Specialization(guards = "parent == getDynamicClass()")
  protected VmDynamic evalDynamicClass(
      VirtualFrame frame,
      @SuppressWarnings("unused") VmClass parent,
      @Cached(value = "createMembers(0)", neverDefault = true)
          UnmodifiableEconomicMap<Object, ObjectMember> members) {
    return new VmDynamic(
        frame.materialize(), BaseModule.getDynamicClass().getPrototype(), members, elements.length);
  }

  @Specialization(
      guards = {
        "checkIsValidListingAmendment()",
        "parent.getLength() == parentLength",
        "checkMaxListingMemberIndex(parentLength)"
      })
  protected VmListing evalListingCached(
      VirtualFrame frame,
      VmListing parent,
      @Cached("parent.getLength()") int parentLength,
      @Cached("createMembers(parentLength)")
          UnmodifiableEconomicMap<Object, ObjectMember> properties) {

    return new VmListing(frame.materialize(), parent, properties, parentLength + elements.length);
  }

  @Specialization(guards = "checkIsValidListingAmendment()")
  protected VmListing evalListingUncached(VirtualFrame frame, VmListing parent) {
    checkMaxListingMemberIndex(parent.getLength());
    return new VmListing(
        frame.materialize(),
        parent,
        createMembers(parent.getLength()),
        parent.getLength() + elements.length);
  }

  @Fallback
  @TruffleBoundary
  protected void fallback(Object parent) {
    elementsEntriesFallback(parent, elements[0], true);
  }

  // offset element keys according to parentLength
  protected UnmodifiableEconomicMap<Object, ObjectMember> createMembers(int parentLength) {
    var result =
        EconomicMaps.<Object, ObjectMember>create(EconomicMaps.size(members) + elements.length);
    EconomicMaps.putAll(result, members);
    for (var i = 0; i < elements.length; i++) {
      EconomicMaps.put(result, (long) (parentLength + i), elements[i]);
    }
    return result;
  }
}
