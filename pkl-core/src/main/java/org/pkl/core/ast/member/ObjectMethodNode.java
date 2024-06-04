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
package org.pkl.core.ast.member;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;

public final class ObjectMethodNode extends RegularMemberNode {
  private final VmLanguage language;
  private final int parameterCount;
  @Children private final @Nullable UnresolvedTypeNode[] unresolvedParameterTypeNodes;
  @Child private @Nullable UnresolvedTypeNode unresolvedReturnTypeNode;

  @CompilationFinal @LateInit private FunctionNode functionNode;

  public ObjectMethodNode(
      VmLanguage language,
      FrameDescriptor descriptor,
      ObjectMember member,
      ExpressionNode bodyNode,
      int parameterCount,
      @Nullable UnresolvedTypeNode[] unresolvedParameterTypeNodes,
      @Nullable UnresolvedTypeNode unresolvedReturnTypeNode) {

    super(language, descriptor, member, bodyNode);

    this.language = language;
    this.parameterCount = parameterCount;
    this.unresolvedParameterTypeNodes = unresolvedParameterTypeNodes;
    this.unresolvedReturnTypeNode = unresolvedReturnTypeNode;
  }

  public @Nullable TypeNode getReturnTypeNode() {
    // this method is only called from child nodes
    assert functionNode != null;
    return functionNode.getReturnTypeNode();
  }

  @Override
  public CallTarget execute(VirtualFrame frame) {
    if (functionNode == null) {
      CompilerDirectives.transferToInterpreter();

      var parameterTypeNodes =
          VmUtils.resolveParameterTypes(frame, getFrameDescriptor(), unresolvedParameterTypeNodes);

      var returnTypeNode =
          unresolvedReturnTypeNode != null ? unresolvedReturnTypeNode.execute(frame) : null;

      functionNode =
          new FunctionNode(
              language,
              getFrameDescriptor(),
              member,
              parameterCount,
              parameterTypeNodes,
              returnTypeNode,
              true,
              bodyNode);
    }

    return functionNode.getCallTarget();
  }
}
