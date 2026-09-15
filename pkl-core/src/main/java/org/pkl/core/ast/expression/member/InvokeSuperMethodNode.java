/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ClassMethod;
import org.pkl.core.ast.member.FunctionNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.runtime.VmUtils;

@ImportStatic(VmUtils.class)
public abstract class InvokeSuperMethodNode extends AbstractInvokeMethodNode {
  private final Identifier methodName;
  private final boolean needsConst;
  @CompilationFinal private @Nullable ClassMethod supermethod;

  protected InvokeSuperMethodNode(
      SourceSection sourceSection,
      Identifier methodName,
      UnresolvedTypeNode @Nullable [] unresolvedTypeArgumentNodes,
      ExpressionNode[] argumentNodes,
      boolean needsConst,
      boolean argsRequireInference) {
    super(sourceSection, unresolvedTypeArgumentNodes, argumentNodes, argsRequireInference);
    this.needsConst = needsConst;

    assert !methodName.isLocalMethod();

    this.methodName = methodName;
  }

  @Specialization(guards = "unresolvedTypeArgumentNodes == null")
  protected Object evalNoArgs(
      VirtualFrame frame,
      @Bind("getSupermethod(frame)") ClassMethod supermethod,
      @Cached(
              value =
                  "instantiateFunction(frame, supermethod, supermethod.getFunctionNode(sourceSection))",
              neverDefault = true)
          @SuppressWarnings("unused")
          FunctionNode functionNode,
      @Cached("create(functionNode.getCallTarget())") DirectCallNode callNode) {
    var args =
        evalArgs(frame, supermethod, supermethod.getOwner(), VmUtils.getReceiverOrNull(frame));
    return callNode.call(args);
  }

  @Specialization(
      guards = {
        "unresolvedTypeArgumentNodes != null",
        "getTypeArgumentsAreFinal(frame)",
        "getClass(receiver) == cachedReceiverClass"
      })
  protected Object evalCached(
      VirtualFrame frame,
      @Bind("getSupermethod(frame)") ClassMethod supermethod,
      @Bind("getReceiverOrNull(frame)") Object receiver,
      @Cached("getClass(receiver)") @SuppressWarnings("unused") VmClass cachedReceiverClass,
      @Cached(
              "create(instantiateFunction(frame, supermethod, supermethod.getFunctionNode(sourceSection)).getCallTarget())")
          DirectCallNode callNode) {
    var args = evalArgs(frame, supermethod, supermethod.getOwner(), receiver);
    return callNode.call(args);
  }

  @Specialization(guards = "unresolvedTypeArgumentNodes != null", replaces = "evalCached")
  protected Object eval(
      VirtualFrame frame, @Bind("getSupermethod(frame)") ClassMethod supermethod) {
    var args =
        evalArgs(frame, supermethod, supermethod.getOwner(), VmUtils.getReceiverOrNull(frame));
    var functionNode =
        instantiateFunction(frame, supermethod, supermethod.getFunctionNode(sourceSection));
    return DirectCallNode.create(functionNode.getCallTarget()).call(args);
  }

  protected ClassMethod getSupermethod(VirtualFrame frame) {
    if (supermethod != null) return supermethod;

    CompilerDirectives.transferToInterpreterAndInvalidate();
    var owner = VmUtils.getOwner(frame);
    while (owner instanceof VmFunction) {
      owner = owner.getEnclosingOwner();
    }
    assert owner != null : "VmFunction always has a parent";
    assert owner.isPrototype();

    var superclass = owner.getVmClass().getSuperclass();
    assert superclass != null;

    // note the use of getMethod() rather than getDeclaredMethod()
    supermethod = superclass.getMethod(methodName);
    if (supermethod != null) {
      if (needsConst && !supermethod.isConst()) {
        throw exceptionBuilder().evalError("methodMustBeConst", methodName.toString()).build();
      }
      return supermethod;
    }

    var parent = owner.getParent();
    assert parent != null;
    throw exceptionBuilder()
        .cannotFindMethod(parent, methodName, argumentNodes.length, false)
        .build();
  }
}
