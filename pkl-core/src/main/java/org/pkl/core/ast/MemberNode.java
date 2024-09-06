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
package org.pkl.core.ast;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import java.util.function.Function;
import org.pkl.core.ast.member.DefaultPropertyBodyNode;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.util.Nullable;

public abstract class MemberNode extends PklRootNode {
  @Child protected ExpressionNode bodyNode;

  protected MemberNode(
      @Nullable VmLanguage language, FrameDescriptor descriptor, ExpressionNode bodyNode) {

    super(language, descriptor);
    this.bodyNode = bodyNode;
  }

  public abstract SourceSection getHeaderSection();

  public final SourceSection getBodySection() {
    return bodyNode.getSourceSection();
  }

  public final ExpressionNode getBodyNode() {
    return bodyNode;
  }

  public final void replaceBody(Function<ExpressionNode, ExpressionNode> replacer) {
    bodyNode = insert(replacer.apply(bodyNode));
  }

  protected final Object executeBody(VirtualFrame frame) {
    return executeBody(frame, bodyNode);
  }

  protected final VmExceptionBuilder exceptionBuilder() {
    return new VmExceptionBuilder().withSourceSection(getHeaderSection());
  }

  public boolean isUndefined() {
    return bodyNode instanceof DefaultPropertyBodyNode propBodyNode && propBodyNode.isUndefined();
  }
}
