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

import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.util.Nullable;

public abstract class Member {
  protected final SourceSection sourceSection;

  protected final SourceSection headerSection;

  protected final int modifiers;

  protected final @Nullable Identifier name;

  protected final String qualifiedName;

  public Member(
      SourceSection sourceSection,
      SourceSection headerSection,
      int modifiers,
      @Nullable Identifier name,
      String qualifiedName) {

    this.sourceSection = sourceSection;
    this.headerSection = headerSection;
    this.modifiers = modifiers;
    this.name = name;
    this.qualifiedName = qualifiedName;
  }

  public final SourceSection getSourceSection() {
    return sourceSection;
  }

  public final SourceSection getHeaderSection() {
    return headerSection;
  }

  public final int getModifiers() {
    return modifiers;
  }

  /** Null for members that don't have a name, such as listing/mapping members and lambdas. */
  public @Nullable Identifier getNameOrNull() {
    return name;
  }

  public Identifier getName() {
    assert name != null;
    return name;
  }

  /** For use in user-facing messages. May contain placeholders for computed name parts. */
  public final String getQualifiedName() {
    return qualifiedName;
  }

  /** For use in user-facing messages. Non-null iff getName() is non-null. */
  public abstract @Nullable String getCallSignature();

  public final boolean isLocal() {
    return VmModifier.isLocal(modifiers);
  }

  public final boolean isConst() {
    return VmModifier.isConst(modifiers);
  }

  public final boolean isFixed() {
    return VmModifier.isFixed(modifiers);
  }

  public final boolean isHidden() {
    return VmModifier.isHidden(modifiers);
  }

  public final boolean isExternal() {
    return VmModifier.isExternal(modifiers);
  }

  public final boolean isClass() {
    return VmModifier.isClass(modifiers);
  }

  public final boolean isTypeAlias() {
    return VmModifier.isTypeAlias(modifiers);
  }

  public final boolean isImport() {
    return VmModifier.isImport(modifiers);
  }

  public final boolean isGlob() {
    return VmModifier.isGlob(modifiers);
  }

  public final boolean isAbstract() {
    return VmModifier.isAbstract(modifiers);
  }

  public final boolean isType() {
    return VmModifier.isType(modifiers);
  }

  public final boolean isDelete() {
    return VmModifier.isDelete(modifiers);
  }

  public final boolean isLocalOrExternalOrHidden() {
    return VmModifier.isLocalOrExternalOrHidden(modifiers);
  }

  public final boolean isConstOrFixed() {
    return VmModifier.isConstOrFixed(modifiers);
  }

  public final boolean isLocalOrExternalOrAbstractOrDelete() {
    return VmModifier.isLocalOrExternalOrAbstractOrDelete(modifiers);
  }
}
