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
import org.pkl.core.ast.member.Member;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.Nullable;

public abstract class MemberNode extends PklRootNode {
  protected final Member member;
  @Child protected ExpressionNode bodyNode;

  protected MemberNode(
      @Nullable VmLanguage language,
      FrameDescriptor descriptor,
      Member member,
      ExpressionNode bodyNode) {

    super(language, descriptor);
    this.member = member;
    this.bodyNode = bodyNode;
  }

  @Override
  public final SourceSection getSourceSection() {
    return member.getSourceSection();
  }

  public final SourceSection getHeaderSection() {
    return member.getHeaderSection();
  }

  public final SourceSection getBodySection() {
    return bodyNode.getSourceSection();
  }

  public final ExpressionNode getBodyNode() {
    return bodyNode;
  }

  @Override
  public final String getName() {
    return member.getQualifiedName();
  }

  public final void replaceBody(Function<ExpressionNode, ExpressionNode> replacer) {
    bodyNode = insert(replacer.apply(bodyNode));
  }

  protected final Object executeBody(VirtualFrame frame) {
    return executeBody(frame, bodyNode);
  }

  protected final VmExceptionBuilder exceptionBuilder() {
    return new VmExceptionBuilder().withSourceSection(member.getHeaderSection());
  }

  /**
   * If true, the property value computed by this node is not the final value exposed to user code
   * but will still be amended.
   *
   * <p>Used to disable type check for to-be-amended properties. See {@link
   * org.pkl.core.runtime.VmUtils#SKIP_TYPECHECK_MARKER}. IDEA: might be more appropriate to only
   * skip constraints check
   */
  protected final boolean shouldRunTypecheck(VirtualFrame frame) {
    return frame.getArguments().length == 4
        && frame.getArguments()[3] == VmUtils.SKIP_TYPECHECK_MARKER;
  }

  public boolean isUndefined() {
    return bodyNode instanceof DefaultPropertyBodyNode propBodyNode && propBodyNode.isUndefined();
  }
}
