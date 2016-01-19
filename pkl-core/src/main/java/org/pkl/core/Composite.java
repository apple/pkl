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
package org.pkl.core;

import java.util.Map;
import org.pkl.core.util.Nullable;

/** A container of properties. */
public interface Composite extends Value {
  /** Shorthand for {@code getProperties.containsKey(name)}. */
  default boolean hasProperty(String name) {
    return getProperties().containsKey(name);
  }

  /**
   * Returns the value of the property with the given name, or throws {@link
   * NoSuchPropertyException} if no such property exists.
   */
  Object getProperty(String name);

  /** Shorthand for {@code getProperties().get(name)}; */
  default @Nullable Object getPropertyOrNull(String name) {
    return getProperties().get(name);
  }

  /**
   * Same as {@link #getProperty} except that this method returns {@code null} instead of {@link
   * PNull} for Pkl value {@code null}.
   */
  default @Nullable Object get(String name) {
    var result = getProperty(name);
    return result instanceof PNull ? null : result;
  }

  /** Returns the properties of this composite. */
  Map<String, Object> getProperties();
}
