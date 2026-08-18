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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.member.Method;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmUtils;

public final class InferParentWithinMethodArgumentNode extends AbstractInferParentNode {
  private final int argIndex;
  @CompilationFinal private @Nullable Object inferredParent;

  public InferParentWithinMethodArgumentNode(
      SourceSection sourceSection, VmLanguage language, int argIndex) {
    super(sourceSection, language, false);
    this.argIndex = argIndex;
  }

  @Override
  protected TypeInfo getTypeInfo(VirtualFrame frame) {
    var methodSlot =
        frame.getFrameDescriptor().getAuxiliarySlots().get(VmUtils.METHOD_FRAME_SLOT_ID);
    if (methodSlot == null) {
      // used in intrinsic constructor e.g. pkl.base#List()
      throw exceptionBuilder().evalError("cannotInferParent").build();
    }

    var method = (Method) frame.getAuxiliarySlot(methodSlot);
    if (method == null) {
      // used in FunctionN.apply()
      throw exceptionBuilder().evalError("cannotInferParent").build();
    }

    return new TypeInfo(
        method.getParameterTypeNode(frame, argIndex),
        method.getHeaderSection(),
        method.getQualifiedName());
  }
}
