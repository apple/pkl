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
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.Nullable;

// IDEA: don't materialize frames when all members are constants
@NodeChild(value = "parentNode", type = ExpressionNode.class)
public abstract class ObjectLiteralNode extends ExpressionNode {
  protected final VmLanguage language;
  protected final String qualifiedScopeName;
  protected final boolean isCustomThisScope;
  protected final @Nullable FrameDescriptor parametersDescriptor;
  @Children protected final UnresolvedTypeNode[] parameterTypes;

  public ObjectLiteralNode(
      SourceSection sourceSection,
      VmLanguage language,
      String qualifiedScopeName,
      boolean isCustomThisScope,
      @Nullable FrameDescriptor parametersDescriptor,
      UnresolvedTypeNode[] parameterTypes) {

    super(sourceSection);
    this.language = language;
    this.qualifiedScopeName = qualifiedScopeName;
    this.isCustomThisScope = isCustomThisScope;
    this.parametersDescriptor = parametersDescriptor;
    this.parameterTypes = parameterTypes;
  }

  protected abstract ExpressionNode getParentNode();

  protected abstract Object executeWithParent(VirtualFrame frame, Object parent);

  protected abstract ObjectLiteralNode copy(ExpressionNode newParentNode);

  protected final AmendFunctionNode createAmendFunctionNode(VirtualFrame frame) {
    var resolvedParameterTypes =
        parametersDescriptor == null
            ? new TypeNode[0]
            : VmUtils.resolveParameterTypes(frame, parametersDescriptor, parameterTypes);
    return new AmendFunctionNode(this, resolvedParameterTypes);
  }

  @Idempotent
  protected static boolean isTypedObjectClass(VmClass clazz) {
    return !(clazz.isListingClass() || clazz.isMappingClass() || clazz.isDynamicClass());
  }

  protected final boolean checkIsValidFunctionAmendment(VmFunction parent) {
    var length = parameterTypes.length;
    if (length > 0 && length != parent.getParameterCount()) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("wrongFunctionAmendmentParameterCount", length, parent.getParameterCount())
          .withSourceSection(getParentNode().getSourceSection())
          .build();
    }
    return true;
  }
}
