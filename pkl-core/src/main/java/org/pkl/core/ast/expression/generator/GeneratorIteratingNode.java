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
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.LateInit;

public abstract class GeneratorIteratingNode extends GeneratorMemberNode {

  @Child protected GeneratorIterableNode iterableNode;
  @Child @LateInit private IndirectCallNode callNode;

  protected GeneratorIteratingNode(
      SourceSection sourceSection, GeneratorIterableNode iterableNode) {
    super(sourceSection);
    this.iterableNode = iterableNode;
  }

  protected Object evalIterable(VirtualFrame frame, ObjectData data) {
    var currentForVariables = data.getCurrentForBindings();
    if (currentForVariables == null) {
      currentForVariables = new Object[0];
    }
    var arguments = new Object[4];
    arguments[0] = VmUtils.getReceiver(frame);
    arguments[1] = VmUtils.getOwner(frame);
    arguments[2] = currentForVariables;
    var frameSlotValues = new Object[frame.getFrameDescriptor().getNumberOfSlots()];
    for (var i = 0; i < frame.getFrameDescriptor().getNumberOfSlots(); i++) {
      if (frame.getTag(i) != FrameSlotKind.Illegal.tag) {
        frameSlotValues[i] = frame.getValue(i);
      }
    }
    arguments[3] = frameSlotValues;
    return getCallNode().call(iterableNode.getCallTarget(), arguments);
  }

  private IndirectCallNode getCallNode() {
    if (callNode == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      callNode = IndirectCallNode.create();
    }
    return callNode;
  }
}
