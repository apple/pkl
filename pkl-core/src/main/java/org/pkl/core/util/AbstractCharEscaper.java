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

public abstract class AbstractCharEscaper {
  protected abstract @Nullable String findReplacement(char ch);

  public String escape(String str) {
    // Optimization: Return original string if no escaping required.
    // Because only escaping of chars is supported,
    // it's safe to iterate over chars instead of code points.
    var length = str.length();
    for (var i = 0; i < length; i++) {
      var ch = str.charAt(i);
      if (findReplacement(ch) != null) {
        return doEscape(str, i, new StringBuilder(length * 2)).toString();
      }
    }
    return str;
  }

  public void escape(String str, StringBuilder builder) {
    doEscape(str, 0, builder);
  }

  private StringBuilder doEscape(String str, int offset, StringBuilder builder) {
    var start = 0;
    var length = str.length();
    for (var i = offset; i < length; i++) {
      var ch = str.charAt(i);
      var replacement = findReplacement(ch);
      if (replacement == null) continue;
      builder.append(str, start, i).append(replacement);
      start = i + 1;
    }
    if (start < length) {
      builder.append(str, start, length);
    }
    return builder;
  }
}
