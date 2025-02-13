/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.regex.Pattern;
import org.pkl.core.parser.Lexer;

// To instantiate this class, use ValueRenderers.pcf().
final class PcfRenderer implements ValueRenderer {
  private static final String LINE_SEPARATOR = "\n";

  private final Writer writer;
  private final String indent;
  private final boolean omitNullProperties;
  private final ValueFormatter valueFormatter;

  private String currIndent = "";

  public PcfRenderer(
      Writer writer, String indent, boolean omitNullProperties, boolean useCustomStringDelimiters) {
    this.writer = writer;
    this.indent = indent;
    this.omitNullProperties = omitNullProperties;
    this.valueFormatter = new ValueFormatter(true, useCustomStringDelimiters);
  }

  @Override
  public void renderDocument(Object value) {
    if (!(value instanceof Composite composite)) {
      throw new RendererException(
          String.format(
              "The top-level value of a Pcf document must have type `Composite`, but got type `%s`.",
              value.getClass().getTypeName()));
    }

    new Visitor().doVisitProperties(composite.getProperties());
  }

  @Override
  public void renderValue(Object value) {
    new Visitor().visit(value);
  }

  private class Visitor implements ValueVisitor {
    @Override
    public void visitString(String value) {
      try {
        valueFormatter.formatStringValue(value, currIndent + indent, writer);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void visitInt(Long value) {
      write(value.toString());
    }

    @Override
    public void visitFloat(Double value) {
      write(value.toString());
    }

    @Override
    public void visitBoolean(Boolean value) {
      write(value.toString());
    }

    @Override
    public void visitDuration(Duration value) {
      write(value.toString());
    }

    @Override
    public void visitDataSize(DataSize value) {
      write(value.toString());
    }

    @Override
    public void visitPair(Pair<?, ?> value) {
      doVisitIterable(value, "Pair(");
    }

    @Override
    public void visitList(List<?> value) {
      doVisitIterable(value, "List(");
    }

    @Override
    public void visitSet(Set<?> value) {
      doVisitIterable(value, "Set(");
    }

    @Override
    public void visitMap(Map<?, ?> value) {
      var first = true;
      write("Map(");
      for (var entry : value.entrySet()) {
        if (first) {
          first = false;
        } else {
          write(", ");
        }
        if (entry.getKey() instanceof Composite) {
          write("new ");
        }
        visit(entry.getKey());
        write(", ");
        if (entry.getValue() instanceof Composite) {
          write("new ");
        }
        visit(entry.getValue());
      }
      write(')');
    }

    @Override
    public void visitObject(PObject value) {
      doVisitComposite(value);
    }

    @Override
    public void visitModule(PModule value) {
      doVisitComposite(value);
    }

    @Override
    public void visitClass(PClass value) {
      throw new RendererException(
          String.format(
              "Values of type `Class` cannot be rendered as Pcf. Value: %s",
              value.getSimpleName()));
    }

    @Override
    public void visitTypeAlias(TypeAlias value) {
      throw new RendererException(
          String.format(
              "Values of type `TypeAlias` cannot be rendered as Pcf. Value: %s",
              value.getSimpleName()));
    }

    @Override
    public void visitNull() {
      write("null");
    }

    @Override
    public void visitRegex(Pattern value) {
      write("Regex(");
      visitString(value.pattern());
      write(')');
    }

    private void doVisitIterable(Iterable<?> iterable, String prefix) {
      var first = true;
      write(prefix);
      for (var elem : iterable) {
        if (first) {
          first = false;
        } else {
          write(", ");
        }
        if (elem == null) { // unevaluated property
          write("?");
          continue;
        }
        if (elem instanceof Composite) {
          write("new ");
        }
        visit(elem);
      }
      write(')');
    }

    private void doVisitComposite(Composite composite) {
      if (composite.getProperties().isEmpty()) {
        write("{}");
        return;
      }

      write('{');
      write(LINE_SEPARATOR);
      currIndent += indent;
      doVisitProperties(composite.getProperties());
      currIndent = currIndent.substring(0, currIndent.length() - indent.length());
      write(currIndent);
      write('}');
    }

    private void doVisitProperties(Map<String, Object> properties) {
      properties.forEach(
          (name, value) -> {
            if (omitNullProperties && value instanceof PNull) return;

            write(currIndent);
            writeIdentifier(name);

            if (value == null) { // unevaluated property
              write(" = ?");
            } else if (value instanceof Composite) {
              write(' ');
              visit(value);
            } else {
              write(" = ");
              visit(value);
            }

            write(LINE_SEPARATOR);
          });
    }

    private void write(char ch) {
      try {
        writer.write(ch);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    private void write(String str) {
      try {
        writer.write(str);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    private void writeIdentifier(String identifier) {
      if (Lexer.isRegularIdentifier(identifier)) {
        write(identifier);
      } else {
        write('`');
        write(identifier);
        write('`');
      }
    }
  }
}
