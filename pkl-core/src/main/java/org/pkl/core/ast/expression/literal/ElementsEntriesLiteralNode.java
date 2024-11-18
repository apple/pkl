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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.Nullable;

/**
 * Object literal that contains both elements and entries (and possibly properties). Example: `new
 * foo { "pigeon", [3] = "barn owl" }`
 */
@ImportStatic(BaseModule.class)
public abstract class ElementsEntriesLiteralNode extends SpecializedObjectLiteralNode {
  private final ObjectMember[] elements;
  @Children private final ExpressionNode[] keyNodes;
  private final ObjectMember[] values;

  public ElementsEntriesLiteralNode(
      SourceSection sourceSection,
      VmLanguage language,
      String qualifiedScopeName,
      boolean isCustomThisScope,
      @Nullable FrameDescriptor parametersDescriptor,
      UnresolvedTypeNode[] parameterTypes,
      UnmodifiableEconomicMap<Object, ObjectMember> properties,
      ObjectMember[] elements,
      ExpressionNode[] keyNodes,
      ObjectMember[] values) {

    super(
        sourceSection,
        language,
        qualifiedScopeName,
        isCustomThisScope,
        parametersDescriptor,
        parameterTypes,
        properties);
    this.elements = elements;
    this.keyNodes = keyNodes;
    this.values = values;

    assert elements.length > 0;
  }

  @Override
  protected ElementsEntriesLiteralNode copy(ExpressionNode newParentNode) {
    //noinspection ConstantConditions
    return ElementsEntriesLiteralNodeGen.create(
        sourceSection,
        language,
        qualifiedScopeName,
        isCustomThisScope,
        null, // copied node no longer has parameters
        new UnresolvedTypeNode[0], // ditto
        members,
        elements,
        keyNodes,
        values,
        newParentNode);
  }

  @Specialization(guards = "checkIsValidListingAmendment()")
  protected VmListing evalListing(VirtualFrame frame, VmListing parent) {
    return new VmListing(
        frame.materialize(),
        parent,
        createMembers(frame, parent.getLength()),
        parent.getLength() + elements.length);
  }

  @Specialization
  protected VmDynamic evalDynamic(VirtualFrame frame, VmDynamic parent) {
    return new VmDynamic(
        frame.materialize(),
        parent,
        createMembers(frame, parent.getLength()),
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

  @Specialization(guards = {"parent == getListingClass()", "checkIsValidListingAmendment()"})
  protected VmListing evalListingClass(
      VirtualFrame frame, @SuppressWarnings("unused") VmClass parent) {

    return new VmListing(
        frame.materialize(),
        BaseModule.getListingClass().getPrototype(),
        createMembers(frame, 0),
        elements.length);
  }

  @Specialization(guards = "parent == getDynamicClass()")
  protected VmDynamic evalDynamicClass(
      VirtualFrame frame, @SuppressWarnings("unused") VmClass parent) {

    return new VmDynamic(
        frame.materialize(),
        BaseModule.getDynamicClass().getPrototype(),
        createMembers(frame, 0),
        elements.length);
  }

  @Fallback
  @TruffleBoundary
  protected void fallback(Object parent) {
    elementsEntriesFallback(parent, elements[0], true);
  }

  @ExplodeLoop
  protected UnmodifiableEconomicMap<Object, ObjectMember> createMembers(
      VirtualFrame frame, int parentLength) {
    var result =
        EconomicMaps.<Object, ObjectMember>create(
            EconomicMaps.size(members) + keyNodes.length + elements.length);

    EconomicMaps.putAll(result, members);

    addListEntries(frame, parentLength, result, keyNodes, values);

    for (var i = 0; i < elements.length; i++) {
      EconomicMaps.put(result, (long) (parentLength + i), elements[i]);
    }

    return result;
  }
}
