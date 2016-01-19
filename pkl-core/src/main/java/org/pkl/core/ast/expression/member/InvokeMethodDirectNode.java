/**
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
package org.pkl.core.ast.expression.member;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ClassMethod;
import org.pkl.core.runtime.VmObjectLike;

/** A non-virtual ("direct") method call. */
public final class InvokeMethodDirectNode extends ExpressionNode {
  private final VmObjectLike owner;
  @Child private ExpressionNode receiverNode;
  @Children private final ExpressionNode[] argumentNodes;

  @Child private DirectCallNode callNode;

  public InvokeMethodDirectNode(
      SourceSection sourceSection,
      ClassMethod method,
      ExpressionNode receiverNode,
      ExpressionNode[] argumentNodes) {

    super(sourceSection);
    this.owner = method.getOwner();
    this.receiverNode = receiverNode;
    this.argumentNodes = argumentNodes;

    callNode = DirectCallNode.create(method.getCallTarget(sourceSection));
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    var args = new Object[2 + argumentNodes.length];
    args[0] = receiverNode.executeGeneric(frame);
    args[1] = owner;
    for (var i = 0; i < argumentNodes.length; i++) {
      args[2 + i] = argumentNodes[i].executeGeneric(frame);
    }

    return callNode.call(args);
  }
}
