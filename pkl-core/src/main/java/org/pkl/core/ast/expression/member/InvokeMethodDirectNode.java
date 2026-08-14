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
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ClassMethod;
import org.pkl.core.runtime.VmObjectLike;

/** A non-virtual ("direct") method call. Used only for methods on {@code pkl:base}. */
public final class InvokeMethodDirectNode extends AbstractInvokeMethodNode {
  private final ClassMethod method;
  private final VmObjectLike owner;
  @Child private ExpressionNode receiverNode;

  @Child private DirectCallNode callNode;

  public InvokeMethodDirectNode(
      SourceSection sourceSection,
      ClassMethod method,
      ExpressionNode receiverNode,
      ExpressionNode[] argumentNodes) {

    super(sourceSection, argumentNodes);
    this.method = method;
    this.owner = method.getOwner();
    this.receiverNode = receiverNode;

    callNode = DirectCallNode.create(method.getCallTarget(sourceSection));
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    var args = evalArgs(frame, method, owner, receiverNode.executeGeneric(frame));
    return callNode.call(args);
  }
}
