/*
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
package org.pkl.core.stdlib;

import org.pkl.core.runtime.Identifier;

final class PathConverterSupport {
  private PathConverterSupport() {}

  public static boolean pathMatches(Iterable<Object> pathSpec, Iterable<Object> path) {
    var pathIterator = path.iterator();
    for (var partSpec : pathSpec) {
      if (!pathIterator.hasNext() || !isMatch(pathIterator.next(), partSpec)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isMatch(Object pathPart, Object pathPartSpec) {
    if (pathPartSpec == PklConverter.WILDCARD_PROPERTY) {
      return pathPart == PklConverter.WILDCARD_PROPERTY || pathPart instanceof Identifier;
    }

    if (pathPartSpec == PklConverter.WILDCARD_ELEMENT) {
      return pathPart instanceof Long
          || pathPart instanceof String
          || !(pathPart instanceof Identifier || pathPart == PklConverter.TOP_LEVEL_VALUE);
    }

    return pathPart.equals(pathPartSpec);
  }
}
