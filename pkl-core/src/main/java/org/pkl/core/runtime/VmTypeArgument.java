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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.member.FunctionNode;
import org.pkl.core.ast.type.TypeArgumentTypeNode;
import org.pkl.core.ast.type.TypeNode;

/**
 * Type argument information passed from a type argument (in a method call) as an argument to a
 * {@link FunctionNode}.
 */
public class VmTypeArgument {

  private final @Nullable MaterializedFrame enclosingFrame;
  private final TypeArgumentTypeNode rootNode;

  public VmTypeArgument(TypeArgumentTypeNode rootNode, @Nullable MaterializedFrame enclosingFrame) {
    this.enclosingFrame = enclosingFrame;
    this.rootNode = rootNode;
  }

  public @Nullable MaterializedFrame getEnclosingFrame() {
    return enclosingFrame;
  }

  public CallTarget getCallTarget() {
    return rootNode.getCallTarget();
  }

  private TypeNode getTypeNode() {
    return rootNode.getTypeNode();
  }

  public VmType reify() {
    var type = getTypeNode().getType();
    var frame = enclosingFrame != null ? enclosingFrame : VmUtils.createEmptyMaterializedFrame();
    return type.reify(frame);
  }

  public @Nullable Object createDefaultValue(
      VmLanguage language, SourceSection headerSection, String qualifiedName) {
    var frame = enclosingFrame != null ? enclosingFrame : VmUtils.createEmptyMaterializedFrame();
    return getTypeNode().createDefaultValue(frame, language, headerSection, qualifiedName);
  }
}
