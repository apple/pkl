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
package org.pkl.core.ast.expression.ternary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.BaseModule;

@NodeInfo(shortName = "if")
public final class IfElseNode extends ExpressionNode {
  @Child private ExpressionNode conditionNode;

  @Child private ExpressionNode thenNode;

  @Child private ExpressionNode elseNode;

  public IfElseNode(
      SourceSection sourceSection,
      ExpressionNode conditionNode,
      ExpressionNode thenNode,
      ExpressionNode elseNode) {
    super(sourceSection);
    this.conditionNode = conditionNode;
    this.thenNode = thenNode;
    this.elseNode = elseNode;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return evaluateCondition(frame)
        ? thenNode.executeGeneric(frame)
        : elseNode.executeGeneric(frame);
  }

  private boolean evaluateCondition(VirtualFrame frame) {
    try {
      return conditionNode.executeBoolean(frame);
    } catch (UnexpectedResultException e) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .typeMismatch(e.getResult(), BaseModule.getBooleanClass())
          .withSourceSection(conditionNode.getSourceSection())
          .build();
    }
  }
}
