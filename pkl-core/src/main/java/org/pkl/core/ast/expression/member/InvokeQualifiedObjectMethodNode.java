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

import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.member.Method;
import org.pkl.core.ast.member.ObjectMethodNode;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmObjectLike;

/** A non-virtual call of an object method, invoked off of an explicit receiver. */
public final class InvokeQualifiedObjectMethodNode extends AbstractInvokeQualifiedMethodNode {
  public InvokeQualifiedObjectMethodNode(
      SourceSection sourceSection,
      Identifier methodName,
      ExpressionNode[] argumentNodes,
      boolean needsConst,
      ExpressionNode getReceiverNode) {
    super(sourceSection, methodName, argumentNodes, needsConst, getReceiverNode);
  }

  @Override
  protected void doCheckConst(VmObjectLike owner) {
    var member = owner.getMember(methodName);
    assert member != null;
    if (!VmModifier.isConst(member.getModifiers())) {
      throw exceptionBuilder().evalError("methodMustBeConst", methodName).build();
    }
  }

  @Override
  protected Method getMethod(VmObjectLike owner) {
    var member = owner.getMember(methodName);
    assert member != null && member.isLocal();
    var method = (ObjectMethodNode) member.getMemberNode();
    assert method != null;
    return method;
  }
}
