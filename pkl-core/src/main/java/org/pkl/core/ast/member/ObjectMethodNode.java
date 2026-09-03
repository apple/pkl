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
package org.pkl.core.ast.member;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.*;

public final class ObjectMethodNode extends RegularMemberNode {
  private final VmLanguage language;
  private final int parameterCount;
  private final List<VmTypeParameter> typeParameters;
  @Children private final @Nullable UnresolvedTypeNode[] unresolvedParameterTypeNodes;
  @Child private @Nullable UnresolvedTypeNode unresolvedReturnTypeNode;

  @CompilationFinal private @Nullable FunctionNode functionNode;

  public ObjectMethodNode(
      VmLanguage language,
      FrameDescriptor descriptor,
      ObjectMember member,
      ExpressionNode bodyNode,
      int parameterCount,
      List<VmTypeParameter> typeParameters,
      @Nullable UnresolvedTypeNode[] unresolvedParameterTypeNodes,
      @Nullable UnresolvedTypeNode unresolvedReturnTypeNode) {
    super(language, descriptor, member, bodyNode);

    this.language = language;
    this.parameterCount = parameterCount;
    this.typeParameters = typeParameters;
    this.unresolvedParameterTypeNodes = unresolvedParameterTypeNodes;
    this.unresolvedReturnTypeNode = unresolvedReturnTypeNode;
  }

  public ObjectMethod reify(VmObjectLike owner) {
    var newFrame =
        Truffle.getRuntime().createVirtualFrame(new Object[] {owner, owner}, getFrameDescriptor());
    return new ObjectMethod(
        getFunctionNode(newFrame, true), getSourceSection(), getQualifiedName());
  }

  public static class ObjectMethod implements Method {
    private final FunctionNode functionNode;
    private final SourceSection headerSection;
    private final String qualifiedName;

    private ObjectMethod(
        FunctionNode functionNode, SourceSection headerSection, String qualifiedName) {
      this.functionNode = functionNode;
      this.headerSection = headerSection;
      this.qualifiedName = qualifiedName;
    }

    @Override
    public FunctionNode getFunctionNode(@Nullable SourceSection callSite) {
      return functionNode;
    }

    @Override
    public SourceSection getHeaderSection() {
      return headerSection;
    }

    @Override
    public String getQualifiedName() {
      return qualifiedName;
    }

    @Override
    public boolean isChildOf(Method other) {
      return this == other;
    }
  }

  @Override
  protected CallTarget executeImpl(VirtualFrame frame) {
    return getFunctionNode(frame, false).getCallTarget();
  }

  private FunctionNode getFunctionNode(VirtualFrame frame, boolean isPreInit) {
    if (functionNode != null) return functionNode;
    CompilerDirectives.transferToInterpreterAndInvalidate();

    if (isPreInit) {
      // TODO: with VmType/CreateDefaultValueNode this may be removable
      adoptChildren();
    }
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
            typeParameters.size(),
            bodyNode);
    return functionNode;
  }
}
