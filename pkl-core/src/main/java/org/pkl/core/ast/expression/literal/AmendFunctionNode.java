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
package org.pkl.core.ast.expression.literal;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.PklNode;
import org.pkl.core.ast.PklRootNode;
import org.pkl.core.ast.SimpleRootNode;
import org.pkl.core.ast.frame.ReadFrameSlotNodeGen;
import org.pkl.core.ast.member.FunctionNode;
import org.pkl.core.ast.member.Lambda;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Nullable;

public final class AmendFunctionNode extends PklNode {
  private final boolean isCustomThisScope;
  private final PklRootNode initialFunctionRootNode;
  @CompilationFinal private int customThisSlot = -1;

  public AmendFunctionNode(ObjectLiteralNode hostNode, TypeNode[] parameterTypeNodes) {
    super(hostNode.getSourceSection());

    isCustomThisScope = hostNode.isCustomThisScope;

    var builder = FrameDescriptor.newBuilder();
    var hostDescriptor = hostNode.parametersDescriptor;
    int[] parameterSlots;
    if (hostDescriptor != null) {
      parameterSlots = new int[hostDescriptor.getNumberOfSlots()];
      for (int i = 0; i < hostDescriptor.getNumberOfSlots(); i++) {
        var slotKind = hostDescriptor.getSlotKind(i);
        var slot = builder.addSlot(slotKind, hostDescriptor.getSlotName(i), null);
        parameterSlots[i] = slot;
      }
    } else {
      parameterSlots = new int[0];
    }
    var objectToAmendSlot = builder.addSlot(FrameSlotKind.Object, null, null);
    var frameDescriptor = builder.build();

    var subsequentFunctionRootNode =
        new SimpleRootNode(
            hostNode.language,
            frameDescriptor,
            sourceSection,
            hostNode.qualifiedScopeName + ".<function>",
            new AmendFunctionBodyNode(
                sourceSection,
                hostNode.copy(
                    ReadFrameSlotNodeGen.create(
                        hostNode.getParentNode().getSourceSection(), objectToAmendSlot)),
                parameterSlots,
                objectToAmendSlot,
                null));

    if (parameterSlots.length > 0) {
      var parameterCount = hostNode.parameterTypes.length;
      initialFunctionRootNode =
          new FunctionNode(
              hostNode.language,
              frameDescriptor,
              new Lambda(sourceSection, hostNode.qualifiedScopeName + ".<function>"),
              parameterCount,
              parameterTypeNodes,
              null,
              true,
              new AmendFunctionBodyNode(
                  sourceSection,
                  hostNode.copy(
                      ReadFrameSlotNodeGen.create(
                          hostNode.getParentNode().getSourceSection(), objectToAmendSlot)),
                  parameterSlots,
                  objectToAmendSlot,
                  subsequentFunctionRootNode));
    } else {
      initialFunctionRootNode = subsequentFunctionRootNode;
    }
  }

  public VmFunction execute(VirtualFrame frame, VmFunction functionToAmend) {
    if (isCustomThisScope && customThisSlot == -1) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      customThisSlot = VmUtils.findCustomThisSlot(frame);
    }
    return new VmFunction(
        frame.materialize(),
        isCustomThisScope ? frame.getAuxiliarySlot(customThisSlot) : VmUtils.getReceiver(frame),
        functionToAmend.getParameterCount(),
        initialFunctionRootNode,
        new Context(functionToAmend, null));
  }

  private static class AmendFunctionBodyNode extends ExpressionNode {
    @Child private ExpressionNode amendObjectNode;
    private final int[] parameterSlots;
    private final int valueToAmendSlot;
    private final @Nullable PklRootNode nextFunctionRootNode;

    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    public AmendFunctionBodyNode(
        SourceSection sourceSection,
        ExpressionNode amendObjectNode,
        int[] parameterSlots,
        int valueToAmendSlot,
        @Nullable PklRootNode nextFunctionRootNode) {

      super(sourceSection);
      this.amendObjectNode = amendObjectNode;
      this.parameterSlots = parameterSlots;
      this.valueToAmendSlot = valueToAmendSlot;
      this.nextFunctionRootNode = nextFunctionRootNode;
    }

    @Override
    @ExplodeLoop
    public Object executeGeneric(VirtualFrame frame) {
      var frameArguments = frame.getArguments();
      var currentFunction = (VmFunction) VmUtils.getOwner(frame);
      var context = (Context) currentFunction.getExtraStorage();
      if (nextFunctionRootNode != null) {
        context = context.setFrame(frame.materialize());
      }
      var functionToAmend = context.function;

      var arguments = new Object[frameArguments.length];
      arguments[0] = functionToAmend.getThisValue();
      arguments[1] = functionToAmend;
      System.arraycopy(frameArguments, 2, arguments, 2, frameArguments.length - 2);

      var valueToAmend = callNode.call(functionToAmend.getCallTarget(), arguments);
      if (!(valueToAmend instanceof VmFunction newFunctionToAmend)) {
        var materializedFrame = context.frame;
        if (materializedFrame != null) {
          for (var slot : parameterSlots) {
            // could use WriteFrameSlotNode and Read(Other)FrameSlotNode to specialize
            frame.setObject(slot, materializedFrame.getValue(slot));
          }
        }
        frame.setObject(valueToAmendSlot, valueToAmend);
        return amendObjectNode.executeGeneric(frame);
      }

      return currentFunction.copy(
          newFunctionToAmend.getParameterCount(),
          nextFunctionRootNode,
          context.setFunction(newFunctionToAmend));
    }
  }

  @ValueType
  private static class Context {
    public final VmFunction function;
    public final @Nullable MaterializedFrame frame;

    public Context(VmFunction function, @Nullable MaterializedFrame frame) {
      this.function = function;
      this.frame = frame;
    }

    public Context setFunction(VmFunction newFunction) {
      return new Context(newFunction, frame);
    }

    public Context setFrame(MaterializedFrame newFrame) {
      return new Context(function, newFrame);
    }
  }
}
