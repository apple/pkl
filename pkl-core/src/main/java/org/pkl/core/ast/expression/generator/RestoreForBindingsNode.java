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

import com.oracle.truffle.api.frame.VirtualFrame;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.VmUtils;

/**
 * Restores for-generator variable bindings when evaluating members nested inside one or multiple
 * for-generators.
 */
public final class RestoreForBindingsNode extends ExpressionNode {
  private @Child ExpressionNode child;

  public RestoreForBindingsNode(ExpressionNode child) {
    super(child.getSourceSection());
    this.child = child;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    var generatorFrame = ObjectData.getGeneratorFrame(frame);
    var numSlots = frame.getFrameDescriptor().getNumberOfSlots();
    // This value is constant and could be a constructor argument.
    var startSlot = generatorFrame.getFrameDescriptor().getNumberOfSlots() - numSlots;
    assert startSlot >= 0;
    // Copy locals that are for-generator variables into this frame.
    // Slots before `startSlot` (if any) are function arguments
    // and must not be copied to preserve scoping rules.
    VmUtils.copyLocals(generatorFrame, startSlot, frame, 0, numSlots);
    return child.executeGeneric(frame);
  }
}
