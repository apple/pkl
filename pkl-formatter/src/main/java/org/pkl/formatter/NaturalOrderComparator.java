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
package org.pkl.formatter;

import java.util.Comparator;

final class NaturalOrderComparator implements Comparator<String> {

  private final boolean ignoreCase;

  NaturalOrderComparator(boolean ignoreCase) {
    this.ignoreCase = ignoreCase;
  }

  NaturalOrderComparator() {
    this(false);
  }

  @Override
  public int compare(String s1, String s2) {
    var i = 0;
    var j = 0;

    while (i < s1.length() && j < s2.length()) {
      var c1 = ignoreCase ? Character.toLowerCase(s1.charAt(i)) : s1.charAt(i);
      var c2 = ignoreCase ? Character.toLowerCase(s2.charAt(j)) : s2.charAt(j);

      if (Character.isDigit(c1) && Character.isDigit(c2)) {
        var pair1 = getNumber(s1, i);
        var pair2 = getNumber(s2, j);

        var numComparison = Long.compare(pair1.l, pair2.l);
        if (numComparison != 0) {
          return numComparison;
        }
        i = pair1.i;
        j = pair2.i;
      } else {
        var charComparison = Character.compare(c1, c2);
        if (charComparison != 0) {
          return charComparison;
        }
        i++;
        j++;
      }
    }

    return Integer.compare(s1.length(), s2.length());
  }

  private static LongAndInt getNumber(String s, int startIndex) {
    var i = startIndex;
    while (i < s.length() && Character.isDigit(s.charAt(i))) {
      i++;
    }
    try {
      var number = Long.parseLong(s, startIndex, i, 10);
      return new LongAndInt(number, i);
    } catch (NumberFormatException e) {
      return new LongAndInt(0L, i);
    }
  }

  private record LongAndInt(long l, int i) {}
}
