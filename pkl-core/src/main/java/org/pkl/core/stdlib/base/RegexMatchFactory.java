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
package org.pkl.core.stdlib.base;

import java.util.regex.MatchResult;
import org.pkl.core.runtime.BaseModule;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.VmObjectFactory;
import org.pkl.core.util.Pair;

final class RegexMatchFactory {
  private RegexMatchFactory() {}

  static VmTyped create(Pair<MatchResult, Integer> extraStorage) {
    return factory.create(extraStorage);
  }

  private static final VmObjectFactory<Pair<MatchResult, Integer>> factory =
      new VmObjectFactory<>(BaseModule::getRegexMatchClass);

  static {
    factory
        .addStringProperty(
            "value",
            resultAndIndex -> {
              // index -1 -> regex match, index 0..n -> group match 0..n
              // Regex match and group 0 match differ (only) in that the former has groups.
              var index = resultAndIndex.second;
              return resultAndIndex.first.group(index == -1 ? 0 : index);
            })
        .addIntProperty(
            "start",
            resultAndIndex -> {
              var index = resultAndIndex.second;
              return resultAndIndex.first.start(index == -1 ? 0 : index);
            })
        .addIntProperty(
            "end",
            resultAndIndex -> {
              var index = resultAndIndex.second;
              return resultAndIndex.first.end(index == -1 ? 0 : index);
            })
        .addListProperty(
            "groups",
            resultAndIndex -> {
              var groupIndex = resultAndIndex.second;
              if (groupIndex != -1) {
                // a group match has no groups of its own
                return VmList.EMPTY;
              }
              var result = resultAndIndex.first;
              var builder = VmList.EMPTY.builder();
              for (var i = 0; i <= result.groupCount(); i++) {
                if (result.start(i) == -1) {
                  // group produced no match
                  builder.add(VmNull.withoutDefault());
                } else {
                  builder.add(factory.create(Pair.of(result, i)));
                }
              }
              return builder.build();
            });
  }
}
