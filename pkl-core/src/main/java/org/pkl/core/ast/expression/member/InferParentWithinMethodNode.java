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
package org.pkl.core.ast.expression.member;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.type.TypeNode.UnknownTypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.LateInit;

/** Infers the parent to amend in `function createPerson(): Person = new { ... }`. */
public final class InferParentWithinMethodNode extends ExpressionNode {
  private final VmLanguage language;
  private final Identifier methodName;
  @Child private ExpressionNode ownerNode;
  @CompilationFinal @LateInit private Object inferredParent;

  public InferParentWithinMethodNode(
      SourceSection sourceSection,
      VmLanguage language,
      Identifier methodName,
      ExpressionNode ownerNode) {

    super(sourceSection);
    this.language = language;
    this.methodName = methodName;
    this.ownerNode = ownerNode;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    if (inferredParent != null) return inferredParent;

    // remaining code only runs first time this node is executed
    // (assuming evaluation isn't continued despite errors)

    CompilerDirectives.transferToInterpreter();

    var owner = (VmObjectLike) ownerNode.executeGeneric(frame);
    assert owner.isPrototype();

    var method = owner.getVmClass().getDeclaredMethod(methodName);
    assert method != null;

    var returnTypeNode = method.getReturnTypeNode();
    if (returnTypeNode == null || returnTypeNode instanceof UnknownTypeNode) {
      inferredParent = VmDynamic.empty();
      ownerNode = null;
      return inferredParent;
    }

    var returnTypeDefaultValue =
        returnTypeNode.createDefaultValue(
            language, method.getHeaderSection(), method.getQualifiedName());
    if (returnTypeDefaultValue != null) {
      inferredParent = returnTypeDefaultValue;
      ownerNode = null;
      return inferredParent;
    }

    throw exceptionBuilder().evalError("cannotInferParent").build();
  }
}
