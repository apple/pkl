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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.type.TypeNode.ConstrainedTypeNode;
import org.pkl.core.ast.type.TypeNode.TypeAliasTypeNode;
import org.pkl.core.ast.type.TypeNode.UnionTypeNode;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.Nullable;

/**
 * Property body for properties that don't have an explicit body. Returns the default value for the
 * property's type, or throws if the type doesn't have a default value.
 */
public final class DefaultPropertyBodyNode extends ExpressionNode {
  private final Identifier propertyName;
  private final @Nullable PropertyTypeNode typeNode;

  public DefaultPropertyBodyNode(
      SourceSection sourceSection, Identifier propertyName, @Nullable PropertyTypeNode typeNode) {
    super(sourceSection);
    this.propertyName = propertyName;
    this.typeNode = typeNode;
  }

  public boolean isUndefined() {
    return typeNode == null || typeNode.getDefaultValue() == null;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    if (typeNode != null) {
      var defaultValue = typeNode.getDefaultValue();
      if (defaultValue != null) return defaultValue;
    }

    CompilerDirectives.transferToInterpreter();

    // attempt to give a hint when a union type is missing a default
    String aliasName = null;
    String unionTypeSource = null;
    if (typeNode != null) {
      var tn = typeNode.getTypeNode();
      while (true) {
        if (tn instanceof TypeAliasTypeNode typeAlias) {
          aliasName = typeAlias.getVmTypeAlias().getSimpleName();
          tn = typeAlias.getVmTypeAlias().getTypeNode();
        } else if (tn instanceof ConstrainedTypeNode constrained) {
          tn = constrained.getBaseTypeNode();
        } else {
          break;
        }
      }
      if (tn instanceof UnionTypeNode union) {
        unionTypeSource = union.getSourceSection().getCharacters().toString();
      }
    }

    throw exceptionBuilder()
        .undefinedPropertyValue(
            propertyName, VmUtils.getReceiver(frame), unionTypeSource, aliasName)
        .build();
  }
}
