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
package org.pkl.core.ast.expression.literal;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.BaseModule;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmException;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmUtils;

// IDEA: don't materialize frames when all members are constants
@NodeChild(value = "parentNode", type = ExpressionNode.class)
public abstract class ObjectLiteralNode extends ExpressionNode {
  protected final VmLanguage language;
  protected final String qualifiedScopeName;
  protected final boolean isCustomThisScope;
  protected final boolean needsCapture;
  protected final @Nullable FrameDescriptor parametersDescriptor;
  @Children protected final UnresolvedTypeNode[] parameterTypes;

  public ObjectLiteralNode(
      SourceSection sourceSection,
      VmLanguage language,
      String qualifiedScopeName,
      boolean isCustomThisScope,
      boolean needsCapture,
      @Nullable FrameDescriptor parametersDescriptor,
      UnresolvedTypeNode[] parameterTypes) {

    super(sourceSection);
    this.language = language;
    this.qualifiedScopeName = qualifiedScopeName;
    this.isCustomThisScope = isCustomThisScope;
    this.needsCapture = needsCapture;
    this.parametersDescriptor = parametersDescriptor;
    this.parameterTypes = parameterTypes;
  }

  protected abstract Object executeWithParent(VirtualFrame frame, Object parent);

  protected abstract ExpressionNode getParentNode();

  protected abstract ObjectLiteralNode copy(ExpressionNode newParentNode);

  protected final AmendFunctionNode createAmendFunctionNode(VirtualFrame frame) {
    var resolvedParameterTypes =
        parametersDescriptor == null
            ? new TypeNode[0]
            : VmUtils.resolveParameterTypes(frame, parametersDescriptor, parameterTypes);
    return new AmendFunctionNode(this, resolvedParameterTypes);
  }

  @Idempotent
  protected static boolean isTypedObjectClass(VmClass clazz) {
    if (clazz.isListingClass()
        || clazz.isMappingClass()
        || clazz.isDynamicClass()
        || clazz.isFunctionClass()
        || clazz.isFunctionNClass()) {
      return false;
    }
    return BaseModule.getTypedClass().isSuperclassOf(clazz);
  }

  protected final boolean checkIsValidFunctionAmendment(VmFunction parent) {
    var length = parameterTypes.length;
    if (length > 0 && length != parent.getParameterCount()) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("wrongFunctionAmendmentParameterCount", length, parent.getParameterCount())
          .withSourceSection(getParentNode().getSourceSection())
          .build();
    }
    return true;
  }

  @Idempotent
  protected final boolean checkIsValidFunctionAmendment(Object parent) {
    return checkIsValidFunctionAmendment((VmFunction) parent);
  }

  @Idempotent
  protected final boolean isFunction(Object value) {
    return value instanceof VmFunction;
  }

  protected final Object getNullDefaultValue(VmNull parent) {
    var value = parent.getDefaultValue();
    var count = 0;
    while (value instanceof VmNull n) {
      value = n.getDefaultValue();
      count++;
    }
    LoopNode.reportLoopCount(this, count);
    return value;
  }

  protected final @Nullable MaterializedFrame materializedFrame(VirtualFrame frame) {
    return needsCapture ? frame.materialize() : null;
  }

  @Override
  public final Object executeGeneric(VirtualFrame frame) {
    Object parent;
    try {
      parent = getParentNode().executeGeneric(frame);
    } catch (VmException e) {
      CompilerDirectives.transferToInterpreter();
      // include object amendment in the error message if error found during eval of parent
      if (e.getSourceSection() != null
          && !e.getSourceSection().equals(sourceSection)
          // don't include if the originating error's source section is within the bounds of our own
          // source section.
          && !(VmUtils.sourceSectionContains(sourceSection, e.getSourceSection()))) {
        e.getInsertedStackFrames()
            .putIfAbsent(
                getRootNode().getCallTarget(),
                VmUtils.createStackFrame(sourceSection, qualifiedScopeName));
      }
      throw e;
    }
    return executeWithParent(frame, parent);
  }
}
