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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.Method;
import org.pkl.core.ast.type.TypeArgumentTypeNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmTypeArgument;
import org.pkl.core.util.ArrayUtils;

public abstract class AbstractInvokeMethodNode extends ExpressionNode {

  @Children protected final ExpressionNode[] argumentNodes;
  protected final int methodSlot;
  protected final String qualifiedName;

  protected final int[] slotsToCopy;
  @Children protected UnresolvedTypeNode @Nullable [] unresolvedTypeArgumentNodes;

  @CompilationFinal(dimensions = 1)
  protected TypeArgumentTypeNode @Nullable [] typeArgumentRootNodes;

  @CompilationFinal boolean typeArgumentsNeedMaterializedFrame = false;

  protected AbstractInvokeMethodNode(
      SourceSection sourceSection,
      UnresolvedTypeNode @Nullable [] unresolvedTypeArgumentNodes,
      ExpressionNode[] argumentNodes,
      int methodSlot,
      int[] parameterSlots,
      int[] forGeneratorSlots,
      String qualifiedName) {
    super(sourceSection);
    this.unresolvedTypeArgumentNodes = unresolvedTypeArgumentNodes;
    this.argumentNodes = argumentNodes;
    this.methodSlot = methodSlot;
    this.slotsToCopy = ArrayUtils.concat(parameterSlots, forGeneratorSlots);
    this.qualifiedName = qualifiedName;
  }

  protected TypeArgumentTypeNode @Nullable [] getTypeArgumentRootNodes(VirtualFrame frame) {
    if (typeArgumentRootNodes == null) {
      if (unresolvedTypeArgumentNodes != null) {
        var language = VmLanguage.get(this);
        var frameDescriptor = frame.getFrameDescriptor();
        CompilerDirectives.transferToInterpreterAndInvalidate();

        var rootNodes = new TypeArgumentTypeNode[unresolvedTypeArgumentNodes.length];
        for (var i = 0; i < rootNodes.length; i++) {
          var typeNode = unresolvedTypeArgumentNodes[i].execute(frame);
          rootNodes[i] =
              new TypeArgumentTypeNode(
                  language,
                  frameDescriptor,
                  sourceSection,
                  qualifiedName + ".<typearg#" + (i + 1) + ">",
                  typeNode,
                  slotsToCopy);
          typeArgumentsNeedMaterializedFrame =
              typeArgumentsNeedMaterializedFrame || typeNode.getTypeArgumentRequiresFrame();
        }
        typeArgumentRootNodes = rootNodes;
        unresolvedTypeArgumentNodes = null;
      } else {
        return null;
      }
    }
    return typeArgumentRootNodes;
  }

  protected VmTypeArgument @Nullable [] getTypeArguments(
      VirtualFrame frame, @Nullable Method method) {
    var rootNodes = getTypeArgumentRootNodes(frame);
    if (rootNodes == null) return null;

    var typeParameterCount = method == null ? 0 : method.getFunctionNode().getTypeParameterCount();
    if (rootNodes.length != typeParameterCount) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("wrongTypeArgumentCount", typeParameterCount, rootNodes.length)
          .build();
    }

    var argFrame = typeArgumentsNeedMaterializedFrame ? frame.materialize() : null;
    var typeArgs = new VmTypeArgument[rootNodes.length];
    for (var i = 0; i < typeArgs.length; i++) {
      var rootNode = rootNodes[i];
      typeArgs[i] = new VmTypeArgument(rootNode, argFrame);
    }
    return typeArgs;
  }

  @ExplodeLoop
  protected Object[] evalArgs(
      VirtualFrame frame, @Nullable Method method, Object owner, @Nullable Object receiver) {
    var typeArgs = getTypeArguments(frame, method);
    Object prevMethod = null;
    if (methodSlot > -1) {
      prevMethod = frame.getObject(methodSlot);
      frame.setObject(methodSlot, new MethodCall(method, typeArgs));
    }

    var args = new Object[3 + argumentNodes.length];
    args[0] = receiver;
    args[1] = owner;
    args[2] = typeArgs;

    try {
      for (var i = 0; i < argumentNodes.length; i++) {
        args[3 + i] = argumentNodes[i].executeGeneric(frame);
      }
    } finally {
      if (methodSlot > -1) {
        frame.setObject(methodSlot, prevMethod);
      }
    }

    return args;
  }

  public record MethodCall(@Nullable Method method, VmTypeArgument @Nullable [] typeArguments) {}
}
