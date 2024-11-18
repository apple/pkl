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

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.PklNode;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.util.Nullable;

public abstract class UnresolvedClassMemberNode extends PklNode {
  protected final SourceSection headerSection;
  protected final VmLanguage language;
  protected final FrameDescriptor descriptor;
  protected final @Nullable SourceSection docComment;
  protected final @Children ExpressionNode[] annotationNodes;
  protected final int modifiers;
  protected final Identifier name;
  protected final String qualifiedName;

  public UnresolvedClassMemberNode(
      VmLanguage language,
      SourceSection sourceSection,
      SourceSection headerSection,
      FrameDescriptor descriptor,
      @Nullable SourceSection docComment,
      ExpressionNode[] annotationNodes,
      int modifiers,
      Identifier name,
      String qualifiedName) {

    super(sourceSection);
    this.headerSection = headerSection;
    this.language = language;
    this.descriptor = descriptor;
    this.docComment = docComment;
    this.annotationNodes = annotationNodes;
    this.modifiers = modifiers;
    this.name = name;
    this.qualifiedName = qualifiedName;
  }

  public abstract ClassMember execute(VirtualFrame frame, VmClass clazz);

  public String getQualifiedName() {
    return qualifiedName;
  }
}
