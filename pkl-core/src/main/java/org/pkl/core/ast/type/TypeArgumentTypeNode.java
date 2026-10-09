/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.ast.type;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.PklRootNode;
import org.pkl.core.runtime.VmLanguage;

public final class TypeArgumentTypeNode extends PklRootNode {

  private final SourceSection sourceSection;
  private final String qualifiedName;
  @Child private TypeNode typeNode;

  public TypeArgumentTypeNode(
      VmLanguage language,
      FrameDescriptor frameDescriptor,
      SourceSection sourceSection,
      String qualifiedName,
      TypeNode typeNode) {
    super(language, frameDescriptor, true);
    this.sourceSection = sourceSection;
    this.qualifiedName = qualifiedName;
    this.typeNode = typeNode;
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }

  @Override
  public String getName() {
    return qualifiedName;
  }

  public TypeNode getTypeNode() {
    return typeNode;
  }

  @Override
  protected Object executeImpl(VirtualFrame frame) {
    var enclosingFrame = (MaterializedFrame) frame.getArguments()[3];
    var value = frame.getArguments()[4];
    //noinspection ReplaceNullCheck
    if (enclosingFrame == null) {
      return typeNode.execute(frame, value);
    }
    return typeNode.execute(enclosingFrame, value);
  }
}
