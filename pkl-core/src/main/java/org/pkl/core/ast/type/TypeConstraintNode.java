/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.ast.type;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.PklNode;
import org.pkl.core.ast.lambda.ApplyVmFunction1Node;
import org.pkl.core.runtime.BaseModule;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmUtils;

@NodeChild(value = "bodyNode", type = ExpressionNode.class)
public abstract class TypeConstraintNode extends PklNode {

  @CompilationFinal private int customThisSlot = -1;

  protected TypeConstraintNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  public abstract void execute(VirtualFrame frame);

  protected abstract ExpressionNode getBodyNode();

  public String export() {
    return getSourceSection().getCharacters().toString();
  }

  @Specialization
  protected void eval(VirtualFrame frame, boolean result) {
    initConstraintSlot(frame);

    if (!result) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      var vmContext = VmContext.get(this);
      var localContext = VmLanguage.get(this).localContext.get();
      // Use power assertions if enabled and not in type test or already instrumenting.
      // This prevents `is` checks from triggering instrumentation, but allows them to
      // participate if instrumentation is already active.
      var usePowerAssertions =
          vmContext.getPowerAssertionsEnabled()
              && (!localContext.isInTypeTest() || localContext.hasActiveTracker());
      if (usePowerAssertions) {
        try (var valueTracker = vmContext.getValueTrackerFactory().create()) {
          getBodyNode().executeGeneric(frame);
          throw new VmTypeMismatchException.Constraint(
              sourceSection,
              frame.getAuxiliarySlot(customThisSlot),
              sourceSection,
              valueTracker.values());
        }
      } else {
        throw new VmTypeMismatchException.Constraint(
            sourceSection, frame.getAuxiliarySlot(customThisSlot), sourceSection, null);
      }
    }
  }

  @Specialization
  protected void eval(
      VirtualFrame frame,
      VmFunction function,
      @Cached(value = "createApplyNode()", neverDefault = true) ApplyVmFunction1Node applyNode) {
    initConstraintSlot(frame);

    var value = frame.getAuxiliarySlot(customThisSlot);
    var result = applyNode.executeBoolean(function, value);
    if (!result) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      var vmContext = VmContext.get(this);
      var localContext = VmLanguage.get(this).localContext.get();
      // Use power assertions if enabled and not in type test or already instrumenting.
      // This prevents `is` checks from triggering instrumentation, but allows them to
      // participate if instrumentation is already active.
      var usePowerAssertions =
          vmContext.getPowerAssertionsEnabled()
              && (!localContext.isInTypeTest() || localContext.hasActiveTracker());
      if (usePowerAssertions) {
        try (var valueTracker = vmContext.getValueTrackerFactory().create()) {
          applyNode.executeBoolean(function, value);
          throw new VmTypeMismatchException.Constraint(
              sourceSection,
              value,
              function.getRootNode().getSourceSection(),
              valueTracker.values());
        }
      } else {
        throw new VmTypeMismatchException.Constraint(
            sourceSection, value, function.getRootNode().getSourceSection(), null);
      }
    }
  }

  @Fallback
  protected void fallback(Object object) {
    // supplying a type constraint that's neither a boolean nor a function
    // is always fatal (even within a union type), hence throw VmEvalException
    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder()
        .typeMismatch(object, BaseModule.getBooleanClass(), BaseModule.getFunctionClass())
        .build();
  }

  protected static ApplyVmFunction1Node createApplyNode() {
    return ApplyVmFunction1Node.create();
  }

  private void initConstraintSlot(VirtualFrame frame) {
    if (customThisSlot == -1) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      // deferred until execution time s.t. nodes of inlined type aliases get the right frame slot
      customThisSlot = VmUtils.findCustomThisSlot(frame);
    }
  }
}
