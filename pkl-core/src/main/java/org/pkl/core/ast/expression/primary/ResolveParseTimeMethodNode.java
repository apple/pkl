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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.PklBugException;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.MemberLookupMode;
import org.pkl.core.ast.builder.ConstLevel;
import org.pkl.core.ast.expression.member.InvokeMethodLexicalNode;
import org.pkl.core.ast.expression.member.InvokeMethodVirtualNodeGen;
import org.pkl.core.ast.internal.GetClassNodeGen;
import org.pkl.core.ast.member.Member;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.runtime.VmUtils;

/**
 * Resolves a method that was partially resolved at parse time. This node is needed because mixins
 * and function amending may introduce further level ups that cannot be calculated at parse time.
 */
public final class ResolveParseTimeMethodNode extends ExpressionNode {
  private final PartiallyResolvedMethod pmeth;
  @Children private final ExpressionNode[] argumentNodes;
  private final ConstLevel constLevel;
  private final int constDepth;
  private final Identifier methodName;

  public ResolveParseTimeMethodNode(
      PartiallyResolvedMethod pmeth,
      SourceSection sourceSection,
      ExpressionNode[] argumentNodes,
      ConstLevel constLevel,
      int constDepth,
      Identifier methodName) {
    super(sourceSection);
    this.pmeth = pmeth;
    this.argumentNodes = argumentNodes;
    this.constLevel = constLevel;
    this.constDepth = constDepth;
    this.methodName = methodName;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return replace(doResolve(frame)).executeGeneric(frame);
  }

  private ExpressionNode doResolve(VirtualFrame frame) {
    CompilerDirectives.transferToInterpreter();

    if (pmeth instanceof PartiallyResolvedMethod.LexicalMethodVar) {
      return resolveLexicalMethod(frame);
    }

    if (pmeth instanceof PartiallyResolvedMethod.VirtualMethodVar) {
      return resolveVirtualMethod(frame);
    }

    throw PklBugException.unreachableCode();
  }

  private ExpressionNode resolveLexicalMethod(VirtualFrame frame) {
    var lmvar = (PartiallyResolvedMethod.LexicalMethodVar) pmeth;
    var localMethodName = methodName.toLocalMethod();
    var currOwner = VmUtils.getOwner(frame);
    var levelsUp = 0;

    do {
      if (currOwner.isPrototype()) {
        var localMethod = currOwner.getVmClass().getDeclaredMethod(localMethodName);
        if (localMethod != null) {
          assert localMethod.isLocal();
          if (!lmvar.isConst()) {
            checkConst(currOwner, localMethod, levelsUp);
          }
          return new InvokeMethodLexicalNode(
              sourceSection, localMethod.getCallTarget(sourceSection), levelsUp, argumentNodes);
        }
        var method = currOwner.getVmClass().getDeclaredMethod(methodName);
        if (method != null) {
          assert !method.isLocal();
          if (!lmvar.isConst()) {
            checkConst(currOwner, method, levelsUp);
          }
          return new InvokeMethodLexicalNode(
              sourceSection, method.getCallTarget(sourceSection), levelsUp, argumentNodes);
        }
      } else {
        var localMethod = currOwner.getMember(localMethodName);
        if (localMethod != null) {
          assert localMethod.isLocal();
          if (!lmvar.isConst()) {
            checkConst(currOwner, localMethod, levelsUp);
          }
          var methodCallTarget =
              (CallTarget) localMethod.getCallTarget().call(currOwner, currOwner);
          return new InvokeMethodLexicalNode(
              sourceSection, methodCallTarget, levelsUp, argumentNodes);
        }
      }

      currOwner = currOwner.getEnclosingOwner();
      levelsUp += 1;
    } while (currOwner != null);

    throw PklBugException.unreachableCode();
  }

  private ExpressionNode resolveVirtualMethod(VirtualFrame frame) {
    var vmvar = (PartiallyResolvedMethod.VirtualMethodVar) pmeth;
    var currOwner = VmUtils.getOwner(frame);
    var levelsUp = 0;

    do {
      if (currOwner.isPrototype()) {
        var method = currOwner.getVmClass().getDeclaredMethod(methodName);
        if (method != null) {
          assert !method.isLocal();
          if (!vmvar.isConst()) {
            checkConst(currOwner, method, levelsUp);
          }
          //noinspection ConstantConditions
          return InvokeMethodVirtualNodeGen.create(
              sourceSection,
              methodName,
              argumentNodes,
              MemberLookupMode.IMPLICIT_LEXICAL,
              levelsUp == 0 ? new GetReceiverNode() : new GetEnclosingReceiverNode(levelsUp),
              GetClassNodeGen.create(null));
        }
      }

      currOwner = currOwner.getEnclosingOwner();
      levelsUp += 1;
    } while (currOwner != null);

    throw PklBugException.unreachableCode();
  }

  @SuppressWarnings("DuplicatedCode")
  private void checkConst(VmObjectLike currOwner, Member method, int levelsUp) {
    if (!constLevel.isConst()) {
      return;
    }
    var memberIsOutsideConstScope = levelsUp > constDepth;
    var invalid =
        switch (constLevel) {
          case ALL -> memberIsOutsideConstScope && !method.isConst();
          case MODULE -> currOwner.isModuleObject() && !method.isConst();
          default -> false;
        };
    if (invalid) {
      throw exceptionBuilder().evalError("methodMustBeConst", methodName.toString()).build();
    }
  }
}
