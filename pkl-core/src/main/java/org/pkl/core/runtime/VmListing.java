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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.ast.member.ListingOrMappingTypeCheckNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.Nullable;

public final class VmListing extends VmListingOrMapping<VmListing> {
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
    super(enclosingFrame, Objects.requireNonNull(parent), members, null, null, null);
    this.length = length;
  }

  public VmListing(
      MaterializedFrame enclosingFrame,
      VmObject parent,
      UnmodifiableEconomicMap<Object, ObjectMember> members,
      int length,
      @Nullable VmListing surrogatee,
      ListingOrMappingTypeCheckNode typeCheckNode,
      MaterializedFrame typeNodeFrame) {
    super(
        enclosingFrame,
        Objects.requireNonNull(parent),
        members,
        surrogatee,
        typeCheckNode,
        typeNodeFrame);
    this.length = length;
  }

  public static boolean isDefaultProperty(Object propertyKey) {
    return propertyKey == Identifier.DEFAULT;
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
    var properties = new ArrayList<>(EconomicMaps.size(cachedValues));

    iterateMemberValues(
        (key, prop, value) -> {
          if (isDefaultProperty(key)) return true;

          properties.add(VmValue.exportNullable(value));
          return true;
        });

    return properties;
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
  public VmListing createSurrogate(
      ListingOrMappingTypeCheckNode typeCheckNode, MaterializedFrame typeNodeFrame) {
    return new VmListing(
        getEnclosingFrame(),
        Objects.requireNonNull(parent),
        members,
        length,
        this,
        typeCheckNode,
        typeNodeFrame);
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
      Object key = cursor.getKey();
      if (key instanceof Identifier) continue;

      var value = cursor.getValue();
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
      assert value != null;
      result = 31 * result + value.hashCode();
    }

    cachedHash = result;
    return result;
  }
}
