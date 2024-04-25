/**
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
package org.pkl.core.stdlib.xml;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.AbstractRenderer;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.PklConverter;
import org.pkl.core.util.ArrayCharEscaper;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.xml.XmlValidator;

public final class RendererNodes {
  private RendererNodes() {}

  private static Renderer createRenderer(VmTyped self, StringBuilder builder) {
    var indent = (String) VmUtils.readMember(self, Identifier.INDENT);
    var xmlVersion = (String) VmUtils.readMember(self, Identifier.XML_VERSION);
    var rootElementName = (String) VmUtils.readMember(self, Identifier.ROOT_ELEMENT_NAME);
    var rootElementAttributes =
        (VmMapping) VmUtils.readMember(self, Identifier.ROOT_ELEMENT_ATTRIBUTES);
    var converters = (VmMapping) VmUtils.readMember(self, Identifier.CONVERTERS);
    var converter = new PklConverter(converters);
    return new Renderer(
        builder, indent, xmlVersion, rootElementName, rootElementAttributes, converter);
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

  public static final class Renderer extends AbstractRenderer {
    // it's safe (though not required) to escape all the following characters in text nodes and
    // attribute values
    private static final ArrayCharEscaper stringEscaper =
        ArrayCharEscaper.builder()
            .withEscape('"', "&quot;")
            .withEscape('\'', "&apos;")
            .withEscape('<', "&lt;")
            .withEscape('>', "&gt;")
            .withEscape('&', "&amp;")
            .build();

    private final String version;
    private final String rootElementName;
    private final VmMapping rootElementAttributes;
    private final XmlValidator validator;

    private int lineNumber = 0;
    private @Nullable Object deferredKey;

    public Renderer(
        StringBuilder builder,
        String indent,
        String version,
        String rootElementName,
        VmMapping rootElementAttributes,
        PklConverter converter) {
      super("XML", builder, indent, converter, true, true);
      this.version = version;
      this.rootElementName = rootElementName;
      this.rootElementAttributes = rootElementAttributes;
      validator = XmlValidator.create(version);
    }

    @Override
    public void visitDocument(Object value) {
      builder
          .append("<?xml version=\"")
          .append(stringEscaper.escape(version))
          .append("\" encoding=\"UTF-8\"?>");

      if (isXmlElement(value)) {
        renderXmlElement((VmDynamic) value);
      } else {
        writeXmlElement(rootElementName, rootElementAttributes, value, true, true);
      }

      builder.append(LINE_BREAK);
    }

    @Override
    public void visitTopLevelValue(Object value) {
      if (isXmlElement(value)) {
        renderXmlElement((VmDynamic) value);
      } else {
        visit(value);
      }
    }

    @Override
    protected void visitRenderDirective(VmTyped value) {
      builder.append(VmUtils.readTextProperty(value));
    }

    @Override
    public void visitString(String value) {
      builder.append(stringEscaper.escape(value));
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
    public void visitBoolean(Boolean value) {
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
    public void visitPair(VmPair value) {
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
    protected void startDynamic(VmDynamic value) {
      if (isXmlElement(value)) {
        throw new VmExceptionBuilder()
            .evalError("elementNotSupportedHere")
            .withProgramValue("Value", value)
            .build();
      }
    }

    @Override
    public void startTyped(VmTyped value) {
      if (isXmlInline(value)) {
        throw new VmExceptionBuilder()
            .evalError("inlineNotSupportedHere")
            .withProgramValue("Value", value)
            .build();
      }
    }

    // No-op for XML
    @Override
    protected void startListing(VmListing value) {}

    // No-op for XML
    @Override
    protected void startMapping(VmMapping value) {}

    // No-op for XML
    @Override
    protected void startList(VmList value) {}

    // No-op for XML
    @Override
    protected void startSet(VmSet value) {}

    // No-op for XML
    @Override
    protected void startMap(VmMap value) {}

    @Override
    public void visitNull(VmNull value) {
      cannotRenderTypeAddConverter(value);
    }

    @Override
    protected void visitProperty(Identifier name, Object value, boolean isFirst) {
      if (isXmlElement(value)) {
        renderXmlElement((VmDynamic) value);
      } else if (isXmlInline(value)) {
        renderXmlInline((VmTyped) value);
      } else {
        writeXmlElement(name.toString(), null, value, true, true);
      }
    }

    // No-op for XML
    @Override
    protected void endDynamic(VmDynamic value, boolean isEmpty) {}

    // No-op for XML
    @Override
    protected void endTyped(VmTyped value, boolean isEmpty) {}

    // No-op for XML
    @Override
    protected void endListing(VmListing value, boolean isEmpty) {}

    // No-op for XML
    @Override
    protected void endMapping(VmMapping value, boolean isEmpty) {}

    // No-op for XML
    @Override
    protected void endList(VmList value) {}

    // No-op for XML
    @Override
    protected void endSet(VmSet value) {}

    // No-op for XML
    @Override
    protected void endMap(VmMap value) {}

    private void doVisitElement(Object value) {
      if (!(value instanceof VmNull)) {
        if (isXmlElement(value)) {
          renderXmlElement((VmDynamic) value);
        } else if (isXmlInline(value)) {
          renderXmlInline((VmTyped) value);
        } else if (isContent(value)) {
          visit(value);
        } else if (VmUtils.isRenderDirective(value)) {
          builder.append(VmUtils.readTextProperty(value));
        } else {
          writeXmlElement(VmUtils.getClass(value).getSimpleName(), null, value, true, true);
        }
      }
    }

    @Override
    protected void visitElement(long index, Object value, boolean isFirst) {
      doVisitElement(value);
    }

    @Override
    protected void visitEntryKey(Object key, boolean isFirst) {
      deferredKey = key;
    }

    @Override
    protected void visitEntryValue(Object value) {
      if (isXmlElement(value)) {
        renderXmlElement((VmDynamic) value);
      } else if (isXmlInline(value)) {
        renderXmlInline((VmTyped) value);
      } else {
        assert deferredKey != null;
        assert enclosingValue != null;
        if (VmUtils.isRenderDirective(deferredKey)) {
          writeXmlElement(VmUtils.readTextProperty(deferredKey), null, value, true, false);
        } else if (deferredKey instanceof String string) {
          writeXmlElement(string, null, value, true, true);
        } else {
          cannotRenderNonStringKey(deferredKey);
        }
      }
    }

    @Override
    public void visitTyped(VmTyped value) {
      if (isXmlComment(value)) {
        renderXmlComment(value);
      } else if (isXmlCData(value)) {
        renderXmlCData(value);
      } else {
        super.visitTyped(value);
      }
    }

    private static boolean isScalar(Object value) {
      return value instanceof String
          || value instanceof Boolean
          || value instanceof Long
          || value instanceof Double;
    }

    private static boolean isContent(Object value) {
      return isScalar(value) || isXmlCData(value) || isXmlComment(value);
    }

    private static boolean isXmlElement(Object value) {
      return value instanceof VmDynamic dynamic
          && VmUtils.readMemberOrNull(dynamic, Identifier.IS_XML_ELEMENT) == Boolean.TRUE;
    }

    private void renderXmlElement(VmDynamic value) {
      assert isXmlElement(value);

      var name = VmUtils.readMember(value, Identifier.NAME);
      // this check will be unnecessary once we have an XmlElement Pkl class
      if (!(name instanceof String)) {
        throw new VmExceptionBuilder().typeMismatch(value, BaseModule.getStringClass()).build();
      }

      Object attributes = VmUtils.readMember(value, Identifier.ATTRIBUTES);
      // this check will be unnecessary once we have an XmlElement Pkl class
      if (!(attributes instanceof VmMapping)) {
        throw new VmExceptionBuilder().typeMismatch(value, BaseModule.getMappingClass()).build();
      }

      var isBlockFormat = VmUtils.readMember(value, Identifier.IS_BLOCK_FORMAT);
      // this check will be unnecessary once we have an XmlElement Pkl class
      if (!(isBlockFormat instanceof Boolean)) {
        throw new VmExceptionBuilder().typeMismatch(value, BaseModule.getBooleanClass()).build();
      }

      writeXmlElement((String) name, (VmMapping) attributes, value, (Boolean) isBlockFormat, true);
    }

    private boolean isXmlInline(Object value) {
      return value instanceof VmTyped typed && typed.getVmClass() == XmlModule.getInlineClass();
    }

    private void renderXmlInline(VmTyped object) {
      assert isXmlInline(object);

      var value = VmUtils.readMember(object, Identifier.VALUE);
      visit(value);
    }

    private static boolean isXmlComment(Object value) {
      return value instanceof VmTyped typed && typed.getVmClass() == XmlModule.getCommentClass();
    }

    private void renderXmlComment(VmTyped object) {
      assert isXmlComment(object);

      if (isBlockFormat(object)) startNewLine();

      var comment = VmUtils.readTextProperty(object);
      assert !comment.contains("--"); // guaranteed by `xml.pkl`.

      builder
          .append("<!--")
          .append(VmUtils.readTextProperty(object)) // no escaping for comment
          .append("-->");
    }

    private static boolean isXmlCData(Object value) {
      return value instanceof VmTyped typed && typed.getVmClass() == XmlModule.getCDataClass();
    }

    private void renderXmlCData(VmTyped object) {
      assert isXmlCData(object);

      var text = VmUtils.readTextProperty(object);
      // Escape CDATA end token by splitting it into parts:
      // https://stackoverflow.com/questions/223652/is-there-a-way-to-escape-a-cdata-end-token-in-xml
      var cdataContents = text.replace("]]>", "]]]]><![CDATA[>");
      builder.append("<![CDATA[").append(cdataContents).append("]]>");
    }

    private void writeXmlElement(
        String name,
        @Nullable VmMapping attributes,
        Object content,
        boolean isBlockFormat,
        boolean validateElementName) {
      if (isBlockFormat) startNewLine();

      if (validateElementName) {
        validateName(name, "element");
      }
      builder.append("<").append(name);
      if (attributes != null) {
        attributes.forceAndIterateMemberValues(
            (key, member, value) -> {
              builder.append(' ');
              // this check will be unnecessary once we have an XmlElement Pkl class
              if (!(key instanceof String string)) {
                throw new VmExceptionBuilder()
                    .typeMismatch(name, BaseModule.getStringClass())
                    .build();
              }
              validateName(string, "attribute");
              builder.append(string).append("=\"");
              // this check will be unnecessary once we have an XmlElement Pkl class
              if (!isScalar(value)) {
                throw new VmExceptionBuilder()
                    // can only report two expected types for now
                    .typeMismatch(name, BaseModule.getStringClass(), BaseModule.getBooleanClass())
                    .build();
              }
              builder.append(stringEscaper.escape(value.toString())).append("\"");
              return true;
            });
      }
      builder.append(">");

      var prevLineNumber = lineNumber;
      increaseIndent();

      if (isXmlElement(content)) {
        // this special casing is necessary because renderXmlElement() is implemented in terms of
        // this method
        ((VmDynamic) content)
            .forceAndIterateMemberValues(
                (key, member, value) -> {
                  if (member.isElement()) doVisitElement(value);
                  return true;
                });
      } else {
        visit(content);
      }

      decreaseIndent();
      if (prevLineNumber < lineNumber) startNewLine();

      builder.append("</").append(name).append(">");
    }

    private void validateName(String name, String kind) {
      if (validator.isValidName(name)) return;

      throw new VmExceptionBuilder().evalError("invalidXmlName", version, kind, name).build();
    }

    private void startNewLine() {
      if (builder.length() == 0) return;

      lineNumber += 1;
      builder.append(LINE_BREAK).append(currIndent);
    }
  }

  private static boolean isBlockFormat(VmObjectLike object) {
    return (Boolean) VmUtils.readMember(object, Identifier.IS_BLOCK_FORMAT);
  }
}
