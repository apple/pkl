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

import com.oracle.truffle.api.object.Shape;

/** Factory for Truffle {@link Shape} instances used by Pkl objects. */
public final class PklShape {

  /** The root shape for all Pkl object instances. */
  private static final Shape ROOT_SHAPE = Shape.newBuilder().build();

  private PklShape() {}

  /**
   * Returns the root shape for Pkl objects.
   *
   * <p>This is the base shape from which all instance shapes derive. Properties are added
   * dynamically as values are cached via {@link
   * com.oracle.truffle.api.object.DynamicObjectLibrary#put}.
   */
  public static Shape getRootShape() {
    return ROOT_SHAPE;
  }
}
