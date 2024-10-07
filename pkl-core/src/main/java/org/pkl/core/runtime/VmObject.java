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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import java.util.*;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.util.CollectionUtils;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.Nullable;

/** Corresponds to `pkl.base#Object`. */
public abstract class VmObject extends VmObjectLike {
  private static final Object DELETED_ELEMENT_INDICES_KEY = new Object();
  private static final Object DELETED_ENTRIES_AND_PROPERTIES_KEY = new Object();

  @CompilationFinal protected @Nullable VmObject parent;
  protected final UnmodifiableEconomicMap<Object, ObjectMember> members;
  protected final EconomicMap<Object, Object> cachedValues;

  protected int cachedHash;
  private boolean forced;

  public VmObject(
      MaterializedFrame enclosingFrame,
      @Nullable VmObject parent,
      UnmodifiableEconomicMap<Object, ObjectMember> members,
      EconomicMap<Object, Object> cachedValues) {
    super(enclosingFrame);
    this.parent = parent;
    this.members = members;
    this.cachedValues =
        extractDeletions(parent != null && parent.hasElements(), members, cachedValues);

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

  @Override
  public final @Nullable ObjectMember getMember(Object key) {
    return EconomicMaps.get(members, key);
  }

  @Override
  public final UnmodifiableEconomicMap<Object, ObjectMember> getMembers() {
    return members;
  }

  @SuppressWarnings("unchecked")
  public final @Nullable SortedSet<Long> getDeletedIndices() {
    return (SortedSet<Long>) getCachedValue(DELETED_ELEMENT_INDICES_KEY);
  }

  @SuppressWarnings("unchecked")
  public final @Nullable Set<Object> getDeletedKeys() {
    return (Set<Object>) getCachedValue(DELETED_ENTRIES_AND_PROPERTIES_KEY);
  }

  /** Tells whether this object has any elements. */
  public abstract boolean hasElements();

  /**
   * Members in this {@code VmObject} are subscripted with their {@code referenceKey}. Given that
   * the definition of this object may contain use of {@code delete}, the keys used in said
   * definition are different from the {@code referenceKey}. In case of element deletions, the
   * definition key may be higher {@code long} than the {@code referenceKey}. In case of entry or
   * property deletions, the key is no longer valid for this object.
   *
   * @param referenceKey The key used from outside of this object to dereference a member.
   * @return {@code null} if the key is deleted on this object, an offset {@code Long} in case of an
   *     element index with earlier elements being deleted in this object, or the original input.
   */
  public @Nullable Object toDefinitionKey(Object referenceKey) {
    if (!(referenceKey instanceof Long index) || !hasElements()) {
      var member = getMember(referenceKey);
      if (member != null && member.isDelete()) {
        return null;
      }
      return referenceKey;
    }

    var deletedIndices = getDeletedIndices();
    if (deletedIndices == null) {
      return referenceKey;
    }

    var lowerBound = 0L;
    var add = 0L;
    do {
      add = deletedIndices.subSet(lowerBound, index + 1L).size();
      lowerBound = index + 1L;
      index += add;
    } while (add > 0L);

    return index;
  }

  @Override
  public @Nullable Object getCachedValue(Object key) {
    return EconomicMaps.get(cachedValues, key);
  }

  @Override
  public void setCachedValue(Object key, Object value, ObjectMember objectMember) {
    EconomicMaps.put(cachedValues, key, value);
  }

  @Override
  public final boolean hasCachedValue(Object key) {
    return EconomicMaps.containsKey(cachedValues, key);
  }

  @Override
  @TruffleBoundary
  public final boolean iterateMemberValues(MemberValueConsumer consumer) {
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
  public final boolean forceAndIterateMemberValues(ForcedMemberValueConsumer consumer) {
    force(false, false);
    return iterateAlreadyForcedMemberValues(consumer);
  }

  @Override
  @TruffleBoundary
  public final boolean iterateAlreadyForcedMemberValues(ForcedMemberValueConsumer consumer) {
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
  public final boolean iterateMembers(MemberConsumer consumer) {
    var ancestors = new ArrayDeque<VmObject>();
    var deletedKeys = new HashMap<Object, Integer>();
    var deletedIndices = new ArrayDeque<SortedSet<Long>>();

    for (var owner = this; owner != null; owner = owner.getParent()) {
      ancestors.addFirst(owner);
      var keysDeletedHere = owner.getDeletedKeys();
      for (var key : keysDeletedHere == null ? Collections.EMPTY_LIST : keysDeletedHere) {
        deletedKeys.compute(key, (k, v) -> v == null ? 1 : v + 1);
      }
      var indicesDeletedHere = owner.getDeletedIndices();
      if (indicesDeletedHere != null) {
        deletedIndices.push(indicesDeletedHere);
      }
    }

    while (!ancestors.isEmpty()) {
      var current = ancestors.peek();

      var entries = current.members.getEntries();
      while (entries.advance()) {
        var definitionKey = entries.getKey();
        var referenceKey = toReferenceKey(definitionKey, deletedKeys, deletedIndices);

        var member = entries.getValue();
        if (member.isLocal() || referenceKey == null) continue;

        if (!consumer.accept(referenceKey, member)) return false;
      }

      ancestors.pop();

      var keysDeletedHere = current.getDeletedKeys();
      for (var key : keysDeletedHere == null ? Collections.EMPTY_LIST : keysDeletedHere) {
        deletedKeys.compute(key, (k, v) -> Objects.requireNonNull(v) == 1 ? null : v - 1);
      }
      if (current.getDeletedIndices() != null) {
        deletedIndices.pop();
      }
    }
    assert deletedKeys.isEmpty() && deletedIndices.isEmpty();
    return true;
  }

  private @Nullable Object toReferenceKey(
      Object definitionKey,
      Map<Object, Integer> deletedKeys,
      Collection<SortedSet<Long>> deletedIndices) {
    if (deletedKeys.containsKey(definitionKey)) {
      return null;
    }
    if (!(definitionKey instanceof Long index) || !hasElements()) {
      return definitionKey;
    }

    for (var indicesDeletedHere : deletedIndices) {
      if (indicesDeletedHere.contains(index)) {
        return null;
      }
      index -= indicesDeletedHere.subSet(0L, index).size();
    }

    return index;
  }

  /** Evaluates this object's members. Skips local, hidden, and external members. */
  @Override
  @TruffleBoundary
  public final void force(boolean allowUndefinedValues, boolean recurse) {
    if (forced) return;

    if (recurse) forced = true;

    var deletedKeys = new HashMap<Object, Integer>();
    var deletedIndices = new ArrayDeque<SortedSet<Long>>();

    try {
      for (var owner = this; owner != null; owner = owner.getParent()) {

        var indicesDeletedHere = owner.getDeletedIndices();
        if (indicesDeletedHere != null) {
          deletedIndices.push(indicesDeletedHere);
        }
        var keysDeletedHere = owner.getDeletedKeys();
        for (var key : keysDeletedHere == null ? Collections.EMPTY_LIST : keysDeletedHere) {
          deletedKeys.compute(key, (k, v) -> v == null ? 1 : v + 1);
        }

        var cursor = owner.members.getEntries();
        var clazz = owner.getVmClass();
        while (cursor.advance()) {
          var referenceKey = toReferenceKey(cursor.getKey(), deletedKeys, deletedIndices);
          var member = cursor.getValue();

          // isAbstract() can occur when VmAbstractObject.toString() is called
          // on a prototype of an abstract class (e.g., in the Java debugger)
          if (referenceKey == null
              || member.isLocalOrExternalOrAbstractOrDelete()
              || clazz.isHiddenProperty(referenceKey)) {
            continue;
          }

          var memberValue = getCachedValue(referenceKey);
          if (memberValue == null) {
            try {
              memberValue = VmUtils.doReadMember(this, owner, referenceKey, member);
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
    var result = CollectionUtils.<String, Object>newLinkedHashMap(EconomicMaps.size(cachedValues));

    iterateMemberValues(
        (key, member, value) -> {
          if (member.isClass() || member.isTypeAlias()) return true;

          result.put(key.toString(), VmValue.exportNullable(value));
          return true;
        });

    return result;
  }

  private static EconomicMap<Object, Object> extractDeletions(
      boolean hasElements,
      UnmodifiableEconomicMap<Object, ObjectMember> members,
      EconomicMap<Object, Object> cachedValues) {

    var elementDeletions = new TreeSet<Long>();
    var otherDeletions = new HashSet<>();
    for (var member : members.getValues()) {
      if (hasElements) {
        break;
      }
      hasElements = member.isElement();
    }
    for (var memberKey : members.getKeys()) {
      if (!members.get(memberKey).isDelete()) {
        continue;
      }
      if (memberKey instanceof Long key && hasElements) {
        elementDeletions.add(key);
      } else {
        otherDeletions.add(memberKey);
      }
    }
    if (!elementDeletions.isEmpty()) {
      EconomicMaps.put(cachedValues, DELETED_ELEMENT_INDICES_KEY, elementDeletions);
    }
    if (!otherDeletions.isEmpty()) {
      EconomicMaps.put(cachedValues, DELETED_ENTRIES_AND_PROPERTIES_KEY, otherDeletions);
    }
    return cachedValues;
  }
}
