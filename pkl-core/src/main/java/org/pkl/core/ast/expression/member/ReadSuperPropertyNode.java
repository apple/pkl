/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.ast.expression.member;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.DefaultPropertyBodyNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.*;

public final class ReadSuperPropertyNode extends ExpressionNode {

  private final Identifier propertyName;
  private final boolean needsConst;

  private final boolean isPropertyAmendment;

  @Child private IndirectCallNode callNode = IndirectCallNode.create();

  public ReadSuperPropertyNode(
      SourceSection sourceSection, Identifier propertyName, boolean needsConst) {
    this(sourceSection, propertyName, needsConst, false);
  }

  public ReadSuperPropertyNode(
      SourceSection sourceSection,
      Identifier propertyName,
      boolean needsConst,
      boolean isPropertyAmendment) {
    super(sourceSection);
    this.propertyName = propertyName;
    this.needsConst = needsConst;
    this.isPropertyAmendment = isPropertyAmendment;
  }

  // TODO: how can this be optimized?
  // (result not cached and expensive lookups on every execution)
  public Object executeGeneric(VirtualFrame frame) {
    var receiver = VmUtils.getObjectReceiver(frame);

    // start from the parent of the owner of the `super.<propertyName>` expression
    // skip any function object owners (same as when resolving `this`)
    // `receiver` must be passed on unchanged to make sure that overridden properties still take
    // effect
    var initialOwner = VmUtils.getOwner(frame);
    while (initialOwner instanceof VmFunction) {
      initialOwner = initialOwner.getEnclosingOwner();
    }
    assert initialOwner != null : "VmFunction always has a parent";
    initialOwner = initialOwner.getParent();

    for (var owner = initialOwner; owner != null; owner = owner.getParent()) {
      var property = owner.getMember(propertyName);
      if (property == null) continue;
      if (needsConst && !property.isConst()) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder().evalError("propertyMustBeConst", propertyName.toString()).build();
      }

      var constantValue = property.getConstantValue();
      if (constantValue != null) return constantValue; // TODO: type check

      // caching the result of a super call is tricky (function of both receiver and owner)
      try {
        return callNode.call(
            property.getCallTarget(),
            // TODO: should the marker only turn off constraint checking, not overall type checking?
            receiver,
            owner,
            propertyName,
            VmUtils.SKIP_TYPECHECK_MARKER);
      } catch (VmUndefinedValueException e) {
        if (!isPropertyAmendment) throw e;
        CompilerDirectives.transferToInterpreter();
        throw convertUndefinedAmendment(property, e);
      }
    }

    // TODO: refine when to return VmDynamic.empty() and when to fail
    return VmDynamic.empty();
  }

  @TruffleBoundary
  private VmException convertUndefinedAmendment(
      ObjectMember property, VmUndefinedValueException original) {
    var memberNode = property.getMemberNode();
    if (memberNode == null
        || !(memberNode.getBodyNode() instanceof DefaultPropertyBodyNode defaultBody)) {
      return original;
    }
    var typeNode = defaultBody.getTypeNode();
    if (typeNode == null) return original;
    return exceptionBuilder()
        .evalError(
            "cannotInstantiateTypeWithoutDefault", typeNode.getSourceSection().getCharacters())
        .withSourceSection(typeNode.getSourceSection())
        .build();
  }
}
