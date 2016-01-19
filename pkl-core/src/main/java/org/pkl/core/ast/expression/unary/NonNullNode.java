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
package org.pkl.core.ast.expression.unary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.VmNull;

@NodeInfo(shortName = "!!")
// Truffle DSL/codegen is overkill for this node, hence don't extend UnaryExpressionNode
public final class NonNullNode extends ExpressionNode {
  private @Child ExpressionNode operandNode;

  public NonNullNode(SourceSection sourceSection, ExpressionNode operandNode) {
    super(sourceSection);
    this.operandNode = operandNode;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    var operand = operandNode.executeGeneric(frame);
    if (!(operand instanceof VmNull)) return operand;

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder()
        .evalError("expectedNonNullValue")
        .withSourceSection(operandNode.getSourceSection())
        .build();
  }
}
