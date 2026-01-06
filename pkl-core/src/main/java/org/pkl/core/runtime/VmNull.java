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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import org.pkl.core.PNull;
import org.pkl.core.util.Nullable;

@ValueType
public final class VmNull extends VmValue {
  private static final VmNull WITHOUT_DEFAULT = new VmNull(null);

  // worthwhile to create this lazily?
  private final @Nullable Object defaultValue;

  public static VmNull withoutDefault() {
    return WITHOUT_DEFAULT;
  }

  public static VmNull withDefault(@Nullable Object defaultValue) {
    return defaultValue == null ? withoutDefault() : new VmNull(defaultValue);
  }

  public static Object lift(@Nullable Object nullable) {
    return nullable == null ? withoutDefault() : nullable;
  }

  public static @Nullable Object unwrap(Object value) {
    return value instanceof VmNull ? null : value;
  }

  private VmNull(@Nullable Object defaultValue) {
    this.defaultValue = defaultValue;
  }

  public Object getDefaultValue() {
    // defer calling VmDynamic.empty() until this method is called
    return defaultValue == null ? VmDynamic.empty() : defaultValue;
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getNullClass();
  }

  @Override
  public void force(boolean allowUndefinedValues) {
    // do nothing
  }

  @Override
  public Object export() {
    return PNull.getInstance();
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitNull(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertNull(this, path);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    return obj instanceof VmNull;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public String toString() {
    return "null";
  }
}
