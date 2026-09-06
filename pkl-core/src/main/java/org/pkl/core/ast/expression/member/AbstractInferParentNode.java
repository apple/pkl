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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.TypeNode.TypeVariableNode;
import org.pkl.core.ast.type.TypeNode.UnknownTypeNode;
import org.pkl.core.runtime.VmDynamic;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmUtils;

public abstract class AbstractInferParentNode extends ExpressionNode {

  protected final VmLanguage language;

  public AbstractInferParentNode(SourceSection sourceSection, VmLanguage language) {
    super(sourceSection);
    this.language = language;
  }

  protected Object getDefaultValue(
      VirtualFrame frame,
      @Nullable TypeNode typeNode,
      SourceSection headerSection,
      String qualifiedName) {
    if (typeNode == null || typeNode instanceof UnknownTypeNode) {
      return VmDynamic.empty();
    }

    var defaultValue = typeNode.createDefaultValue(frame, language, headerSection, qualifiedName);
    if (defaultValue != null) {
      return defaultValue;
    }

    CompilerDirectives.transferToInterpreter();

    if (typeNode instanceof TypeVariableNode) {
      throw exceptionBuilder().evalError("cannotInferParent").build();
    }

    // try to produce a more specific error message than "cannotInstantiateType"
    var clazz = typeNode.getVmClass();
    if (clazz != null) {
      VmUtils.checkIsInstantiable(clazz, typeNode);
    }

    throw exceptionBuilder()
        .evalError("cannotInstantiateType", typeNode.getSourceSection().getCharacters())
        .build();
  }
}
