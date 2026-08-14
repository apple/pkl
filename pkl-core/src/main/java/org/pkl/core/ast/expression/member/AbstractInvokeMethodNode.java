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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.Method;
import org.pkl.core.runtime.PklTags.Expression;
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.runtime.VmUtils;

public abstract class AbstractInvokeMethodNode extends ExpressionNode {

  @Children protected ExpressionNode[] argumentNodes;
  
  public AbstractInvokeMethodNode(SourceSection sourceSection, ExpressionNode[] argumentNodes) {
    super(sourceSection);
    this.argumentNodes = argumentNodes;
  }

  @TruffleBoundary
  protected int getMethodSlot(FrameDescriptor frameDescriptor) {
    // can't store the slot id as this node may be called from different root nodes
    // (see constraints14 snippet)
    return frameDescriptor.findOrAddAuxiliarySlot(VmUtils.METHOD_FRAME_SLOT_ID);
  }
  
  @ExplodeLoop
  protected Object[] evalArgs(VirtualFrame frame, @Nullable Method method, Object owner, @Nullable Object receiver) {
    var methodSlot = getMethodSlot(frame.getFrameDescriptor());
    var prevMethod = frame.getAuxiliarySlot(methodSlot);
    frame.setAuxiliarySlot(methodSlot, method);

    var args = new Object[2 + argumentNodes.length];
    args[0] = receiver;
    args[1] = owner;

    try {
      for (var i = 0; i < argumentNodes.length; i++) {
        args[2 + i] = argumentNodes[i].executeGeneric(frame);
      }
    } finally {
      frame.setAuxiliarySlot(methodSlot, prevMethod);
    }
    
    return args;
  }
}
