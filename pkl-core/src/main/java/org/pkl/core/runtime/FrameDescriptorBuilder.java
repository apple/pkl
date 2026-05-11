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
package org.pkl.core.runtime;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import java.util.Arrays;
import org.pkl.core.util.Nullable;

/**
 * A wrapper for Truffle's {@link FrameDescriptor.Builder}, but also lets us find the slot of a
 * given {@link Identifier}.
 */
public class FrameDescriptorBuilder {

  private Identifier[] names;
  private int size;

  private final FrameDescriptor.Builder underlying;

  private static final int DEFAULT_CAPACITY = 8;

  public FrameDescriptorBuilder() {
    this(DEFAULT_CAPACITY);
  }

  public FrameDescriptorBuilder(int capacity) {
    underlying = FrameDescriptor.newBuilder(capacity);
    this.names = new Identifier[capacity];
  }

  public FrameDescriptorBuilder(FrameDescriptor descriptor) {
    this(descriptor.getNumberOfSlots());
    for (var i = 0; i < descriptor.getNumberOfSlots(); i++) {
      addSlot(
          descriptor.getSlotKind(i),
          (Identifier) descriptor.getSlotName(i),
          descriptor.getSlotInfo(i));
    }
  }

  private void ensureCapacity(int count) {
    if (names.length < size + count) {
      var newLength = Math.max(size + count, size * 2);
      names = Arrays.copyOf(names, newLength);
    }
  }

  public int addSlot(FrameSlotKind kind, @Nullable Identifier name, @Nullable Object info) {
    ensureCapacity(1);
    names[size] = name;
    size++;
    return underlying.addSlot(kind, name, info);
  }

  public int findSlot(Identifier identifier) {
    // go backwards to account for shadowed variables
    // (this happens in the case of nested for generators).
    for (var i = size - 1; i >= 0; i--) {
      if (names[i] != null && names[i].equals(identifier)) {
        return i;
      }
    }
    return -1;
  }

  public FrameDescriptor build() {
    return underlying.build();
  }
}
