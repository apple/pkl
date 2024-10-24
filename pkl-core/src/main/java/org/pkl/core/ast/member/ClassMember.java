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
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.util.Nullable;

public abstract class ClassMember extends Member {
  protected final @Nullable SourceSection docComment;
  protected final List<VmTyped> annotations;
  // store prototype instead of class because the former is needed much more often
  private final VmTyped owner;

  public ClassMember(
      SourceSection sourceSection,
      SourceSection headerSection,
      int modifiers,
      Identifier name,
      String qualifiedName,
      @Nullable SourceSection docComment,
      List<VmTyped> annotations,
      VmTyped owner) {

    super(sourceSection, headerSection, modifiers, name, qualifiedName);

    this.docComment = docComment;
    this.annotations = annotations;
    this.owner = owner;
  }

  public final @Nullable SourceSection getDocComment() {
    return docComment;
  }

  public final List<VmTyped> getAnnotations() {
    return annotations;
  }

  /** Returns the prototype of the class that declares this member. */
  public final VmTyped getOwner() {
    return owner;
  }

  public final VmClass getDeclaringClass() {
    return owner.getVmClass();
  }
}
