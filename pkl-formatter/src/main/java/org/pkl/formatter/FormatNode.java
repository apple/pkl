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

import java.util.List;
import java.util.Set;

sealed interface FormatNode
    permits Text,
        Empty,
        Line,
        ForceLine,
        SpaceOrLine,
        Space,
        Indent,
        Nodes,
        Group,
        MultilineStringGroup,
        IfWrap {

  default int width(Set<Integer> wrapped) {
    if (this instanceof Nodes n) {
      return n.nodes().stream().mapToInt(node -> node.width(wrapped)).sum();
    } else if (this instanceof Group g) {
      return g.nodes().stream().mapToInt(node -> node.width(wrapped)).sum();
    } else if (this instanceof Indent i) {
      return i.nodes().stream().mapToInt(node -> node.width(wrapped)).sum();
    } else if (this instanceof IfWrap iw) {
      return wrapped.contains(iw.id()) ? iw.ifWrap().width(wrapped) : iw.ifNotWrap().width(wrapped);
    } else if (this instanceof Text t) {
      return t.text().length();
    } else if (this instanceof SpaceOrLine || this instanceof Space) {
      return 1;
    } else if (this instanceof ForceLine || this instanceof MultilineStringGroup) {
      return Generator.MAX;
    } else {
      return 0;
    }
  }
}

record Text(String text) implements FormatNode {}

record Empty() implements FormatNode {
  static final Empty INSTANCE = new Empty();
}

record Line() implements FormatNode {
  static final Line INSTANCE = new Line();
}

record ForceLine() implements FormatNode {
  static final ForceLine INSTANCE = new ForceLine();
}

record SpaceOrLine() implements FormatNode {
  static final SpaceOrLine INSTANCE = new SpaceOrLine();
}

record Space() implements FormatNode {
  static final Space INSTANCE = new Space();
}

record Indent(List<FormatNode> nodes) implements FormatNode {}

record Nodes(List<FormatNode> nodes) implements FormatNode {}

record Group(int id, List<FormatNode> nodes) implements FormatNode {}

record MultilineStringGroup(int endQuoteCol, List<FormatNode> nodes) implements FormatNode {}

record IfWrap(int id, FormatNode ifWrap, FormatNode ifNotWrap) implements FormatNode {}
