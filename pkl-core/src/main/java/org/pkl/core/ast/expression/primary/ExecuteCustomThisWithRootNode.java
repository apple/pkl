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
package org.pkl.core.ast.expression.primary;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.SimpleRootNode;
import org.pkl.core.ast.builder.SymbolTable.CustomThisScope;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmUtils;

/**
 * A node that executes {@code expressionNode} within its own root node, with the custom this value
 * set.
 *
 * <p>This is required, for example, when let expressions are used inside type constraints, because:
 *
 * <ul>
 *   <li>let expressions write to frame slots.
 *   <li>type nodes don't add slots to the enclosing frame.
 * </ul>
 */
public final class ExecuteCustomThisWithRootNode extends ExpressionNode {
  private @Child ExpressionNode customThisNode =
      new CustomThisNode(VmUtils.unavailableSourceSection());
  private @Child DirectCallNode callNode;

  public ExecuteCustomThisWithRootNode(
      SourceSection sourceSection,
      ExpressionNode expressionNode,
      FrameDescriptor frameDescriptor,
      String qualifiedName,
      int[] forGeneratorSlots,
      int[] parameterSlots) {
    super(sourceSection);
    frameDescriptor.findOrAddAuxiliarySlot(CustomThisScope.FRAME_SLOT_ID);
    var rootNode =
        new SimpleRootNode(
            VmLanguage.get(this),
            frameDescriptor,
            sourceSection,
            qualifiedName,
            new WithCustomThisExpression(expressionNode, forGeneratorSlots, parameterSlots));
    this.callNode = DirectCallNode.create(rootNode.getCallTarget());
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    var customThis = customThisNode.executeGeneric(frame);
    return callNode.call(VmUtils.getReceiver(frame), VmUtils.getOwner(frame), customThis, frame);
  }
}
