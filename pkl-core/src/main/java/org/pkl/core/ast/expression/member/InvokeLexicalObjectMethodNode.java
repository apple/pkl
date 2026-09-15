/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.member.Method;
import org.pkl.core.ast.member.ObjectMethodNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.runtime.VmUtils;

/** A non-virtual call of an object method that is lexically scoped. */
@ImportStatic(VmUtils.class)
public abstract class InvokeLexicalObjectMethodNode extends AbstractInvokeLexicalMethodNode {
  protected InvokeLexicalObjectMethodNode(
      SourceSection sourceSection,
      Identifier methodName,
      int levelsUp,
      UnresolvedTypeNode @Nullable [] unresolvedTypeArgumentNodes,
      ExpressionNode[] argumentNodes,
      boolean needsConst,
      boolean argsRequireInference) {
    super(
        sourceSection,
        methodName,
        levelsUp,
        unresolvedTypeArgumentNodes,
        argumentNodes,
        needsConst,
        argsRequireInference);
  }

  @Override
  protected void doCheckConst(Object ownerObj) {
    var owner = (VmObjectLike) ownerObj;
    var member = owner.getMember(methodName);
    assert member != null;
    if (!VmModifier.isConst(member.getModifiers())) {
      throw exceptionBuilder().evalError("methodMustBeConst", methodName).build();
    }
  }

  @Override
  protected Method getMethod(Object ownerObj) {
    var owner = (VmObjectLike) ownerObj;
    var member = owner.getMember(methodName);
    assert member != null && member.isLocal();
    var methodNode = (ObjectMethodNode) member.getMemberNode();
    assert methodNode != null;
    return methodNode.reify(owner);
  }

  // keep specializations in sync with other AbstractInvokeLexicalOrQualifiedMethodNode subclasses

  @Specialization(guards = "unresolvedTypeArgumentNodes == null")
  public final Object evalNoArgs(
      VirtualFrame frame,
      @Bind("getEffectiveFrame(frame)") @SuppressWarnings("unused") VirtualFrame effectiveFrame,
      @Bind("getObjectReceiver(effectiveFrame)") VmObjectLike receiver,
      @Bind("getOwner(effectiveFrame)") VmObjectLike owner,
      @Cached(value = "getMethod(owner)", neverDefault = true) Method method,
      @Cached("create(method.getFunctionNode(sourceSection).getCallTarget())")
          DirectCallNode callNode) {
    return invoke(frame, owner, receiver, method, callNode);
  }

  @Specialization(
      guards = {"unresolvedTypeArgumentNodes != null", "getTypeArgumentsAreFinal(frame)"})
  public final Object evalArgsCached(
      VirtualFrame frame,
      @Bind("getEffectiveFrame(frame)") @SuppressWarnings("unused") VirtualFrame effectiveFrame,
      @Bind("getObjectReceiver(effectiveFrame)") VmObjectLike receiver,
      @Bind("getOwner(effectiveFrame)") VmObjectLike owner,
      @Cached(value = "getMethod(owner)", neverDefault = true) Method method,
      @Cached(
              "create(instantiateFunction(frame, method, method.getFunctionNode()).getCallTarget())")
          DirectCallNode callNode) {
    return invoke(frame, owner, receiver, method, callNode);
  }

  @Specialization(guards = "unresolvedTypeArgumentNodes != null", replaces = "evalArgsCached")
  public final Object evalArgs(
      VirtualFrame frame,
      @Bind("getEffectiveFrame(frame)") @SuppressWarnings("unused") VirtualFrame effectiveFrame,
      @Bind("getObjectReceiver(effectiveFrame)") VmObjectLike receiver,
      @Bind("getOwner(effectiveFrame)") VmObjectLike owner,
      @Cached(value = "getMethod(owner)", neverDefault = true) Method method) {
    var functionNode = instantiateFunction(frame, method, method.getFunctionNode());
    var callNode = DirectCallNode.create(functionNode.getCallTarget());
    return invoke(frame, owner, receiver, method, callNode);
  }
}
