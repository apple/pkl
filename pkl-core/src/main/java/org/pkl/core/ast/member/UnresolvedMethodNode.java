/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import java.util.List;
import org.pkl.core.TypeParameter;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Nullable;

public final class UnresolvedMethodNode extends UnresolvedClassMemberNode {
  private final int parameterCount;
  private final List<TypeParameter> typeParameters;
  @Children private final @Nullable UnresolvedTypeNode[] unresolvedParameterTypeNodes;
  @Child private @Nullable UnresolvedTypeNode unresolvedReturnTypeNode;
  private final boolean isReturnTypeChecked;
  private final ExpressionNode bodyNode;

  public UnresolvedMethodNode(
      VmLanguage language,
      SourceSection sourceSection,
      SourceSection headerSection,
      FrameDescriptor descriptor,
      SourceSection @Nullable [] docComment,
      ExpressionNode[] annotationNodes,
      int modifiers,
      Identifier name,
      String qualifiedName,
      int parameterCount,
      List<TypeParameter> typeParameters,
      @Nullable UnresolvedTypeNode[] unresolvedParameterTypeNodes,
      @Nullable UnresolvedTypeNode unresolvedReturnTypeNode,
      boolean isReturnTypeChecked,
      ExpressionNode bodyNode) {

    super(
        language,
        sourceSection,
        headerSection,
        descriptor,
        docComment,
        annotationNodes,
        modifiers,
        name,
        qualifiedName);

    this.parameterCount = parameterCount;
    this.typeParameters = typeParameters;
    this.unresolvedParameterTypeNodes = unresolvedParameterTypeNodes;
    this.unresolvedReturnTypeNode = unresolvedReturnTypeNode;
    this.isReturnTypeChecked = isReturnTypeChecked;
    this.bodyNode = bodyNode;
  }

  public Identifier getName() {
    return name;
  }

  public SourceSection getHeaderSection() {
    return headerSection;
  }

  public boolean isLocal() {
    return VmModifier.isLocal(modifiers);
  }

  @Override
  public ClassMethod execute(VirtualFrame frame, VmClass clazz) {
    CompilerDirectives.transferToInterpreter();

    var annotations = VmUtils.evaluateAnnotations(frame, annotationNodes);
    var parameterTypeNodes =
        VmUtils.resolveParameterTypes(frame, descriptor, unresolvedParameterTypeNodes);
    var returnTypeNode =
        unresolvedReturnTypeNode != null ? unresolvedReturnTypeNode.execute(frame) : null;

    String deprecation = null;
    for (var annotation : annotations) {
      if (annotation.getVmClass() == BaseModule.getDeprecatedClass()) {
        var messageObj = VmUtils.readMemberOrNull(annotation, Identifier.MESSAGE);
        deprecation = messageObj instanceof String string ? string : "";
        break;
      }
    }

    ClassMethod method =
        new ClassMethod(
            sourceSection,
            headerSection,
            modifiers,
            name,
            qualifiedName,
            docComment,
            annotations,
            clazz.getPrototype(),
            typeParameters,
            deprecation);

    FunctionNode functionNode =
        new FunctionNode(
            language,
            descriptor,
            method,
            parameterCount,
            parameterTypeNodes,
            returnTypeNode,
            isReturnTypeChecked,
            bodyNode);

    method.initFunctionNode(functionNode);
    return method;
  }
}
