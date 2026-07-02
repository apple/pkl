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
package org.pkl.core.ast.type;

import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.type.UnresolvedTypeNode.TypeVariable;

public abstract sealed class TypeExpressionNode extends ExpressionNode
    permits TypeTestNode, TypeCastNode {
  @Child protected @Nullable TypeNode typeNode;
  @Child protected @Nullable UnresolvedTypeNode unresolvedTypeNode;

  protected TypeExpressionNode(SourceSection sourceSection, UnresolvedTypeNode unresolvedTypeNode) {
    super(sourceSection);
    this.unresolvedTypeNode = unresolvedTypeNode;
  }

  /** Inserts the resolved type node for this expression. */
  public final void insertTypeNode(TypeNode typeNode) {
    assert this.typeNode == null;
    // calls through to `transferToInterpreterAndInvalidate`
    insert(typeNode);
    this.typeNode = typeNode;
    unresolvedTypeNode = null;
  }

  /** If the type node is a type variable, return its index. Otherwise, returns {@code -1}. */
  public final int getTypeParameterIndex() {
    assert unresolvedTypeNode != null;
    if (!(unresolvedTypeNode instanceof TypeVariable typeVariable)) return -1;
    return typeVariable.getTypeParameterIndex();
  }
}
