/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util.paguro.collections;

import java.util.Set;

/**
 * Implements equals and hashCode() methods compatible with java.util.Set (which ignores order) to
 * make defining unmod sets easier, especially for implementing Map.keySet() and such.
 */
public abstract class AbstractUnmodSet<T> extends AbstractUnmodIterable<T> implements UnmodSet<T> {
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Set)) {
      return false;
    }
    Set that = (Set) other;
    return (size() == that.size()) && containsAll(that);
  }
}
