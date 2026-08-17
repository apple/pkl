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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.expression.binary.LetExprNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmUtils;

public final class InferParentWithinLetBindingNode extends ExpressionNode {
  private final VmLanguage language;
  @CompilationFinal private @Nullable Object inferredParent;

  public InferParentWithinLetBindingNode(SourceSection sourceSection, VmLanguage language) {
    super(sourceSection);
    this.language = language;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    if (inferredParent != null) return inferredParent;

    // remaining code only runs first time this node is executed
    // (assuming evaluation isn't continued despite errors)
    // except when binding type is a non-final self type (not cacheable)

    CompilerDirectives.transferToInterpreterAndInvalidate();

    Node child = this;
    LetExprNode letNode = null;
    for (var node = getParent(); node != null; node = node.getParent()) {
      if (node instanceof LetExprNode let && let.getBindingNode() == child) {
        letNode = let;
        break;
      }
      child = node;
    }
    assert letNode != null
        : "AstBuilder created an InferParentWithinLetBindingNode outside of a let binding";

    // >> Keep in sync with GetParentForTypeNode.executeGeneric

    var typeNode = letNode.getTypeNode(frame);
    var defaultValue =
        typeNode.createDefaultValue(
            frame, language, letNode.getSourceSection(), letNode.getQualifiedName());

    if (defaultValue == null) {
      // try to produce a more specific error message than "cannotInstantiateType"
      var clazz = typeNode.getVmClass();
      if (clazz != null) VmUtils.checkIsInstantiable(clazz, typeNode);

      // fallback in case typeNode is synthesized (e.g. when LetExprNode.slot == -1):
      // use the TypeNode's exported PType's string representation
      var typeSourceSection = typeNode.getSourceSection();
      var typeName =
          typeSourceSection.isAvailable()
              ? typeSourceSection.getCharacters()
              : TypeNode.export(typeNode).toString();

      throw exceptionBuilder().evalError("cannotInstantiateType", typeName).build();
    }

    // can't cache default value for non-final `module`/`this` types because they're a self-types
    // (the default value changes when inherited).
    if (typeNode.isFinalType()) {
      inferredParent = defaultValue;
    }
    return defaultValue;
  }
}
