/*
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

import com.oracle.truffle.api.source.SourceSection;
import java.util.List;
import org.pkl.core.Member.SourceLocation;
import org.pkl.core.PClass;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Nullable;

public final class ClassProperty extends ClassMember {
  private final @Nullable PropertyTypeNode typeNode;
  private final ObjectMember initializer;

  public ClassProperty(
      SourceSection sourceSection,
      SourceSection headerSection,
      int modifiers,
      Identifier name,
      String qualifiedName,
      @Nullable SourceSection docComment,
      List<VmTyped> annotations,
      VmTyped owner,
      @Nullable PropertyTypeNode typeNode,
      ObjectMember initializer) {

    super(
        sourceSection,
        headerSection,
        modifiers,
        name,
        qualifiedName,
        docComment,
        annotations,
        owner);

    this.typeNode = typeNode;
    this.initializer = initializer;
  }

  public @Nullable PropertyTypeNode getTypeNode() {
    return typeNode;
  }

  public ObjectMember getInitializer() {
    return initializer;
  }

  @Override
  public String getCallSignature() {
    assert name != null;
    return name.toString();
  }

  public VmTyped getMirror() {
    return MirrorFactories.propertyFactory.create(this);
  }

  public VmSet getModifierMirrors() {
    return VmModifier.getMirrors(modifiers, false);
  }

  public VmTyped getTypeMirror() {
    return PropertyTypeNode.getMirror(typeNode);
  }

  public PClass.Property export(PClass owner) {
    assert name != null;
    return new PClass.Property(
        owner,
        VmUtils.exportDocComment(docComment),
        new SourceLocation(getHeaderSection().getStartLine(), sourceSection.getEndLine()),
        VmModifier.export(modifiers, false),
        VmUtils.exportAnnotations(annotations),
        name.toString(),
        PropertyTypeNode.export(typeNode));
  }
}
