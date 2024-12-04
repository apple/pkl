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
package org.pkl.core.ast.expression.generator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.expression.literal.AmendFunctionNode;
import org.pkl.core.ast.expression.literal.ObjectLiteralNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Nullable;

/** An object literal node that contains at least one for- or when-expression. */
@ImportStatic(BaseModule.class)
public abstract class GeneratorObjectLiteralNode extends ObjectLiteralNode {
  @Children private final GeneratorMemberNode[] memberNodes;

  public GeneratorObjectLiteralNode(
      SourceSection sourceSection,
      VmLanguage language,
      String qualifiedScopeName,
      boolean isCustomThisScope,
      @Nullable FrameDescriptor parametersDescriptor,
      UnresolvedTypeNode[] parameterTypes,
      GeneratorMemberNode[] memberNodes) {

    super(
        sourceSection,
        language,
        qualifiedScopeName,
        isCustomThisScope,
        parametersDescriptor,
        parameterTypes);
    this.memberNodes = memberNodes;
  }

  protected GeneratorObjectLiteralNode copy(ExpressionNode newParentNode) {
    //noinspection ConstantConditions
    return GeneratorObjectLiteralNodeGen.create(
        sourceSection,
        language,
        qualifiedScopeName,
        isCustomThisScope,
        null, // copied node no longer has parameters
        new UnresolvedTypeNode[0], // ditto
        memberNodes,
        newParentNode);
  }

  @Specialization(guards = "checkObjectCannotHaveParameters()")
  protected VmDynamic evalDynamic(VirtualFrame frame, VmDynamic parent) {
    var data = createData(frame, parent, parent.getLength());
    var result = new VmDynamic(frame.materialize(), parent, data.members(), data.length());
    return data.storedIn(result);
  }

  @Specialization(guards = "checkObjectCannotHaveParameters()")
  protected VmTyped evalTyped(VirtualFrame frame, VmTyped parent) {
    VmUtils.checkIsInstantiable(parent.getVmClass(), getParentNode());
    var data = createData(frame, parent, 0);
    assert data.hasNoGeneratorFrames();
    return new VmTyped(frame.materialize(), parent, parent.getVmClass(), data.members());
  }

  @Specialization(guards = "checkListingCannotHaveParameters()")
  protected VmListing evalListing(VirtualFrame frame, VmListing parent) {
    var data = createData(frame, parent, parent.getLength());
    var result = new VmListing(frame.materialize(), parent, data.members(), data.length());
    return data.storedIn(result);
  }

  @Specialization(guards = "checkMappingCannotHaveParameters()")
  protected VmMapping evalMapping(VirtualFrame frame, VmMapping parent) {
    var data = createData(frame, parent, 0);
    var result = new VmMapping(frame.materialize(), parent, data.members());
    return data.storedIn(result);
  }

  @Specialization(guards = "checkObjectCannotHaveParameters()")
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

  @Specialization(guards = {"parent == getDynamicClass()", "checkObjectCannotHaveParameters()"})
  protected VmDynamic evalDynamicClass(VirtualFrame frame, VmClass parent) {
    var data = createData(frame, parent, 0);
    var result =
        new VmDynamic(frame.materialize(), parent.getPrototype(), data.members(), data.length());
    return data.storedIn(result);
  }

  @Specialization(guards = {"parent == getMappingClass()", "checkMappingCannotHaveParameters()"})
  protected VmMapping evalMappingClass(VirtualFrame frame, VmClass parent) {
    var data = createData(frame, parent, 0);
    var result = new VmMapping(frame.materialize(), parent.getPrototype(), data.members());
    return data.storedIn(result);
  }

  @Specialization(guards = {"parent == getListingClass()", "checkListingCannotHaveParameters()"})
  protected VmListing evalListingClass(VirtualFrame frame, VmClass parent) {
    var data = createData(frame, parent, 0);
    var result =
        new VmListing(frame.materialize(), parent.getPrototype(), data.members(), data.length());
    return data.storedIn(result);
  }

  @Specialization(guards = {"isTypedObjectClass(parent)", "checkObjectCannotHaveParameters()"})
  protected VmTyped evalTypedObjectClass(VirtualFrame frame, VmClass parent) {
    VmUtils.checkIsInstantiable(parent, getParentNode());
    var data = createData(frame, parent, 0);
    assert data.hasNoGeneratorFrames();
    return new VmTyped(frame.materialize(), parent.getPrototype(), parent, data.members());
  }

  @Fallback
  @TruffleBoundary
  protected void fallback(Object parent) {
    VmUtils.checkIsInstantiable(
        parent instanceof VmClass vmClass ? vmClass : VmUtils.getClass(parent), getParentNode());

    throw exceptionBuilder().unreachableCode().build();
  }

  @Idempotent
  protected boolean checkObjectCannotHaveParameters() {
    if (parametersDescriptor == null) return true;

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder()
        .evalError("objectAmendmentCannotHaveParameters")
        .withLocation(parameterTypes[0])
        .build();
  }

  @Idempotent
  protected boolean checkListingCannotHaveParameters() {
    if (parametersDescriptor == null) return true;

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder()
        .evalError("listingAmendmentCannotHaveParameters")
        .withLocation(parameterTypes[0])
        .build();
  }

  @Idempotent
  protected boolean checkMappingCannotHaveParameters() {
    if (parametersDescriptor == null) return true;

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder()
        .evalError("mappingAmendmentCannotHaveParameters")
        .withLocation(parameterTypes[0])
        .build();
  }

  @ExplodeLoop
  private ObjectData createData(VirtualFrame frame, Object parent, int parentLength) {
    var data = new ObjectData(parentLength);
    for (var memberNode : memberNodes) {
      memberNode.execute(frame, parent, data);
    }
    return data;
  }
}
