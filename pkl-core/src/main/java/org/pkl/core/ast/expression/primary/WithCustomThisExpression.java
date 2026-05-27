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
package org.pkl.core.ast.expression.primary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.ArrayUtils;

public final class WithCustomThisExpression extends ExpressionNode {

  private @Child ExpressionNode expressionNode;
  private final int[] slotsToCopy;
  @CompilationFinal private int customThisSlot = -1;

  public WithCustomThisExpression(
      ExpressionNode expressionNode, int[] forGeneratorSlots, int[] parameterSlots) {
    super(expressionNode.getSourceSection());
    this.expressionNode = expressionNode;
    this.slotsToCopy = ArrayUtils.concat(parameterSlots, forGeneratorSlots);
  }

  public int getCustomThisSlot(VirtualFrame frame) {
    if (customThisSlot == -1) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      customThisSlot = VmUtils.findCustomThisSlot(frame);
    }
    return customThisSlot;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    var customThisSlot = getCustomThisSlot(frame);
    // arguments passed in by `ExecuteCustomThisWithRootNode`
    frame.setAuxiliarySlot(customThisSlot, frame.getArguments()[2]);
    var originalFrame = (VirtualFrame) frame.getArguments()[3];
    VmUtils.copyLocals(originalFrame, frame, slotsToCopy);
    return expressionNode.executeGeneric(frame);
  }
}
