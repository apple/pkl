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

import java.util.HashSet;
import java.util.Set;

final class Generator {

  static final int MAX = 100;
  private static final String INDENT = "  ";

  private final Appendable buf;
  private int indent = 0;
  private int size = 0;
  private final Set<Integer> wrapped = new HashSet<>();
  private boolean shouldAddIndent = false;

  Generator(Appendable buf) {
    this.buf = buf;
  }

  void generate(FormatNode node) {
    node(node, Wrap.DETECT);
  }

  @SuppressWarnings("StatementWithEmptyBody")
  private void node(FormatNode node, Wrap wrap) {
    if (node instanceof Empty) {
      // nothing
    } else if (node instanceof Nodes n) {
      for (var child : n.nodes()) {
        node(child, wrap);
      }
    } else if (node instanceof Group g) {
      var width = 0;
      for (var child : g.nodes()) {
        width += child.width(wrapped);
      }
      var groupWrap = wrap;
      if (size + width > MAX) {
        wrapped.add(g.id());
        groupWrap = Wrap.ENABLED;
      } else {
        groupWrap = Wrap.DETECT;
      }
      for (var child : g.nodes()) {
        node(child, groupWrap);
      }
    } else if (node instanceof IfWrap iw) {
      if (wrapped.contains(iw.id())) {
        node(iw.ifWrap(), Wrap.ENABLED);
      } else {
        node(iw.ifNotWrap(), wrap);
      }
    } else if (node instanceof Text t) {
      text(t.text());
    } else if (node instanceof Line) {
      if (wrap.isEnabled()) {
        newline(true);
      }
    } else if (node instanceof ForceLine) {
      newline(true);
    } else if (node instanceof SpaceOrLine) {
      if (wrap.isEnabled()) {
        newline(true);
      } else {
        text(" ");
      }
    } else if (node instanceof Space) {
      text(" ");
    } else if (node instanceof Indent ind) {
      if (wrap.isEnabled() && !ind.nodes().isEmpty()) {
        size += INDENT.length();
        indent++;
        for (var child : ind.nodes()) {
          node(child, wrap);
        }
        indent--;
      } else {
        for (var child : ind.nodes()) {
          node(child, wrap);
        }
      }
    } else if (node instanceof MultilineStringGroup multi) {
      var indentLength = indent * INDENT.length();
      var oldIndent = indentFor(multi);
      var previousNewline = false;
      var nodes = multi.nodes();
      for (var i = 0; i < nodes.size(); i++) {
        var child = nodes.get(i);
        if (child instanceof ForceLine) {
          newline(false);
        } else if (child instanceof Text t
            && previousNewline
            && t.text().isBlank()
            && t.text().length() == oldIndent
            && nodes.get(i + 1) instanceof ForceLine) {
          // skip blank line indentation that will be repositioned
        } else if (child instanceof Text t && previousNewline) {
          text(reposition(t.text(), multi.endQuoteCol() - 1, indentLength));
        } else {
          node(child, Wrap.DETECT);
        }
        previousNewline = child instanceof ForceLine;
      }
    }
  }

  private void text(String value) {
    try {
      if (shouldAddIndent) {
        for (var i = 0; i < indent; i++) {
          buf.append(INDENT);
        }
        shouldAddIndent = false;
      }
      size += value.length();
      buf.append(value);
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  private void newline(boolean shouldIndent) {
    try {
      size = INDENT.length() * indent;
      buf.append('\n');
      shouldAddIndent = shouldIndent;
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  // accept text indented by originalOffset characters (tabs or spaces)
  // and return it indented by newOffset characters (spaces only)
  private static String reposition(String text, int originalOffset, int newOffset) {
    return " ".repeat(newOffset) + text.substring(originalOffset);
  }

  private static int indentFor(MultilineStringGroup multi) {
    var nodes = multi.nodes();
    if (nodes.size() < 2) return 0;
    var beforeLast = nodes.get(nodes.size() - 2);
    return beforeLast instanceof Text t ? t.text().length() : 0;
  }
}
