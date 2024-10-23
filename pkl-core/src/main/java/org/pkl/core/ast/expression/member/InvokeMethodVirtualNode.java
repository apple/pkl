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
package org.pkl.core.ast.expression.member;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.MemberLookupMode;
import org.pkl.core.ast.internal.GetClassNode;
import org.pkl.core.ast.member.ClassMethod;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmFunction;

/** A virtual method call. */
@ImportStatic(Identifier.class)
@NodeChild(value = "receiverNode", type = ExpressionNode.class)
@NodeChild(value = "receiverClassNode", type = GetClassNode.class, executeWith = "receiverNode")
public abstract class InvokeMethodVirtualNode extends ExpressionNode {
  protected final Identifier methodName;
  @Children private final ExpressionNode[] argumentNodes;
  private final MemberLookupMode lookupMode;
  private final boolean needsConst;

  protected InvokeMethodVirtualNode(
      SourceSection sourceSection,
      Identifier methodName,
      ExpressionNode[] argumentNodes,
      MemberLookupMode lookupMode,
      boolean needsConst) {

    super(sourceSection);
    this.methodName = methodName;
    this.argumentNodes = argumentNodes;
    this.lookupMode = lookupMode;
    this.needsConst = needsConst;
  }

  protected InvokeMethodVirtualNode(
      SourceSection sourceSection,
      Identifier methodName,
      ExpressionNode[] argumentNodes,
      MemberLookupMode lookupMode) {
    this(sourceSection, methodName, argumentNodes, lookupMode, false);
  }

  /**
   * When only using this execute method, pass `null` for `receiverNode` and `receiverClassNode` to
   * {@link InvokeMethodVirtualNodeGen#create}.
   */
  public abstract Object executeWith(VirtualFrame frame, Object value, VmClass clazz);

  /** Intrinsifies `FunctionN.apply()` calls. */
  @ExplodeLoop
  @Specialization(guards = {"methodName == APPLY", "receiver.getCallTarget() == cachedCallTarget"})
  protected Object evalFunctionCached(
      VirtualFrame frame,
      VmFunction receiver,
      @SuppressWarnings("unused") VmClass receiverClass,
      @Cached("receiver.getCallTarget()") @SuppressWarnings("unused")
          RootCallTarget cachedCallTarget,
      @Cached("create(cachedCallTarget)") DirectCallNode callNode) {

    var args = new Object[2 + argumentNodes.length];
    args[0] = receiver.getThisValue();
    args[1] = receiver;
    for (var i = 0; i < argumentNodes.length; i++) {
      args[2 + i] = argumentNodes[i].executeGeneric(frame);
    }

    return callNode.call(args);
  }

  /** Intrinsifies `FunctionN.apply()` calls. */
  @ExplodeLoop
  @Specialization(guards = "methodName == APPLY", replaces = "evalFunctionCached")
  protected Object evalFunction(
      VirtualFrame frame,
      VmFunction receiver,
      @SuppressWarnings("unused") VmClass receiverClass,
      @Exclusive @Cached("create()") IndirectCallNode callNode) {

    var args = new Object[2 + argumentNodes.length];
    args[0] = receiver.getThisValue();
    args[1] = receiver;
    for (var i = 0; i < argumentNodes.length; i++) {
      args[2 + i] = argumentNodes[i].executeGeneric(frame);
    }

    return callNode.call(receiver.getCallTarget(), args);
  }

  @ExplodeLoop
  @Specialization(guards = "receiverClass == cachedReceiverClass")
  protected Object evalCached(
      VirtualFrame frame,
      Object receiver,
      @SuppressWarnings("unused") VmClass receiverClass,
      @Cached("receiverClass") @SuppressWarnings("unused") VmClass cachedReceiverClass,
      @Cached("resolveMethod(receiverClass)") ClassMethod method,
      @Cached("create(method.getCallTarget(sourceSection))") DirectCallNode callNode) {

    var args = new Object[2 + argumentNodes.length];
    args[0] = receiver;
    args[1] = method.getOwner();
    for (var i = 0; i < argumentNodes.length; i++) {
      args[2 + i] = argumentNodes[i].executeGeneric(frame);
    }

    return callNode.call(args);
  }

  @ExplodeLoop
  @Specialization(replaces = "evalCached")
  protected Object eval(
      VirtualFrame frame,
      Object receiver,
      VmClass receiverClass,
      @Exclusive @Cached("create()") IndirectCallNode callNode) {

    var method = resolveMethod(receiverClass);
    var args = new Object[2 + argumentNodes.length];
    args[0] = receiver;
    args[1] = method.getOwner();
    for (var i = 0; i < argumentNodes.length; i++) {
      args[2 + i] = argumentNodes[i].executeGeneric(frame);
    }

    // Deprecation should not report here (getCallTarget(sourceSection)), as this happens for each
    // and every call.
    return callNode.call(method.getCallTarget(), args);
  }

  protected ClassMethod resolveMethod(VmClass receiverClass) {
    var method = receiverClass.getMethod(methodName);
    if (method != null) {
      checkConst(method);
      return method;
    }

    CompilerDirectives.transferToInterpreter();

    throw exceptionBuilder()
        .cannotFindMethod(
            receiverClass.getPrototype(),
            methodName,
            argumentNodes.length,
            lookupMode != MemberLookupMode.EXPLICIT_RECEIVER)
        .build();
  }

  private void checkConst(ClassMethod method) {
    if (needsConst && !method.isConst()) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder().evalError("methodMustBeConst", methodName.toString()).build();
    }
  }
}
