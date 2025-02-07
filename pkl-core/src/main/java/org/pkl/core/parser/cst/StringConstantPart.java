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
package org.pkl.core.parser.cst;

import java.util.List;
import java.util.Objects;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

@SuppressWarnings("ALL")
public abstract sealed class StringConstantPart extends AbstractNode {

  public StringConstantPart(Span span, @Nullable List<? extends @Nullable Node> children) {
    super(span, children);
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitStringConstantPart(this);
  }

  public static final class StringNewline extends StringConstantPart {
    public StringNewline(Span span) {
      super(span, null);
    }
  }

  public static final class ConstantPart extends StringConstantPart {
    private final String str;

    public ConstantPart(String str, Span span) {
      super(span, null);
      this.str = str;
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

  public static final class StringUnicodeEscape extends StringConstantPart {
    private final String escape;

    public StringUnicodeEscape(String escape, Span span) {
      super(span, null);
      this.escape = escape;
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

  public static final class StringEscape extends StringConstantPart {
    private final EscapeType type;

    public StringEscape(EscapeType type, Span span) {
      super(span, null);
      this.type = type;
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

  public enum EscapeType {
    NEWLINE,
    TAB,
    RETURN,
    QUOTE,
    BACKSLASH
  }
}
