/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A mutable reference in a box. If you need a thread-safe box, use {@link AtomicReference} instead.
 */
public final class MutableReference<T> {
  private @Nullable T value;

  public MutableReference(@Nullable T value) {
    this.value = value;
  }

  public T get() {
    assert value != null;
    return value;
  }

  public @Nullable T getOrNull() {
    return value;
  }

  public boolean isNull() {
    return value == null;
  }

  public void set(@Nullable T value) {
    this.value = value;
  }
}
