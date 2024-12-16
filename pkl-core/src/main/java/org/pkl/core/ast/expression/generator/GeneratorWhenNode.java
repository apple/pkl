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
package org.pkl.core.ast.expression.generator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.BaseModule;

public final class GeneratorWhenNode extends GeneratorMemberNode {
  @Child private ExpressionNode conditionNode;
  @Children private final GeneratorMemberNode[] thenNodes;
  @Children private final GeneratorMemberNode[] elseNodes;

  public GeneratorWhenNode(
      SourceSection sourceSection,
      ExpressionNode conditionNode,
      GeneratorMemberNode[] thenNodes,
      GeneratorMemberNode[] elseNodes) {

    super(sourceSection, false);
    this.conditionNode = conditionNode;
    this.thenNodes = thenNodes;
    this.elseNodes = elseNodes;
  }

  @Override
  @ExplodeLoop
  public void execute(VirtualFrame frame, Object parent, ObjectData data) {
    boolean condition;
    try {
      condition = conditionNode.executeBoolean(frame);
    } catch (UnexpectedResultException e) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .typeMismatch(e.getResult(), BaseModule.getBooleanClass())
          .withSourceSection(conditionNode.getSourceSection())
          .build();
    }
    for (var node : condition ? thenNodes : elseNodes) {
      node.execute(frame, parent, data);
    }
  }
}
