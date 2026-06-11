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
package org.pkl.core.ast.expression.binary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.VmException;
import org.pkl.core.runtime.VmUtils;

@NodeChild(value = "bindingNode", type = ExpressionNode.class)
public abstract class LetExprNode extends ExpressionNode {

  private final String qualifiedName;
  private @Child @Nullable UnresolvedTypeNode unresolvedTypeNode;
  private @Child ExpressionNode bodyNode;
  private @Child @Nullable TypeNode typeNode;
  private final int slot;

  protected LetExprNode(
      SourceSection sourceSection,
      String qualifiedName,
      @Nullable UnresolvedTypeNode unresolvedTypeNode,
      ExpressionNode bodyNode,
      int slot) {
    super(sourceSection);
    this.qualifiedName = qualifiedName;
    this.unresolvedTypeNode = unresolvedTypeNode;
    this.bodyNode = bodyNode;
    this.slot = slot;
  }

  private TypeNode getTypeNode(VirtualFrame frame) {
    if (typeNode == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      if (unresolvedTypeNode != null) {
        typeNode = unresolvedTypeNode.execute(frame);
      } else {
        typeNode = new TypeNode.UnknownTypeNode(VmUtils.unavailableSourceSection());
      }
      typeNode.initWriteSlotNode(slot);
      frame.getFrameDescriptor().setSlotKind(slot, typeNode.getFrameSlotKind());
      insert(typeNode);
    }
    assert typeNode != null;
    return typeNode;
  }

  @Specialization
  protected Object eval(VirtualFrame frame, Object value) {
    if (slot != -1) {
      getTypeNode(frame).executeAndSet(frame, value);
    }
    try {
      return bodyNode.executeGeneric(frame);
    } catch (VmException e) {
      CompilerDirectives.transferToInterpreter();
      e.getInsertedStackFrames()
          .put(
              getRootNode().getCallTarget(),
              VmUtils.createStackFrame(getSourceSection(), qualifiedName));
      throw e;
    }
  }
}
