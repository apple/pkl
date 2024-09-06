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
package org.pkl.core.ast.expression.primary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ConstantValueNode;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.MemberLookupMode;
import org.pkl.core.ast.builder.ConstLevel;
import org.pkl.core.ast.expression.member.ReadLocalPropertyNode;
import org.pkl.core.ast.expression.member.ReadPropertyNodeGen;
import org.pkl.core.ast.frame.ReadAuxiliarySlotNode;
import org.pkl.core.ast.frame.ReadEnclosingAuxiliarySlotNode;
import org.pkl.core.ast.frame.ReadEnclosingFrameSlotNodeGen;
import org.pkl.core.ast.frame.ReadFrameSlotNodeGen;
import org.pkl.core.ast.member.Member;
import org.pkl.core.runtime.*;

/**
 * Resolves a variable name, for example `foo` in `x = foo`.
 *
 * <p>A variable name can refer to any of the following: - a (potentially `local`) property in the
 * lexical scope - a method or lambda parameter in the lexical scope - a base module property - a
 * property accessible through `this`
 *
 * <p>This node's task is to make a one-time decision between these alternatives for the call site
 * it represents.
 */
// TODO: Move this to parse time (required for supporting local variables, more efficient)
//
// TODO: In REPL, undo replace if environment changes to make the following work.
// Perhaps instrumenting this node in REPL would be a good solution.
// x = { y = z }
// :force x // Property not found: z
// z = 1
// :force x // should work but doesn't
public final class ResolveVariableNode extends ExpressionNode {
  private final Identifier variableName;
  private final boolean isBaseModule;
  private final boolean isCustomThisScope;
  private final ConstLevel constLevel;
  private final int constDepth;

  public ResolveVariableNode(
      SourceSection sourceSection,
      Identifier variableName,
      boolean isBaseModule,
      boolean isCustomThisScope,
      ConstLevel constLevel,
      int constDepth) {
    super(sourceSection);
    this.variableName = variableName;
    this.isBaseModule = isBaseModule;
    this.isCustomThisScope = isCustomThisScope;
    this.constLevel = constLevel;
    this.constDepth = constDepth;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return replace(doResolve(frame)).executeGeneric(frame);
  }

  private ExpressionNode doResolve(VirtualFrame frame) {
    // don't compile this (only runs once)
    // invalidation will be done by Node.replace() in the caller
    CompilerDirectives.transferToInterpreter();

    var localPropertyName = variableName.toLocalProperty();

    // search the frame for auxiliary slots carrying this variable (placed by
    // `WriteForVariablesNode`)
    var variableSlot = VmUtils.findAuxiliarySlot(frame, localPropertyName);
    if (variableSlot == -1) {
      variableSlot = VmUtils.findAuxiliarySlot(frame, variableName);
    }
    if (variableSlot != -1) {
      return new ReadAuxiliarySlotNode(getSourceSection(), variableSlot);
    }
    // search the frame for slots carrying this variable
    variableSlot = VmUtils.findSlot(frame, localPropertyName);
    if (variableSlot == -1) {
      variableSlot = VmUtils.findSlot(frame, variableName);
    }
    if (variableSlot != -1) {
      return ReadFrameSlotNodeGen.create(getSourceSection(), variableSlot);
    }

    var currFrame = frame;
    var currOwner = VmUtils.getOwner(currFrame);
    var levelsUp = 0;

    // Search lexical scope for a matching method/lambda parameter, `for` generator variable, or
    // object property.
    do {
      var parameterSlot = VmUtils.findSlot(currFrame, variableName);
      if (parameterSlot == -1) {
        parameterSlot = VmUtils.findSlot(currFrame, localPropertyName);
      }
      if (parameterSlot != -1) {
        return levelsUp == 0
            ? ReadFrameSlotNodeGen.create(getSourceSection(), parameterSlot)
            : ReadEnclosingFrameSlotNodeGen.create(getSourceSection(), parameterSlot, levelsUp);
      }
      var auxiliarySlot = VmUtils.findAuxiliarySlot(currFrame, variableName);
      if (auxiliarySlot == -1) {
        auxiliarySlot = VmUtils.findAuxiliarySlot(currFrame, localPropertyName);
      }
      if (auxiliarySlot != -1) {
        return levelsUp == 0
            ? new ReadAuxiliarySlotNode(getSourceSection(), auxiliarySlot)
            : new ReadEnclosingAuxiliarySlotNode(getSourceSection(), auxiliarySlot, levelsUp);
      }

      var localMember = currOwner.getMember(localPropertyName);
      if (localMember != null) {
        assert localMember.isLocal();

        checkConst(currOwner, localMember, levelsUp);

        var value = localMember.getConstantValue();
        if (value != null) {
          // This is the only code path that resolves local constant properties.
          // Since this code path doesn't use ObjectMember.getCachedValue(),
          // there is no point in calling localMember.setCachedValue() either.
          return new ConstantValueNode(sourceSection, value);
        }

        return new ReadLocalPropertyNode(sourceSection, localMember, levelsUp);
      }

      var member = currOwner.getMember(variableName);
      if (member != null) {
        assert !member.isLocal();

        checkConst(currOwner, member, levelsUp);

        // Non-local properties are late-bound, which is why we can't ever return ConstantNode here.
        //
        // Assuming this node isn't used in Truffle ASTs of `external` pkl.base classes whose values
        // aren't VmObject's,
        // we only ever need VmObject-compatible specializations here.
        // We don't exploit this fact here but ReadLocalPropertyNode (used above) does.
        return ReadPropertyNodeGen.create(
            sourceSection,
            variableName,
            MemberLookupMode.IMPLICIT_LEXICAL,
            // we already checked for const-safety, no need to recheck
            false,
            levelsUp == 0 ? new GetReceiverNode() : new GetEnclosingReceiverNode(levelsUp));
      }

      currFrame = currOwner.getEnclosingFrame();
      currOwner = VmUtils.getOwnerOrNull(currFrame);
      levelsUp += 1;
    } while (currOwner != null);

    // Search base module, unless call site is inside base module.
    if (!isBaseModule) {
      var baseModule = BaseModule.getModule();

      var cachedValue = baseModule.getCachedValue(variableName);
      if (cachedValue != null) {
        return new ConstantValueNode(sourceSection, cachedValue);
      }

      var member = baseModule.getMember(variableName);

      if (member != null) {
        var constantValue = member.getConstantValue();
        if (constantValue != null) {
          baseModule.setCachedValue(variableName, constantValue, member);
          return new ConstantValueNode(sourceSection, constantValue);
        }

        var computedValue = member.getCallTarget().call(baseModule, baseModule);
        baseModule.setCachedValue(variableName, computedValue, member);
        return new ConstantValueNode(sourceSection, computedValue);
      }
    }

    // Assuming this property exists at all, it must be a property accessible through `this`.
    ///
    // Reading a property off of implicit `this` needs a const check if this node is not in a const
    // scope.
    // open class A {
    //   a = 1
    // }
    //
    // class B extends A {
    //   const b = a // <-- implicit this lookup of `a`, which is not in a const scope.
    // }
    //
    // A const scope exists if there is an object body, for example.
    //
    // class B extends A {
    //   const b = new { a } // <-- `new {}` creates a const scope.
    // }
    boolean needsConst = constLevel == ConstLevel.ALL && constDepth == -1 && !isCustomThisScope;
    return ReadPropertyNodeGen.create(
        sourceSection,
        variableName,
        MemberLookupMode.IMPLICIT_THIS,
        needsConst,
        VmUtils.createThisNode(VmUtils.unavailableSourceSection(), isCustomThisScope));
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
