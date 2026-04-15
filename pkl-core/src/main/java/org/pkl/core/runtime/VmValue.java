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
package org.pkl.core.runtime;

import org.pkl.core.util.Nullable;

public interface VmValue {
  VmClass getVmClass();

  default VmTyped getPrototype() {
    return getVmClass().getPrototype();
  }

  default boolean isPrototype() {
    return false;
  }

  default boolean isDynamic() {
    return this instanceof VmDynamic;
  }

  default boolean isListing() {
    return this instanceof VmListing;
  }

  default boolean isMapping() {
    return this instanceof VmMapping;
  }

  default boolean isTyped() {
    return this instanceof VmTyped;
  }

  /**
   * Tells if this value is a {@link VmCollection}, {@link VmListing}, or {@link VmDynamic} with
   * {@link VmDynamic#hasElements() elements}.
   */
  default boolean isSequence() {
    return false;
  }

  /** Forces recursive (deep) evaluation of this value. */
  void force(boolean allowUndefinedValues);

  Object export();

  void accept(VmValueVisitor visitor);

  <T> T accept(VmValueConverter<T> converter, Iterable<Object> path);

  /** Forces recursive (deep) evaluation of the given value. */
  static void force(Object value, boolean allowUndefinedValues) {
    if (value instanceof VmValue vmValue) {
      vmValue.force(allowUndefinedValues);
    }
  }

  /**
   * Used to export values other than object member values. Such values aren't `@Nullable` (but can
   * be `VmNull`).
   */
  static Object export(Object value) {
    if (value instanceof VmValue vmValue) {
      return vmValue.export();
    }
    return value;
  }

  /** Used to export object member values. Such values are `null` if they haven't been forced. */
  static @Nullable Object exportNullable(@Nullable Object value) {
    if (value instanceof VmValue vmValue) {
      return vmValue.export();
    }
    return value;
  }
}
