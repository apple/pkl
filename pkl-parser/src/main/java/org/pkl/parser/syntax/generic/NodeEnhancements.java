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
package org.pkl.parser.syntax.generic;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import org.pkl.parser.syntax.Node;
import org.pkl.parser.util.Nullable;

public class NodeEnhancements {
  private static final WeakHashMap<Node, Enhancement> enhancements = new WeakHashMap<>();

  public static void addAffix(Node node, Comment affix) {
    var enh = enhancements.computeIfAbsent(node, (k) -> new Enhancement(new ArrayList<>()));
    enh.affixes.add(affix);
  }

  public static @Nullable List<Comment> affixes(Node node) {
    var enh = enhancements.get(node);
    if (enh == null) return null;
    return enh.affixes;
  }

  record Enhancement(List<Comment> affixes) {}
}
