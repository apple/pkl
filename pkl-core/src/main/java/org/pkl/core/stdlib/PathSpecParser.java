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
package org.pkl.core.stdlib;

import java.util.ArrayList;
import java.util.Collections;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmException;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmValueConverter;

final class PathSpecParser {
  /**
   * Parses the path spec of a path-based Pkl output converter into a reverse array of path parts.
   * Each part of the returned array is one of the following: - an Identifier (to be matched against
   * property names) - a String (to be matched against map(ping) keys of type String) - a
   * VmValueConverter.WILDCARD_PROPERTY (matches all property names) - a
   * VmValueConverter.WILDCARD_ELEMENT (matches all list(ing)/set/map(ping) keys) - a
   * VmValueConverter.TOP_LEVEL_VALUE
   */
  Object[] parse(String pathSpec) {
    var result = new ArrayList<>();

    // 0 -> start or after leading `^`
    // 1 -> in property
    // 2 -> in element
    // 3 -> after `]`
    // 4 -> after `.*`
    // 5 -> after `[*`
    var state = 0;

    var partStartIdx = 0;
    var codePoints = pathSpec.codePoints().toArray();
    for (var idx = 0; idx < codePoints.length; idx++) {
      switch (codePoints[idx]) {
        case '^':
          if (idx != 0) throw invalidPattern(pathSpec);
          result.add(VmValueConverter.TOP_LEVEL_VALUE);
          partStartIdx = 1;
          break;
        case '.':
          switch (state) {
            case 1:
              int count = idx - partStartIdx;
              if (count == 0) throw invalidPattern(pathSpec);
              result.add(Identifier.get(new String(codePoints, partStartIdx, count)));
              break;
            case 3:
            case 4:
              break;
            default:
              throw invalidPattern(pathSpec);
          }
          partStartIdx = idx + 1;
          state = 1;
          break;
        case '[':
          switch (state) {
            case 1:
              int count = idx - partStartIdx;
              if (count == 0) throw invalidPattern(pathSpec);
              result.add(Identifier.get(new String(codePoints, partStartIdx, count)));
              break;
            case 0:
            case 3:
            case 4:
              break;
            default:
              throw invalidPattern(pathSpec);
          }
          partStartIdx = idx + 1;
          state = 2;
          break;
        case ']':
          switch (state) {
            case 2:
              int count = idx - partStartIdx;
              if (count == 0) throw invalidPattern(pathSpec);
              result.add(new String(codePoints, partStartIdx, count));
              break;
            case 5:
              break;
            default:
              throw invalidPattern(pathSpec);
          }
          state = 3;
          break;
        case '*':
          switch (state) {
            case 0:
            case 1:
              if (partStartIdx != idx) throw invalidPattern(pathSpec);
              result.add(VmValueConverter.WILDCARD_PROPERTY);
              state = 4;
              break;
            case 2:
              if (partStartIdx != idx) throw invalidPattern(pathSpec);
              result.add(VmValueConverter.WILDCARD_ELEMENT);
              state = 5;
              break;
            default:
              throw invalidPattern(pathSpec);
          }
          break;
        default:
          if (state > 2) throw invalidPattern(pathSpec);
          if (state == 0) state = 1;
          break;
      }
    }

    switch (state) {
      case 0:
        if (result.isEmpty()) {
          // "" matches top-level value (deprecated in 0.15, use "^" instead)
          result.add(VmValueConverter.TOP_LEVEL_VALUE);
        }
        break;
      case 1:
        var count = codePoints.length - partStartIdx;
        if (count == 0) throw invalidPattern(pathSpec);
        result.add(Identifier.get(new String(codePoints, partStartIdx, count)));
        break;
      case 3:
      case 4:
        break;
      default:
        throw invalidPattern(pathSpec);
    }

    Collections.reverse(result);
    return result.toArray();
  }

  private static VmException invalidPattern(String pathSpec) {
    return new VmExceptionBuilder().evalError("invalidConverterPath", pathSpec).build();
  }
}
