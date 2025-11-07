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
package org.pkl.core.stdlib.base;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.AbstractStringRenderer;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.PklConverter;
import org.pkl.core.util.ArrayCharEscaper;

public final class PListRendererNodes {
  private PListRendererNodes() {}

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

  private static PListRenderer createRenderer(VmTyped self, StringBuilder builder) {
    var indent = (String) VmUtils.readMember(self, Identifier.INDENT);
    var converters = (VmMapping) VmUtils.readMember(self, Identifier.CONVERTERS);
    var converter = new PklConverter(converters);
    return new PListRenderer(builder, indent, converter);
  }

  // keep in sync with org.pkl.core.PListRenderer
  private static final class PListRenderer extends AbstractStringRenderer {

    // it's safe (though not required) to escape all the following characters in XML text nodes
    private static final ArrayCharEscaper charEscaper =
        ArrayCharEscaper.builder()
            .withEscape('"', "&quot;")
            .withEscape('\'', "&apos;")
            .withEscape('<', "&lt;")
            .withEscape('>', "&gt;")
            .withEscape('&', "&amp;")
            .build();

    public PListRenderer(StringBuilder builder, String indent, PklConverter converter) {
      super("XML property list", builder, indent, converter, true, true);
    }

    @Override
    public void visitString(String value) {
      builder.append("<string>").append(charEscaper.escape(value)).append("</string>");
    }

    @Override
    public void visitInt(Long value) {
      builder.append("<integer>").append(value).append("</integer>");
    }

    @Override
    @SuppressWarnings("Duplicates")
    public void visitFloat(Double value) {
      builder.append("<real>");

      if (value.isNaN()) {
        builder.append("nan");
      } else if (value == Double.POSITIVE_INFINITY) {
        builder.append("+infinity");
      } else if (value == Double.NEGATIVE_INFINITY) {
        builder.append("-infinity");
      } else {
        builder.append(value);
      }

      builder.append("</real>");
    }

    @Override
    public void visitBoolean(Boolean value) {
      if (value) {
        builder.append("<true/>");
      } else {
        builder.append("<false/>");
      }
    }

    @Override
    public void visitDuration(VmDuration value) {
      throw new VmExceptionBuilder()
          .evalError("cannotRenderType", "Duration", "XML property list")
          .withProgramValue("Value", value)
          .build();
    }

    @Override
    public void visitDataSize(VmDataSize value) {
      throw new VmExceptionBuilder()
          .evalError("cannotRenderType", "DataSize", "XML property list")
          .withProgramValue("Value", value)
          .build();
    }

    @Override
    public void visitBytes(VmBytes value) {
      builder.append("<data>");
      builder.append(value.base64());
      builder.append("</data>");
    }

    @Override
    public void visitRegex(VmRegex value) {
      throw new VmExceptionBuilder()
          .evalError("cannotRenderType", "Regex", "XML property list")
          .withProgramValue("Value", value)
          .build();
    }

    @Override
    public void visitIntSeq(VmIntSeq value) {
      throw new VmExceptionBuilder()
          .evalError("cannotRenderType", "IntSeq", "XML property list")
          .withProgramValue("Value", value)
          .build();
    }

    @Override
    public void visitPair(VmPair value) {
      cannotRenderTypeAddConverter(value);
    }

    @Override
    public void visitNull(VmNull value) {
      cannotRenderTypeAddConverter(value);
    }

    @Override
    protected void visitDocument(Object value) {
      if (!(value instanceof VmCollection
          || value instanceof VmMap
          || value instanceof VmObjectLike)) {
        throw new VmExceptionBuilder()
            .evalError("invalidPListTopLevelValue", VmUtils.getClass(value))
            .withProgramValue("Value", value)
            .build();
      }
      builder
          .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
          .append(LINE_BREAK)
          .append(
              "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">")
          .append(LINE_BREAK)
          .append("<plist version=\"1.0\">")
          .append(LINE_BREAK);

      visit(value);

      builder.append(LINE_BREAK).append("</plist>").append(LINE_BREAK);
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
    protected void startDynamic(VmDynamic value) {
      increaseIndent();
    }

    @Override
    protected void startTyped(VmTyped value) {
      increaseIndent();
    }

    @Override
    protected void startListing(VmListing value) {
      increaseIndent();
    }

    @Override
    protected void startMapping(VmMapping value) {
      increaseIndent();
    }

    @Override
    protected void startList(VmList value) {
      increaseIndent();
    }

    @Override
    protected void startSet(VmSet value) {
      increaseIndent();
    }

    @Override
    protected void startMap(VmMap value) {
      increaseIndent();
    }

    @Override
    protected void visitElement(long index, Object value, boolean isFirst) {
      if (isFirst) {
        builder.append("<array>").append(LINE_BREAK);
      }
      builder.append(currIndent);
      visit(value);
      builder.append(LINE_BREAK);
    }

    @Override
    protected void visitEntryKey(Object key, boolean isFirst) {
      if (isFirst) {
        builder.append("<dict>").append(LINE_BREAK);
      }

      if (isRenderDirective(key)) {
        key = VmUtils.readTextProperty(key);
      }

      if (key instanceof String string) {
        builder
            .append(currIndent)
            .append("<key>")
            .append(charEscaper.escape(string))
            .append("</key>")
            .append(LINE_BREAK)
            .append(currIndent);
        return;
      }

      cannotRenderNonStringKey(key);
    }

    @Override
    protected void visitEntryValue(Object value) {
      visit(value);
      builder.append(LINE_BREAK);
    }

    @Override
    protected void visitProperty(Identifier name, Object value, boolean isFirst) {
      if (isFirst) {
        builder.append("<dict>").append(LINE_BREAK);
      }
      builder
          .append(currIndent)
          .append("<key>")
          .append(charEscaper.escape(name.toString()))
          .append("</key>")
          .append(LINE_BREAK)
          .append(currIndent);
      visit(value);
      builder.append(LINE_BREAK);
    }

    @Override
    protected void endDynamic(VmDynamic value, boolean isEmpty) {
      if (value.hasElements()) {
        endArray(isEmpty);
      } else {
        endDict(isEmpty);
      }
    }

    @Override
    protected void endTyped(VmTyped value, boolean isEmpty) {
      endDict(isEmpty);
    }

    @Override
    protected void endListing(VmListing value, boolean isEmpty) {
      endArray(isEmpty);
    }

    @Override
    protected void endMapping(VmMapping value, boolean isEmpty) {
      endDict(isEmpty);
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
      endDict(value.isEmpty());
    }

    private void endDict(boolean isEmpty) {
      decreaseIndent();
      builder.append(currIndent);
      if (isEmpty) {
        builder.append("<dict/>");
      } else {
        builder.append("</dict>");
      }
    }

    private void endArray(boolean isEmpty) {
      decreaseIndent();
      builder.append(currIndent);
      if (isEmpty) {
        builder.append("<array/>");
      } else {
        builder.append("</array>");
      }
    }
  }
}
