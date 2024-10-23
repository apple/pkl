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
package org.pkl.core.ast.frame;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;

// modeled after:
// https://github.com/oracle/graal/blob/93c461734f70a37458312b1d5e6d6e5bb26dd757/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/nodes/local/SLWriteLocalVariableNode.java
@NodeChild(value = "valueNode", type = ExpressionNode.class)
public abstract class WriteFrameSlotNode extends ExpressionNode {

  private final int slot;

  public WriteFrameSlotNode(SourceSection sourceSection, int slot) {
    super(sourceSection);
    this.slot = slot;
  }

  public abstract void executeWithValue(VirtualFrame frame, Object value);

  @Specialization(guards = "isIntOrIllegal(frame)")
  protected long evalInt(VirtualFrame frame, long value) {
    frame.getFrameDescriptor().setSlotKind(slot, FrameSlotKind.Long);
    frame.setLong(slot, value);
    return value;
  }

  @Specialization(guards = "isFloatOrIllegal(frame)")
  protected double evalFloat(VirtualFrame frame, double value) {
    frame.getFrameDescriptor().setSlotKind(slot, FrameSlotKind.Double);
    frame.setDouble(slot, value);
    return value;
  }

  @Specialization(guards = "isBooleanOrIllegal(frame)")
  protected boolean evalBoolean(VirtualFrame frame, boolean value) {
    frame.getFrameDescriptor().setSlotKind(slot, FrameSlotKind.Boolean);
    frame.setBoolean(slot, value);
    return value;
  }

  @Specialization(replaces = {"evalInt", "evalFloat", "evalBoolean"})
  protected Object evalGeneric(VirtualFrame frame, Object value) {
    frame.getFrameDescriptor().setSlotKind(slot, FrameSlotKind.Object);
    frame.setObject(slot, value);
    return value;
  }

  protected final boolean isIntOrIllegal(VirtualFrame frame) {
    var kind = frame.getFrameDescriptor().getSlotKind(slot);
    return kind == FrameSlotKind.Long || kind == FrameSlotKind.Illegal;
  }

  protected final boolean isFloatOrIllegal(VirtualFrame frame) {
    var kind = frame.getFrameDescriptor().getSlotKind(slot);
    return kind == FrameSlotKind.Double || kind == FrameSlotKind.Illegal;
  }

  protected final boolean isBooleanOrIllegal(VirtualFrame frame) {
    var kind = frame.getFrameDescriptor().getSlotKind(slot);
    return kind == FrameSlotKind.Boolean || kind == FrameSlotKind.Illegal;
  }
}
