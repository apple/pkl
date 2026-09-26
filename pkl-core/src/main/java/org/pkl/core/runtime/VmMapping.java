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

import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.util.EnumSet;
import java.util.Map;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.member.ListingOrMappingTypeCastNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.VmMappingCursors.CachedEntryCursor;
import org.pkl.core.runtime.VmMappingCursors.EntryCursor;
import org.pkl.core.runtime.VmObjectCursor.CursorOption;
import org.pkl.core.runtime.VmObjectCursor.EmptyCursor;
import org.pkl.core.util.CollectionUtils;
import org.pkl.core.util.EconomicMaps;

public final class VmMapping extends VmListingOrMapping {
  @CompilationFinal private long cachedLength = -1;

  @GuardedBy("this")
  private @Nullable VmSet __allKeys;

  private static final class EmptyHolder {
    private static final VmMapping EMPTY =
        new VmMapping(
            VmUtils.createEmptyMaterializedFrame(),
            BaseModule.getMappingClass().getPrototype(),
            EconomicMaps.create());
  }

  public static VmMapping empty() {
    return EmptyHolder.EMPTY;
  }

  public VmMapping(
      MaterializedFrame enclosingFrame,
      VmObject parent,
      UnmodifiableEconomicMap<Object, ObjectMember> members) {
    super(enclosingFrame, parent, members);
  }

  public VmMapping(
      MaterializedFrame enclosingFrame,
      VmObject parent,
      UnmodifiableEconomicMap<Object, ObjectMember> members,
      ListingOrMappingTypeCastNode typeCastNode,
      Object typeCheckReceiver,
      VmObjectLike typeCheckOwner) {
    super(enclosingFrame, parent, members, typeCastNode, typeCheckReceiver, typeCheckOwner);
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getMappingClass();
  }

  @TruffleBoundary
  public VmSet getAllKeys() {
    synchronized (this) {
      if (__allKeys == null) {
        // building upon parent's `getAllKeys()` should improve at least worst case efficiency
        var parentKeys = parent instanceof VmMapping mapping ? mapping.getAllKeys() : VmSet.EMPTY;
        var builder = VmSet.builder(parentKeys);
        for (var cursor = members.getEntries(); cursor.advance(); ) {
          var member = cursor.getValue();
          if (!member.isEntry()) continue;
          builder.add(cursor.getKey());
        }
        __allKeys = builder.build();
      }
      return __allKeys;
    }
  }

  @Override
  public Map<Object, Object> export() {
    assert isDeepForced() : "Value was not forced prior to export";

    var entries = CollectionUtils.newLinkedHashMap(EconomicMaps.size(cachedValues));
    for (var cursor = entries(); cursor.advance(); ) {
      entries.put(VmValue.export(cursor.key()), VmValue.export(cursor.cachedValue()));
    }
    return entries;
  }

  public Map<Object, Object> toMap(IndirectCallNode callNode) {
    var entries = CollectionUtils.newLinkedHashMap(EconomicMaps.size(cachedValues));
    for (var cursor = entries(); cursor.advance(); ) {
      entries.put(cursor.key(), cursor.value(callNode));
    }
    return entries;
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitMapping(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertMapping(this, path);
  }

  @Override
  public VmObjectCursor properties() {
    return EmptyCursor.INSTANCE;
  }

  @Override
  public VmObjectCursor properties(CursorOption option) {
    return EmptyCursor.INSTANCE;
  }

  @Override
  public VmObjectCursor elements() {
    return EmptyCursor.INSTANCE;
  }

  @Override
  public VmObjectCursor elements(CursorOption option) {
    return EmptyCursor.INSTANCE;
  }

  @Override
  public VmObjectCursor elements(EnumSet<CursorOption> options) {
    return EmptyCursor.INSTANCE;
  }

  public VmObjectCursor entries() {
    return new EntryCursor(this);
  }

  public VmObjectCursor entries(CursorOption option) {
    if (option == CursorOption.ANY_ORDER) {
      return isShallowForced() ? new CachedEntryCursor(this) : new EntryCursor(this);
    }
    if (option == CursorOption.ALL_VALUES) {
      force(false, false);
      return new EntryCursor(this);
    }
    return new EntryCursor(this);
  }

  @Override
  public VmObjectCursor entries(EnumSet<CursorOption> options) {
    var anyOrder = options.contains(CursorOption.ANY_ORDER);
    var allValues = options.contains(CursorOption.ALL_VALUES);
    var lazyRequired = options.contains(CursorOption.LAZY_REQUIRED);
    if (anyOrder && !lazyRequired) {
      if (isShallowForced()) {
        return new CachedEntryCursor(this);
      }
      if (allValues) {
        force(false, false);
        return new CachedEntryCursor(this);
      }
    }
    return new EntryCursor(this);
  }

  @Override
  public VmObjectCursor members() {
    return entries();
  }

  @Override
  public VmObjectCursor members(CursorOption option) {
    return entries(option);
  }

  @Override
  @TruffleBoundary
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof VmMapping other)) return false;

    // could use shallow force, but deep force is cached
    force(false);
    other.force(false);
    if (getLength() != other.getLength()) return false;

    var cursor = cachedValues.getEntries();
    while (cursor.advance()) {
      Object key = cursor.getKey();
      if (key instanceof Identifier) continue;

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
      if (key instanceof Identifier) continue;

      var value = cursor.getValue();
      //noinspection ConstantValue
      assert value != null;
      result += key.hashCode() ^ value.hashCode();
    }

    cachedHash = result;
    return result;
  }

  public long getLength() {
    if (cachedLength != -1) return cachedLength;
    CompilerDirectives.transferToInterpreterAndInvalidate();
    var count = 0;
    for (var cursor = entries(CursorOption.LAZY_REQUIRED); cursor.advance(); ) {
      count++;
    }
    cachedLength = count;
    return cachedLength;
  }

  public boolean isEmpty() {
    return !entries(EnumSet.of(CursorOption.ANY_ORDER, CursorOption.LAZY_REQUIRED)).advance();
  }
}
