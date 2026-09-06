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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.member.Method;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmUtils;

public abstract class InferParentWithinMethodArgumentNode
    extends AbstractInferParentFromMethodNode {
  private final int argIndex;

  public InferParentWithinMethodArgumentNode(
      SourceSection sourceSection, VmLanguage language, int argIndex) {
    super(sourceSection, language);
    this.argIndex = argIndex;
  }

  @TruffleBoundary
  private int getMethodSlot(FrameDescriptor frameDescriptor) {
    var methodSlot = frameDescriptor.getAuxiliarySlots().get(VmUtils.METHOD_FRAME_SLOT_ID);
    if (methodSlot == null) {
      // used in intrinsic constructor e.g. pkl.base#List()
      throw exceptionBuilder().evalError("cannotInferParent").build();
    }
    return methodSlot;
  }

  @Override
  protected Method getMethod(VirtualFrame frame) {
    var method = (Method) frame.getAuxiliarySlot(getMethodSlot(frame.getFrameDescriptor()));
    if (method == null) {
      // used in FunctionN.apply()
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder().evalError("cannotInferParent").build();
    }

    return method;
  }

  @Override
  protected @Nullable TypeNode getTypeNode(VirtualFrame frame, Method method) {
    return method.getParameterTypeNode(frame, argIndex);
  }

  // keep specializations in sync with other AbstractInferParentFromMethodNode subclasses

  @Specialization(
      guards = {"getMethod(frame) == cachedMethod", "isFinalType(cachedMethod, typeNode)"})
  protected final Object evalCached(
      @SuppressWarnings("unused") VirtualFrame frame,
      @Cached("getMethod(frame)") @SuppressWarnings("unused") Method cachedMethod,
      @Cached("getTypeNode(frame, cachedMethod)") @SuppressWarnings("unused") TypeNode typeNode,
      @Cached(
              "getDefaultValue(frame, typeNode, cachedMethod.getHeaderSection(), cachedMethod.getQualifiedName())")
          Object defaultValue) {
    return defaultValue;
  }

  @Specialization(replaces = "evalCached")
  protected final Object eval(VirtualFrame frame) {
    var method = getMethod(frame);
    var typeNode = getTypeNode(frame, method);
    return getDefaultValue(frame, typeNode, method.getHeaderSection(), method.getQualifiedName());
  }

  @Override
  protected Object getDefaultValue(
      VirtualFrame frame,
      @Nullable TypeNode typeNode,
      SourceSection headerSection,
      String qualifiedName) {
    if (typeNode != null && typeNode.isSelfType()) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder().evalError("cannotInferParent").build();
    }

    return super.getDefaultValue(frame, typeNode, headerSection, qualifiedName);
  }
}
