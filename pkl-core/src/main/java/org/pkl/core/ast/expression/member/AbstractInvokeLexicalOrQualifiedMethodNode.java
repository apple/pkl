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
package org.pkl.core.ast.expression.member;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.Method;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmObjectLike;

/**
 * A non-virtual (statically dispatched) method call.
 *
 * <p>Subclasses differ only in how they obtain the {@code owner}/{@code receiver} that the method
 * is invoked on: either by walking the frame chain ({@link InvokeLexicalClassMethodNode}, {@link
 * InvokeLexicalObjectMethodNode}), or off of an explicit receiver expression ({@link
 * InvokeQualifiedClassMethodNode}, {@link InvokeQualifiedObjectMethodNode}).
 */
public abstract sealed class AbstractInvokeLexicalOrQualifiedMethodNode
    extends AbstractInvokeMethodNode
    permits AbstractInvokeQualifiedMethodNode, AbstractInvokeLexicalMethodNode {

  protected final Identifier methodName;
  private final boolean needsConst;
  @Child private @Nullable DirectCallNode callNode;
  @CompilationFinal protected boolean isConstChecked;

  protected AbstractInvokeLexicalOrQualifiedMethodNode(
      SourceSection sourceSection,
      Identifier methodName,
      ExpressionNode[] argumentNodes,
      boolean needsConst) {
    super(sourceSection, argumentNodes);
    this.methodName = methodName;
    this.needsConst = needsConst;
    this.isConstChecked = false;
  }

  protected final Object invoke(VirtualFrame frame, VmObjectLike owner, Object receiver) {
    checkConst(owner);
    var method = getMethod(owner);
    var args = evalArgs(frame, method, owner, receiver);
    return getCallNode(method, owner).call(args);
  }

  private void checkConst(VmObjectLike owner) {
    if (!needsConst || isConstChecked) {
      return;
    }
    CompilerDirectives.transferToInterpreterAndInvalidate();
    doCheckConst(owner);
    isConstChecked = true;
  }

  protected abstract Method getMethod(VmObjectLike owner);

  protected abstract void doCheckConst(VmObjectLike owner);

  protected DirectCallNode getCallNode(Method method, VmObjectLike owner) {
    if (callNode == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      callNode = DirectCallNode.create(method.getCallTarget(getSourceSection(), owner));
      insert(callNode);
    }
    assert callNode != null;
    return callNode;
  }
}
