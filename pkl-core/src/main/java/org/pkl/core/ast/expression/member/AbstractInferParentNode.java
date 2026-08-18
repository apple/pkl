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
package org.pkl.core.ast.expression.member;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.TypeNode.UnknownTypeNode;
import org.pkl.core.runtime.VmDynamic;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmUtils;

public abstract class AbstractInferParentNode extends ExpressionNode {
  private final VmLanguage language;
  private final boolean defaultToDynamic;
  @CompilationFinal private @Nullable Object inferredParent;

  public AbstractInferParentNode(
      SourceSection sourceSection, VmLanguage language, boolean defaultToDynamic) {
    super(sourceSection);
    this.language = language;
    this.defaultToDynamic = defaultToDynamic;
  }

  public record TypeInfo(
      @Nullable TypeNode typeNode, SourceSection headerSection, String qualifiedName) {}

  protected abstract TypeInfo getTypeInfo(VirtualFrame frame);

  protected void onInfer() {}

  @Override
  public final Object executeGeneric(VirtualFrame frame) {
    if (inferredParent != null) return inferredParent;

    // remaining code only runs first time this node is executed
    // (assuming evaluation isn't continued despite errors)
    // except when the parent type is a non-final self type (not cacheable)

    CompilerDirectives.transferToInterpreterAndInvalidate();

    var typeInfo = getTypeInfo(frame);
    var typeNode = typeInfo.typeNode();
    if (typeNode == null || typeNode instanceof UnknownTypeNode) {
      if (defaultToDynamic) {
        inferredParent = VmDynamic.empty();
        onInfer();
        return inferredParent;
      }
      throw exceptionBuilder().evalError("cannotInferParent").build();
    }

    var defaultValue =
        typeNode.createDefaultValue(
            frame, language, typeInfo.headerSection(), typeInfo.qualifiedName());

    if (defaultValue == null) {
      // try to produce a more specific error message than "cannotInstantiateType"
      var clazz = typeNode.getVmClass();
      if (clazz != null) VmUtils.checkIsInstantiable(clazz, typeNode);

      throw exceptionBuilder()
          .evalError("cannotInstantiateType", typeNode.getSourceSection().getCharacters())
          .build();
    }

    if (typeNode.isFinalType()) {
      inferredParent = defaultValue;
      onInfer();
    }

    return defaultValue;
  }
}
