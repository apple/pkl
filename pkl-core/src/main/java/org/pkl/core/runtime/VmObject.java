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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import java.util.*;
import java.util.function.BiFunction;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.util.CollectionUtils;
import org.pkl.core.util.EconomicMaps;

/** Corresponds to `pkl.base#Object`. */
public abstract class VmObject extends VmObjectLike {
  private static final byte SHALLOW_FORCE_FLAG = 0x1;
  private static final byte DEEP_FORCE_FLAG = 0x2;

  @CompilationFinal protected @Nullable VmObject parent;
  protected final UnmodifiableEconomicMap<Object, ObjectMember> members;
  protected final EconomicMap<Object, Object> cachedValues;

  protected int cachedHash;
  private byte flags;
  private final int depth;

  public VmObject(
      MaterializedFrame enclosingFrame,
      @Nullable VmObject parent,
      UnmodifiableEconomicMap<Object, ObjectMember> members,
      EconomicMap<Object, Object> cachedValues) {
    super(enclosingFrame);
    depth = parent == null ? 0 : parent.depth + 1;
    this.parent = parent;
    this.members = members;
    this.cachedValues = cachedValues;

    assert parent != this;
  }

  public VmObject(
      MaterializedFrame enclosingFrame,
      @Nullable VmObject parent,
      UnmodifiableEconomicMap<Object, ObjectMember> members) {
    this(enclosingFrame, parent, members, EconomicMaps.create());
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
  public final boolean hasMember(Object key) {
    return EconomicMaps.containsKey(members, key);
  }

  public final @Nullable ObjectMember getRootFirstMember(Object key) {
    if (parent != null) {
      var parentMember = parent.getRootFirstMember(key);
      if (parentMember != null) {
        return parentMember;
      }
    }
    return getMember(key);
  }

  @Override
  public final @Nullable ObjectMember getMember(Object key) {
    return EconomicMaps.get(members, key);
  }

  @Override
  public final UnmodifiableEconomicMap<Object, ObjectMember> getMembers() {
    return members;
  }

  @Override
  public @Nullable Object getCachedValue(Object key) {
    return EconomicMaps.get(cachedValues, key);
  }

  @Override
  public final void setCachedValue(Object key, Object value) {
    EconomicMaps.put(cachedValues, key, value);
  }

  @Override
  public final boolean hasCachedValue(Object key) {
    return EconomicMaps.containsKey(cachedValues, key);
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

  protected boolean isShallowForced() {
    return (flags & SHALLOW_FORCE_FLAG) != 0;
  }

  protected boolean isDeepForced() {
    return (flags & DEEP_FORCE_FLAG) != 0;
  }

  /** Evaluates this object's members. Skips local, hidden, and external members. */
  @Override
  public final void force(boolean allowUndefinedValues, boolean recurse) {
    var oldFlags = flags;
    if (recurse) {
      if (isDeepForced()) return;
      flags |= (DEEP_FORCE_FLAG | SHALLOW_FORCE_FLAG);
    } else {
      if (isShallowForced()) return;
      flags |= SHALLOW_FORCE_FLAG;
    }

    var fullyForced = true;

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
              memberValue = VmUtils.doReadMember(this, owner, memberKey, member);
            } catch (VmUndefinedValueException e) {
              if (!allowUndefinedValues) throw e;
              fullyForced = false;
              continue;
            }
          }

          if (recurse) {
            VmValue.force(memberValue, allowUndefinedValues);
          }
        }
      }
    } catch (Throwable t) {
      CompilerDirectives.transferToInterpreter();
      flags = oldFlags;
      throw t;
    }

    // make sure uncached values are not marked as forced
    if (recurse && !fullyForced) flags = 0;
  }

  @Override
  public final void force(boolean allowUndefinedValues) {
    force(allowUndefinedValues, true);
  }

  @TruffleBoundary
  public final String toPklString() {
    force(true, true);
    return VmValueRenderer.singleLine(Integer.MAX_VALUE).render(this);
  }

  /**
   * Exports this object's members. Skips local members, hidden members, class definitions, and type
   * aliases.
   *
   * <p>Assumes that this object has already been forced.
   */
  @TruffleBoundary
  protected final Map<String, Object> exportMembers() {
    assert isDeepForced() : "Value was not forced prior to export";

    var result = CollectionUtils.<String, Object>newLinkedHashMap(EconomicMaps.size(cachedValues));
    for (var cursor = members(); cursor.advance(); ) {
      var member = cursor.member();
      if (member.isType()) continue;
      CollectionUtils.put(result, cursor.key().toString(), VmValue.export(cursor.cachedValue()));
    }
    return result;
  }
}
