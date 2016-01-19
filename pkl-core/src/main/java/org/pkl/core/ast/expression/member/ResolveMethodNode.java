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
package org.pkl.core.ast.expression.member;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ConstantValueNode;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.MemberLookupMode;
import org.pkl.core.ast.builder.ConstLevel;
import org.pkl.core.ast.expression.primary.*;
import org.pkl.core.ast.internal.GetClassNodeGen;
import org.pkl.core.ast.member.Member;
import org.pkl.core.runtime.*;

/**
 * Resolves a method name in a method call with implicit receiver, for example `bar` in `x = bar()`
 * (but not `foo.bar()`).
 *
 * <p>A method name can refer to any of the following: - a (potentially `local`) method in the
 * lexical scope - a base module method - a method accessible through `this`
 *
 * <p>This node's task is to make a one-time decision between these alternatives for the call site
 * it represents.
 */
// TODO: Consider doing this at parse time (cf. ResolveVariableNode).
@NodeInfo(shortName = "resolveMethod")
public final class ResolveMethodNode extends ExpressionNode {
  private final Identifier methodName;
  private final ExpressionNode[] argumentNodes;
  // Tells if the call site is inside the base module.
  private final boolean isBaseModule;
  // Tells if the call site is inside a [CustomThisScope].
  private final boolean isCustomThisScope;
  private final ConstLevel constLevel;

  public ResolveMethodNode(
      SourceSection sourceSection,
      Identifier methodName,
      ExpressionNode[] argumentNodes,
      boolean isBaseModule,
      boolean isCustomThisScope,
      ConstLevel constLevel) {

    super(sourceSection);

    this.methodName = methodName;
    this.argumentNodes = argumentNodes;
    this.isBaseModule = isBaseModule;
    this.isCustomThisScope = isCustomThisScope;
    this.constLevel = constLevel;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return replace(doResolve(VmUtils.getOwner(frame))).executeGeneric(frame);
  }

  @TruffleBoundary
  private ExpressionNode doResolve(VmObjectLike initialOwner) {
    var levelsUp = 0;
    Identifier localMethodName = methodName.toLocalMethod();

    // Search lexical scope.
    for (var currOwner = initialOwner;
        currOwner != null;
        currOwner = currOwner.getEnclosingOwner()) {

      if (currOwner.isPrototype()) {
        var localMethod = currOwner.getVmClass().getDeclaredMethod(localMethodName);
        if (localMethod != null) {
          assert localMethod.isLocal();
          checkConst(currOwner, localMethod);
          return new InvokeMethodLexicalNode(
              sourceSection, localMethod.getCallTarget(sourceSection), levelsUp, argumentNodes);
        }
        var method = currOwner.getVmClass().getDeclaredMethod(methodName);
        if (method != null) {
          assert !method.isLocal();
          checkConst(currOwner, method);
          if (method.getDeclaringClass().isClosed()) {
            return new InvokeMethodLexicalNode(
                sourceSection, method.getCallTarget(sourceSection), levelsUp, argumentNodes);
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
      } else {
        var localMethod = currOwner.getMember(localMethodName);
        if (localMethod != null) {
          assert localMethod.isLocal();
          checkConst(currOwner, localMethod);
          var methodCallTarget =
              // TODO: is it OK to pass owner as receiver here?
              // (calls LocalMethodNode, which only resolves types)
              (CallTarget) localMethod.getCallTarget().call(currOwner, currOwner);

          return new InvokeMethodLexicalNode(
              sourceSection, methodCallTarget, levelsUp, argumentNodes);
        }
      }

      levelsUp += 1;
    }

    // Search base module (unless call site is itself inside base module).
    if (!isBaseModule) {
      var baseModule = BaseModule.getModule();
      // use `getDeclaredMethod()` so as not to resolve to anything declared in class
      // pkl.base#Module
      var method = baseModule.getVmClass().getDeclaredMethod(methodName);
      if (method != null) {
        assert !method.isLocal();
        return new InvokeMethodDirectNode(
            sourceSection, method, new ConstantValueNode(baseModule), argumentNodes);
      }
    }

    // Assuming this method exists at all, it must be a method accessible through `this`.
    //noinspection ConstantConditions
    return InvokeMethodVirtualNodeGen.create(
        sourceSection,
        methodName,
        argumentNodes,
        MemberLookupMode.IMPLICIT_THIS,
        VmUtils.createThisNode(VmUtils.unavailableSourceSection(), isCustomThisScope),
        GetClassNodeGen.create(null));
  }

  private void checkConst(VmObjectLike currOwner, Member method) {
    var invalid = false;
    switch (constLevel) {
      case ALL:
        invalid = !method.isConst();
        break;
      case MODULE:
        invalid = currOwner.isModuleObject() && !method.isConst();
        break;
    }
    if (invalid) {
      throw exceptionBuilder().evalError("methodMustBeConst", methodName.toString()).build();
    }
  }
}
