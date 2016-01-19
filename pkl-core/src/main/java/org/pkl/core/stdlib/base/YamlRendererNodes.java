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
package org.pkl.core.stdlib.base;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmCollection;
import org.pkl.core.runtime.VmDataSize;
import org.pkl.core.runtime.VmDuration;
import org.pkl.core.runtime.VmDynamic;
import org.pkl.core.runtime.VmExceptionBuilder;
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
import org.pkl.core.util.MutableBoolean;
import org.pkl.core.util.yaml.YamlEmitter;

public final class YamlRendererNodes {
  private YamlRendererNodes() {}

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

  private static YamlRenderer createRenderer(VmTyped self, StringBuilder builder) {
    var mode = ((String) VmUtils.readMember(self, Identifier.MODE));
    var indentWidth = ((Long) VmUtils.readMember(self, Identifier.INDENT_WIDTH)).intValue();
    var omitNullProperties = (boolean) VmUtils.readMember(self, Identifier.OMIT_NULL_PROPERTIES);
    var isStream = (boolean) VmUtils.readMember(self, Identifier.IS_STREAM);
    var converters = (VmMapping) VmUtils.readMember(self, Identifier.CONVERTERS);
    var converter = new PklConverter(converters);
    return new YamlRenderer(
        builder, " ".repeat(indentWidth), converter, omitNullProperties, mode, isStream);
  }

  private static final class YamlRenderer extends AbstractRenderer {
    private final boolean isStream;
    private final YamlEmitter emitter;
    private final String elementIndent;

    private YamlRenderer(
        StringBuilder builder,
        String indent,
        PklConverter converter,
        boolean omitNullProperties,
        String mode,
        boolean isStream) {
      super("YAML", builder, indent, converter, omitNullProperties, omitNullProperties);

      this.isStream = isStream;
      this.emitter = YamlEmitter.create(builder, mode, indent);
      elementIndent = indent.substring(1);
    }

    @Override
    public void visitDocument(Object value) {
      if (isStream) {
        visitStream(value);
      } else {
        visit(value);
      }
      startNewLine();
    }

    private void visitStream(Object value) {
      if (value instanceof VmListing) {
        var isFirst = new MutableBoolean(true);
        ((VmListing) value)
            .forceAndIterateMemberValues(
                ((key, member, element) -> {
                  if (!isFirst.getAndSetFalse()) {
                    startNewLine();
                    builder.append("---");
                  }
                  visit(element);
                  return true;
                }));
        return;
      }

      if (value instanceof VmCollection) {
        var first = true;
        for (var element : (VmCollection) value) {
          if (first) {
            first = false;
          } else {
            startNewLine();
            builder.append("---");
          }
          visit(element);
        }
        return;
      }

      throw new VmExceptionBuilder()
          .evalError("invalidYamlStreamTopLevelValue", VmUtils.getClass(value))
          .withProgramValue("Value", value)
          .build();
    }

    @Override
    public void visitTopLevelValue(Object value) {
      visit(value);
    }

    @Override
    public void visitString(String value) {
      if (builder.length() > 0) builder.append(' ');
      emitter.emit(value, currIndent, false);
    }

    @Override
    public void visitInt(Long value) {
      if (builder.length() > 0) builder.append(' ');
      emitter.emit(value);
    }

    @Override
    public void visitFloat(Double value) {
      if (builder.length() > 0) builder.append(' ');
      emitter.emit(value);
    }

    @Override
    public void visitBoolean(Boolean value) {
      if (builder.length() > 0) builder.append(' ');
      emitter.emit(value);
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
    public void visitRenderDirective(VmTyped value) {
      // append verbatim
      builder.append(VmUtils.readTextProperty(value));
    }

    @Override
    public void visitNull(VmNull value) {
      if (builder.length() > 0) builder.append(' ');
      emitter.emitNull();
    }

    @Override
    protected void startDynamic(VmDynamic value) {
      if (value.hasElements()) {
        startYamlSequence();
      } else {
        startYamlMapping();
      }
    }

    @Override
    protected void startTyped(VmTyped value) {
      startYamlMapping();
    }

    @Override
    protected void startListing(VmListing value) {
      startYamlSequence();
    }

    @Override
    protected void startMapping(VmMapping value) {
      startYamlMapping();
    }

    @Override
    protected void startList(VmList value) {
      startYamlSequence();
    }

    @Override
    protected void startSet(VmSet value) {
      startYamlSequence();
    }

    @Override
    protected void startMap(VmMap value) {
      startYamlMapping();
    }

    private void startYamlMapping() {
      if (enclosingValue != null) {
        increaseIndent();
      }
      if (hasEnclosingSequence()) {
        builder.append(elementIndent);
      } else {
        startNewLine();
      }
    }

    private void startYamlSequence() {
      if (hasEnclosingSequence()) {
        increaseIndent();
        builder.append(elementIndent);
      } else {
        startNewLine();
      }
    }

    @Override
    protected void visitElement(long index, Object value, boolean isFirst) {
      if (!isFirst) {
        startNewLine();
      }
      builder.append('-');
      visit(value);
    }

    @Override
    protected void visitEntryKey(Object key, boolean isFirst) {
      if (!isFirst) {
        startNewLine();
      }

      if (key instanceof String) {
        emitter.emit((String) key, currIndent, true);
        builder.append(':');
        return;
      }

      if (VmUtils.isRenderDirective(key)) {
        visitRenderDirective((VmTyped) key);
        builder.append(':');
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
      if (!isFirst) {
        startNewLine();
      }
      emitter.emit(name.toString(), currIndent, true);
      builder.append(':');
      visit(value);
    }

    @Override
    protected void endDynamic(VmDynamic value, boolean isEmpty) {
      if (value.hasElements()) {
        endYamlSequence(isEmpty);
      } else {
        endYamlMapping(isEmpty);
      }
    }

    @Override
    protected void endTyped(VmTyped value, boolean isEmpty) {
      endYamlMapping(isEmpty);
    }

    @Override
    protected void endListing(VmListing value, boolean isEmpty) {
      endYamlSequence(isEmpty);
    }

    @Override
    protected void endMapping(VmMapping value, boolean isEmpty) {
      endYamlMapping(isEmpty);
    }

    @Override
    protected void endList(VmList value) {
      endYamlSequence(value.isEmpty());
    }

    @Override
    protected void endSet(VmSet value) {
      endYamlSequence(value.isEmpty());
    }

    @Override
    protected void endMap(VmMap value) {
      endYamlMapping(value.isEmpty());
    }

    private void endYamlSequence(boolean isEmpty) {
      var hasEnclosingSequence = hasEnclosingSequence();
      if (isEmpty) {
        if (hasEnclosingSequence) {
          builder.append("[]");
        } else {
          undoStartNewLine();
          builder.append(" []");
        }
      }
      if (hasEnclosingSequence) {
        decreaseIndent();
      }
    }

    private void endYamlMapping(boolean isEmpty) {
      if (isEmpty) {
        if (hasEnclosingSequence()) {
          builder.append("{}");
        } else {
          undoStartNewLine();
          builder.append(" {}");
        }
      }
      if (enclosingValue != null) {
        decreaseIndent();
      }
    }

    private boolean hasEnclosingSequence() {
      return enclosingValue != null && enclosingValue.isSequence();
    }

    private void startNewLine() {
      var length = builder.length();
      if (length == 0) return;

      if (builder.charAt(length - 1) != '\n') {
        builder.append('\n');
      }
      builder.append(currIndent);
    }

    private void undoStartNewLine() {
      var length = builder.length();
      if (length == 0) return;

      builder.setLength(length - currIndent.length() - 1);
    }
  }
}
