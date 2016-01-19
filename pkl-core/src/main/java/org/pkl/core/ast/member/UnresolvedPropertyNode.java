/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Nullable;

public final class UnresolvedPropertyNode extends UnresolvedClassMemberNode {
  private final SourceSection propertyNameSection;
  private @Child @Nullable UnresolvedTypeNode unresolvedTypeNode;
  private final @Nullable ExpressionNode bodyNode;

  public UnresolvedPropertyNode(
      VmLanguage language,
      SourceSection sourceSection,
      SourceSection headerSection,
      SourceSection propertyNameSection,
      FrameDescriptor descriptor,
      @Nullable SourceSection docComment,
      ExpressionNode[] annotationNodes,
      int modifiers,
      Identifier name,
      String qualifiedName,
      @Nullable UnresolvedTypeNode unresolvedTypeNode,
      @Nullable ExpressionNode bodyNode) {

    super(
        language,
        sourceSection,
        headerSection,
        descriptor,
        docComment,
        annotationNodes,
        modifiers,
        name,
        qualifiedName);
    this.propertyNameSection = propertyNameSection;
    this.unresolvedTypeNode = unresolvedTypeNode;
    this.bodyNode = bodyNode;
  }

  public Identifier getName() {
    return name;
  }

  public SourceSection getHeaderSection() {
    return headerSection;
  }

  public boolean isLocal() {
    return VmModifier.isLocal(modifiers);
  }

  public boolean isClass() {
    return VmModifier.isClass(modifiers);
  }

  public boolean isTypeAlias() {
    return VmModifier.isTypeAlias(modifiers);
  }

  public boolean isImport() {
    return VmModifier.isImport(modifiers);
  }

  private void checkOverride(VmClass clazz) {
    var superClass = clazz.getSuperclass();
    if (superClass == null) {
      return;
    }
    var superProperty = superClass.getProperty(name);
    if (superProperty == null) {
      return;
    }
    var isFixed = VmModifier.isFixed(modifiers);
    if (superProperty.isFixed() == isFixed) {
      return;
    }
    CompilerDirectives.transferToInterpreter();
    if (superProperty.isFixed()) {
      throw exceptionBuilder()
          .withSourceSection(headerSection)
          .evalError(
              "missingFixedModifier",
              name,
              superClass.getQualifiedName(),
              sourceSection.getCharacters())
          .build();
    }
    var source = headerSection.getCharacters().toString();
    var fixedModifierIdx = source.indexOf("fixed");
    throw exceptionBuilder()
        .withSourceSection(
            headerSection
                .getSource()
                .createSection(headerSection.getCharIndex() + fixedModifierIdx, 5))
        .evalError("cannotApplyFixedModifier", name, superClass.getQualifiedName())
        .build();
  }

  private void checkConst(VmClass clazz) {
    var superClass = clazz.getSuperclass();
    if (superClass == null) {
      return;
    }
    var superProperty = superClass.getProperty(name);
    if (superProperty == null) {
      return;
    }
    var isConst = VmModifier.isConst(modifiers);
    if (superProperty.isConst() == isConst) {
      return;
    }
    CompilerDirectives.transferToInterpreter();

    if (superProperty.isConst()) {
      throw exceptionBuilder()
          .withSourceSection(headerSection)
          .evalError(
              "missingConstModifier",
              name,
              superClass.getQualifiedName(),
              sourceSection.getCharacters())
          .build();
    }
    var source = headerSection.getCharacters().toString();
    var constModifierIdx = source.indexOf("const");
    throw exceptionBuilder()
        .withSourceSection(
            headerSection
                .getSource()
                .createSection(headerSection.getCharIndex() + constModifierIdx, 5))
        .evalError("cannotApplyConstModifier", name, superClass.getQualifiedName())
        .build();
  }

  @Override
  public ClassProperty execute(VirtualFrame frame, VmClass clazz) {
    CompilerDirectives.transferToInterpreter();

    var annotations = VmUtils.evaluateAnnotations(frame, annotationNodes);

    var typeNode =
        unresolvedTypeNode == null
            ? null
            : new PropertyTypeNode(
                language, descriptor, qualifiedName, unresolvedTypeNode.execute(frame));

    checkOverride(clazz);
    checkConst(clazz);

    var effectiveBodyNode =
        bodyNode != null
            ? bodyNode
            :
            // use propertyNameSection as source section of implicit property default
            // to improve stack traces signaling failed type check of such a default
            new DefaultPropertyBodyNode(propertyNameSection, name, typeNode);

    var initializer =
        VmUtils.createObjectProperty(
            language,
            sourceSection,
            headerSection,
            name,
            qualifiedName,
            descriptor,
            modifiers,
            effectiveBodyNode,
            typeNode);

    return new ClassProperty(
        sourceSection,
        headerSection,
        modifiers,
        name,
        qualifiedName,
        docComment,
        annotations,
        clazz.getPrototype(),
        typeNode,
        initializer);
  }
}
