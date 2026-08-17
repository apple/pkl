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
package org.pkl.core.ast.type;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.expression.member.AbstractInferParentNode;
import org.pkl.core.runtime.*;

/** Resolves `<type>` to the type's default value in `new <type> { ... }`. */
public final class GetParentForTypeNode extends AbstractInferParentNode {
  @Child private @Nullable UnresolvedTypeNode unresolvedTypeNode;
  @Child private @Nullable TypeNode typeNode;
  private final String qualifiedName;

  public GetParentForTypeNode(
      SourceSection sourceSection,
      VmLanguage language,
      UnresolvedTypeNode unresolvedTypeNode,
      String qualifiedName) {
    super(sourceSection, language, false);
    this.unresolvedTypeNode = unresolvedTypeNode;
    this.qualifiedName = qualifiedName;
  }

  @Override
  protected TypeInfo getTypeInfo(VirtualFrame frame) {
    if (typeNode == null) {
      assert unresolvedTypeNode != null;
      CompilerDirectives.transferToInterpreterAndInvalidate();
      typeNode = unresolvedTypeNode.execute(frame);
      adoptChildren();
    }

    return new TypeInfo(typeNode, sourceSection, qualifiedName);
  }
}
