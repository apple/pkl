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
package org.pkl.core.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.pkl.core.ValueFormatter;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.StringSimilarity;

public final class KeyLookupSuggestions {
  private KeyLookupSuggestions() {}

  private static final StringSimilarity STRING_SIMILARITY = new StringSimilarity();
  // 0.77 is just about low enough to consider two three-character
  // keys that differ in their first character similar
  private static final double SIMILARITY_THRESHOLD = 0.77;

  public static List<Candidate> forMap(VmMap map, String key) {
    var candidates = new ArrayList<Candidate>();

    map.forEach(
        entry -> {
          if (!(entry.getKey() instanceof String entryKey)) return;
          var similarity = STRING_SIMILARITY.similarity(entryKey, key);
          if (similarity >= SIMILARITY_THRESHOLD) {
            candidates.add(new Candidate(entryKey, similarity));
          }
        });

    candidates.sort(Comparator.naturalOrder());
    return candidates;
  }

  public static List<Candidate> forObject(VmObjectLike object, String key) {
    var candidates = new ArrayList<Candidate>();

    object.iterateMemberValues(
        (memberKey, member, value) -> {
          if (!(memberKey instanceof String stringKey)) return true;
          var similarity = STRING_SIMILARITY.similarity(stringKey, key);
          if (similarity >= SIMILARITY_THRESHOLD) {
            candidates.add(new Candidate(stringKey, similarity));
          }
          return true;
        });

    candidates.sort(Comparator.naturalOrder());
    return candidates;
  }

  public static final class Candidate implements Comparable<Candidate> {
    private final String key;
    private final double similarity;

    public Candidate(String key, double similarity) {
      this.key = key;
      this.similarity = similarity;
    }

    // note: not consistent with equals
    @Override
    public int compareTo(Candidate other) {
      return Double.compare(other.similarity, similarity);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      return (obj instanceof Candidate candidate && candidate.key.equals(key));
    }

    @Override
    public int hashCode() {
      return key.hashCode();
    }

    @Override
    public String toString() {
      return ValueFormatter.basic().formatStringValue(key, "");
    }
  }
}
