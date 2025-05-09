/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.ast.expression.generator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Nullable;

public abstract class GeneratorForNode extends GeneratorMemberNode {
  private final FrameDescriptor generatorDescriptor;
  @Child private ExpressionNode iterableNode;
  @Child private @Nullable UnresolvedTypeNode unresolvedKeyTypeNode;
  @Child private @Nullable UnresolvedTypeNode unresolvedValueTypeNode;
  @Children private final GeneratorMemberNode[] childNodes;
  @Child private @Nullable TypeNode keyTypeNode;
  @Child private @Nullable TypeNode valueTypeNode;

  public GeneratorForNode(
      SourceSection sourceSection,
      FrameDescriptor generatorDescriptor,
      ExpressionNode iterableNode,
      // null if for-generator doesn't bind key or `keyTypeNode` is passed instead of this node
      @Nullable UnresolvedTypeNode unresolvedKeyTypeNode,
      // null if for-generator doesn't bind value or `valueTypeNode` is passed instead of this node
      @Nullable UnresolvedTypeNode unresolvedValueTypeNode,
      // If this node can be constructed at parse time,
      // it should be passed instead of `unresolvedKeyTypeNode`.
      GeneratorMemberNode[] childNodes,
      @Nullable TypeNode keyTypeNode,
      // If this node can be constructed at parse time,
      // it should be passed instead of `unresolvedValueTypeNode`.
      @Nullable TypeNode valueTypeNode) {
    super(sourceSection, false);
    this.generatorDescriptor = generatorDescriptor;
    this.iterableNode = iterableNode;
    this.unresolvedKeyTypeNode = unresolvedKeyTypeNode;
    this.unresolvedValueTypeNode = unresolvedValueTypeNode;
    this.childNodes = childNodes;
    this.keyTypeNode = keyTypeNode;
    this.valueTypeNode = valueTypeNode;
  }

  protected abstract void executeWithIterable(
      VirtualFrame frame, Object parent, ObjectData data, Object iterable);

  @Override
  public final void execute(VirtualFrame frame, Object parent, ObjectData data) {
    initialize(frame);
    executeWithIterable(frame, parent, data, iterableNode.executeGeneric(frame));
  }

  @Specialization
  protected void eval(VirtualFrame frame, Object parent, ObjectData data, VmListing iterable) {
    doEvalObject(frame, iterable, parent, data);
  }

  @Specialization
  protected void eval(VirtualFrame frame, Object parent, ObjectData data, VmMapping iterable) {
    doEvalObject(frame, iterable, parent, data);
  }

  @Specialization
  protected void eval(VirtualFrame frame, Object parent, ObjectData data, VmDynamic iterable) {
    doEvalObject(frame, iterable, parent, data);
  }

  @Specialization
  protected void eval(VirtualFrame frame, Object parent, ObjectData data, VmList iterable) {
    long idx = 0;
    for (var element : iterable) {
      executeIteration(frame, parent, data, idx++, element);
    }
  }

  @Specialization
  protected void eval(VirtualFrame frame, Object parent, ObjectData data, VmMap iterable) {
    for (var entry : iterable) {
      executeIteration(frame, parent, data, VmUtils.getKey(entry), VmUtils.getValue(entry));
    }
  }

  @Specialization
  protected void eval(VirtualFrame frame, Object parent, ObjectData data, VmSet iterable) {
    long idx = 0;
    for (var element : iterable) {
      executeIteration(frame, parent, data, idx++, element);
    }
  }

  @Specialization
  protected void eval(VirtualFrame frame, Object parent, ObjectData data, VmIntSeq iterable) {
    var length = iterable.getLength();
    for (long key = 0, value = iterable.start; key < length; key++, value += iterable.step) {
      executeIteration(frame, parent, data, key, value);
    }
  }

  @Specialization
  protected void eval(VirtualFrame frame, Object parent, ObjectData data, VmBytes iterable) {
    long idx = 0;
    for (var byt : iterable.getBytes()) {
      executeIteration(frame, parent, data, idx++, (long) byt);
    }
  }

  @Fallback
  @SuppressWarnings("unused")
  protected void fallback(VirtualFrame frame, Object parent, ObjectData data, Object iterable) {
    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder()
        .evalError("cannotIterateOverThisValue", VmUtils.getClass(iterable))
        .withLocation(iterableNode)
        .withProgramValue("Value", iterable)
        .build();
  }

  private void doEvalObject(VirtualFrame frame, VmObject iterable, Object parent, ObjectData data) {
    iterable.forceAndIterateMemberValues(
        (key, member, value) -> {
          var convertedKey = member.isProp() ? key.toString() : key;
          // TODO: Executing iteration behind a Truffle boundary is bad for performance.
          // This and similar cases will be fixed in an upcoming PR that replaces method
          // `(forceAnd)iterateMemberValues` with cursor-based external iterators.
          executeIteration(frame, parent, data, convertedKey, value);
          return true;
        });
  }

  @ExplodeLoop
  private void executeIteration(
      VirtualFrame frame, Object parent, ObjectData data, Object key, Object value) {

    // GraalJS uses the same implementation technique here:
    // https://github.com/oracle/graaljs/blob/44a11ce6e87/graal-js/src/com.oracle.truffle.js/
    // src/com/oracle/truffle/js/nodes/function/IterationScopeNode.java#L86-L88
    var newFrame =
        Truffle.getRuntime().createVirtualFrame(frame.getArguments(), generatorDescriptor);
    // the locals in `frame` (if any) are function arguments and/or outer for-generator bindings
    VmUtils.copyLocals(frame, 0, newFrame, 0, frame.getFrameDescriptor().getNumberOfSlots());
    if (keyTypeNode != null) {
      keyTypeNode.executeAndSet(newFrame, key);
    }
    if (valueTypeNode != null) {
      valueTypeNode.executeAndSet(newFrame, value);
    }
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < childNodes.length; i++) {
      childNodes[i].execute(newFrame, parent, data);
    }
  }

  private void initialize(VirtualFrame frame) {
    if (unresolvedKeyTypeNode != null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      var keySlot = frame.getFrameDescriptor().getNumberOfSlots();
      keyTypeNode = insert(unresolvedKeyTypeNode.execute(frame)).initWriteSlotNode(keySlot);
      generatorDescriptor.setSlotKind(keySlot, keyTypeNode.getFrameSlotKind());
      unresolvedKeyTypeNode = null;
    }
    if (unresolvedValueTypeNode != null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      var valueSlot = frame.getFrameDescriptor().getNumberOfSlots() + (keyTypeNode != null ? 1 : 0);
      valueTypeNode = insert(unresolvedValueTypeNode.execute(frame)).initWriteSlotNode(valueSlot);
      generatorDescriptor.setSlotKind(valueSlot, valueTypeNode.getFrameSlotKind());
      unresolvedValueTypeNode = null;
    }
  }
}
