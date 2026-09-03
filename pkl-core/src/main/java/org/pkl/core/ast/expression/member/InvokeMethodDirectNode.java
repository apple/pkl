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

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ClassMethod;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.runtime.VmUtils;

/** A non-virtual ("direct") method call. Used only for methods on {@code pkl:base}. */
@ImportStatic(VmUtils.class)
public abstract class InvokeMethodDirectNode extends AbstractInvokeMethodNode {
  protected final ClassMethod method;
  private final VmObjectLike owner;
  @Child protected ExpressionNode receiverNode;

  @Child private @Nullable DirectCallNode callNode;

  protected InvokeMethodDirectNode(
      SourceSection sourceSection,
      ClassMethod method,
      ExpressionNode receiverNode,
      UnresolvedTypeNode @Nullable [] unresolvedTypeArgumentNodes,
      ExpressionNode[] argumentNodes,
      boolean argsRequireInference) {
    super(sourceSection, unresolvedTypeArgumentNodes, argumentNodes, argsRequireInference);
    this.method = method;
    this.owner = method.getOwner();
    this.receiverNode = receiverNode;

    if (unresolvedTypeArgumentNodes == null) {
      callNode = DirectCallNode.create(method.getCallTarget(sourceSection));
    }
  }

  @Specialization(guards = "unresolvedTypeArgumentNodes == null")
  protected Object evalNoArgs(VirtualFrame frame) {
    assert callNode != null;
    var args = evalArgs(frame, method, owner, receiverNode.executeGeneric(frame));
    return callNode.call(args);
  }

  @Specialization(
      guards = {
        "unresolvedTypeArgumentNodes != null",
        "getTypeArgumentsAreFinal(frame)",
        "getClass(receiver) == cachedReceiverClass"
      })
  protected Object evalArgsCached(
      VirtualFrame frame,
      @Bind("receiverNode.executeGeneric(frame)") Object receiver,
      @Cached("getClass(receiver)") @SuppressWarnings("unused") VmClass cachedReceiverClass,
      @Cached(
              "create(instantiateFunction(frame, method, method.getFunctionNode(sourceSection)).getCallTarget())")
          DirectCallNode callNode) {
    var args = evalArgs(frame, method, owner, receiver);
    return callNode.call(args);
  }

  @Specialization(
      guards = {"unresolvedTypeArgumentNodes != null"},
      replaces = "evalArgsCached")
  protected Object evalArgs(VirtualFrame frame) {
    var args = evalArgs(frame, method, owner, receiverNode.executeGeneric(frame));
    var functionNode = instantiateFunction(frame, method, method.getFunctionNode(sourceSection));
    return DirectCallNode.create(functionNode.getCallTarget()).call(args);
  }
}
