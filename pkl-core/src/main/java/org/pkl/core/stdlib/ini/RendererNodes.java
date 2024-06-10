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
package org.pkl.core.stdlib.ini;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import java.util.List;
import org.pkl.core.runtime.Identifier;
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
import org.pkl.core.runtime.VmValueConverter;
import org.pkl.core.stdlib.AbstractRenderer;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.PklConverter;
import org.pkl.core.util.MutableBoolean;
import org.pkl.core.util.ini.IniUtils;

public final class RendererNodes {

  public abstract static class renderDocument extends ExternalMethod1Node {

    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self, Object value) {
      var builder = new StringBuilder();
      createRenderer(self, builder).renderDocument(value);
      if (builder.charAt(builder.length() - 1) != '\n') {
        // writes break line at the end of the file to comply with ini styleing standards
        builder.append('\n');
      }
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

  private static IniRenderer createRenderer(VmTyped self, StringBuilder builder) {
    var omitNullProperties = (boolean) VmUtils.readMember(self, Identifier.OMIT_NULL_PROPERTIES);
    var restrictCharset = (boolean) VmUtils.readMember(self, Identifier.RESTRICT_CHARSET);
    var converters = (VmMapping) VmUtils.readMember(self, Identifier.CONVERTERS);
    var PklConverter = new PklConverter(converters);
    return new IniRenderer(builder, omitNullProperties, restrictCharset, PklConverter);
  }

  private static final class IniRenderer extends AbstractRenderer {

    private final boolean restrictCharset;

    private boolean isDocument;

    private String storedEntryKey;
    private boolean storedIsFirst;

    public IniRenderer(
        StringBuilder builder,
        boolean omitNullProperties,
        boolean restrictCharset,
        PklConverter converter) {
      super("ini", builder, "", converter, omitNullProperties, omitNullProperties);
      this.restrictCharset = restrictCharset;
    }

    @Override
    public void visitString(String value) {
      writeKeyOrValue(value);
    }

    @Override
    public void visitBoolean(Boolean value) {
      writeKeyOrValue(value.toString());
    }

    @Override
    public void visitInt(Long value) {
      writeKeyOrValue(value.toString());
    }

    @Override
    public void visitFloat(Double value) {
      writeKeyOrValue(value.toString());
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
      if (isDocument) {
        writeSeparator();
        writeLineBreak();
      }
    }

    @Override
    protected void visitDocument(Object value) {
      if (!(value instanceof VmMap
          || value instanceof VmTyped
          || value instanceof VmMapping
          || value instanceof VmDynamic)) {
        throw new VmExceptionBuilder()
            .evalError("invalidPropertiesTopLevelValue", VmUtils.getClass(value))
            .withProgramValue("Value", value)
            .build();
      }
      if (!VmUtils.isRenderDirective(value)) {
        isDocument = true;
      }
      visit(value);
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
      writeSection();
    }

    @Override
    protected void startTyped(VmTyped value) {
      writeSection();
    }

    @Override
    protected void startListing(VmListing value) {
      cannotRenderTypeAddConverter(value);
    }

    @Override
    protected void startMapping(VmMapping value) {
      writeSection();
    }

    // throws error for now
    @Override
    protected void startList(VmList value) {
      cannotRenderTypeAddConverter(value);
    }

    // throws error for now
    @Override
    protected void startSet(VmSet value) {
      cannotRenderTypeAddConverter(value);
    }

    @Override
    protected void startMap(VmMap value) {
      writeSection();
    }

    // element of list so ignored for now
    @Override
    protected void visitElement(long index, Object value, boolean isFirst) {}

    @Override
    protected void visitEntryKey(Object key, boolean isFirst) {
      storedIsFirst = isFirst;
      if (VmUtils.isRenderDirective(key)) {
        visitRenderDirective((VmTyped) key);
        writeSeparator();
        return;
      }

      if (key instanceof String) {
        storedEntryKey = (String) key;
        return;
      }

      cannotRenderNonStringKey(key);
    }

    @Override
    protected void visitEntryValue(Object value) {
      if (!((value instanceof VmTyped)
          || (value instanceof VmDynamic)
          || (value instanceof VmMapping)
          || (value instanceof VmMap))) {
        if (!storedIsFirst) {
          writeLineBreak();
        }
        writeKeyOrValue(storedEntryKey);
        writeSeparator();
        visit(value);
      }
    }

    @Override
    protected void visitProperty(Identifier name, Object value, boolean isFirst) {

      if (!((value instanceof VmTyped)
          || (value instanceof VmDynamic)
          || (value instanceof VmMapping)
          || (value instanceof VmMap))) {
        if (!isFirst) {
          writeLineBreak();
        }
        writeKeyOrValue(name.toString());
        writeSeparator();
        visit(value);
      }
    }

    /*
    These end functions group `VmTyped`, `VmMapping`, `VmDynamic`, `VmMap` at the end of the map and then writes them to builder
    This is to comply with ini style standards. Please look at tests for example's
     */
    @Override
    protected void endDynamic(VmDynamic value, boolean isEmpty) {
      value.forceAndIterateMemberValues(
          ((memberKey, member, memberValue) -> {
            if ((memberValue instanceof VmTyped)
                || (memberValue instanceof VmDynamic)
                || (memberValue instanceof VmMapping)
                || (memberValue instanceof VmMap)) {

              if (memberKey instanceof Identifier) {
                currPath.push(memberKey);
              } else {
                currPath.push(converter.convert(memberKey, List.of()));
              }
              visit(memberValue);
              currPath.pop();
            }
            return true;
          }));
    }

    @Override
    protected void endTyped(VmTyped value, boolean isEmpty) {
      value.forceAndIterateMemberValues(
          ((memberKey, member, memberValue) -> {
            if ((memberValue instanceof VmTyped)
                || (memberValue instanceof VmDynamic)
                || (memberValue instanceof VmMapping)
                || (memberValue instanceof VmMap)) {

              if (memberKey instanceof Identifier) {
                currPath.push(memberKey);
              } else {
                currPath.push(converter.convert(memberKey, List.of()));
              }
              visit(memberValue);
              currPath.pop();
            }
            return true;
          }));
    }

    // ignored for now
    @Override
    protected void endListing(VmListing value, boolean isEmpty) {}

    @Override
    protected void endMapping(VmMapping value, boolean isEmpty) {
      value.forceAndIterateMemberValues(
          ((memberKey, member, memberValue) -> {
            if ((memberValue instanceof VmTyped)
                || (memberValue instanceof VmDynamic)
                || (memberValue instanceof VmMapping)
                || (memberValue instanceof VmMap)) {

              currPath.push(converter.convert(memberKey, List.of()));
              visit(memberValue);
              currPath.pop();
            }
            return true;
          }));
    }

    // ignored for now
    @Override
    protected void endList(VmList value) {}

    // ignored for now
    @Override
    protected void endSet(VmSet value) {}

    @Override
    protected void endMap(VmMap value) {
      value.forEach(
          (key) -> {
            var memberKey = key.getKey();
            var memberValue = key.getValue();
            if ((memberValue instanceof VmTyped)
                || (memberValue instanceof VmDynamic)
                || (memberValue instanceof VmMapping)
                || (memberValue instanceof VmMap)) {
              currPath.push(converter.convert(memberKey, List.of()));
              visit(memberValue);
              currPath.pop();
            }
          });
    }

    private void writeKeyOrValue(String value) {
      builder.append(IniUtils.renderPropertiesKeyOrValue(value, false, restrictCharset));
    }

    private void writeSeparator() {
      builder.append(" = ");
    }

    private void writeLineBreak() {
      builder.append("\n");
    }

    private String getSection() {
      var sectionBuilder = new StringBuilder();
      var isFollowing = new MutableBoolean(false);
      currPath
          .descendingIterator()
          .forEachRemaining(
              path -> {
                if (path == VmValueConverter.TOP_LEVEL_VALUE) {
                  return;
                }
                if (isFollowing.get()) {
                  sectionBuilder.append('.');
                }
                if (VmUtils.isRenderDirective(path)) {
                  sectionBuilder.append(VmUtils.readTextProperty(path));
                } else {
                  sectionBuilder.append(
                      IniUtils.renderPropertiesKeyOrValue(path.toString(), true, restrictCharset));
                }
                isFollowing.set(true);
              });

      return sectionBuilder.toString();
    }

    private void writeSection() {
      if (!currPath.isEmpty() && currPath.getFirst() != VmValueConverter.TOP_LEVEL_VALUE) {
        if (builder.charAt(builder.length() - 1) != '\n') {
          writeLineBreak();
        }
        writeLineBreak(); // writes break line to comply with ini styling standards
        builder.append('[');
        builder.append(getSection());
        builder.append(']');
        writeLineBreak();
      }
    }
  }
}
