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
package org.pkl.core;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.pkl.core.util.Nullable;

/** Common base class for TypeAlias, PClass, PClass.Property, and PClass.Method. */
public abstract class Member implements Serializable {
  @Serial private static final long serialVersionUID = 0L;

  private final @Nullable String docComment;
  private final SourceLocation sourceLocation;
  private final Set<Modifier> modifiers;
  private final List<PObject> annotations;
  private final String simpleName;

  public Member(
      @Nullable String docComment,
      SourceLocation sourceLocation,
      Set<Modifier> modifiers,
      List<PObject> annotations,
      String simpleName) {
    this.docComment = docComment;
    this.sourceLocation = Objects.requireNonNull(sourceLocation, "sourceLocation");
    this.modifiers = Objects.requireNonNull(modifiers, "modifiers");
    this.annotations = Objects.requireNonNull(annotations, "annotations");
    this.simpleName = Objects.requireNonNull(simpleName, "simpleName");
  }

  /** Returns the name of the module that this member is declared in. */
  public abstract String getModuleName();

  /** Returns the documentation comment of this member. */
  public @Nullable String getDocComment() {
    return docComment;
  }

  /** Returns the source location, such as start and end line, of this member. */
  public SourceLocation getSourceLocation() {
    return sourceLocation;
  }

  /** Returns the modifiers of this member. */
  public Set<Modifier> getModifiers() {
    return modifiers;
  }

  /** Returns the annotations of this member. */
  public List<PObject> getAnnotations() {
    return annotations;
  }

  /** Tells if this member has an {@code external} modifier. */
  public boolean isExternal() {
    return modifiers.contains(Modifier.EXTERNAL);
  }

  /** Tells if this member has an {@code abstract} modifier. */
  public boolean isAbstract() {
    return modifiers.contains(Modifier.ABSTRACT);
  }

  /** Tells if this member has a {@code hidden} modifier. */
  public boolean isHidden() {
    return modifiers.contains(Modifier.HIDDEN);
  }

  /** Tells if this member has an {@code open} modifier. */
  public boolean isOpen() {
    return modifiers.contains(Modifier.OPEN);
  }

  /** Tells if this member is defined in Pkl's standard library. */
  public final boolean isStandardLibraryMember() {
    return getModuleName().startsWith("pkl.");
  }

  /** Returns the unqualified name of this member. */
  public String getSimpleName() {
    return simpleName;
  }

  public record SourceLocation(int startLine, int endLine) implements Serializable {
    @Serial private static final long serialVersionUID = 0L;

    /**
     * @deprecated As of 0.28.0, replaced by {@link #startLine()}.
     */
    @Deprecated(forRemoval = true)
    public int getStartLine() {
      return startLine;
    }

    /**
     * @deprecated As of 0.28.0, replaced by {@link #endLine()}.
     */
    @Deprecated(forRemoval = true)
    public int getEndLine() {
      return endLine;
    }
  }
}
