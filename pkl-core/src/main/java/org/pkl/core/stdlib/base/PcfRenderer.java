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

import org.pkl.core.ValueFormatter;
import org.pkl.core.parser.Lexer;
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
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.runtime.VmPair;
import org.pkl.core.runtime.VmRegex;
import org.pkl.core.runtime.VmSet;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.AbstractRenderer;
import org.pkl.core.stdlib.PklConverter;
import org.pkl.core.util.LateInit;

public final class PcfRenderer extends AbstractRenderer {
  private final ValueFormatter valueFormatter;

  private boolean isDocument;
  @LateInit private Object topLevelValue;

  public PcfRenderer(
      StringBuilder builder,
      String indent,
      PklConverter converter,
      boolean omitNullProperties,
      boolean useCustomStringDelimiters) {
    super("Pcf", builder, indent, converter, omitNullProperties, false);
    this.valueFormatter = new ValueFormatter(true, useCustomStringDelimiters);
  }

  @Override
  public void visitString(String value) {
    increaseIndent();
    valueFormatter.formatStringValue(value, currIndent, builder);
    decreaseIndent();
  }

  private void renderStringElement(String value) {
    valueFormatter.formatStringValue(value, currIndent, builder);
  }

  @Override
  public void visitTyped(VmTyped value) {
    if (VmUtils.isPcfRenderDirective(value)) {
      visitPcfRenderDirective(value);
    } else {
      super.visitTyped(value);
    }
  }

  @Override
  public void visitInt(Long value) {
    builder.append((long) value);
  }

  @Override
  public void visitFloat(Double value) {
    builder.append((double) value);
  }

  @Override
  public void visitBoolean(Boolean value) {
    builder.append((boolean) value);
  }

  @Override
  public void visitDuration(VmDuration value) {
    builder.append(value);
  }

  @Override
  public void visitDataSize(VmDataSize value) {
    builder.append(value);
  }

  @Override
  public void visitPair(VmPair value) {
    builder.append("Pair(");
    visitStandaloneValue(value.getFirst());
    builder.append(", ");
    visitStandaloneValue(value.getSecond());
    builder.append(')');
  }

  @Override
  public void visitRegex(VmRegex value) {
    builder.append(value);
  }

  @Override
  public void visitIntSeq(VmIntSeq value) {
    builder.append(value);
  }

  @Override
  public void visitNull(VmNull value) {
    builder.append("null");
  }

  @Override
  protected void visitRenderDirective(VmTyped value) {
    builder.append(VmUtils.readTextProperty(value));
  }

  private void visitPcfRenderDirective(VmTyped value) {
    var before = VmUtils.readMember(value, Identifier.BEFORE);
    if (before instanceof String) { // not VmNull
      builder.append((String) before);
    }
    visit(VmUtils.readMember(value, Identifier.VALUE));
    var after = VmUtils.readMember(value, Identifier.AFTER);
    if (after instanceof String) { // not VmNull
      builder.append((String) after);
    }
  }

  @Override
  protected void visitDocument(Object value) {
    if (!(value instanceof VmTyped || value instanceof VmDynamic)) {
      throw new VmExceptionBuilder()
          .evalError("invalidPcfTopLevelValue", VmUtils.getClass(value))
          .withProgramValue("Value", value)
          .build();
    }
    isDocument = true;
    topLevelValue = value;
    visit(value);
    if (builder.length() > 0) {
      builder.append('\n');
    }
  }

  @Override
  protected void visitTopLevelValue(Object value) {
    topLevelValue = value;
    visit(value);
  }

  @Override
  protected boolean canRenderPropertyOrEntryOf(VmDynamic object) {
    return true;
  }

  @Override
  protected void startDynamic(VmDynamic value) {
    startObject(value);
  }

  @Override
  protected void startTyped(VmTyped value) {
    startObject(value);
  }

  @Override
  protected void startListing(VmListing value) {
    startObject(value);
  }

  @Override
  protected void startMapping(VmMapping value) {
    startObject(value);
  }

  @Override
  protected void startList(VmList value) {
    builder.append("List(");
  }

  @Override
  protected void startSet(VmSet value) {
    builder.append("Set(");
  }

  @Override
  protected void startMap(VmMap value) {
    builder.append("Map(");
  }

  @Override
  protected void visitElement(long index, Object value, boolean isFirst) {
    if (enclosingValue instanceof VmObjectLike) {
      builder.append('\n');
      builder.append(currIndent);
      if (value instanceof String) {
        renderStringElement((String) value);
      } else {
        visitStandaloneValue(value);
      }
    } else {
      if (!isFirst) {
        builder.append(", ");
      }
      visitStandaloneValue(value);
    }
  }

  private void visitStandaloneValue(Object value) {
    if (value instanceof VmObjectLike && !VmUtils.isRenderDirective(value)) {
      builder.append("new ");
    }
    visit(value);
  }

  @Override
  protected void visitEntryKey(Object key, boolean isFirst) {
    if (enclosingValue instanceof VmObjectLike) {
      builder.append('\n');
      builder.append(currIndent);
      builder.append('[');
      visitStandaloneValue(key);
      builder.append(']');
    } else {
      if (!isFirst) {
        builder.append(", ");
      }
      visitStandaloneValue(key);
    }
  }

  @Override
  protected void visitEntryValue(Object value) {
    if (enclosingValue instanceof VmObjectLike) {
      if (value instanceof VmObjectLike) {
        builder.append(' ');
      } else {
        builder.append(" = ");
      }
      visit(value);
    } else {
      builder.append(", ");
      visitStandaloneValue(value);
    }
  }

  @Override
  protected void visitProperty(Identifier name, Object value, boolean isFirst) {
    if (builder.length() > 0) {
      builder.append('\n');
      builder.append(currIndent);
    }
    builder.append(Lexer.maybeQuoteIdentifier(name.toString()));
    if (value instanceof VmObjectLike) {
      builder.append(' ');
    } else {
      builder.append(" = ");
    }
    visit(value);
  }

  @Override
  protected void endDynamic(VmDynamic value, boolean isEmpty) {
    endObject(value, isEmpty);
  }

  @Override
  protected void endTyped(VmTyped value, boolean isEmpty) {
    endObject(value, isEmpty);
  }

  @Override
  protected void endListing(VmListing value, boolean isEmpty) {
    endObject(value, isEmpty);
  }

  @Override
  protected void endMapping(VmMapping value, boolean isEmpty) {
    endObject(value, isEmpty);
  }

  @Override
  protected void endList(VmList value) {
    builder.append(')');
  }

  @Override
  protected void endSet(VmSet value) {
    builder.append(')');
  }

  @Override
  protected void endMap(VmMap value) {
    builder.append(')');
  }

  private void startObject(VmObjectLike value) {
    if (isDocument && value == topLevelValue) return;

    increaseIndent();
    builder.append('{');
  }

  private void endObject(VmObjectLike value, boolean isEmpty) {
    if (isDocument && value == topLevelValue) return;

    decreaseIndent();
    if (!isEmpty) {
      builder.append('\n');
      builder.append(currIndent);
    }
    builder.append('}');
  }
}
