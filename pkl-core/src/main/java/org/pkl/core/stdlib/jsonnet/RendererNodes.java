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
package org.pkl.core.stdlib.jsonnet;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import java.util.Set;
import java.util.regex.Pattern;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.JsonnetModule;
import org.pkl.core.runtime.VmDataSize;
import org.pkl.core.runtime.VmDuration;
import org.pkl.core.runtime.VmDynamic;
import org.pkl.core.runtime.VmIntSeq;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmListing;
import org.pkl.core.runtime.VmMap;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmPair;
import org.pkl.core.runtime.VmRegex;
import org.pkl.core.runtime.VmSet;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.AbstractRenderer;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.PklConverter;
import org.pkl.core.util.ArrayCharEscaper;
import org.pkl.core.util.IoUtils;

public final class RendererNodes {
  private static Renderer createRenderer(VmTyped self, StringBuilder builder) {
    var indent = (String) VmNull.unwrap(VmUtils.readMember(self, Identifier.INDENT));
    if (indent == null) indent = "";
    var omitNullProperties = (boolean) VmUtils.readMember(self, Identifier.OMIT_NULL_PROPERTIES);
    var converters = (VmMapping) VmUtils.readMember(self, Identifier.CONVERTERS);
    var converter = new PklConverter(converters);
    return new Renderer(builder, indent, omitNullProperties, converter);
  }

  public abstract static class renderDocument extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self, Object value) {
      var builder = new StringBuilder();
      createRenderer(self, builder).renderDocument(value);
      return builder.toString();
    }
  }

  public abstract static class renderValue extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self, Object value) {
      var builder = new StringBuilder();
      createRenderer(self, builder).renderValue(value);
      return builder.toString();
    }
  }

  private static final class Renderer extends AbstractRenderer {
    // Pattern for object fields that we can render without any quotes.
    // From: https://jsonnet.org/ref/spec.html#lexing
    private static final Pattern ID_PATTERN = Pattern.compile("[_a-zA-Z][_a-zA-Z0-9]*");

    // Reserved Jsonnet language keywords.
    // Object fields having these names must always be quoted.
    private static final Set<String> RESERVED_KEYWORDS =
        Set.of(
            "assert",
            "else",
            "error",
            "false",
            "for",
            "function",
            "if",
            "import",
            "importstr",
            "in",
            "local",
            "null",
            "self",
            "super",
            "tailstrict",
            "then",
            "true");

    // Pattern for multiline strings that are safe to print as multiline text blocks (using `|||`).
    // Strings that have leading horizontal whitespace are not safe to print as text blocks,
    // as it won't allow Jsonnet to unambiguously find the terminating `|||`.
    private static final Pattern FIRST_NON_EMPTY_LINE_STARTS_WITH_NON_WHITESPACE =
        Pattern.compile("\\n*\\S");

    private static final ArrayCharEscaper SINGLE_QUOTE_ESCAPER =
        createBaseEscaper().withEscape('\'', "\\'").build();

    private static final ArrayCharEscaper DOUBLE_QUOTE_ESCAPER =
        createBaseEscaper().withEscape('"', "\\\"").build();

    // behaves the same as jsonnetfmt's `jsonnet_string_escape()`
    private static ArrayCharEscaper.Builder createBaseEscaper() {
      var builder = ArrayCharEscaper.builder();
      for (char ch = 0; ch < 0x20; ch++) {
        builder.withEscape(ch, IoUtils.toUnicodeEscape(ch));
      }
      for (char ch = 0x80; ch <= 0x9f; ch++) {
        builder.withEscape(ch, IoUtils.toUnicodeEscape(ch));
      }
      return builder
          .withEscape('\\', "\\\\")
          .withEscape('\b', "\\b")
          .withEscape('\f', "\\f")
          .withEscape('\n', "\\n")
          .withEscape('\r', "\\r");
    }

    private final boolean renderInline;
    private final char memberSeparator;

    private Renderer(
        StringBuilder builder, String indent, boolean omitNullProperties, PklConverter converter) {
      super("Jsonnet", builder, indent, converter, omitNullProperties, omitNullProperties);
      renderInline = indent.isEmpty();
      memberSeparator = renderInline ? ' ' : LINE_BREAK;
    }

    @Override
    protected void visitDocument(Object value) {
      visit(value);
      builder.append(LINE_BREAK);
    }

    @Override
    protected void visitTopLevelValue(Object value) {
      visit(value);
    }

    @Override
    protected void visitRenderDirective(VmTyped value) {
      builder.append(VmUtils.readTextProperty(value));
    }

    @Override
    public void visitTyped(VmTyped value) {
      var vmClass = value.getVmClass();
      if (vmClass == JsonnetModule.getImportStrClass()) {
        // `importstr` constructs may not use multiline strings
        builder.append("importstr ");
        renderAsQuotedString((String) VmUtils.readMember(value, Identifier.PATH));
      } else if (vmClass == JsonnetModule.getExtVarClass()) {
        builder.append("std.extVar(");
        visitString((String) VmUtils.readMember(value, Identifier.NAME));
        builder.append(')');
      } else {
        super.visitTyped(value);
      }
    }

    @Override
    public void visitString(String value) {
      renderAsString(value);
    }

    @Override
    public void visitBoolean(Boolean value) {
      builder.append(value);
    }

    @Override
    public void visitInt(Long value) {
      builder.append(value);
    }

    @Override
    public void visitFloat(Double value) {
      builder.append(value);
    }

    @Override
    public void visitDuration(VmDuration value) {
      cannotRenderTypeAddConverter(value);
    }

    @Override
    public void visitDataSize(VmDataSize value) {
      cannotRenderTypeAddConverter(value);
    }

    @Override
    public void visitIntSeq(VmIntSeq value) {
      cannotRenderTypeAddConverter(value);
    }

    private void startArray() {
      builder.append('[');
      increaseIndent();
    }

    private void startObject() {
      builder.append('{');
      increaseIndent();
    }

    private void endArray(boolean isEmpty) {
      endValue(isEmpty);
      builder.append(']');
    }

    private void endObject(boolean isEmpty) {
      endValue(isEmpty);
      if (!isEmpty && renderInline) {
        builder.append(" }");
      } else {
        builder.append('}');
      }
    }

    private void endValue(boolean isEmpty) {
      decreaseIndent();
      if (!isEmpty && !renderInline) {
        builder.append(",\n").append(currIndent);
      }
    }

    @Override
    public void startList(VmList value) {
      startArray();
    }

    @Override
    public void startSet(VmSet value) {
      startArray();
    }

    @Override
    public void startMap(VmMap value) {
      startObject();
    }

    @Override
    public void startTyped(VmTyped value) {
      startObject();
    }

    @Override
    public void startDynamic(VmDynamic value) {
      if (value.hasElements()) {
        startArray();
      } else {
        startObject();
      }
    }

    @Override
    public void startListing(VmListing value) {
      startArray();
    }

    @Override
    public void startMapping(VmMapping value) {
      startObject();
    }

    @Override
    protected void visitEntryKey(Object key, boolean isFirst) {
      if (!isFirst) {
        builder.append(',');
      }
      builder.append(memberSeparator).append(currIndent);
      if (key instanceof String string) {
        renderAsFieldName(string);
      } else if (VmUtils.isRenderDirective(key)) {
        visitRenderDirective((VmTyped) key);
        builder.append(": ");
      } else {
        cannotRenderNonStringKey(key);
      }
    }

    @Override
    protected void visitEntryValue(Object value) {
      visit(value);
    }

    @Override
    protected void visitProperty(Identifier name, Object value, boolean isFirst) {
      if (!isFirst) {
        builder.append(',');
      }
      builder.append(memberSeparator).append(currIndent);
      renderAsFieldName(name.toString());
      visit(value);
    }

    @Override
    protected void visitElement(long index, Object value, boolean isFirst) {
      if (!isFirst) {
        builder.append(',');
      }
      // inline arrays don't have separators between the brackets; e.g. [1, 2, 3]
      if (!isFirst || !renderInline) {
        builder.append(memberSeparator).append(currIndent);
      }
      visit(value);
    }

    @Override
    protected void endDynamic(VmDynamic value, boolean isEmpty) {
      if (value.hasElements()) {
        endArray(isEmpty);
      } else {
        endObject(isEmpty);
      }
    }

    @Override
    protected void endList(VmList value) {
      endArray(value.isEmpty());
    }

    @Override
    protected void endListing(VmListing value, boolean isEmpty) {
      endArray(isEmpty);
    }

    @Override
    protected void endMap(VmMap value) {
      endObject(value.isEmpty());
    }

    @Override
    protected void endMapping(VmMapping value, boolean isEmpty) {
      endObject(isEmpty);
    }

    @Override
    protected void endSet(VmSet value) {
      endArray(value.isEmpty());
    }

    @Override
    protected void endTyped(VmTyped value, boolean isEmpty) {
      endObject(isEmpty);
    }

    @Override
    public void visitPair(VmPair value) {
      cannotRenderTypeAddConverter(value);
    }

    @Override
    public void visitRegex(VmRegex value) {
      cannotRenderTypeAddConverter(value);
    }

    @Override
    public void visitNull(VmNull value) {
      builder.append("null");
    }

    private void renderAsFieldName(String key) {
      if (ID_PATTERN.matcher(key).matches() && !RESERVED_KEYWORDS.contains(key)) {
        builder.append(key);
      } else {
        renderAsString(key);
      }
      builder.append(": ");
    }

    private void renderAsString(String value) {
      if (renderInline
          || !value.contains("\n")
          || !FIRST_NON_EMPTY_LINE_STARTS_WITH_NON_WHITESPACE.matcher(value).lookingAt()) {
        renderAsQuotedString(value);
      } else {
        renderAsTextBlock(value);
      }
    }

    private void renderAsQuotedString(String value) {
      if (value.contains("'") && !value.contains("\"")) {
        builder.append('"').append(DOUBLE_QUOTE_ESCAPER.escape(value)).append('"');
      } else {
        builder.append('\'').append(SINGLE_QUOTE_ESCAPER.escape(value)).append('\'');
      }
    }

    private void renderAsTextBlock(String value) {
      builder.append("|||\n");
      value
          .lines()
          .forEach(line -> builder.append(currIndent).append(indent).append(line).append('\n'));
      builder.append(currIndent).append("|||");
    }
  }
}
