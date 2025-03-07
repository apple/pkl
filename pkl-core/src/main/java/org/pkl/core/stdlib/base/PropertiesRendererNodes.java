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
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmBytes;
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
import org.pkl.core.runtime.VmValue;
import org.pkl.core.runtime.VmValueConverter;
import org.pkl.core.stdlib.AbstractRenderer;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.PklConverter;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.MutableBoolean;
import org.pkl.core.util.properties.PropertiesUtils;

public final class PropertiesRendererNodes {
  private PropertiesRendererNodes() {}

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

  private static PropertiesRenderer createRenderer(VmTyped self, StringBuilder builder) {
    var omitNullProperties = (boolean) VmUtils.readMember(self, Identifier.OMIT_NULL_PROPERTIES);
    var restrictCharset = (boolean) VmUtils.readMember(self, Identifier.RESTRICT_CHARSET);
    var converters = (VmMapping) VmUtils.readMember(self, Identifier.CONVERTERS);
    var PklConverter = new PklConverter(converters);
    return new PropertiesRenderer(builder, omitNullProperties, restrictCharset, PklConverter);
  }

  private static final class PropertiesRenderer extends AbstractRenderer {
    private final boolean restrictCharset;

    private boolean isDocument;

    public PropertiesRenderer(
        StringBuilder builder,
        boolean omitNullProperties,
        boolean restrictCharset,
        PklConverter converter) {
      super("Properties", builder, "", converter, omitNullProperties, omitNullProperties);
      this.restrictCharset = restrictCharset;
    }

    @Override
    public void visitString(String value) {
      visitPropertyValue(value);
    }

    @Override
    public void visitBoolean(Boolean value) {
      visitPropertyValue(value);
    }

    @Override
    public void visitInt(Long value) {
      visitPropertyValue(value);
    }

    @Override
    public void visitFloat(Double value) {
      visitPropertyValue(value);
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
        writeKey();
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
      if ((value instanceof VmMap
              || value instanceof VmTyped
              || value instanceof VmMapping
              || value instanceof VmDynamic)
          && !VmUtils.isRenderDirective(value)) {
        cannotRenderTypeAddConverter((VmValue) value);
      }
      isDocument = false;
      visit(value);
    }

    @Override
    protected void visitRenderDirective(VmTyped value) {
      if (isDocument) {
        writeKey();
        writeSeparator();
      }
      builder.append(VmUtils.readTextProperty(value));
      if (isDocument) {
        writeLineBreak();
      }
    }

    @Override
    protected void startDynamic(VmDynamic value) {}

    @Override
    protected void startTyped(VmTyped value) {}

    @Override
    protected void startListing(VmListing value) {
      cannotRenderTypeAddConverter(value);
    }

    @Override
    protected void startMapping(VmMapping value) {}

    @Override
    protected void startList(VmList value) {
      cannotRenderTypeAddConverter(value);
    }

    @Override
    protected void startSet(VmSet value) {
      cannotRenderTypeAddConverter(value);
    }

    @Override
    protected void startMap(VmMap value) {}

    @Override
    protected void visitElement(long index, Object value, boolean isFirst) {}

    @Override
    protected void visitEntryKey(Object key, boolean isFirst) {}

    @Override
    protected void visitEntryValue(Object value) {
      visitKeyedValue(value);
    }

    @Override
    protected void visitProperty(Identifier name, Object value, boolean isFirst) {
      visitKeyedValue(value);
    }

    @Override
    protected void endDynamic(VmDynamic value, boolean isEmpty) {}

    @Override
    protected void endTyped(VmTyped value, boolean isEmpty) {}

    @Override
    protected void endListing(VmListing value, boolean isEmpty) {}

    @Override
    protected void endMapping(VmMapping value, boolean isEmpty) {}

    @Override
    protected void endList(VmList value) {}

    @Override
    protected void endSet(VmSet value) {}

    @Override
    protected void endMap(VmMap value) {}

    private void writeValue(String value) {
      builder.append(PropertiesUtils.renderPropertiesKeyOrValue(value, false, restrictCharset));
    }

    private void writeSeparator() {
      builder.append(" = ");
    }

    private void writeLineBreak() {
      builder.append("\n");
    }

    private void visitPropertyValue(Object value) {
      if (isDocument) {
        writeKey();
        writeSeparator();
      }
      writeValue(value.toString());
      if (isDocument) {
        writeLineBreak();
      }
    }

    private void visitKeyedValue(Object value) {
      // Edge-case: Dynamics are implicitly converted to Listing.
      if (value instanceof VmDynamic dynamic && dynamic.hasElements()) {
        var newValue =
            new VmListing(
                VmUtils.createEmptyMaterializedFrame(),
                dynamic,
                EconomicMaps.create(),
                dynamic.getLength());
        visit(converter.convert(newValue, currPath));
      } else {
        visit(value);
      }
    }

    private void writeKey() {
      var isFollowing = new MutableBoolean(false);
      currPath
          .descendingIterator()
          .forEachRemaining(
              path -> {
                if (path == VmValueConverter.TOP_LEVEL_VALUE) {
                  return;
                }
                if (isFollowing.get()) {
                  builder.append('.');
                }
                if (VmUtils.isRenderDirective(path)) {
                  builder.append(VmUtils.readTextProperty(path));
                } else {
                  builder.append(
                      PropertiesUtils.renderPropertiesKeyOrValue(
                          path.toString(), true, restrictCharset));
                }
                isFollowing.set(true);
              });
    }
  }
}
