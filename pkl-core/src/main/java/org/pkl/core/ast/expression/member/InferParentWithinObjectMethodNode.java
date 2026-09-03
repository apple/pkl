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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.Method;
import org.pkl.core.ast.member.ObjectMethodNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmObjectLike;

/** Infers the parent to amend in `obj { local function createPerson(): Person = new { ... } }`. */
public abstract class InferParentWithinObjectMethodNode extends AbstractInferParentFromMethodNode {
  private final Identifier localMethodName;
  @Child private ExpressionNode ownerNode;

  protected InferParentWithinObjectMethodNode(
      SourceSection sourceSection,
      VmLanguage language,
      Identifier localMethodName,
      ExpressionNode ownerNode) {

    super(sourceSection, language);
    this.localMethodName = localMethodName;
    this.ownerNode = ownerNode;

    assert localMethodName.isLocalMethod();
  }

  @Override
  protected Method getMethod(VirtualFrame frame) {
    var owner = (VmObjectLike) ownerNode.executeGeneric(frame);

    var member = owner.getMember(localMethodName);
    assert member != null;

    var methodNode = (ObjectMethodNode) member.getMemberNode();
    assert methodNode != null;
    return methodNode.reify(owner);
  }

  @Override
  protected @Nullable TypeNode getTypeNode(VirtualFrame frame, Method method) {
    return method.getFunctionNode().getReturnTypeNode();
  }

  // keep specializations in sync with other AbstractInferParentFromMethodNode subclasses

  @Specialization(
      guards = {"getMethod(frame) == cachedMethod", "isFinalType(cachedMethod, typeNode)"})
  protected final Object evalCached(
      @SuppressWarnings("unused") VirtualFrame frame,
      @Cached("getMethod(frame)") @SuppressWarnings("unused") Method cachedMethod,
      @Cached("getTypeNode(frame, cachedMethod)") @SuppressWarnings("unused") TypeNode typeNode,
      @Cached(
              "getDefaultValue(frame, typeNode, cachedMethod.getHeaderSection(), cachedMethod.getQualifiedName())")
          Object defaultValue) {
    return defaultValue;
  }

  @Specialization(replaces = "evalCached")
  protected final Object eval(VirtualFrame frame) {
    var method = getMethod(frame);
    var typeNode = getTypeNode(frame, method);
    return getDefaultValue(frame, typeNode, method.getHeaderSection(), method.getQualifiedName());
  }
}
