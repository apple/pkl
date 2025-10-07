/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.PklBugException;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.frame.ReadEnclosingFrameSlotNodeGen;
import org.pkl.core.ast.frame.ReadFrameSlotNodeGen;
import org.pkl.core.runtime.VmUtils;

/**
 * Resolves a variable that was partially resolved at parse time. This node is needed mainly because
 * of two reasons:
 *
 * <ol>
 *   <li>Frame slot nodes cannot always be resolved at parse time.
 *   <li>Mixins/function amending may introduce futher level ups that cannot be calculated at parse
 *       time
 * </ol>
 */
public final class ResolveParseTimeVariableNode extends ExpressionNode {
  private final PartiallyResolvedVariable pvar;

  public ResolveParseTimeVariableNode(PartiallyResolvedVariable pvar, SourceSection sourceSection) {
    super(sourceSection);
    this.pvar = pvar;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return replace(doResolve(frame)).executeGeneric(frame);
  }

  private ExpressionNode doResolve(VirtualFrame frame) {
    // don't compile this (only runs once)
    // invalidation will be done by Node.replace() in the caller
    CompilerDirectives.transferToInterpreter();

    // frame slot vars need to be resolved at runtime
    if (pvar instanceof PartiallyResolvedVariable.FrameSlotVar flvar) {
      var variableName = flvar.name();
      var localPropertyName = variableName.toLocalProperty();
      var currFrame = frame;
      var currOwner = VmUtils.getOwner(currFrame);
      var levelsUp = 0;

      do {
        var slot = ResolveVariableNode.findFrameSlot(currFrame, variableName, localPropertyName);
        if (slot != -1) {
          return levelsUp == 0
              ? ReadFrameSlotNodeGen.create(getSourceSection(), slot)
              : ReadEnclosingFrameSlotNodeGen.create(getSourceSection(), slot, levelsUp);
        }

        currFrame = currOwner.getEnclosingFrame();
        currOwner = VmUtils.getOwnerOrNull(currFrame);
        levelsUp += 1;
      } while (currOwner != null);

      // if this variable was resolved at parse time, this should never happen
      throw PklBugException.unreachableCode();
    }
    // Resolved vars are already resolved, so just return them
    if (pvar instanceof PartiallyResolvedVariable.Resolved res) {
      return res.node();
    }
    throw PklBugException.unreachableCode();
  }
}
