/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import org.pkl.core.ast.ConstantValueNode;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.MemberLookupMode;
import org.pkl.core.ast.builder.ConstLevel;
import org.pkl.core.ast.expression.member.ReadLocalPropertyNode;
import org.pkl.core.ast.expression.member.ReadPropertyNodeGen;
import org.pkl.core.ast.frame.ReadEnclosingFrameSlotNodeGen;
import org.pkl.core.ast.frame.ReadFrameSlotNodeGen;
import org.pkl.core.ast.member.Member;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.runtime.VmUtils;

/**
 * Resolves a variable that was partially resolved at parse time. This node is needed mainly because
 * of two reasons:
 *
 * <ol>
 *   <li>Frame slot nodes cannot always be resolved at parse time.
 *   <li>Mixins/function amending may introduce further level ups that cannot be calculated at parse
 *       time
 * </ol>
 */
public final class ResolveParseTimeVariableNode extends ExpressionNode {
  private final PartiallyResolvedVariable pvar;
  private final ConstLevel constLevel;
  private final int constDepth;
  private final Identifier variableName;

  public ResolveParseTimeVariableNode(
      PartiallyResolvedVariable pvar,
      SourceSection sourceSection,
      ConstLevel constLevel,
      int constDepth,
      Identifier variableName) {
    super(sourceSection);
    this.pvar = pvar;
    this.constLevel = constLevel;
    this.constDepth = constDepth;
    this.variableName = variableName;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return replace(doResolve(frame)).executeGeneric(frame);
  }

  private ExpressionNode doResolve(VirtualFrame frame) {
    // don't compile this (only runs once)
    // invalidation will be done by Node.replace() in the caller
    CompilerDirectives.transferToInterpreter();

    if (pvar instanceof PartiallyResolvedVariable.ConstantVar cvar) {
      return new ConstantValueNode(sourceSection, cvar.value());
    }

    if (pvar instanceof PartiallyResolvedVariable.LocalPropertyVar lpvar) {
      var name = lpvar.name();
      var currFrame = frame;
      var currOwner = VmUtils.getOwner(currFrame);
      var levelsUp = 0;

      do {
        var localMember = currOwner.getMember(name);
        if (localMember != null) {
          assert localMember.isLocal();

          if (!lpvar.isConst()) {
            checkConst(currOwner, localMember, levelsUp);
          }

          var value = localMember.getConstantValue();
          if (value != null) {
            return new ConstantValueNode(sourceSection, value);
          }

          return new ReadLocalPropertyNode(sourceSection, name, levelsUp);
        }

        currFrame = currOwner.getEnclosingFrame();
        currOwner = VmUtils.getOwnerOrNull(currFrame);
        levelsUp += 1;
      } while (currOwner != null);

      throw PklBugException.unreachableCode();
    }

    if (pvar instanceof PartiallyResolvedVariable.PropertyVar ppvar) {
      var name = ppvar.name();
      var currFrame = frame;
      var currOwner = VmUtils.getOwner(currFrame);
      var levelsUp = 0;

      do {
        var member = currOwner.getMember(name);
        if (member != null) {
          assert !member.isLocal();

          if (!ppvar.isConst()) {
            checkConst(currOwner, member, levelsUp);
          }

          return ReadPropertyNodeGen.create(
              sourceSection,
              name,
              MemberLookupMode.IMPLICIT_LEXICAL,
              false,
              levelsUp == 0 ? new GetReceiverNode() : new GetEnclosingReceiverNode(levelsUp));
        }

        currFrame = currOwner.getEnclosingFrame();
        currOwner = VmUtils.getOwnerOrNull(currFrame);
        levelsUp += 1;
      } while (currOwner != null);

      throw PklBugException.unreachableCode();
    }

    if (pvar instanceof PartiallyResolvedVariable.FrameSlotVar flvar) {
      var fsvName = flvar.name();
      var localPropertyName = fsvName.toLocalProperty();
      var currFrame = frame;
      var currOwner = VmUtils.getOwner(currFrame);
      var levelsUp = 0;

      do {
        var slot = ResolveVariableNode.findFrameSlot(currFrame, fsvName, localPropertyName);
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

    throw PklBugException.unreachableCode();
  }

  @SuppressWarnings("DuplicatedCode")
  private void checkConst(VmObjectLike currOwner, Member member, int levelsUp) {
    if (!constLevel.isConst()) {
      return;
    }
    var memberIsOutsideConstScope = levelsUp > constDepth;
    var invalid =
        switch (constLevel) {
          case ALL -> memberIsOutsideConstScope && !member.isConst();
          case MODULE -> currOwner.isModuleObject() && !member.isConst();
          default -> false;
        };
    if (invalid) {
      throw exceptionBuilder().evalError("propertyMustBeConst", variableName.toString()).build();
    }
  }
}
