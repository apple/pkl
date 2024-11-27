/*
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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import java.util.Map;
import java.util.Objects;
import javax.annotation.concurrent.GuardedBy;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.ast.member.ListingOrMappingTypeCastNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.util.CollectionUtils;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.LateInit;

public final class VmMapping extends VmListingOrMapping<VmMapping> {

  private int cachedEntryCount = -1;

  @GuardedBy("this")
  private @LateInit VmSet __allKeys;

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

    super(enclosingFrame, Objects.requireNonNull(parent), members, null, null, null);
  }

  public VmMapping(
      MaterializedFrame enclosingFrame,
      VmObject parent,
      UnmodifiableEconomicMap<Object, ObjectMember> members,
      VmMapping delegate,
      ListingOrMappingTypeCastNode typeCheckNode,
      MaterializedFrame typeNodeFrame) {
    super(
        enclosingFrame,
        Objects.requireNonNull(parent),
        members,
        delegate,
        typeCheckNode,
        typeNodeFrame);
  }

  public static boolean isDefaultProperty(Object propertyKey) {
    return propertyKey == Identifier.DEFAULT;
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getMappingClass();
  }

  @TruffleBoundary
  public VmSet getAllKeys() {
    if (delegate != null) {
      return delegate.getAllKeys();
    }
    synchronized (this) {
      if (__allKeys == null) {
        // building upon parent's `getAllKeys()` should improve at least worst case efficiency
        var parentKeys =
            getParent() instanceof VmMapping mapping ? mapping.getAllKeys() : VmSet.EMPTY;
        var builder = VmSet.builder(parentKeys);
        for (var cursor = getMembers().getEntries(); cursor.advance(); ) {
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
  @TruffleBoundary
  public Map<Object, Object> export() {
    var properties = CollectionUtils.newLinkedHashMap(getCacheSize());

    iterateMemberValues(
        (key, prop, value) -> {
          if (isDefaultProperty(key)) return true;

          properties.put(VmValue.export(key), VmValue.exportNullable(value));
          return true;
        });

    return properties;
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
  @TruffleBoundary
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof VmMapping other)) return false;

    if (getEntryCount() != other.getEntryCount()) return false;
    // could use shallow force, but deep force is cached
    force(false);
    other.force(false);
    for (var key : getAllKeys()) {
      var value = getCachedValue(key);
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
    // It's possible that the delegate has already computed its hash code.
    // If so, we can go ahead and use it.
    if (delegate != null && delegate.cachedHash != 0) return delegate.cachedHash;

    force(false);
    var result = 0;
    for (var key : getAllKeys()) {
      if (key instanceof Identifier) continue;
      var value = getCachedValue(key);
      assert value != null;
      result += key.hashCode() ^ value.hashCode();
    }
    cachedHash = result;
    return result;
  }

  public int getEntryCount() {
    if (cachedEntryCount != -1) return cachedEntryCount;
    cachedEntryCount = getAllKeys().getLength();
    return cachedEntryCount;
  }

  @Override
  @TruffleBoundary
  public VmMapping withCheckedMembers(
      ListingOrMappingTypeCastNode typeCheckNode, MaterializedFrame typeNodeFrame) {
    return new VmMapping(
        getEnclosingFrame(),
        Objects.requireNonNull(getParent()),
        getMembers(),
        this,
        typeCheckNode,
        typeNodeFrame);
  }
}
