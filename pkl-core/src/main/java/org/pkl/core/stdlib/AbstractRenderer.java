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
package org.pkl.core.stdlib;

import com.oracle.truffle.api.source.SourceSection;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmCollection;
import org.pkl.core.runtime.VmDynamic;
import org.pkl.core.runtime.VmException;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmListing;
import org.pkl.core.runtime.VmMap;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmSet;
import org.pkl.core.runtime.VmTypeAlias;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUndefinedValueException;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.runtime.VmValue;
import org.pkl.core.runtime.VmValueConverter;
import org.pkl.core.runtime.VmValueVisitor;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.MutableBoolean;
import org.pkl.core.util.Nullable;

/** Base class for renderers that are part of the standard library. */
public abstract class AbstractRenderer implements VmValueVisitor {
  protected static final char LINE_BREAK = '\n';

  /** The name of this renderer. */
  protected final String name;

  protected final StringBuilder builder;

  /** The indent to be used. */
  protected final String indent;

  protected final PklConverter converter;

  /** Whether to skip visiting properties with null value (after conversion). */
  private final boolean skipNullProperties;

  /** Whether to skip visiting entries with null value (after conversion). */
  private final boolean skipNullEntries;

  @LateInit protected Deque<Object> currPath;

  /** The value directly enclosing the currently visited value, if any. */
  protected @Nullable VmValue enclosingValue;

  /** The value passed to either {@link #renderDocument(Object)} or {@link #renderValue(Object)}. */
  @LateInit private Object topLevelValue;

  /** The current indent. Modified by {@link #increaseIndent()} and {@link #decreaseIndent()}. */
  protected final StringBuilder currIndent = new StringBuilder();

  /** The (closest) {@link SourceSection} of the value being visited, for better error messages. */
  protected @Nullable SourceSection currSourceSection = null;

  public AbstractRenderer(
      String name,
      StringBuilder builder,
      String indent,
      PklConverter converter,
      boolean skipNullProperties,
      boolean skipNullEntries) {
    this.name = name;
    this.builder = builder;
    this.indent = indent;
    this.converter = converter;
    this.skipNullProperties = skipNullProperties;
    this.skipNullEntries = skipNullEntries;
  }

  public final void renderDocument(Object value) {
    currPath = new ArrayDeque<>();
    currPath.push(VmValueConverter.TOP_LEVEL_VALUE);
    var converted = converter.convert(value, currPath);
    this.topLevelValue = converted;
    try {
      visitDocument(converted);
    } catch (VmException err) {
      if (converted != value) {
        err.setHint(
            String.format(
                "This value was converted during rendering. Previous: %s. After: %s.",
                new VmException.ProgramValue("before", value),
                new VmException.ProgramValue("after", converted)));
        throw err;
      }
      throw err;
    }
  }

  public final void renderValue(Object value) {
    currPath = new ArrayDeque<>();
    currPath.push(VmValueConverter.TOP_LEVEL_VALUE);
    var converted = converter.convert(value, currPath);
    this.topLevelValue = converted;
    visitTopLevelValue(converted);
  }

  @Override
  public void visit(Object value) {
    try {
      VmValueVisitor.super.visit(value);
    } catch (VmUndefinedValueException e) {
      // Only fill in the path if rendering the leaf member that threw the error. We know we are
      // at a leaf if the error's receiver is the same as `value`.
      if (e.getReceiver() == value) {
        throw e.fillInHint(currPath, topLevelValue);
      }
      throw e;
    }
  }

  protected abstract void visitDocument(Object value);

  protected abstract void visitTopLevelValue(Object value);

  /**
   * Perform logic for rendering a render directive. Render directives should be rendered verbatim.
   */
  protected abstract void visitRenderDirective(VmTyped value);

  protected abstract void startDynamic(VmDynamic value);

  protected abstract void startTyped(VmTyped value);

  protected abstract void startListing(VmListing value);

  protected abstract void startMapping(VmMapping value);

  protected abstract void startList(VmList value);

  protected abstract void startSet(VmSet value);

  protected abstract void startMap(VmMap value);

  /**
   * Visits an element of a {@link VmDynamic}, {@link VmListing}, {@link VmList}, or {@link VmSet}.
   */
  protected abstract void visitElement(long index, Object value, boolean isFirst);

  /** Visits an entry key of a {@link VmDynamic}, {@link VmMapping}, or {@link VmMap}. */
  protected abstract void visitEntryKey(Object key, boolean isFirst);

  /** Visits an entry value of a {@link VmDynamic}, {@link VmMapping}, or {@link VmMap}. */
  protected abstract void visitEntryValue(Object value);

  /** Visits a property of a {@link VmDynamic} or {@link VmTyped}. */
  protected abstract void visitProperty(Identifier name, Object value, boolean isFirst);

  protected abstract void endDynamic(VmDynamic value, boolean isEmpty);

  protected abstract void endTyped(VmTyped value, boolean isEmpty);

  protected abstract void endListing(VmListing value, boolean isEmpty);

  protected abstract void endMapping(VmMapping value, boolean isEmpty);

  protected abstract void endList(VmList value);

  protected abstract void endSet(VmSet value);

  protected abstract void endMap(VmMap value);

  private void doVisitProperty(
      Identifier name, Object value, SourceSection sourceSection, MutableBoolean isFirst) {
    var prevSourceSection = currSourceSection;
    currSourceSection = sourceSection;
    currPath.push(name);
    var convertedValue = converter.convert(value, currPath);
    if (!(skipNullProperties && convertedValue instanceof VmNull)) {
      visitProperty(name, convertedValue, isFirst.getAndSetFalse());
    }
    currPath.pop();
    currSourceSection = prevSourceSection;
  }

  private void doVisitEntry(
      Object key, Object value, @Nullable SourceSection sourceSection, MutableBoolean isFirst) {
    var prevSourceSection = currSourceSection;
    if (sourceSection != null) {
      currSourceSection = sourceSection;
    }
    var valuePath = currPath;
    try {
      var convertedKey = converter.convert(key, List.of());
      valuePath.push(convertedKey);
      var convertedValue = converter.convert(value, valuePath);
      if (skipNullEntries && (convertedValue instanceof VmNull)) {
        return;
      }

      currPath = new ArrayDeque<>();
      visitEntryKey(convertedKey, isFirst.getAndSetFalse());
      currPath = valuePath;
      visitEntryValue(convertedValue);
    } finally {
      valuePath.pop();
      currSourceSection = prevSourceSection;
    }
  }

  private void doVisitElement(
      long index, Object value, @Nullable SourceSection sourceSection, boolean isFirst) {
    var prevSourceSection = currSourceSection;
    if (sourceSection != null) {
      currSourceSection = sourceSection;
    }
    currPath.push(index);
    var convertedValue = converter.convert(value, currPath);
    visitElement(index, convertedValue, isFirst);
    currPath.pop();
    currSourceSection = prevSourceSection;
  }

  @Override
  public void visitTyped(VmTyped value) {
    // value.getParent().getMember(value);
    if (VmUtils.isRenderDirective(value)) {
      visitRenderDirective(value);
      return;
    }

    startTyped(value);
    var prevEnclosingValue = enclosingValue;
    enclosingValue = value;
    var isFirst = new MutableBoolean(true);

    value.forceAndIterateMemberValues(
        (memberKey, member, memberValue) -> {
          if (member.isClass() || member.isTypeAlias()) return true;
          assert member.isProp();
          doVisitProperty((Identifier) memberKey, memberValue, member.getSourceSection(), isFirst);
          return true;
        });

    enclosingValue = prevEnclosingValue;
    endTyped(value, isFirst.get());
  }

  @Override
  public final void visitDynamic(VmDynamic value) {
    startDynamic(value);

    var prevEnclosingValue = enclosingValue;
    enclosingValue = value;
    var isFirst = new MutableBoolean(true);
    var canRenderPropertyOrEntry = canRenderPropertyOrEntryOf(value);

    value.forceAndIterateMemberValues(
        (memberKey, member, memberValue) -> {
          var sourceSection = member.getSourceSection();
          if (member.isProp()) {
            if (!canRenderPropertyOrEntry) cannotRenderObjectWithElementsAndOtherMembers(value);
            doVisitProperty((Identifier) memberKey, memberValue, sourceSection, isFirst);
          } else if (member.isEntry()) {
            if (!canRenderPropertyOrEntry) cannotRenderObjectWithElementsAndOtherMembers(value);
            doVisitEntry(memberKey, memberValue, sourceSection, isFirst);
          } else {
            doVisitElement((long) memberKey, memberValue, sourceSection, isFirst.getAndSetFalse());
          }
          return true;
        });

    enclosingValue = prevEnclosingValue;
    endDynamic(value, isFirst.get());
  }

  protected boolean canRenderPropertyOrEntryOf(VmDynamic object) {
    return !object.hasElements();
  }

  private void cannotRenderObjectWithElementsAndOtherMembers(VmDynamic object) {
    throw new VmExceptionBuilder()
        .evalError("cannotRenderObjectWithElementsAndOtherMembers", name)
        .withProgramValue("Object", object)
        .build();
  }

  @Override
  public final void visitListing(VmListing value) {
    startListing(value);
    var prevEnclosingValue = enclosingValue;
    enclosingValue = value;
    var isFirst = new MutableBoolean(true);

    value.forceAndIterateMemberValues(
        (memberKey, member, memberValue) -> {
          assert member.isElement();
          doVisitElement(
              (long) memberKey, memberValue, member.getSourceSection(), isFirst.getAndSetFalse());
          return true;
        });

    enclosingValue = prevEnclosingValue;
    endListing(value, isFirst.get());
  }

  @Override
  public final void visitMapping(VmMapping value) {
    startMapping(value);
    var prevEnclosingValue = enclosingValue;
    enclosingValue = value;
    var isFirst = new MutableBoolean(true);

    value.forceAndIterateMemberValues(
        (memberKey, member, memberValue) -> {
          assert member.isEntry();
          doVisitEntry(memberKey, memberValue, member.getSourceSection(), isFirst);
          return true;
        });

    enclosingValue = prevEnclosingValue;
    endMapping(value, isFirst.get());
  }

  @Override
  public final void visitList(VmList value) {
    startList(value);
    doVisitCollectionElements(value);
    endList(value);
  }

  @Override
  public final void visitSet(VmSet value) {
    startSet(value);
    doVisitCollectionElements(value);
    endSet(value);
  }

  private void doVisitCollectionElements(VmCollection value) {
    var prevEnclosingValue = enclosingValue;
    enclosingValue = value;
    var index = 0L;

    for (var elem : value) {
      doVisitElement(index, elem, null, index == 0);
      index += 1;
    }

    enclosingValue = prevEnclosingValue;
  }

  @Override
  public final void visitMap(VmMap value) {
    startMap(value);
    var prevEnclosingValue = enclosingValue;
    enclosingValue = value;
    var isFirst = new MutableBoolean(true);

    for (var entry : value) {
      doVisitEntry(entry.getKey(), entry.getValue(), null, isFirst);
    }

    enclosingValue = prevEnclosingValue;
    endMap(value);
  }

  @Override
  public final void visitTypeAlias(VmTypeAlias value) {
    cannotRenderTypeAddConverter(value);
  }

  @Override
  public final void visitClass(VmClass value) {
    cannotRenderTypeAddConverter(value);
  }

  @Override
  public final void visitFunction(VmFunction value) {
    cannotRenderTypeAddConverter(value);
  }

  protected void cannotRenderTypeAddConverter(VmValue value) {
    var builder =
        new VmExceptionBuilder()
            .evalError("cannotRenderTypeAddConverter", value.getVmClass(), name)
            .withProgramValue("Value", value);
    if (currSourceSection != null) {
      builder.withSourceSection(currSourceSection);
    }
    throw builder.build();
  }

  protected void cannotRenderNonStringKey(Object key) {
    assert enclosingValue != null;
    var isMap = enclosingValue instanceof VmMap;
    throw new VmExceptionBuilder()
        .evalError(isMap ? "cannotRenderNonStringMap" : "cannotRenderObjectWithNonStringKey", name)
        .withProgramValue(isMap ? "Map" : "Object", enclosingValue)
        .withProgramValue("Key", key)
        .build();
  }

  protected void cannotRenderNonScalarKey(Object key) {
    assert enclosingValue != null;
    var isMap = enclosingValue instanceof VmMap;
    throw new VmExceptionBuilder()
        .evalError(isMap ? "cannotRenderNonScalarMap" : "cannotRenderObjectWithNonScalarKey", name)
        .withProgramValue(isMap ? "Map" : "Object", enclosingValue)
        .withProgramValue("Key", key)
        .build();
  }

  protected void increaseIndent() {
    currIndent.append(indent);
  }

  protected void decreaseIndent() {
    currIndent.setLength(currIndent.length() - indent.length());
  }
}
