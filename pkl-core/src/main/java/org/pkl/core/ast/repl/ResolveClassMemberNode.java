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
package org.pkl.core.ast.repl;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.PklRootNode;
import org.pkl.core.ast.member.UnresolvedClassMemberNode;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmLanguage;

public final class ResolveClassMemberNode extends PklRootNode {
  @Child private UnresolvedClassMemberNode unresolvedNode;
  private final VmClass clazz;

  public ResolveClassMemberNode(
      VmLanguage language,
      FrameDescriptor descriptor,
      UnresolvedClassMemberNode unresolvedNode,
      VmClass clazz) {

    super(language, descriptor);
    this.clazz = clazz;
    this.unresolvedNode = unresolvedNode;
  }

  @Override
  public SourceSection getSourceSection() {
    return unresolvedNode.getSourceSection();
  }

  @Override
  public String getName() {
    return unresolvedNode.getQualifiedName();
  }

  @Override
  protected Object executeImpl(VirtualFrame frame) {
    return unresolvedNode.execute(frame, clazz);
  }
}
