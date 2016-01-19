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

import java.util.concurrent.atomic.AtomicBoolean;

/** A mutable boolean in a box. If you need a thread-safe box, use {@link AtomicBoolean} instead. */
public final class MutableBoolean {
  private boolean value;

  public MutableBoolean(boolean value) {
    this.value = value;
  }

  public boolean get() {
    return value;
  }

  public boolean getAndSetFalse() {
    if (!value) return false;

    value = false;
    return true;
  }

  public void set(boolean value) {
    this.value = value;
  }
}
