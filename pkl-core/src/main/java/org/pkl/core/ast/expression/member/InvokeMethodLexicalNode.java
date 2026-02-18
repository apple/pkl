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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.VmUtils;

/**
 * A non-virtual method call whose call target is in the lexical scope of the call site. Mainly used
 * for calling `local` methods.
 */
public final class InvokeMethodLexicalNode extends ExpressionNode {
  @Children private final ExpressionNode[] argumentNodes;
  private final int levelsUp;

  @Child private DirectCallNode callNode;

  public InvokeMethodLexicalNode(
      SourceSection sourceSection,
      CallTarget callTarget,
      int levelsUp,
      ExpressionNode[] argumentNodes) {

    super(sourceSection);
    this.levelsUp = levelsUp;
    this.argumentNodes = argumentNodes;

    callNode = DirectCallNode.create(callTarget);
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    var args = new Object[2 + argumentNodes.length];
    var enclosingFrame = getEnclosingFrame(frame);
    args[0] = VmUtils.getReceiver(enclosingFrame);
    args[1] = VmUtils.getOwner(enclosingFrame);
    for (var i = 0; i < argumentNodes.length; i++) {
      args[2 + i] = argumentNodes[i].executeGeneric(frame);
    }

    return callNode.call(args);
  }

  @ExplodeLoop
  private Frame getEnclosingFrame(VirtualFrame frame) {
    if (levelsUp == 0) return frame;

    var owner = VmUtils.getOwner(frame);
    for (var i = 1; i < levelsUp; i++) {
      owner = owner.getEnclosingOwner();
      assert owner != null;
    }
    return owner.getEnclosingFrame();
  }
}
