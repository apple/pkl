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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import java.util.*;
import java.util.function.BiFunction;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.util.CollectionUtils;
import org.pkl.core.util.DynamicObjectMapCursor;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.MapCursor;
import org.pkl.core.util.Nullable;

/**
 * Corresponds to `pkl.base#Object`.
 *
 * <p>Extends {@link DynamicObject} to leverage Truffle's object storage and inline caching
 * capabilities. Cached property values are stored directly in this object using the Dynamic Object
 * Model.
 */
public abstract class VmObject extends DynamicObject implements VmObjectLike {
  // moved from VmObjectLike
  protected final MaterializedFrame enclosingFrame;
  protected @Nullable Object extraStorage;

  @CompilationFinal protected @Nullable VmObject parent;
  protected final UnmodifiableEconomicMap<Object, ObjectMember> members;

  protected int cachedHash;
  private boolean forced;

  /**
   * Separate cache for local property values.
   *
   * <p>This is kept separate from the DynamicObject storage to avoid shape transitions.
   */
  private @Nullable IdentityHashMap<ObjectMember, Object> localPropertyCache;

  protected VmObject(
      Shape shape,
      MaterializedFrame enclosingFrame,
      @Nullable VmObject parent,
      UnmodifiableEconomicMap<Object, ObjectMember> members) {
    super(shape);
    this.enclosingFrame = enclosingFrame;
    this.parent = parent;
    this.members = members;
    assert parent != this;
  }

  @Override
  public final MaterializedFrame getEnclosingFrame() {
    return enclosingFrame;
  }

  @Override
  public final @Nullable Object getExtraStorage() {
    return extraStorage;
  }

  @Override
  public final void setExtraStorage(@Nullable Object extraStorage) {
    this.extraStorage = extraStorage;
  }

  public final void lateInitParent(VmObject parent) {
    assert this.parent == null;
    this.parent = parent;
  }

  @Override
  public @Nullable VmObject getParent() {
    return parent;
  }

  @Override
  @TruffleBoundary
  public final boolean hasMember(Object key) {
    return EconomicMaps.containsKey(members, key);
  }

  @Override
  @TruffleBoundary
  public final @Nullable ObjectMember getMember(Object key) {
    return EconomicMaps.get(members, key);
  }

  @Override
  public final UnmodifiableEconomicMap<Object, ObjectMember> getMembers() {
    return members;
  }

  @Override
  @TruffleBoundary
  public @Nullable Object getCachedValue(Object key) {
    return DynamicObjectLibrary.getUncached().getOrDefault(this, key, null);
  }

  @Override
  @TruffleBoundary
  public void setCachedValue(Object key, Object value) {
    DynamicObjectLibrary.getUncached().put(this, key, value);
  }

  @Override
  @TruffleBoundary
  public boolean hasCachedValue(Object key) {
    return DynamicObjectLibrary.getUncached().containsKey(this, key);
  }

  @Override
  public MapCursor<Object, Object> getCachedValueEntries() {
    return new DynamicObjectMapCursor(this);
  }

  @Override
  @TruffleBoundary
  public int getCachedValueCount() {
    return DynamicObjectLibrary.getUncached().getKeyArray(this).length;
  }

  /**
   * Clean all cached values. Local or otherwise. Resets cached values to null without removing the
   * keys, preserving the object's shape for pre-allocated slots.
   */
  @TruffleBoundary
  public void cleanAllCachedValues() {
    if (localPropertyCache != null) {
      localPropertyCache.clear();
    }

    var lib = DynamicObjectLibrary.getUncached();
    Object[] keys = lib.getKeyArray(this);
    for (Object key : keys) {
      lib.put(this, key, null);
    }

    forced = false;
  }

  /**
   * Gets a cached local property value.
   *
   * @param property the ObjectMember representing the local property declaration
   * @return the cached value, or null if not cached
   */
  @TruffleBoundary
  public @Nullable Object getLocalCachedValue(ObjectMember property) {
    return localPropertyCache == null ? null : localPropertyCache.get(property);
  }

  /**
   * Sets a cached local property value.
   *
   * @param property the ObjectMember representing the local property declaration
   * @param value the value to cache
   */
  @TruffleBoundary
  public void setLocalCachedValue(ObjectMember property, Object value) {
    if (localPropertyCache == null) {
      localPropertyCache = new IdentityHashMap<>(4);
    }
    localPropertyCache.put(property, value);
  }

  @Override
  @TruffleBoundary
  public final boolean iterateMemberValues(VmObjectLike.MemberValueConsumer consumer) {
    var visited = new HashSet<>();
    return iterateMembers(
        (key, member) -> {
          var alreadyVisited = !visited.add(key);
          // important to record hidden member as visited before skipping it
          // because any overriding member won't carry a `hidden` identifier
          if (alreadyVisited || member.isLocalOrExternalOrHidden()) return true;
          return consumer.accept(key, member, getCachedValue(key));
        });
  }

  @Override
  @TruffleBoundary
  public final boolean forceAndIterateMemberValues(
      VmObjectLike.ForcedMemberValueConsumer consumer) {
    force(false, false);
    return iterateAlreadyForcedMemberValues(consumer);
  }

  @Override
  @TruffleBoundary
  public final boolean iterateAlreadyForcedMemberValues(
      VmObjectLike.ForcedMemberValueConsumer consumer) {
    var visited = new HashSet<>();
    return iterateMembers(
        (key, member) -> {
          var alreadyVisited = !visited.add(key);
          // important to record hidden member as visited before skipping it
          // because any overriding member won't carry a `hidden` identifier
          if (alreadyVisited || member.isLocalOrExternalOrHidden()) return true;
          Object cachedValue = getCachedValue(key);
          assert cachedValue != null; // forced
          return consumer.accept(key, member, cachedValue);
        });
  }

  @Override
  @TruffleBoundary
  public final boolean iterateMembers(BiFunction<Object, ObjectMember, Boolean> consumer) {
    var parent = getParent();
    if (parent != null) {
      var completed = parent.iterateMembers(consumer);
      if (!completed) return false;
    }
    var entries = members.getEntries();
    while (entries.advance()) {
      var member = entries.getValue();
      if (member.isLocal()) continue;
      if (!consumer.apply(entries.getKey(), member)) return false;
    }
    return true;
  }

  /** Evaluates this object's members. Skips local, hidden, and external members. */
  @Override
  @TruffleBoundary
  public final void force(boolean allowUndefinedValues, boolean recurse) {
    if (forced) return;

    if (recurse) forced = true;

    // use cached call node from this object's class to avoid getUncached() overhead
    var callNode = getVmClass().getCachedCallNode();

    try {
      for (VmObjectLike owner = this; owner != null; owner = owner.getParent()) {
        var cursor = EconomicMaps.getEntries(owner.getMembers());
        var clazz = owner.getVmClass();
        while (cursor.advance()) {
          var memberKey = cursor.getKey();
          var member = cursor.getValue();
          // isAbstract() can occur when VmAbstractObject.toString() is called
          // on a prototype of an abstract class (e.g., in the Java debugger)
          if (member.isLocalOrExternalOrAbstract() || clazz.isHiddenProperty(memberKey)) {
            continue;
          }

          var memberValue = getCachedValue(memberKey);
          if (memberValue == null) {
            try {
              memberValue = VmUtils.doReadMember(this, owner, memberKey, member, true, callNode);
            } catch (VmUndefinedValueException e) {
              if (!allowUndefinedValues) throw e;
              continue;
            }
          }

          if (recurse) {
            VmValue.force(memberValue, allowUndefinedValues);
          }
        }
      }
    } catch (Throwable t) {
      forced = false;
      throw t;
    }
  }

  @Override
  public final void force(boolean allowUndefinedValues) {
    force(allowUndefinedValues, true);
  }

  public final String toString() {
    force(true, true);
    return VmValueRenderer.singleLine(Integer.MAX_VALUE).render(this);
  }

  /**
   * Exports this object's members. Skips local members, hidden members, class definitions, and type
   * aliases. Members that haven't been forced have a `null` value.
   */
  @TruffleBoundary
  protected final Map<String, Object> exportMembers() {
    var result = CollectionUtils.<String, Object>newLinkedHashMap(getCachedValueCount());

    iterateMemberValues(
        (key, member, value) -> {
          if (member.isClass() || member.isTypeAlias()) return true;

          result.put(key.toString(), VmValue.exportNullable(value));
          return true;
        });

    return result;
  }
}
