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
import org.pkl.core.ast.MemberNode;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.util.Nullable;

/** A {@code MemberNode} that is shared between multiple {@linkplain Member members}. */
public class SharedMemberNode extends MemberNode {
  private final SourceSection sourceSection;
  private final SourceSection headerSection;
  private final @Nullable String qualifiedName;

  public SharedMemberNode(
      SourceSection sourceSection,
      SourceSection headerSection,
      @Nullable String qualifiedName,
      @Nullable VmLanguage language,
      FrameDescriptor descriptor,
      ExpressionNode bodyNode) {

    super(language, descriptor, bodyNode);
    this.sourceSection = sourceSection;
    this.headerSection = headerSection;
    this.qualifiedName = qualifiedName;
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }

  public SourceSection getHeaderSection() {
    return headerSection;
  }

  @Override
  public @Nullable String getName() {
    return qualifiedName;
  }

  @Override
  protected Object executeImpl(VirtualFrame frame) {
    return bodyNode.executeGeneric(frame);
  }
}
