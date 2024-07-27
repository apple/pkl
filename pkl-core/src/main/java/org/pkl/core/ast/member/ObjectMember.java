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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ConstantNode;
import org.pkl.core.ast.MemberNode;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmDynamic;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.Nullable;

public final class ObjectMember extends Member {
  @CompilationFinal private @Nullable Object constantValue;
  @CompilationFinal private @Nullable MemberNode memberNode;

  public ObjectMember(
      SourceSection sourceSection,
      SourceSection headerSection,
      int modifiers,
      @Nullable Identifier name,
      String qualifiedName) {

    super(sourceSection, headerSection, modifiers, name, qualifiedName);
  }

  public void initConstantValue(ConstantNode node) {
    initConstantValue(node.getValue());
  }

  public void initConstantValue(Object value) {
    assert constantValue == null;
    assert memberNode == null;

    constantValue = value;
  }

  public void initMemberNode(MemberNode node) {
    assert constantValue == null;
    assert memberNode == null;

    memberNode = node;
  }

  /**
   * Tells if this member is a property.
   *
   * <p>Not named `isProperty()` to work around <a
   * href="https://bugs.openjdk.java.net/browse/JDK-8185424">JDK-8185424</a> (which is apparently
   * triggered by `-Xdoclint:none`).
   */
  public boolean isProp() {
    return name != null;
  }

  /** Tells if this member is an element. */
  public boolean isElement() {
    return VmModifier.isElement(modifiers);
  }

  /**
   * Tells if this member is an entry. Note that this returns true if an existing element is
   * overridden with entry syntax (e.g., `[3] = ...`).
   */
  public boolean isEntry() {
    return VmModifier.isEntry(modifiers);
  }

  public @Nullable Object getConstantValue() {
    return constantValue;
  }

  public @Nullable MemberNode getMemberNode() {
    return memberNode;
  }

  public RootCallTarget getCallTarget() {
    assert constantValue == null : "Must not call getCallTarget() if constantValue is non-null.";

    assert getMemberNode() != null
        : "Either constantValue or memberNode must be set, but both are null.";

    var callTarget = getMemberNode().getCallTarget();
    assert callTarget != null;
    return callTarget;
  }

  public boolean isUndefined() {
    return getMemberNode() != null && getMemberNode().isUndefined();
  }

  public @Nullable Object getLocalPropertyDefaultValue() {
    assert isProp() && isLocal();
    return getMemberNode() instanceof LocalTypedPropertyNode propertyNode
        ? propertyNode.getDefaultValue()
        : VmDynamic.empty();
  }

  @Override
  public @Nullable String getCallSignature() {
    return name != null ? name.toString() : null;
  }

  public SourceSection getBodySection() {
    if (getMemberNode() != null) {
      return getMemberNode().getBodySection();
    }

    var source = sourceSection.getSource();
    var start = headerSection.getCharEndIndex();
    var offset = start - sourceSection.getCharIndex();
    var candidate = source.createSection(start, sourceSection.getCharLength() - offset);
    if (candidate.getCharLength() == 0) {
      // TODO: return null or candidate?
      return VmUtils.unavailableSourceSection();
    }

    var skip = 0;
    var text = candidate.getCharacters();
    var ch = text.charAt(skip);
    // body section of entries needs to chomp the ending delimiter too.
    if (isEntry()) {
      while (ch == ']' || ch == '=' || Character.isWhitespace(ch)) {
        ch = text.charAt(++skip);
      }
    } else {
      while (ch == '=' || Character.isWhitespace(ch)) {
        ch = text.charAt(++skip);
      }
    }
    return source.createSection(candidate.getCharIndex() + skip, candidate.getCharLength() - skip);
  }

  // sometimes used as key in VmObject.setCachedValue()
  @Override
  public boolean equals(@Nullable Object obj) {
    return this == obj;
  }

  // sometimes used as key in VmObject.setCachedValue()
  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }
}
