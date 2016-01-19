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
package org.pkl.core.ast.expression.binary;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.VmException;
import org.pkl.core.runtime.VmUtils;

/**
 * A binary expression whose right operand may be short-circuited. Does not inherit from
 * BinaryExpressionNode for technical reasons.
 */
@NodeChild(value = "leftNode", type = ExpressionNode.class)
public abstract class ShortCircuitingExpressionNode extends ExpressionNode {
  @Child protected ExpressionNode rightNode;

  protected abstract ExpressionNode getLeftNode();

  protected ShortCircuitingExpressionNode(SourceSection sourceSection, ExpressionNode rightNode) {
    super(sourceSection);
    this.rightNode = rightNode;
  }

  @Fallback
  @TruffleBoundary
  protected Object fallback(Object left) {
    throw operatorNotDefined(left);
  }

  @TruffleBoundary
  protected VmException operatorNotDefined(Object left) {
    return exceptionBuilder()
        .evalError("operatorNotDefinedLeft", getShortName(), VmUtils.getClass(left))
        .withProgramValue("Left operand", left)
        .build();
  }

  @TruffleBoundary
  protected VmException operatorNotDefined(Object left, Object right) {
    return exceptionBuilder()
        .evalError(
            "operatorNotDefined2", getShortName(), VmUtils.getClass(left), VmUtils.getClass(right))
        .withProgramValue("Left operand", left)
        .withProgramValue("Right operand", right)
        .build();
  }
}
