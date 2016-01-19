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
package org.pkl.core.ast.member;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import java.util.ArrayList;
import java.util.List;
import org.pkl.core.TypeParameter;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.VmTypeAlias;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.Nullable;

public final class TypeAliasNode extends ExpressionNode {
  private final SourceSection headerSection;
  private final @Nullable SourceSection docComment;
  @Children private final ExpressionNode[] annotationNodes;
  private final int modifiers;
  private final String simpleName;
  private final String qualifiedName;
  private final List<TypeParameter> typeParameters;
  private @Child UnresolvedTypeNode typeAnnotationNode;

  // use same caching scheme as ClassNode
  @CompilationFinal private @Nullable VmTypeAlias cachedTypeAlias;

  public TypeAliasNode(
      SourceSection sourceSection,
      SourceSection headerSection,
      @Nullable SourceSection docComment,
      ExpressionNode[] annotationNodes,
      int modifiers,
      String simpleName,
      String qualifiedName,
      List<TypeParameter> typeParameters,
      UnresolvedTypeNode typeAnnotationNode) {
    super(sourceSection);
    this.headerSection = headerSection;
    this.docComment = docComment;
    this.annotationNodes = annotationNodes;
    this.modifiers = modifiers;
    this.simpleName = simpleName;
    this.qualifiedName = qualifiedName;
    this.typeParameters = typeParameters;
    this.typeAnnotationNode = typeAnnotationNode;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    if (cachedTypeAlias != null) return cachedTypeAlias;

    CompilerDirectives.transferToInterpreter();

    var annotations = new ArrayList<VmTyped>();
    var module = VmUtils.getTypedObjectReceiver(frame);

    cachedTypeAlias =
        new VmTypeAlias(
            getSourceSection(),
            headerSection,
            docComment,
            modifiers,
            annotations,
            simpleName,
            module,
            qualifiedName,
            typeParameters);

    VmUtils.evaluateAnnotations(frame, annotationNodes, annotations);
    cachedTypeAlias.initTypeCheckNode(typeAnnotationNode.execute(frame));

    return cachedTypeAlias;
  }
}
