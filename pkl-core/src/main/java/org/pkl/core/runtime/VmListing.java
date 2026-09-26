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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.member.ListingOrMappingTypeCastNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.VmListingCursors.CachedElementCursor;
import org.pkl.core.runtime.VmListingCursors.ElementCursor;
import org.pkl.core.runtime.VmObjectCursor.CursorOption;
import org.pkl.core.runtime.VmObjectCursor.EmptyCursor;
import org.pkl.core.util.EconomicMaps;

public final class VmListing extends VmListingOrMapping {
  private static final class EmptyHolder {
    private static final VmListing EMPTY =
        new VmListing(
            VmUtils.createEmptyMaterializedFrame(),
            BaseModule.getListingClass().getPrototype(),
            EconomicMaps.create(),
            0);
  }

  private final int length;

  public static VmListing empty() {
    return EmptyHolder.EMPTY;
  }

  public VmListing(
      MaterializedFrame enclosingFrame,
      VmObject parent,
      UnmodifiableEconomicMap<Object, ObjectMember> members,
      int length) {
    super(enclosingFrame, parent, members);
    this.length = length;
  }

  public VmListing(
      MaterializedFrame enclosingFrame,
      VmObject parent,
      UnmodifiableEconomicMap<Object, ObjectMember> members,
      int length,
      ListingOrMappingTypeCastNode typeCastNode,
      Object typeCheckReceiver,
      VmObjectLike typeCheckOwner) {
    super(enclosingFrame, parent, members, typeCastNode, typeCheckReceiver, typeCheckOwner);
    this.length = length;
  }

  public int getLength() {
    return length;
  }

  public boolean isEmpty() {
    return length == 0;
  }

  @Override
  public boolean isSequence() {
    return true;
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getListingClass();
  }

  @Override
  @TruffleBoundary
  public List<Object> export() {
    assert isDeepForced() : "Value was not forced prior to export";

    var elements = new ArrayList<>(EconomicMaps.size(cachedValues));
    for (var cursor = elements(CursorOption.ALL_VALUES); cursor.advance(); ) {
      elements.add(VmValue.export(cursor.cachedValue()));
    }
    return elements;
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitListing(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertListing(this, path);
  }

  @Override
  @TruffleBoundary
  public VmObjectCursor elements() {
    return new ElementCursor(this);
  }

  @Override
  @TruffleBoundary
  public VmObjectCursor elements(CursorOption option) {
    if (option == CursorOption.ANY_ORDER) {
      return isShallowForced() ? new CachedElementCursor(this) : new ElementCursor(this);
    }
    if (option == CursorOption.ALL_VALUES) {
      force(false, false);
      return new ElementCursor(this);
    }
    return new ElementCursor(this);
  }

  @Override
  public VmObjectCursor elements(EnumSet<CursorOption> options) {
    var anyOrder = options.contains(CursorOption.ANY_ORDER);
    var allValues = options.contains(CursorOption.ALL_VALUES);
    var lazyRequired = options.contains(CursorOption.LAZY_REQUIRED);
    if (anyOrder && !lazyRequired) {
      if (isShallowForced()) {
        return new CachedElementCursor(this);
      }
      if (allValues) {
        force(false, false);
        return new CachedElementCursor(this);
      }
    }
    return new ElementCursor(this);
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
  public VmObjectCursor entries() {
    return EmptyCursor.INSTANCE;
  }

  @Override
  public VmObjectCursor entries(CursorOption option) {
    return EmptyCursor.INSTANCE;
  }

  @Override
  public VmObjectCursor entries(EnumSet<CursorOption> options) {
    return EmptyCursor.INSTANCE;
  }

  @Override
  public VmObjectCursor members() {
    return elements();
  }

  @Override
  public VmObjectCursor members(CursorOption option) {
    return elements(option);
  }

  @Override
  @TruffleBoundary
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof VmListing other)) return false;

    if (length != other.length) return false;
    // could use shallow force, but deep force is cached
    force(false);
    other.force(false);

    var cursor = cachedValues.getEntries();
    while (cursor.advance()) {
      var key = cursor.getKey();
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
      result = 31 * result + value.hashCode();
    }

    cachedHash = result;
    return result;
  }
}
