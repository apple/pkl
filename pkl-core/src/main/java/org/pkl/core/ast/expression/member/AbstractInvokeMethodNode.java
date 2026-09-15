/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.FunctionNode;
import org.pkl.core.ast.member.Method;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.TypeNode.SelfTypeNode;
import org.pkl.core.ast.type.TypeNode.TypeVariableNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.VmUtils;

public abstract class AbstractInvokeMethodNode extends ExpressionNode {

  @Children protected final ExpressionNode[] argumentNodes;
  @Children protected final UnresolvedTypeNode @Nullable [] unresolvedTypeArgumentNodes;

  @CompilationFinal(dimensions = 1)
  @Children
  protected TypeNode @Nullable [] typeArgumentNodes;

  protected final boolean argsRequireInference;
  @CompilationFinal private boolean allTypeArgumentsAreFinal = true;

  protected AbstractInvokeMethodNode(
      SourceSection sourceSection,
      UnresolvedTypeNode @Nullable [] unresolvedTypeArgumentNodes,
      ExpressionNode[] argumentNodes,
      boolean argsRequireInference) {
    super(sourceSection);
    this.unresolvedTypeArgumentNodes = unresolvedTypeArgumentNodes;
    this.argumentNodes = argumentNodes;
    this.argsRequireInference = argsRequireInference;
  }

  @TruffleBoundary
  private int getMethodSlot(FrameDescriptor frameDescriptor) {
    // can't store the slot id as this node may be called from different root nodes
    // (see constraints14 snippet)
    return frameDescriptor.findOrAddAuxiliarySlot(VmUtils.METHOD_FRAME_SLOT_ID);
  }

  protected TypeNode @Nullable [] getTypeArgumentNodes(VirtualFrame frame) {
    if (unresolvedTypeArgumentNodes == null) return null;
    if (typeArgumentNodes != null) return typeArgumentNodes;

    CompilerDirectives.transferToInterpreterAndInvalidate();
    var typeNodes = new TypeNode[unresolvedTypeArgumentNodes.length];
    for (var i = 0; i < typeNodes.length; i++) {
      typeNodes[i] = unresolvedTypeArgumentNodes[i].execute(frame);
      allTypeArgumentsAreFinal = allTypeArgumentsAreFinal && typeNodes[i].isFinalType();
    }
    typeArgumentNodes = typeNodes;
    return typeNodes;
  }

  @Idempotent
  protected boolean getTypeArgumentsAreFinal(VirtualFrame frame) {
    // call for side effect of setting allTypeArgumentsAreFinal
    getTypeArgumentNodes(frame);
    return allTypeArgumentsAreFinal;
  }

  protected FunctionNode instantiateFunction(
      VirtualFrame frame, Method method, FunctionNode original) {
    if (unresolvedTypeArgumentNodes == null) return original;
    if (unresolvedTypeArgumentNodes.length != original.getTypeParameterCount()) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError(
              "wrongTypeArgumentCount",
              original.getTypeParameterCount(),
              unresolvedTypeArgumentNodes.length)
          .build();
    }
    var fn = (FunctionNode) original.deepCopy();
    var typeArgs = getTypeArgumentNodes(frame);
    assert typeArgs != null;
    fn.accept(
        node -> {
          if (node instanceof TypeVariableNode typeVariableNode) {
            var typeParam = typeVariableNode.getTypeParameter();
            if (typeParam.getOwner() instanceof Method m && method.isChildOf(m)) {
              var originalTypeNode = typeArgs[typeParam.getIndex()];
              var replacement =
                  (originalTypeNode instanceof SelfTypeNode selfTypeNode
                          ? selfTypeNode.getReifiedTypeNode(frame)
                          : deepCopy(originalTypeNode))
                      .initWriteSlotNode(typeVariableNode.getFrameSlot());
              if (node.getParent() instanceof AbstractInvokeMethodNode abstractInvokeMethodNode) {
                abstractInvokeMethodNode.typeArgumentNodes = null;
              }
              node.replace(replacement);
            }
          } else if (node instanceof UnresolvedTypeNode.TypeVariable unresolvedTypeVariableNode) {
            var typeParam = unresolvedTypeVariableNode.getTypeParameter();
            if (typeParam.getOwner() instanceof Method m && method.isChildOf(m)) {
              var originalTypeNode = typeArgs[typeParam.getIndex()];
              var replacement =
                  (originalTypeNode instanceof SelfTypeNode selfTypeNode
                      ? selfTypeNode.getReifiedTypeNode(frame)
                      : deepCopy(originalTypeNode));
              node.replace(new UnresolvedTypeNode.Resolved(sourceSection, replacement));
            }
          }
          return true;
        });
    return fn;
  }

  private static TypeNode deepCopy(TypeNode typeNode) {
    return ((TypeNode) typeNode.deepCopy());
  }

  @ExplodeLoop
  protected Object[] evalArgs(
      VirtualFrame frame, @Nullable Method method, Object owner, @Nullable Object receiver) {
    int methodSlot = -1;
    Object prevMethod = null;
    if (argsRequireInference) {
      methodSlot = getMethodSlot(frame.getFrameDescriptor());
      prevMethod = frame.getAuxiliarySlot(methodSlot);
      frame.setAuxiliarySlot(methodSlot, method);
    }

    var args = new Object[2 + argumentNodes.length];
    args[0] = receiver;
    args[1] = owner;

    try {
      for (var i = 0; i < argumentNodes.length; i++) {
        args[2 + i] = argumentNodes[i].executeGeneric(frame);
      }
    } finally {
      if (argsRequireInference) {
        frame.setAuxiliarySlot(methodSlot, prevMethod);
      }
    }

    return args;
  }
}
