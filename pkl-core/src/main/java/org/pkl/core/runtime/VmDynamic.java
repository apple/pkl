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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import java.util.EnumSet;
import java.util.Objects;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.jspecify.annotations.Nullable;
import org.pkl.core.PClassInfo;
import org.pkl.core.PObject;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.VmDynamicCursors.CachedElementCursor;
import org.pkl.core.runtime.VmDynamicCursors.CachedEntryCursor;
import org.pkl.core.runtime.VmDynamicCursors.ElementCursor;
import org.pkl.core.runtime.VmDynamicCursors.EntryCursor;
import org.pkl.core.runtime.VmDynamicCursors.MemberCursor;
import org.pkl.core.runtime.VmDynamicCursors.PropertyCursor;
import org.pkl.core.runtime.VmDynamicCursors.UnorderedEntryCursor;
import org.pkl.core.runtime.VmDynamicCursors.UnorderedMemberCursor;
import org.pkl.core.runtime.VmDynamicCursors.UnorderedPropertyCursor;
import org.pkl.core.runtime.VmObjectCursor.CursorOption;
import org.pkl.core.util.CollectionUtils;
import org.pkl.core.util.EconomicMaps;

public final class VmDynamic extends VmObject {
  private int cachedRegularMemberCount = -1;

  private static final class EmptyHolder {
    private static final VmDynamic EMPTY =
        new VmDynamic(
            VmUtils.createEmptyMaterializedFrame(),
            BaseModule.getDynamicClass().getPrototype(),
            EconomicMaps.create(),
            0);
  }

  private final int length;

  public static VmDynamic empty() {
    return EmptyHolder.EMPTY;
  }

  public VmDynamic(
      MaterializedFrame enclosingFrame,
      VmObject parent,
      UnmodifiableEconomicMap<Object, ObjectMember> members,
      int length) {
    super(enclosingFrame, Objects.requireNonNull(parent), members);
    this.length = length;
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getDynamicClass();
  }

  /** Returns the number of elements in this object. */
  public int getLength() {
    return length;
  }

  /** Tells whether this object has any elements. */
  public boolean hasElements() {
    return length != 0;
  }

  @Override
  public boolean isSequence() {
    return hasElements();
  }

  @Override
  public PObject export() {
    assert isDeepForced() : "Value was not forced prior to export";

    var properties =
        CollectionUtils.<String, Object>newLinkedHashMap(EconomicMaps.size(cachedValues));
    // export all members, not just properties
    for (var cursor = members(); cursor.advance(); ) {
      CollectionUtils.put(
          properties, cursor.key().toString(), VmValue.export(cursor.cachedValue()));
    }
    return new PObject(PClassInfo.Dynamic, properties);
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitDynamic(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertDynamic(this, path);
  }

  @Override
  public VmObjectCursor properties() {
    return new PropertyCursor(this);
  }

  @Override
  public VmObjectCursor properties(CursorOption option) {
    // never shallow-force because it's impossible to only force properties
    var anyOrder = option == CursorOption.ANY_ORDER;
    return anyOrder ? new UnorderedPropertyCursor(this) : new PropertyCursor(this);
  }

  @Override
  public VmObjectCursor elements() {
    return new ElementCursor(this);
  }

  @Override
  public VmObjectCursor elements(CursorOption option) {
    // never shallow-force because it's impossible to only force elements
    var anyOrder = option == CursorOption.ANY_ORDER;
    return anyOrder && isShallowForced() ? new CachedElementCursor(this) : new ElementCursor(this);
  }

  @Override
  public VmObjectCursor elements(EnumSet<CursorOption> options) {
    // never shallow-force because it's impossible to only force elements
    var anyOrder = options.contains(CursorOption.ANY_ORDER);
    return anyOrder && isShallowForced() ? new CachedElementCursor(this) : new ElementCursor(this);
  }

  @Override
  public VmObjectCursor entries() {
    return new EntryCursor(this);
  }

  @Override
  public VmObjectCursor entries(CursorOption option) {
    // never shallow-force because it's impossible to only force entries
    var anyOrder = option == CursorOption.ANY_ORDER;
    if (anyOrder) {
      return isShallowForced() ? new CachedEntryCursor(this) : new UnorderedEntryCursor(this);
    }
    return new EntryCursor(this);
  }

  @Override
  public VmObjectCursor entries(EnumSet<CursorOption> options) {
    // never shallow-force because it's impossible to only force entries
    var anyOrder = options.contains(CursorOption.ANY_ORDER);
    if (anyOrder) {
      return isShallowForced() ? new CachedEntryCursor(this) : new UnorderedEntryCursor(this);
    }
    return new EntryCursor(this);
  }

  @Override
  public VmObjectCursor members() {
    return new MemberCursor(this);
  }

  @Override
  public VmObjectCursor members(CursorOption option) {
    if (option == CursorOption.ALL_VALUES) {
      force(false, false);
    }
    return option == CursorOption.ANY_ORDER
        ? new UnorderedMemberCursor(this)
        : new MemberCursor(this);
  }

  @Override
  @TruffleBoundary
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof VmDynamic other)) return false;

    // could use shallow force, but deep force is cached
    force(false);
    other.force(false);
    if (getRegularMemberCount() != other.getRegularMemberCount()) return false;

    var cursor = cachedValues.getEntries();
    while (cursor.advance()) {
      Object key = cursor.getKey();
      if (isHiddenOrLocalProperty(key)) continue;

      var value = cursor.getValue();
      //noinspection ConstantValue
      assert value != null;
      var otherValue = other.getCachedValue(key);
      if (!value.equals(otherValue)) return false;
    }

    return true;
  }

  @Override
  @TruffleBoundary
  public int hashCode() {
    if (cachedHash != 0) return cachedHash;

    force(false);
    var result = 0;
    var cursor = cachedValues.getEntries();

    while (cursor.advance()) {
      var key = cursor.getKey();
      if (isHiddenOrLocalProperty(key)) continue;

      var value = cursor.getValue();
      //noinspection ConstantValue
      assert value != null;
      result += key.hashCode() ^ value.hashCode();
    }

    cachedHash = result;
    return result;
  }

  public int getRegularMemberCount() {
    assert isShallowForced();
    if (cachedRegularMemberCount != -1) return cachedRegularMemberCount;
    var result = 0;
    for (var key : cachedValues.getKeys()) {
      if (!isHiddenOrLocalProperty(key)) result += 1;
    }
    cachedRegularMemberCount = result;
    return result;
  }

  private boolean isHiddenOrLocalProperty(Object key) {
    // only local members have the entire `ObjectMember` stored as a key in cachedValues
    if (key instanceof ObjectMember member) {
      assert member.isLocal();
      return true;
    }
    return key instanceof Identifier identifier
        && (key == Identifier.DEFAULT || identifier.isLocalProp());
  }
}
