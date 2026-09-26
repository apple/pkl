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
package org.pkl.core.ast.expression.generator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.internal.ReadCursorValueNode;
import org.pkl.core.ast.internal.ReadCursorValueNodeGen;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.runtime.Iterators.TruffleIterator;
import org.pkl.core.util.ArrayUtils;

public abstract class GeneratorForNode extends GeneratorMemberNode {
  private final FrameDescriptor generatorDescriptor;
  private final LoopConditionProfile loopConditionProfile = LoopConditionProfile.create();
  @Child private ExpressionNode iterableNode;
  @Child private @Nullable UnresolvedTypeNode unresolvedKeyTypeNode;
  @Child private @Nullable UnresolvedTypeNode unresolvedValueTypeNode;
  @Children private final GeneratorMemberNode[] childNodes;
  @Child private @Nullable TypeNode keyTypeNode;
  @Child private @Nullable TypeNode valueTypeNode;

  @Child
  private ReadCursorValueNode readCursorValueNode = ReadCursorValueNodeGen.create(sourceSection);

  private final int keySlot;
  private final int valueSlot;
  private final int[] slotsToCopy;

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
      @Nullable TypeNode valueTypeNode,
      int keySlot,
      int valueSlot,
      int[] outerForGeneratorSlots,
      int[] parameterSlots) {
    super(sourceSection, false);
    this.generatorDescriptor = generatorDescriptor;
    this.iterableNode = iterableNode;
    this.unresolvedKeyTypeNode = unresolvedKeyTypeNode;
    this.unresolvedValueTypeNode = unresolvedValueTypeNode;
    this.childNodes = childNodes;
    this.keyTypeNode = keyTypeNode;
    this.valueTypeNode = valueTypeNode;
    this.keySlot = keySlot;
    this.valueSlot = valueSlot;
    this.slotsToCopy = ArrayUtils.concat(parameterSlots, outerForGeneratorSlots);
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
    var idx = 0L;
    loopConditionProfile.profileCounted(iterable.getLength());
    for (var element : iterable) {
      executeIteration(frame, parent, data, idx++, element);
    }
  }

  @Specialization
  protected void eval(VirtualFrame frame, Object parent, ObjectData data, VmMap iterable) {
    loopConditionProfile.profileCounted(iterable.getLength());
    var iterator = new TruffleIterator<>(iterable);
    while (loopConditionProfile.inject(iterator.hasNext())) {
      var entry = iterator.next();
      executeIteration(frame, parent, data, VmUtils.getKey(entry), VmUtils.getValue(entry));
    }
  }

  @Specialization
  protected void eval(VirtualFrame frame, Object parent, ObjectData data, VmSet iterable) {
    var idx = 0L;
    loopConditionProfile.profileCounted(iterable.getLength());
    var iterator = new TruffleIterator<>(iterable);
    while (loopConditionProfile.inject(iterator.hasNext())) {
      var element = iterator.next();
      executeIteration(frame, parent, data, idx++, element);
    }
  }

  @Specialization
  protected void eval(VirtualFrame frame, Object parent, ObjectData data, VmIntSeq iterable) {
    var length = iterable.getLength();
    loopConditionProfile.profileCounted(iterable.getLength());
    for (long key = 0, value = iterable.start;
        loopConditionProfile.inject(key < length);
        key++, value += iterable.step) {
      executeIteration(frame, parent, data, key, value);
    }
  }

  @Specialization
  protected void eval(VirtualFrame frame, Object parent, ObjectData data, VmBytes iterable) {
    var bytes = iterable.getBytes();
    loopConditionProfile.profileCounted(bytes.length);
    for (var idx = 0; loopConditionProfile.inject(idx < bytes.length); idx++) {
      executeIteration(frame, parent, data, (long) idx, (long) bytes[idx]);
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
    // TODO: skip types for module objects?
    var cursor = iterable.members();
    if (cursor.getLength() == -1) {
      doEvalObjectProfiled(frame, parent, data, cursor);
    } else {
      doEvalObjectCounted(frame, parent, data, cursor);
    }
  }

  private void doEvalObjectCounted(
      VirtualFrame frame, Object parent, ObjectData data, VmObjectCursor cursor) {
    loopConditionProfile.profileCounted(cursor.getLength());
    while (loopConditionProfile.inject(cursor.advance())) {
      var key = cursor.isProperty() ? cursor.keyToString() : cursor.key();
      var value = readCursorValueNode.execute(frame, cursor);
      executeIteration(frame, parent, data, key, value);
    }
  }

  private void doEvalObjectProfiled(
      VirtualFrame frame, Object parent, ObjectData data, VmObjectCursor cursor) {
    while (loopConditionProfile.profile(cursor.advance())) {
      var key = cursor.isProperty() ? cursor.keyToString() : cursor.key();
      var value = readCursorValueNode.execute(frame, cursor);
      executeIteration(frame, parent, data, key, value);
    }
  }

  @ExplodeLoop
  private void executeIteration(
      VirtualFrame frame, Object parent, ObjectData data, Object key, Object value) {

    // GraalJS uses the same implementation technique here:
    // https://github.com/oracle/graaljs/blob/44a11ce6e87/graal-js/src/com.oracle.truffle.js/src/com/oracle/truffle/js/nodes/function/IterationScopeNode.java#L86-L88
    var newFrame =
        Truffle.getRuntime().createVirtualFrame(frame.getArguments(), generatorDescriptor);
    VmUtils.copyLocals(frame, newFrame, slotsToCopy);
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
      keyTypeNode = insert(unresolvedKeyTypeNode.execute(frame)).initWriteSlotNode(keySlot);
      generatorDescriptor.setSlotKind(keySlot, keyTypeNode.getFrameSlotKind());
      unresolvedKeyTypeNode = null;
    }
    if (unresolvedValueTypeNode != null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      valueTypeNode = insert(unresolvedValueTypeNode.execute(frame)).initWriteSlotNode(valueSlot);
      generatorDescriptor.setSlotKind(valueSlot, valueTypeNode.getFrameSlotKind());
      unresolvedValueTypeNode = null;
    }
  }
}
