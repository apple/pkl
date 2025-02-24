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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.SourceSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;
import org.pkl.core.Member.SourceLocation;
import org.pkl.core.PObject;
import org.pkl.core.TypeAlias;
import org.pkl.core.TypeParameter;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.TypeNode.ConstrainedTypeNode;
import org.pkl.core.ast.type.TypeNode.TypeVariableNode;
import org.pkl.core.ast.type.TypeNode.UnknownTypeNode;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;

public final class VmTypeAlias extends VmValue {
  private final SourceSection sourceSection;
  private final SourceSection headerSection;
  private final SourceSection @Nullable [] docComment;
  private final int modifiers;
  private final List<VmTyped> annotations;
  private final String simpleName;
  private final VmTyped module;
  private final String qualifiedName;
  private final List<TypeParameter> typeParameters;
  private final MaterializedFrame enclosingFrame;

  @LateInit private TypeNode typeNode;

  @LateInit
  @GuardedBy("pTypeAliasLock")
  private TypeAlias __pTypeAlias;

  private final Object pTypeAliasLock = new Object();

  @LateInit
  @GuardedBy("mirrorLock")
  private VmTyped __mirror;

  private final Object mirrorLock = new Object();

  public VmTypeAlias(
      SourceSection sourceSection,
      SourceSection headerSection,
      SourceSection @Nullable [] docComment,
      int modifiers,
      List<VmTyped> annotations,
      String simpleName,
      VmTyped module,
      String qualifiedName,
      List<TypeParameter> typeParameters,
      MaterializedFrame enclosingFrame) {
    this.sourceSection = sourceSection;
    this.headerSection = headerSection;
    this.docComment = docComment;
    this.modifiers = modifiers;
    this.annotations = annotations;
    this.simpleName = simpleName;
    this.module = module;
    this.qualifiedName = qualifiedName;
    this.typeParameters = typeParameters;
    this.enclosingFrame = enclosingFrame;
  }

  public void initTypeCheckNode(TypeNode typeNode) {
    assert this.typeNode == null;
    this.typeNode = typeNode;
  }

  public SourceSection getHeaderSection() {
    return headerSection;
  }

  /**
   * Assuming a type alias of the form `typealias X = Y(constraint)`, returns the source section of
   * `Y`.
   */
  @TruffleBoundary
  public SourceSection getBaseTypeSection() {
    if (typeNode instanceof ConstrainedTypeNode constrainedTypeNode) {
      return constrainedTypeNode.getBaseTypeSection();
    }

    throw new VmExceptionBuilder()
        .bug("Not a type alias of the form `typealias X = Y(constraint)`.")
        .withSourceSection(typeNode.getSourceSection())
        .build();
  }

  /**
   * Assuming a type alias of the form `typealias X = Y(constraint)`, returns the source section of
   * `constraint`.
   */
  @TruffleBoundary
  public SourceSection getConstraintSection() {
    if (typeNode instanceof ConstrainedTypeNode) {
      return ((ConstrainedTypeNode) typeNode).getFirstConstraintSection();
    }

    throw new VmExceptionBuilder()
        .bug("Not a type alias of the form `typealias X = Y(constraint)`.")
        .withSourceSection(typeNode.getSourceSection())
        .build();
  }

  public boolean isInitialized() {
    return typeNode != null;
  }

  public SourceSection @Nullable [] getDocComment() {
    return docComment;
  }

  public List<VmTyped> getAnnotations() {
    return annotations;
  }

  public String getModuleName() {
    return module.getVmClass().getModuleName();
  }

  public VmTyped getModuleMirror() {
    return module.getModuleInfo().getMirror(module);
  }

  public String getSimpleName() {
    return simpleName;
  }

  public String getQualifiedName() {
    return qualifiedName;
  }

  public int getTypeParameterCount() {
    return typeParameters.size();
  }

  public TypeNode getTypeNode() {
    return typeNode;
  }

  public Frame getEnclosingFrame() {
    return enclosingFrame;
  }

  @TruffleBoundary
  public TypeNode instantiate(TypeNode[] typeArgumentNodes) {
    // Cloning the type node means that the entire type check remains within a single root node,
    // which should be good for interpreted and compiled performance alike:
    // * Fewer root nodes to call
    // * ControlFlowException used to implement union types doesn't escape root node
    var clone = (TypeNode) typeNode.deepCopy();

    if (typeParameters.isEmpty()) return clone;

    clone.accept(
        node -> {
          if (node instanceof TypeVariableNode typeVarNode) {
            int index = typeVarNode.getTypeParameterIndex();
            // should not need to clone type argument node because it is not used by its original
            // root node
            node.replace(
                typeArgumentNodes.length == 0
                    ? new UnknownTypeNode(sourceSection)
                    : typeArgumentNodes[index]);
          }
          return true;
        });

    return clone;
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getTypeAliasClass();
  }

  @Override
  public void force(boolean allowUndefinedValues) {
    // do nothing
  }

  @Override
  public TypeAlias export() {
    synchronized (pTypeAliasLock) {
      if (__pTypeAlias == null) {
        var pAnnotations = new ArrayList<PObject>(annotations.size());

        __pTypeAlias =
            new TypeAlias(
                VmUtils.exportDocComment(docComment),
                new SourceLocation(headerSection.getStartLine(), sourceSection.getEndLine()),
                VmModifier.export(modifiers, true),
                pAnnotations,
                simpleName,
                getModuleName(),
                qualifiedName,
                typeParameters);

        for (var parameter : typeParameters) {
          parameter.initOwner(__pTypeAlias);
        }

        VmUtils.exportAnnotations(annotations, pAnnotations);
        __pTypeAlias.initAliasedType(TypeNode.export(typeNode));
      }

      return __pTypeAlias;
    }
  }

  public VmTyped getMirror() {
    synchronized (mirrorLock) {
      if (__mirror == null) {
        __mirror = MirrorFactories.typeAliasFactory.create(this);
      }
      return __mirror;
    }
  }

  public VmSet getModifierMirrors() {
    return VmModifier.getMirrors(modifiers, false);
  }

  public VmList getTypeParameterMirrors() {
    var builder = VmList.EMPTY.builder();
    for (var typeParameter : typeParameters) {
      builder.add(MirrorFactories.typeParameterFactory.create(typeParameter));
    }
    return builder.build();
  }

  public VmTyped getTypeMirror() {
    return typeNode.getMirror();
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitTypeAlias(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertTypeAlias(this, path);
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj;
  }

  @Override
  int computeHashCode(Set<VmValue> seenValues) {
    return qualifiedName.hashCode();
  }

  @Override
  public String toString() {
    return qualifiedName.startsWith("pkl.base#") ? simpleName : qualifiedName;
  }
}
