/*
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
package org.pkl.core.ast.expression.binary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.FunctionNode;
import org.pkl.core.ast.member.UnresolvedFunctionNode;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.LateInit;

public final class LetExprNode extends ExpressionNode {
  private @Child UnresolvedFunctionNode unresolvedFunctionNode;
  private @Child ExpressionNode valueNode;
  private final boolean isCustomThisScope;

  @CompilationFinal @LateInit private FunctionNode functionNode;
  @Child @LateInit private DirectCallNode callNode;
  @CompilationFinal private int customThisSlot = -1;

  public LetExprNode(
      SourceSection sourceSection,
      UnresolvedFunctionNode functionNode,
      ExpressionNode valueNode,
      boolean isCustomThisScope) {

    super(sourceSection);
    this.unresolvedFunctionNode = functionNode;
    this.valueNode = valueNode;
    this.isCustomThisScope = isCustomThisScope;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    if (functionNode == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      functionNode = unresolvedFunctionNode.execute(frame);
      callNode = insert(DirectCallNode.create(functionNode.getCallTarget()));
      if (isCustomThisScope) {
        // deferred until execution time s.t. nodes of inlined type aliases get the right frame slot
        customThisSlot = VmUtils.findCustomThisSlot(frame);
      }
    }

    var function =
        new VmFunction(
            frame.materialize(),
            isCustomThisScope ? frame.getAuxiliarySlot(customThisSlot) : VmUtils.getReceiver(frame),
            1,
            functionNode,
            null);

    var value = valueNode.executeGeneric(frame);

    return callNode.call(function.getThisValue(), function, value);
  }
}
