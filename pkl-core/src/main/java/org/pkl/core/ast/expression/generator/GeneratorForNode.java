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
package org.pkl.core.ast.expression.generator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import java.util.*;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.ast.type.VmTypeMismatchException;
import org.pkl.core.runtime.*;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.Pair;

public abstract class GeneratorForNode extends GeneratorIteratingNode {
  private final int keySlot;
  private final int valueSlot;
  @Child private @Nullable UnresolvedTypeNode unresolvedKeyTypeNode;
  @Child private @Nullable UnresolvedTypeNode unresolvedValueTypeNode;
  @Children private final GeneratorMemberNode[] childNodes;
  @Child private @Nullable TypeNode keyTypeNode;
  @Child @LateInit private TypeNode valueTypeNode;

  public GeneratorForNode(
      SourceSection sourceSection,
      int keySlot,
      int valueSlot,
      GeneratorIterableNode iterableNode,
      @Nullable UnresolvedTypeNode unresolvedKeyTypeNode,
      @Nullable UnresolvedTypeNode unresolvedValueTypeNode,
      GeneratorMemberNode[] childNodes,
      boolean hasKeyIdentifier,
      boolean hasValueIdentifier) {

    super(sourceSection, iterableNode);
    this.keySlot = keySlot;
    this.valueSlot = valueSlot;
    this.iterableNode = iterableNode;
    this.unresolvedKeyTypeNode = unresolvedKeyTypeNode;
    this.unresolvedValueTypeNode = unresolvedValueTypeNode;
    this.childNodes = childNodes;

    // initialize now if possible to save later insert()
    if (unresolvedKeyTypeNode == null && hasKeyIdentifier) {
      keyTypeNode =
          new TypeNode.UnknownTypeNode(VmUtils.unavailableSourceSection())
              .initWriteSlotNode(keySlot);
    }
    if (unresolvedValueTypeNode == null && hasValueIdentifier) {
      valueTypeNode =
          new TypeNode.UnknownTypeNode(VmUtils.unavailableSourceSection())
              .initWriteSlotNode(valueSlot);
    }
  }

  protected abstract void executeWithIterable(
      VirtualFrame frame, Object parent, ObjectData data, Object iterable);

  @Override
  public final void execute(VirtualFrame frame, Object parent, ObjectData data) {
    executeWithIterable(frame, parent, data, evalIterable(frame, data));
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
    initTypeNodes(frame);
    long idx = 0;
    for (Object element : iterable) {
      executeIteration(frame, parent, data, idx++, element);
    }
    resetFrameSlots(frame);
  }

  @Specialization
  protected void eval(VirtualFrame frame, Object parent, ObjectData data, VmMap iterable) {
    initTypeNodes(frame);
    for (var entry : iterable) {
      executeIteration(frame, parent, data, VmUtils.getKey(entry), VmUtils.getValue(entry));
    }
    resetFrameSlots(frame);
  }

  @Specialization
  protected void eval(VirtualFrame frame, Object parent, ObjectData data, VmSet iterable) {
    initTypeNodes(frame);
    long idx = 0;
    for (var element : iterable) {
      executeIteration(frame, parent, data, idx++, element);
    }
    resetFrameSlots(frame);
  }

  @Specialization
  protected void eval(VirtualFrame frame, Object parent, ObjectData data, VmIntSeq iterable) {
    initTypeNodes(frame);
    var length = iterable.getLength();
    for (long key = 0, value = iterable.start; key < length; key++, value += iterable.step) {
      executeIteration(frame, parent, data, key, value);
    }
    resetFrameSlots(frame);
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

  @SuppressWarnings("ForLoopReplaceableByForEach")
  private void doEvalObject(VirtualFrame frame, VmObject iterable, Object parent, ObjectData data) {
    initTypeNodes(frame);
    var members = evaluateMembers(iterable);
    for (int i = 0; i < members.size(); i++) {
      var member = members.get(i);
      executeIteration(frame, parent, data, member.first, member.second);
    }
    resetFrameSlots(frame);
  }

  private void resetFrameSlots(VirtualFrame frame) {
    if (keySlot != -1) {
      frame.clear(keySlot);
    }
    if (valueSlot != -1) {
      frame.clear(valueSlot);
    }
  }

  private void initTypeNodes(VirtualFrame frame) {
    if (unresolvedKeyTypeNode != null) {
      CompilerDirectives.transferToInterpreter();
      keyTypeNode = insert(unresolvedKeyTypeNode.execute(frame)).initWriteSlotNode(keySlot);
      unresolvedKeyTypeNode = null;
    }

    if (unresolvedValueTypeNode != null) {
      CompilerDirectives.transferToInterpreter();
      valueTypeNode = insert(unresolvedValueTypeNode.execute(frame)).initWriteSlotNode(valueSlot);
      unresolvedValueTypeNode = null;
    }
  }

  /**
   * Evaluate members upfront to make sure that `childNode.execute()` is not behind a Truffle
   * boundary.
   */
  @TruffleBoundary
  private List<Pair<Object, Object>> evaluateMembers(VmObject object) {
    var members = new ArrayList<Pair<Object, Object>>();
    object.forceAndIterateMemberValues(
        (key, member, value) -> {
          members.add(Pair.of(member.isProp() ? key.toString() : key, value));
          return true;
        });
    return members;
  }

  @ExplodeLoop
  private void executeIteration(
      VirtualFrame frame, Object parent, ObjectData data, Object key, Object value) {

    try {
      if (keyTypeNode != null) {
        keyTypeNode.executeAndSet(frame, key);
      }
      if (valueTypeNode != null) {
        valueTypeNode.executeAndSet(frame, value);
      }
    } catch (VmTypeMismatchException e) {
      CompilerDirectives.transferToInterpreter();
      throw e.toVmException();
    }

    Object[] prevBindings = null;
    if (keyTypeNode != null && valueTypeNode != null) {
      prevBindings = data.addForBinding(key, value);
    } else if (valueTypeNode != null) {
      prevBindings = data.addForBinding(value);
    } else if (keyTypeNode != null) {
      prevBindings = data.addForBinding(key);
    }

    for (var childNode : childNodes) {
      childNode.execute(frame, parent, data);
    }

    data.resetForBindings(prevBindings);
  }
}
