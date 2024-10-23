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
package org.pkl.core.ast.expression.literal;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.builder.SymbolTable.CustomThisScope;
import org.pkl.core.ast.member.FunctionNode;
import org.pkl.core.ast.member.UnresolvedFunctionNode;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.Nullable;

public final class FunctionLiteralNode extends ExpressionNode {
  private @Child UnresolvedFunctionNode unresolvedFunctionNode;
  private final boolean isCustomThisScope;

  @CompilationFinal private @Nullable FunctionNode functionNode;
  @CompilationFinal private int customThisSlot = -1;

  public FunctionLiteralNode(
      SourceSection sourceSection, UnresolvedFunctionNode functionNode, boolean isCustomThisScope) {

    super(sourceSection);
    this.unresolvedFunctionNode = functionNode;
    this.isCustomThisScope = isCustomThisScope;
  }

  @Override
  public VmFunction executeGeneric(VirtualFrame frame) {
    if (functionNode == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      functionNode = unresolvedFunctionNode.execute(frame);
      if (isCustomThisScope) {
        customThisSlot = VmUtils.findAuxiliarySlot(frame, CustomThisScope.FRAME_SLOT_ID);
      }
    }

    return new VmFunction(
        frame.materialize(),
        isCustomThisScope ? frame.getAuxiliarySlot(customThisSlot) : VmUtils.getReceiver(frame),
        functionNode.getParameterCount(),
        functionNode,
        null);
  }
}
