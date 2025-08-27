/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.util.ArrayDeque;
import java.util.Deque;
import org.pkl.core.ValueFormatter;
import org.pkl.core.util.MutableBoolean;
import org.pkl.parser.Lexer;

/**
 * Renders values for use in REPL and error messages. Does not force values to avoid consecutive
 * errors and keep output succinct. (Alternatively it could force and recover from errors.)
 *
 * <p>Currently prints fully qualified class name for outermost object (if rendered value is an
 * object) and omits class names otherwise.
 */
public final class VmValueRenderer {
  private final int lengthLimit;
  private final String leadingOrTrailingNewline;
  private final String interiorNewline;
  private final String indent;
  private String currIndent = "";
  private static final VmValueRenderer maxSingleLine =
      new VmValueRenderer(Integer.MAX_VALUE, " ", "; ", "");

  public static VmValueRenderer singleLine(int lengthLimit) {
    if (lengthLimit == Integer.MAX_VALUE) {
      return maxSingleLine;
    }
    return new VmValueRenderer(lengthLimit, " ", "; ", "");
  }

  public static VmValueRenderer multiLine(int lengthLimit) {
    return new VmValueRenderer(lengthLimit, "\n", "\n", "  ");
  }

  private VmValueRenderer(
      int lengthLimit, String leadingOrTrailingNewline, String interiorNewline, String indent) {
    this.lengthLimit = lengthLimit;
    this.leadingOrTrailingNewline = leadingOrTrailingNewline;
    this.interiorNewline = interiorNewline;
    this.indent = indent;
  }

  public String render(Object value) {
    var builder = new StringBuilder();
    render(value, builder);
    return builder.toString();
  }

  private void render(Object value, StringBuilder builder) {
    try {
      new Visitor(builder).visit(value);
    } catch (LengthLimitReached ignored) {
    }
  }

  private enum Context {
    EXPLICIT,
    IMPLICIT
  }

  private class Visitor implements VmValueVisitor {
    private final StringBuilder builder;
    private final int initialLength;
    private final ValueFormatter valueFormatter;

    private final Deque<Context> contexts = new ArrayDeque<>();

    private Visitor(StringBuilder builder) {
      this.builder = builder;
      this.valueFormatter = ValueFormatter.basic();
      initialLength = builder.length();
      contexts.push(Context.EXPLICIT);
    }

    @Override
    public void visitString(String value) {
      valueFormatter.formatStringValue(value, "", builder);
      checkLengthLimit();
    }

    @Override
    public void visitBoolean(Boolean value) {
      append(value);
    }

    @Override
    public void visitInt(Long value) {
      append(value);
    }

    @Override
    public void visitFloat(Double value) {
      append(value);
    }

    @Override
    public void visitDuration(VmDuration value) {
      append(value);
    }

    @Override
    public void visitDataSize(VmDataSize value) {
      append(value);
    }

    private void renderByteSize(VmDataSize size) {
      var value = size.getValue();
      if (value % 1 == 0) {
        append((int) value);
      } else if ((value * 10) % 1 == 0) {
        append(String.format("%.1f", value));
      } else {
        append(String.format("%.2f", value));
      }
      append(".");
      append(size.getUnit());
    }

    @Override
    public void visitBytes(VmBytes value) {
      append("Bytes(");
      // truncate bytes if over 8 bytes
      renderByteElems(value, Math.min(value.getLength(), 8));
      if (value.getLength() > 8) {
        append(", ... <total size: ");
        renderByteSize(value.getSize());
        append(">");
      }
      append(")");
    }

    private void renderByteElems(VmBytes value, int limit) {
      var isFirst = true;
      var bytes = value.getBytes();
      for (var i = 0; i < limit; i++) {
        if (isFirst) {
          isFirst = false;
        } else {
          append(", ");
        }
        append(Byte.toUnsignedInt(bytes[i]));
      }
    }

    @Override
    public void visitPair(VmPair value) {
      append("Pair(");
      visit(value.getFirst());
      append(", ");
      visit(value.getSecond());
      append(')');
    }

    @Override
    public void visitRegex(VmRegex value) {
      append(value);
    }

    @Override
    public void visitIntSeq(VmIntSeq value) {
      append(value);
    }

    @Override
    public void visitList(VmList value) {
      doVisitCollection(value, "List(");
    }

    @Override
    public void visitSet(VmSet value) {
      doVisitCollection(value, "Set(");
    }

    @Override
    public void visitMap(VmMap value) {
      contexts.push(Context.EXPLICIT);

      append("Map(");
      var isFirst = true;
      for (var entry : value) {
        if (isFirst) {
          isFirst = false;
        } else {
          append(", ");
        }

        visit(entry.getKey());

        append(", ");

        visit(entry.getValue());
      }
      append(')');

      contexts.pop();
    }

    @Override
    public void visitDynamic(VmDynamic value) {
      var context = contexts.peek();

      if (context == Context.EXPLICIT) {
        append("new Dynamic ");
      }

      doVisitObject(value);
    }

    @Override
    public void visitTyped(VmTyped value) {
      writeClassName(value);
      doVisitObject(value);
    }

    @Override
    public void visitListing(VmListing value) {
      var context = contexts.peek();

      if (context == Context.EXPLICIT) {
        append("new Listing ");
      }

      doVisitObject(value);
    }

    @Override
    public void visitMapping(VmMapping value) {
      var context = contexts.peek();

      if (context == Context.EXPLICIT) {
        append("new Mapping ");
      }

      doVisitObject(value);
    }

    @Override
    public void visitFunction(VmFunction value) {
      writeClassName(value);
      append("{}");
    }

    @Override
    public void visitClass(VmClass value) {
      append(value);
    }

    @Override
    public void visitTypeAlias(VmTypeAlias value) {
      append(value);
    }

    @Override
    public void visitNull(VmNull value) {
      append("null");
    }

    @Override
    public void visitReference(VmReference value) {
      contexts.push(Context.EXPLICIT);
      append("Reference(");
      visit(value.getRootValue());
      append(")");
      for (var elem : value.getPath()) {
        visit(elem);
      }
      contexts.pop();
    }

    @Override
    public void visitReferenceAccess(VmReference.Access value) {
      if (value.isProperty()) {
        append(".");
        writeIdentifier(value.getProperty());
      } else {
        append("[");
        visit(value.getKey());
        append("]");
      }
    }

    private void append(Object value) {
      builder.append(value);
      checkLengthLimit();
    }

    private void checkLengthLimit() {
      if (builder.length() - initialLength < lengthLimit) return;

      builder.delete(lengthLimit - 3, builder.length());
      builder.append("...");
      throw new LengthLimitReached();
    }

    private void doVisitCollection(VmCollection collection, String prefix) {
      contexts.push(Context.EXPLICIT);

      append(prefix);
      var isFirst = true;
      for (var elem : collection) {
        if (isFirst) {
          isFirst = false;
        } else {
          append(", ");
        }
        visit(elem);
      }
      append(')');

      contexts.pop();
    }

    private void doVisitObject(VmObjectLike object) {
      append('{');
      var lengthAfterOpeningDelimiter = builder.length();

      currIndent += indent;

      var isEmpty = new MutableBoolean(true);
      object.iterateMemberValues(
          (key, member, value) -> {
            // don't render type definitions
            if (member.isClass() || member.isTypeAlias()) return true;

            if (isEmpty.get()) {
              append(leadingOrTrailingNewline);
              isEmpty.set(false);
            }

            append(currIndent);

            if (member.isProp()) {
              contexts.push(Context.IMPLICIT);
              writeIdentifier(key.toString());
              if (value instanceof VmObjectLike) {
                append(' ');
              } else {
                append(" = ");
              }
            } else if (member.isElement()) {
              contexts.push(Context.EXPLICIT);
            } else {
              assert member.isEntry();
              contexts.push(Context.EXPLICIT);
              append('[');
              visit(key);
              append(']');
              contexts.pop();
              contexts.push(Context.IMPLICIT);
              if (value instanceof VmObjectLike) {
                append(' ');
              } else {
                append(" = ");
              }
            }

            if (value == null) { // not forced
              append('?');
            } else {
              visit(value);
            }

            contexts.pop();
            append(interiorNewline);
            return true;
          });

      if (!isEmpty.get()) {
        // replace last interiorNewline with leadingOrTrailingNewline
        builder.delete(builder.length() - interiorNewline.length(), builder.length());
        append(leadingOrTrailingNewline);
      }

      currIndent = currIndent.substring(0, currIndent.length() - indent.length());
      if (builder.length() > lengthAfterOpeningDelimiter) {
        append(currIndent);
      }
      append('}');
    }

    private void writeClassName(VmValue value) {
      var context = contexts.peek();
      if (context == Context.IMPLICIT) return;

      VmClass clazz = value.getVmClass();
      append("new ");
      append(clazz.getSimpleName());
      append(' ');
    }

    private void writeIdentifier(String identifier) {
      if (Lexer.isRegularIdentifier(identifier)) {
        append(identifier);
      } else {
        append('`');
        append(identifier);
        append('`');
      }
    }
  }

  private static final class LengthLimitReached extends RuntimeException {
    @Override
    public Throwable fillInStackTrace() {
      return this;
    }
  }
}
