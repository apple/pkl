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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.runtime.VmUtils;

public abstract sealed class AbstractInvokeMethodLexicalNode extends ExpressionNode
    permits InvokeObjectMethodNode, InvokeClassMethodNode {

  protected final Identifier methodName;
  protected final int levelsUp;
  private final boolean needsConst;
  @Children private ExpressionNode[] argumentNodes;
  @Child private DirectCallNode callNode;
  @CompilationFinal protected boolean isConstChecked;

  protected AbstractInvokeMethodLexicalNode(
      SourceSection sourceSection,
      Identifier methodName,
      int levelsUp,
      ExpressionNode[] argumentNodes,
      boolean needsConst) {
    super(sourceSection);
    this.methodName = methodName;
    this.levelsUp = levelsUp;
    this.argumentNodes = argumentNodes;
    this.needsConst = needsConst;
    this.isConstChecked = false;
  }

  @Override
  @ExplodeLoop
  public final Object executeGeneric(VirtualFrame frame) {
    var args = new Object[2 + argumentNodes.length];
    var capturedFrame = VmUtils.getFrame(frame, levelsUp);
    var owner = VmUtils.getOwner(capturedFrame);
    var receiver = VmUtils.getReceiver(capturedFrame);
    checkConst(owner);
    args[0] = receiver;
    args[1] = owner;
    for (var i = 0; i < argumentNodes.length; i++) {
      args[2 + i] = argumentNodes[i].executeGeneric(frame);
    }
    return getCallNode(owner).call(args);
  }

  private void checkConst(VmObjectLike owner) {
    if (!needsConst || isConstChecked) {
      return;
    }
    CompilerDirectives.transferToInterpreterAndInvalidate();
    doCheckConst(owner);
    isConstChecked = true;
  }

  protected abstract CallTarget getCallTarget(VmObjectLike owner);

  protected abstract void doCheckConst(VmObjectLike owner);

  protected final VmObjectLike getOwner(VirtualFrame frame) {
    return VmUtils.getOwner(frame, levelsUp);
  }

  protected DirectCallNode getCallNode(VmObjectLike owner) {
    if (callNode == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      callNode = DirectCallNode.create(getCallTarget(owner));
      insert(callNode);
    }
    return callNode;
  }
}
