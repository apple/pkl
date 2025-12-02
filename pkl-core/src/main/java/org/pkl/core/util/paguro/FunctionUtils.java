/*
 * Copyright © 2014-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util.paguro;

import org.pkl.core.util.Nullable;

/** A dumping ground for utility functions. */
public class FunctionUtils {

  // I don't want any instances of this class.
  private FunctionUtils() {
    throw new UnsupportedOperationException("No instantiation");
  }

  /**
   * Replace with com.planbase.taint.Taintable.stringify. That method is safe. This one is a joke.
   */
  //  @Deprecated(forRemoval = true)
  public static String stringify(@Nullable Object o) {
    if (o == null) {
      return "null";
    }
    if (o instanceof String) {
      return "\"" + o + "\"";
    }
    return o.toString();
  }
}
