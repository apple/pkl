/**
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

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.VmUtils;

public class ReadEnclosingAuxiliarySlotNode extends ExpressionNode {
  private final int slot;
  private final int levelsUp;

  public ReadEnclosingAuxiliarySlotNode(SourceSection sourceSection, int slot, int levelsUp) {
    super(sourceSection);
    this.slot = slot;
    this.levelsUp = levelsUp;
  }

  // could be factored out into a separate node s.t. it
  // won't be repeated in case of FrameSlotTypeException
  @ExplodeLoop
  protected final MaterializedFrame getCapturedFrame(VirtualFrame frame) {
    var owner = VmUtils.getOwner(frame);
    for (var i = 0; i < levelsUp - 1; i++) {
      owner = owner.getEnclosingOwner();
      assert owner != null; // guaranteed by AstBuilder
    }
    return owner.getEnclosingFrame();
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return getCapturedFrame(frame).getAuxiliarySlot(slot);
  }
}
