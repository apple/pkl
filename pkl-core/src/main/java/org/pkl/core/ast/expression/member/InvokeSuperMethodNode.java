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
package org.pkl.core.ast.expression.member;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ClassMethod;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmUtils;

public abstract class InvokeSuperMethodNode extends ExpressionNode {
  private final Identifier methodName;
  @Children private final ExpressionNode[] argumentNodes;
  private final boolean needsConst;

  protected InvokeSuperMethodNode(
      SourceSection sourceSection,
      Identifier methodName,
      ExpressionNode[] argumentNodes,
      boolean needsConst) {

    super(sourceSection);
    this.needsConst = needsConst;

    assert !methodName.isLocalMethod();

    this.methodName = methodName;
    this.argumentNodes = argumentNodes;
  }

  @ExplodeLoop
  @Specialization
  protected Object eval(
      VirtualFrame frame,
      @Cached("findSupermethod(frame)") ClassMethod supermethod,
      @Cached("create(supermethod.getCallTarget(sourceSection))") DirectCallNode callNode) {

    if (needsConst && !supermethod.isConst()) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder().evalError("methodMustBeConst", methodName.toString()).build();
    }
    var args = new Object[2 + argumentNodes.length];
    args[0] = VmUtils.getReceiverOrNull(frame);
    args[1] = supermethod.getOwner();
    for (int i = 0; i < argumentNodes.length; i++) {
      args[2 + i] = argumentNodes[i].executeGeneric(frame);
    }

    return callNode.call(args);
  }

  protected ClassMethod findSupermethod(VirtualFrame frame) {
    var owner = VmUtils.getOwner(frame);
    assert owner.isPrototype();

    var superclass = owner.getVmClass().getSuperclass();
    assert superclass != null;

    // note the use of getMethod() rather than getDeclaredMethod()
    var supermethod = superclass.getMethod(methodName);
    if (supermethod != null) return supermethod;

    CompilerDirectives.transferToInterpreter();
    var parent = owner.getParent();
    assert parent != null;
    throw exceptionBuilder()
        .cannotFindMethod(parent, methodName, argumentNodes.length, false)
        .build();
  }
}
