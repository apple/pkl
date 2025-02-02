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
package org.pkl.core.newparser.cst;

import java.util.List;
import java.util.Objects;
import org.pkl.core.newparser.Span;

public sealed interface StringConstantPart extends Node {

  final class StringNewline implements StringConstantPart {
    private final Span span;
    private Node parent;

    public StringNewline(Span span) {
      this.span = span;
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    @Override
    public List<Node> children() {
      return List.of();
    }

    @Override
    public String toString() {
      return "StringNewline{span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      StringNewline that = (StringNewline) o;
      return Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(span);
    }
  }

  final class ConstantPart implements StringConstantPart {
    private final String str;
    private final Span span;
    private Node parent;

    public ConstantPart(String str, Span span) {
      this.str = str;
      this.span = span;
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    @Override
    public List<Node> children() {
      return List.of();
    }

    public String getStr() {
      return str;
    }

    @Override
    public String toString() {
      return "ConstantPart{str='" + str + '\'' + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ConstantPart that = (ConstantPart) o;
      return Objects.equals(str, that.str) && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(str, span);
    }
  }

  final class StringUnicodeEscape implements StringConstantPart {
    private final String escape;
    private final Span span;
    private Node parent;

    public StringUnicodeEscape(String escape, Span span) {
      this.escape = escape;
      this.span = span;
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    @Override
    public List<Node> children() {
      return List.of();
    }

    public String getEscape() {
      return escape;
    }

    @Override
    public String toString() {
      return "StringUnicodeEscape{escape='" + escape + '\'' + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      StringUnicodeEscape that = (StringUnicodeEscape) o;
      return Objects.equals(escape, that.escape) && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(escape, span);
    }
  }

  final class StringEscape implements StringConstantPart {
    private final EscapeType type;
    private final Span span;
    private Node parent;

    public StringEscape(EscapeType type, Span span) {
      this.type = type;
      this.span = span;
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    @Override
    public List<Node> children() {
      return List.of();
    }

    public EscapeType getType() {
      return type;
    }

    @Override
    public String toString() {
      return "StringEscape{type=" + type + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      StringEscape that = (StringEscape) o;
      return type == that.type && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, span);
    }
  }

  enum EscapeType {
    NEWLINE,
    TAB,
    RETURN,
    QUOTE,
    BACKSLASH
  }
}
