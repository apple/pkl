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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ObjectMethodNode;
import org.pkl.core.ast.type.TypeNode.UnknownTypeNode;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmDynamic;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.util.LateInit;

/** Infers the parent to amend in `obj { local function createPerson(): Person = new { ... } }`. */
public final class InferParentWithinObjectMethodNode extends ExpressionNode {
  private final VmLanguage language;
  private final Identifier localMethodName;
  @Child private ExpressionNode ownerNode;
  @CompilationFinal @LateInit private Object inferredParent;

  public InferParentWithinObjectMethodNode(
      SourceSection sourceSection,
      VmLanguage language,
      Identifier localMethodName,
      ExpressionNode ownerNode) {

    super(sourceSection);
    this.language = language;
    this.localMethodName = localMethodName;
    this.ownerNode = ownerNode;

    assert localMethodName.isLocalMethod();
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    if (inferredParent != null) return inferredParent;

    // remaining code only runs first time this node is executed
    // (assuming evaluation isn't continued despite errors)

    CompilerDirectives.transferToInterpreter();

    var owner = (VmObjectLike) ownerNode.executeGeneric(frame);

    var member = owner.getMember(localMethodName);
    assert member != null;

    var methodNode = (ObjectMethodNode) member.getMemberNode();
    assert methodNode != null;

    var returnTypeNode = methodNode.getReturnTypeNode();
    if (returnTypeNode == null || returnTypeNode instanceof UnknownTypeNode) {
      inferredParent = VmDynamic.empty();
      ownerNode = null;
      return inferredParent;
    }

    Object defaultReturnTypeValue =
        returnTypeNode.createDefaultValue(
            frame, language, member.getHeaderSection(), member.getQualifiedName());
    if (defaultReturnTypeValue != null) {
      inferredParent = defaultReturnTypeValue;
      ownerNode = null;
      return inferredParent;
    }

    throw exceptionBuilder().evalError("cannotInferParent").build();
  }
}
