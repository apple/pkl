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
package org.pkl.core.util;

import java.util.Arrays;
import javax.annotation.concurrent.Immutable;

/**
 * The Jaro–Winkler distance metric is designed and best suited for short strings such as person
 * names, and to detect typos; it is (roughly) a variation of Damerau-Levenshtein, where the
 * substitution of 2 close characters is considered less important then the substitution of 2
 * characters that a far from each other. Jaro-Winkler was developed in the area of record linkage
 * (duplicate detection) (Winkler, 1990). It returns a value in the interval [0.0, 1.0]. The
 * distance is computed as 1 - Jaro-Winkler similarity.
 *
 * @author Thibault Debatty
 */
@Immutable
public class StringSimilarity {
  private static final double DEFAULT_THRESHOLD = 0.7;
  private static final int THREE = 3;
  private static final double JW_COEF = 0.1;
  private final double threshold;

  /** Instantiate with default threshold (0.7). */
  public StringSimilarity() {
    this.threshold = DEFAULT_THRESHOLD;
  }

  /**
   * Instantiate with given threshold to determine when Winkler bonus should be used. Set threshold
   * to a negative value to get the Jaro distance.
   */
  public StringSimilarity(final double threshold) {
    this.threshold = threshold;
  }

  /**
   * Returns the current value of the threshold used for adding the Winkler bonus. The default value
   * is 0.7.
   *
   * @return the current value of the threshold
   */
  public final double getThreshold() {
    return threshold;
  }

  /**
   * Compute Jaro-Winkler similarity.
   *
   * @param s1 The first string to compare.
   * @param s2 The second string to compare.
   * @return The Jaro-Winkler similarity in the range [0, 1]
   */
  public final double similarity(final String s1, final String s2) {
    if (s1.equals(s2)) {
      return 1;
    }

    var mtp = matches(s1, s2);
    var m = (double) mtp[0];
    if (m == 0) return m;

    var j = ((m / s1.length() + m / s2.length() + (m - mtp[1]) / m)) / THREE;
    var jw = j;

    if (j > getThreshold()) {
      jw = j + Math.min(JW_COEF, 1.0 / mtp[THREE]) * mtp[2] * (1 - j);
    }
    return jw;
  }

  /**
   * Return 1 - similarity.
   *
   * @param s1 The first string to compare.
   * @param s2 The second string to compare.
   * @return 1 - similarity.
   */
  public final double distance(final String s1, final String s2) {
    return 1.0 - similarity(s1, s2);
  }

  private int[] matches(final String s1, final String s2) {
    String max, min;
    if (s1.length() > s2.length()) {
      max = s1;
      min = s2;
    } else {
      max = s2;
      min = s1;
    }
    var range = Math.max(max.length() / 2 - 1, 0);
    var matchIndexes = new int[min.length()];
    Arrays.fill(matchIndexes, -1);
    var matchFlags = new boolean[max.length()];
    var matches = 0;
    for (var mi = 0; mi < min.length(); mi++) {
      var c1 = min.charAt(mi);
      for (int xi = Math.max(mi - range, 0), xn = Math.min(mi + range + 1, max.length());
          xi < xn;
          xi++) {
        if (!matchFlags[xi] && c1 == max.charAt(xi)) {
          matchIndexes[mi] = xi;
          matchFlags[xi] = true;
          matches++;
          break;
        }
      }
    }
    var ms1 = new char[matches];
    var ms2 = new char[matches];
    for (int i = 0, si = 0; i < min.length(); i++) {
      if (matchIndexes[i] != -1) {
        ms1[si] = min.charAt(i);
        si++;
      }
    }
    for (int i = 0, si = 0; i < max.length(); i++) {
      if (matchFlags[i]) {
        ms2[si] = max.charAt(i);
        si++;
      }
    }
    var transpositions = 0;
    for (var mi = 0; mi < ms1.length; mi++) {
      if (ms1[mi] != ms2[mi]) {
        transpositions++;
      }
    }
    var prefix = 0;
    for (var mi = 0; mi < min.length(); mi++) {
      if (s1.charAt(mi) == s2.charAt(mi)) {
        prefix++;
      } else {
        break;
      }
    }
    return new int[] {matches, transpositions / 2, prefix, max.length()};
  }
}
