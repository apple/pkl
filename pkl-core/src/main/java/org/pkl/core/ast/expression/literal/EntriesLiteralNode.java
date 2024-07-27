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
package org.pkl.core.ast.expression.literal;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.runtime.VmException.ProgramValue;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.Nullable;

/**
 * Object literal that contains entries (and possibly properties) but not elements. Additionally, at
 * least one entry key is not a constant. (If all keys are constants, ConstantEntriesLiteralNode is
 * used.) Example: `foo { ["on" + "e"] = 1 }`
 */
// IDEA: don't materialize frames if all members have constant values
@ImportStatic(BaseModule.class)
public abstract class EntriesLiteralNode extends SpecializedObjectLiteralNode {
  @Children private final ExpressionNode[] keyNodes;
  private final ObjectMember[] values;

  public EntriesLiteralNode(
      SourceSection sourceSection,
      VmLanguage language,
      // contains local properties and default property (if present)
      // does *not* contain entries with constant keys to maintain definition order of entries
      String qualifiedScopeName,
      boolean isCustomThisScope,
      @Nullable FrameDescriptor parametersDescriptor,
      UnresolvedTypeNode[] parameterTypes,
      UnmodifiableEconomicMap<Object, ObjectMember> members,
      ExpressionNode[] keyNodes,
      ObjectMember[] values) {

    super(
        sourceSection,
        language,
        qualifiedScopeName,
        isCustomThisScope,
        parametersDescriptor,
        parameterTypes,
        members);
    this.keyNodes = keyNodes;
    this.values = values;

    assert keyNodes.length > 0;
    assert keyNodes.length == values.length;
  }

  @Override
  public EntriesLiteralNode copy(ExpressionNode newParentNode) {
    //noinspection ConstantConditions
    return EntriesLiteralNodeGen.create(
        sourceSection,
        language,
        qualifiedScopeName,
        isCustomThisScope,
        null, // copied node no longer has parameters
        new UnresolvedTypeNode[0], // ditto
        members,
        keyNodes,
        values,
        newParentNode);
  }

  @Specialization(guards = "checkIsValidMappingAmendment()")
  protected VmMapping evalMapping(VirtualFrame frame, VmMapping parent) {
    return new VmMapping(frame.materialize(), parent, createMapMembers(frame));
  }

  @Specialization
  protected VmDynamic evalDynamic(VirtualFrame frame, VmDynamic parent) {
    return new VmDynamic(frame.materialize(), parent, createMapMembers(frame), parent.getLength());
  }

  @Specialization(guards = "checkIsValidListingAmendment()")
  protected VmListing evalListing(VirtualFrame frame, VmListing parent) {
    return new VmListing(
        frame.materialize(),
        parent,
        createListMembers(frame, parent.getLength()),
        parent.getLength() + keyNodes.length);
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

  @Specialization(guards = {"parent == getMappingClass()", "checkIsValidMappingAmendment()"})
  protected VmMapping evalMappingClass(
      VirtualFrame frame, @SuppressWarnings("unused") VmClass parent) {
    return new VmMapping(
        frame.materialize(), BaseModule.getMappingClass().getPrototype(), createMapMembers(frame));
  }

  @Specialization(guards = "parent == getDynamicClass()")
  protected VmDynamic evalDynamicClass(
      VirtualFrame frame, @SuppressWarnings("unused") VmClass parent) {
    return new VmDynamic(
        frame.materialize(),
        BaseModule.getDynamicClass().getPrototype(),
        createMapMembers(frame),
        0);
  }

  @Specialization(guards = {"parent == getListingClass()", "checkIsValidListingAmendment()"})
  protected void evalListingClass(@SuppressWarnings("unused") VmClass parent) {
    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder()
        .evalError("cannotAddElementWithEntrySyntax")
        .withSourceSection(keyNodes[0].getSourceSection())
        .build();
  }

  @Fallback
  @TruffleBoundary
  protected Object fallback(Object parent) {
    return elementsEntriesFallback(parent, values[0], false);
  }

  @ExplodeLoop
  protected EconomicMap<Object, ObjectMember> createMapMembers(VirtualFrame frame) {
    var result =
        EconomicMaps.<Object, ObjectMember>create(EconomicMaps.size(members) + keyNodes.length);
    EconomicMaps.putAll(result, members);

    for (var i = 0; i < keyNodes.length; i++) {
      var key = keyNodes[i].executeGeneric(frame);
      var value = values[i];
      var previousValue = EconomicMaps.put(result, key, value);
      if (previousValue != null) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder()
            .evalError("duplicateDefinition", new ProgramValue("", key))
            .withSourceSection(value.getHeaderSection())
            .build();
      }
    }

    return result;
  }

  protected UnmodifiableEconomicMap<Object, ObjectMember> createListMembers(
      VirtualFrame frame, int parentLength) {
    var result =
        EconomicMaps.<Object, ObjectMember>create(EconomicMaps.size(members) + keyNodes.length);
    EconomicMaps.putAll(result, members);
    addListEntries(frame, parentLength, result, keyNodes, values);
    return result;
  }
}
