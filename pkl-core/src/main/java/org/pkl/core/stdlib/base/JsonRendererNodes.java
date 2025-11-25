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
package org.pkl.core.stdlib.base;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.AbstractStringRenderer;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.PklConverter;
import org.pkl.core.util.json.JsonEscaper;

public final class JsonRendererNodes {
  private JsonRendererNodes() {}

  public abstract static class renderDocument extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self, Object value) {
      // JSON document can have any top-level value
      // https://stackoverflow.com/a/3833312
      // http://www.ietf.org/rfc/rfc7159.txt
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

  private static JsonRenderer createRenderer(VmTyped self, StringBuilder builder) {
    var indent = (String) VmUtils.readMember(self, Identifier.INDENT);
    var omitNullProperties = (boolean) VmUtils.readMember(self, Identifier.OMIT_NULL_PROPERTIES);
    return new JsonRenderer(builder, indent, PklConverter.fromRenderer(self), omitNullProperties);
  }

  private static final class JsonRenderer extends AbstractStringRenderer {
    private final String separator;

    private final JsonEscaper escaper = new JsonEscaper(false);

    JsonRenderer(
        StringBuilder builder, String indent, PklConverter converter, boolean omitNullProperties) {
      super("JSON", builder, indent, converter, omitNullProperties, omitNullProperties);
      separator = indent.isEmpty() ? ":" : ": ";
    }

    @Override
    protected void visitDocument(Object value) {
      visit(value);
      builder.append('\n');
    }

    @Override
    protected void visitTopLevelValue(Object value) {
      visit(value);
    }

    /** Use same escaping strategy as {@link org.pkl.core.util.json.JsonWriter}. */
    @Override
    public void visitString(String value) {
      builder.append('"');
      escaper.escape(value, builder);
      builder.append('"');
    }

    @Override
    public void visitInt(Long value) {
      builder.append((long) value);
    }

    @Override
    public void visitFloat(Double value) {
      if (value.isNaN() || value.isInfinite()) {
        throw new VmExceptionBuilder().evalError("cannotRenderValue", value, name).build();
      }
      builder.append((double) value);
    }

    @Override
    public void visitBoolean(Boolean value) {
      builder.append((boolean) value);
    }

    @Override
    public void visitNull(VmNull value) {
      builder.append("null");
    }

    @Override
    public void visitRenderDirective(VmTyped value) {
      // append verbatim
      builder.append(VmUtils.readTextProperty(value));
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
    public void visitBytes(VmBytes value) {
      cannotRenderTypeAddConverter(value);
    }

    @Override
    public void visitRegex(VmRegex value) {
      cannotRenderTypeAddConverter(value);
    }

    @Override
    public void visitIntSeq(VmIntSeq value) {
      cannotRenderTypeAddConverter(value);
    }

    @Override
    public void visitPair(VmPair value) {
      cannotRenderTypeAddConverter(value);
    }

    @Override
    protected void startDynamic(VmDynamic value) {
      if (value.hasElements()) {
        doBeginArray();
      } else {
        doBeginObject();
      }
    }

    @Override
    protected void startTyped(VmTyped value) {
      doBeginObject();
    }

    @Override
    protected void startListing(VmListing value) {
      doBeginArray();
    }

    protected void startMapping(VmMapping value) {
      doBeginObject();
    }

    @Override
    protected void startList(VmList value) {
      doBeginArray();
    }

    @Override
    protected void startSet(VmSet value) {
      doBeginArray();
    }

    @Override
    protected void startMap(VmMap value) {
      doBeginObject();
    }

    private void doBeginArray() {
      builder.append("[");
      increaseIndent();
    }

    private void doBeginObject() {
      builder.append("{");
      increaseIndent();
    }

    @Override
    protected void visitElement(long index, Object value, boolean isFirst) {
      if (!isFirst) builder.append(',');
      startNewLine();
      visit(value);
    }

    @Override
    protected void visitEntryKey(Object key, boolean isFirst) {
      if (!isFirst) builder.append(',');
      startNewLine();

      if (key instanceof String string) {
        visitString(string);
        builder.append(separator);
        return;
      }

      if (isRenderDirective(key)) {
        visitRenderDirective((VmTyped) key);
        builder.append(separator);
        return;
      }

      cannotRenderNonStringKey(key);
    }

    @Override
    protected void visitEntryValue(Object value) {
      visit(value);
    }

    @Override
    protected void visitProperty(Identifier name, Object value, boolean isFirst) {
      if (!isFirst) builder.append(',');
      startNewLine();
      visitString(name.toString());
      builder.append(separator);
      visit(value);
    }

    @Override
    protected void visitPropertyRenderDirective(VmTyped value, boolean isFirst) {
      if (!isFirst) builder.append(',');
      startNewLine();
      visitRenderDirective(value);
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
    protected void endTyped(VmTyped value, boolean isEmpty) {
      endObject(isEmpty);
    }

    @Override
    protected void endListing(VmListing value, boolean isEmpty) {
      endArray(isEmpty);
    }

    @Override
    protected void endMapping(VmMapping value, boolean isEmpty) {
      endObject(isEmpty);
    }

    @Override
    protected void endList(VmList value) {
      endArray(value.isEmpty());
    }

    @Override
    protected void endSet(VmSet value) {
      endArray(value.isEmpty());
    }

    @Override
    protected void endMap(VmMap value) {
      endObject(value.isEmpty());
    }

    private void endArray(boolean isEmpty) {
      decreaseIndent();
      if (!isEmpty) startNewLine();
      builder.append(']');
    }

    private void endObject(boolean isEmpty) {
      decreaseIndent();
      if (!isEmpty) startNewLine();
      builder.append('}');
    }

    private void startNewLine() {
      if (indent.isEmpty()) return;

      builder.append('\n');
      builder.append(currIndent);
    }
  }
}
