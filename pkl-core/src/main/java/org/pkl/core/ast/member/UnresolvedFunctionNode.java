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
package org.pkl.core.ast.member;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.PklNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Nullable;

public final class UnresolvedFunctionNode extends PklNode {
  private final VmLanguage language;
  private final FrameDescriptor descriptor;
  private final Member member;
  private final int parameterCount;
  @Children private final @Nullable UnresolvedTypeNode[] unresolvedParameterTypeNodes;
  @Child private @Nullable UnresolvedTypeNode unresolvedReturnTypeNode;
  private final ExpressionNode bodyNode;

  public UnresolvedFunctionNode(
      VmLanguage language,
      FrameDescriptor descriptor,
      Member member,
      int parameterCount,
      @Nullable UnresolvedTypeNode[] unresolvedParameterTypeNodes,
      @Nullable UnresolvedTypeNode unresolvedReturnTypeNode,
      ExpressionNode bodyNode) {

    super(member.getSourceSection());

    this.language = language;
    this.descriptor = descriptor;
    this.member = member;
    this.parameterCount = parameterCount;
    this.unresolvedParameterTypeNodes = unresolvedParameterTypeNodes;
    this.unresolvedReturnTypeNode = unresolvedReturnTypeNode;
    this.bodyNode = bodyNode;
  }

  public FunctionNode execute(VirtualFrame frame) {
    CompilerAsserts.neverPartOfCompilation();

    var parameterTypeNodes =
        VmUtils.resolveParameterTypes(frame, descriptor, unresolvedParameterTypeNodes);
    var returnTypeNode =
        unresolvedReturnTypeNode != null ? unresolvedReturnTypeNode.execute(frame) : null;

    return new FunctionNode(
        language,
        descriptor,
        member,
        parameterCount,
        parameterTypeNodes,
        returnTypeNode,
        true,
        bodyNode);
  }
}
