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

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.pkl.core.ast.PklNode;

public abstract class WriteNthFrameSlotNode extends PklNode {

  public abstract void executeWithSlotAndValue(VirtualFrame frame, int slot, Object value);

  @Specialization(guards = "isBooleanOrIllegal(frame, slot)")
  protected void evalBoolean(VirtualFrame frame, int slot, boolean value) {
    frame.getFrameDescriptor().setSlotKind(slot, FrameSlotKind.Boolean);
    frame.setBoolean(slot, value);
  }

  @Specialization(guards = "isIntOrIllegal(frame, slot)")
  protected void evalInt(VirtualFrame frame, int slot, int value) {
    frame.getFrameDescriptor().setSlotKind(slot, FrameSlotKind.Long);
    frame.setLong(slot, value);
  }

  @Specialization(guards = "isFloatOrIllegal(frame, slot)")
  protected void evalFloat(VirtualFrame frame, int slot, double value) {
    frame.getFrameDescriptor().setSlotKind(slot, FrameSlotKind.Double);
    frame.setDouble(slot, value);
  }

  @Specialization(replaces = {"evalInt", "evalFloat", "evalBoolean"})
  protected void evalGeneric(VirtualFrame frame, int slot, Object value) {
    frame.getFrameDescriptor().setSlotKind(slot, FrameSlotKind.Object);
    frame.setObject(slot, value);
  }

  protected final boolean isIntOrIllegal(VirtualFrame frame, int slot) {
    var kind = frame.getFrameDescriptor().getSlotKind(slot);
    return kind == FrameSlotKind.Long || kind == FrameSlotKind.Illegal;
  }

  protected final boolean isFloatOrIllegal(VirtualFrame frame, int slot) {
    var kind = frame.getFrameDescriptor().getSlotKind(slot);
    return kind == FrameSlotKind.Double || kind == FrameSlotKind.Illegal;
  }

  protected final boolean isBooleanOrIllegal(VirtualFrame frame, int slot) {
    var kind = frame.getFrameDescriptor().getSlotKind(slot);
    return kind == FrameSlotKind.Boolean || kind == FrameSlotKind.Illegal;
  }
}
