/**
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
package org.pkl.core.util;

import java.util.concurrent.atomic.AtomicLong;

/** A mutable long in a box. If you need a thread-safe box, use {@link AtomicLong} instead. */
public final class MutableLong {
  private long value;

  public MutableLong(long value) {
    this.value = value;
  }

  public long get() {
    return value;
  }

  public void set(long value) {
    this.value = value;
  }

  public long getAndIncrement() {
    return value++;
  }
}
