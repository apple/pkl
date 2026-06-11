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
import org.jspecify.annotations.Nullable;

/** A wrapper for Truffle's {@link FrameDescriptor.Builder}, but also gives us the current size. */
public class FrameDescriptorBuilder {

  private @Nullable Identifier[] names;
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

  private void ensureCapacity(int count) {
    if (names.length < size + count) {
      var newLength = Math.max(size + count, size * 2);
      names = Arrays.copyOf(names, newLength);
    }
  }

  public FrameSlotVariable addSlot(FrameSlotKind kind, Identifier name, @Nullable Object info) {
    ensureCapacity(1);
    names[size] = name;
    size++;
    var slot = underlying.addSlot(kind, name, info);
    return new FrameSlotVariable(name.toString(), slot);
  }

  public FrameDescriptor build() {
    return underlying.build();
  }

  public int getSize() {
    return size;
  }
}
