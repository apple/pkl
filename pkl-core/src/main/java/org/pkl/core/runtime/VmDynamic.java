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
import java.util.Objects;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.PClassInfo;
import org.pkl.core.PObject;
import org.pkl.core.ast.member.ObjectMember;
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

  @Override
  public boolean hasElements() {
    return length != 0;
  }

  @Override
  public boolean isSequence() {
    return hasElements();
  }

  @Override
  @TruffleBoundary
  public PObject export() {
    var properties =
        CollectionUtils.<String, Object>newLinkedHashMap(EconomicMaps.size(cachedValues));

    iterateMemberValues(
        (key, member, value) -> {
          properties.put(key.toString(), VmValue.exportNullable(value));
          return true;
        });

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
  @TruffleBoundary
  public boolean equals(Object obj) {
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
      assert value != null;
      result += key.hashCode() ^ value.hashCode();
    }

    cachedHash = result;
    return result;
  }

  // assumes object has been forced
  public int getRegularMemberCount() {
    if (cachedRegularMemberCount != -1) return cachedRegularMemberCount;

    var result = 0;
    for (var key : cachedValues.getKeys()) {
      if (!isHiddenOrLocalProperty(key)) result += 1;
    }
    cachedRegularMemberCount = result;
    return result;
  }

  private boolean isHiddenOrLocalProperty(Object key) {
    return key instanceof Identifier
        && (key == Identifier.DEFAULT || ((Identifier) key).isLocalProp());
  }
}
