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
package org.pkl.core.ast.expression.primary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.builder.SymbolTable.CustomThisScope;
import org.pkl.core.runtime.VmUtils;

/** `this` inside `CustomThisScope` (type constraint, object member predicate). */
@NodeInfo(shortName = "this")
public final class CustomThisNode extends ExpressionNode {
  @CompilationFinal private int customThisSlot = -1;

  public CustomThisNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    if (customThisSlot == -1) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      // deferred until execution time s.t. nodes of inlined type aliases get the right frame slot
      customThisSlot = VmUtils.findAuxiliarySlot(frame, CustomThisScope.FRAME_SLOT_ID);
    }
    return frame.getAuxiliarySlot(customThisSlot);
  }
}
