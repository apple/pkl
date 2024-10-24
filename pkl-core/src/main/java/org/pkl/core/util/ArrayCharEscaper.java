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
package org.pkl.core.util;

import java.util.*;

public final class ArrayCharEscaper extends AbstractCharEscaper {
  private final char minEscape;
  private final @Nullable String[] replacements;

  private ArrayCharEscaper(char minEscape, String[] replacements) {
    this.minEscape = minEscape;
    this.replacements = replacements;
  }

  public static Builder builder(int maxAllowedSize) {
    return new Builder(maxAllowedSize);
  }

  public static Builder builder() {
    return new Builder(256);
  }

  @Override
  protected @Nullable String findReplacement(char ch) {
    var index = ch - minEscape;
    return index >= 0 && index < replacements.length ? replacements[index] : null;
  }

  public static class Builder {
    private final int maxAllowedSize;
    private final List<Pair<Character, String>> escapes = new ArrayList<>();

    private char minEscape = Character.MAX_VALUE;
    private char maxEscape = Character.MIN_VALUE;

    private Builder(int maxAllowedSize) {
      this.maxAllowedSize = maxAllowedSize;
    }

    public Builder withEscape(char ch, String escape) {
      if (ch >= Character.MIN_SURROGATE) {
        throw new IllegalArgumentException(String.valueOf(ch));
      }
      escapes.add(Pair.of(ch, escape));
      if (ch < minEscape) minEscape = ch;
      if (ch > maxEscape) maxEscape = ch;
      return this;
    }

    public ArrayCharEscaper build() {
      var arraySize = escapes.isEmpty() ? 0 : maxEscape - minEscape + 1;
      if (arraySize > maxAllowedSize) {
        throw new IllegalStateException(
            "Actual array size "
                + arraySize
                + " is greater than maximum allowed size "
                + maxAllowedSize);
      }
      var replacements = new String[arraySize];
      for (var pair : escapes) {
        replacements[pair.first - minEscape] = pair.second;
      }
      return new ArrayCharEscaper(minEscape, replacements);
    }
  }
}
