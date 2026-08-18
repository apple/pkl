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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ObjectMethodNode;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmObjectLike;

/** Infers the parent to amend in `obj { local function createPerson(): Person = new { ... } }`. */
public final class InferParentWithinObjectMethodNode extends AbstractInferParentNode {
  private final Identifier localMethodName;
  @Child private @Nullable ExpressionNode ownerNode;

  public InferParentWithinObjectMethodNode(
      SourceSection sourceSection,
      VmLanguage language,
      Identifier localMethodName,
      ExpressionNode ownerNode) {

    super(sourceSection, language, true);
    this.localMethodName = localMethodName;
    this.ownerNode = ownerNode;

    assert localMethodName.isLocalMethod();
  }

  @Override
  protected TypeInfo getTypeInfo(VirtualFrame frame) {
    assert ownerNode != null;
    var owner = (VmObjectLike) ownerNode.executeGeneric(frame);

    var member = owner.getMember(localMethodName);
    assert member != null;

    var methodNode = (ObjectMethodNode) member.getMemberNode();
    assert methodNode != null;

    return new TypeInfo(
        methodNode.getReturnTypeNode(),
        methodNode.getHeaderSection(),
        methodNode.getQualifiedName());
  }

  @Override
  protected void onInfer() {
    ownerNode = null;
  }
}
