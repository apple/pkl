/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.LateInit;

/** Resolves `<type>` to the type's default value in `new <type> { ... }`. */
public final class GetParentForTypeNode extends ExpressionNode {
  @Child private UnresolvedTypeNode unresolvedTypeNode;
  private final String qualifiedName;

  @CompilationFinal @LateInit Object defaultValue;

  public GetParentForTypeNode(
      SourceSection sourceSection, UnresolvedTypeNode unresolvedTypeNode, String qualifiedName) {
    super(sourceSection);
    this.unresolvedTypeNode = unresolvedTypeNode;
    this.qualifiedName = qualifiedName;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    if (defaultValue != null) return defaultValue;

    CompilerDirectives.transferToInterpreterAndInvalidate();

    var typeNode = unresolvedTypeNode.execute(frame);
    defaultValue = typeNode.createDefaultValue(VmLanguage.get(this), sourceSection, qualifiedName);

    if (defaultValue != null) {
      unresolvedTypeNode = null;
      return defaultValue;
    }

    // try to produce a more specific error message than "cannotInstantiateType"
    var clazz = typeNode.getVmClass();
    if (clazz != null) VmUtils.checkIsInstantiable(clazz, typeNode);

    throw exceptionBuilder()
        .evalError("cannotInstantiateType", typeNode.getSourceSection().getCharacters())
        .build();
  }
}
