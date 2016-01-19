/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Nullable;

/**
 * Object literal that contains entries (and possibly properties) but not elements. Additionally,
 * all entry keys are constants. Example: `new foo { ["one"] = 1 }`
 */
// IDEA: don't materialize frames if all members have constant values
@ImportStatic(BaseModule.class)
public abstract class ConstantEntriesLiteralNode extends SpecializedObjectLiteralNode {
  public ConstantEntriesLiteralNode(
      SourceSection sourceSection,
      VmLanguage language,
      String qualifiedScopeName,
      boolean isCustomThisScope,
      @Nullable FrameDescriptor parametersDescriptor,
      UnresolvedTypeNode[] parameterTypes,
      UnmodifiableEconomicMap<Object, ObjectMember> members) {

    super(
        sourceSection,
        language,
        qualifiedScopeName,
        isCustomThisScope,
        parametersDescriptor,
        parameterTypes,
        members);
  }

  @Override
  public ConstantEntriesLiteralNode copy(ExpressionNode newParentNode) {
    //noinspection ConstantConditions
    return ConstantEntriesLiteralNodeGen.create(
        sourceSection,
        language,
        qualifiedScopeName,
        isCustomThisScope,
        null, // copied node no longer has parameters
        new UnresolvedTypeNode[0], // ditto
        members,
        newParentNode);
  }

  @Specialization(guards = "checkIsValidMappingAmendment()")
  protected VmMapping evalMapping(VirtualFrame frame, VmMapping parent) {
    return new VmMapping(frame.materialize(), parent, members);
  }

  @Specialization
  protected VmDynamic evalDynamic(VirtualFrame frame, VmDynamic parent) {
    return new VmDynamic(frame.materialize(), parent, members, parent.getLength());
  }

  @Specialization(guards = "checkIsValidListingAmendment()")
  protected VmListing evalListing(VirtualFrame frame, VmListing parent) {
    checkMaxListingMemberIndex(parent.getLength());
    return new VmListing(frame.materialize(), parent, members, parent.getLength());
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
      @Cached("createAmendFunctionNode(frame)") AmendFunctionNode amendFunctionNode) {

    return amendFunctionNode.execute(frame, parent);
  }

  @Specialization(guards = {"parent == getMappingClass()", "checkIsValidMappingAmendment()"})
  protected VmMapping evalMappingClass(
      VirtualFrame frame, @SuppressWarnings("unused") VmClass parent) {
    return new VmMapping(frame.materialize(), BaseModule.getMappingClass().getPrototype(), members);
  }

  @Specialization(guards = "parent == getDynamicClass()")
  protected VmDynamic evalDynamicClass(
      VirtualFrame frame, @SuppressWarnings("unused") VmClass parent) {
    return new VmDynamic(
        frame.materialize(), BaseModule.getDynamicClass().getPrototype(), members, 0);
  }

  @Specialization(
      guards = {
        "parent == getListingClass()",
        "checkIsValidListingAmendment()",
        "checkMaxListingMemberIndex(0)"
      })
  protected void evalListingClass(@SuppressWarnings("unused") VmClass parent) {
    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder().unreachableCode().build();
  }

  @Fallback
  @TruffleBoundary
  protected void fallback(Object parent) {
    elementsEntriesFallback(parent, findFirstNonProperty(members), false);
  }
}
