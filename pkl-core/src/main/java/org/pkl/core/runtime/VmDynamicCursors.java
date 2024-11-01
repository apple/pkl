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

import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.VmObjectCursor.AbstractCachedMemberCursor;
import org.pkl.core.runtime.VmObjectCursor.AbstractOrderedCursor;
import org.pkl.core.util.ArrayBuilder;
import org.pkl.core.util.EconomicSets;
import org.pkl.core.util.LateInit;

final class VmDynamicCursors {
  private VmDynamicCursors() {}

  static final class PropertyCursor extends AbstractMemberCursor {
    public PropertyCursor(VmDynamic iteratee) {
      super(iteratee);
    }

    @Override
    public boolean shouldVisit(ObjectMember member) {
      return member.isProp()
          && member.getNameOrNull() != Identifier.DEFAULT
          && !member.isLocalOrExternalOrHidden();
    }

    @Override
    public boolean isProperty() {
      return true;
    }
  }

  static final class UnorderedPropertyCursor extends AbstractUnorderedMemberCursor {
    public UnorderedPropertyCursor(VmDynamic iteratee) {
      super(iteratee);
    }

    @Override
    public boolean shouldVisit(ObjectMember member) {
      return member.isProp()
          && member.getNameOrNull() != Identifier.DEFAULT
          && !member.isLocalOrExternalOrHidden();
    }

    @Override
    public boolean isProperty() {
      return true;
    }
  }

  static final class ElementCursor extends AbstractOrderedCursor<VmDynamic> {
    private int currentIndex = -1;

    public ElementCursor(VmDynamic iteratee) {
      super(iteratee);
    }

    @Override
    public boolean advance() {
      currentIndex += 1;
      return currentIndex < iteratee.getLength();
    }

    @Override
    public Object key() {
      assert currentIndex >= 0 && currentIndex < iteratee.getLength() : "illegal state";
      return (long) currentIndex;
    }

    @Override
    public int getLength() {
      return iteratee.getLength();
    }

    @Override
    public boolean isElement() {
      return true;
    }
  }

  static final class CachedElementCursor extends AbstractCachedMemberCursor<VmDynamic> {
    public CachedElementCursor(VmDynamic iteratee) {
      super(iteratee);
    }

    @Override
    protected boolean shouldVisit(Object key) {
      return key instanceof Long l && l < iteratee.getLength();
    }

    @Override
    public boolean isElement() {
      return true;
    }
  }

  static final class EntryCursor extends AbstractMemberCursor {
    public EntryCursor(VmDynamic iteratee) {
      super(iteratee);
    }

    @Override
    public boolean shouldVisit(ObjectMember member) {
      return !(key() instanceof Long l) || l >= iteratee.getLength();
    }

    @Override
    public boolean isEntry() {
      return true;
    }
  }

  static final class UnorderedEntryCursor extends AbstractUnorderedMemberCursor {
    public UnorderedEntryCursor(VmDynamic iteratee) {
      super(iteratee);
    }

    @Override
    public boolean shouldVisit(ObjectMember member) {
      return !(key() instanceof Long l) || l >= iteratee.getLength();
    }

    @Override
    public boolean isEntry() {
      return true;
    }
  }

  static final class CachedEntryCursor extends AbstractCachedMemberCursor<VmDynamic> {
    public CachedEntryCursor(VmDynamic iteratee) {
      super(iteratee);
    }

    @Override
    protected boolean shouldVisit(Object key) {
      if (key instanceof Identifier) return false;
      if (key instanceof Long l) return l >= iteratee.getLength();
      return true;
    }

    @Override
    public boolean isEntry() {
      return true;
    }
  }

  static final class MemberCursor extends AbstractMemberCursor {
    public MemberCursor(VmDynamic iteratee) {
      super(iteratee);
    }

    @Override
    public boolean shouldVisit(ObjectMember member) {
      return !member.isLocalOrExternalOrHidden() && member.getNameOrNull() != Identifier.DEFAULT;
    }

    @Override
    public boolean isProperty() {
      return key() instanceof Identifier;
    }

    @Override
    public boolean isElement() {
      return key() instanceof Long l && l < iteratee.getLength();
    }

    @Override
    public boolean isEntry() {
      return !isProperty() && !isElement();
    }
  }

  static final class UnorderedMemberCursor extends AbstractUnorderedMemberCursor {
    public UnorderedMemberCursor(VmDynamic iteratee) {
      super(iteratee);
    }

    @Override
    public boolean shouldVisit(ObjectMember member) {
      return !member.isLocalOrExternalOrHidden() && member.getNameOrNull() != Identifier.DEFAULT;
    }

    @Override
    public boolean isProperty() {
      return key() instanceof Identifier;
    }

    @Override
    public boolean isElement() {
      return key() instanceof Long l && l < iteratee.getLength();
    }

    @Override
    public boolean isEntry() {
      return !isProperty() && !isElement();
    }
  }

  abstract static sealed class AbstractMemberCursor extends AbstractOrderedCursor<VmDynamic> {
    private final UnmodifiableMapCursor<Object, ObjectMember>[] allMembers;
    private @LateInit UnmodifiableMapCursor<Object, ObjectMember> currentMembers;
    private int allMembersIndex;
    private final EconomicSet<Object> seenKeys = EconomicSets.create();

    public AbstractMemberCursor(VmDynamic iteratee) {
      super(iteratee);
      var builder = ArrayBuilder.of(UnmodifiableMapCursor[]::new);
      for (VmObject object = iteratee;
          object != null && object.parent != objectPrototype;
          object = object.parent) {
        builder.add(object.members.getEntries());
      }
      allMembers = builder.toOversizedArray();
      allMembersIndex = builder.lastIndex();
      currentMembers = allMembers[allMembersIndex];
    }

    public abstract boolean shouldVisit(ObjectMember member);

    @Override
    public boolean advance() {
      while (true) {
        while (currentMembers.advance()) {
          if (shouldVisit(currentMembers.getValue())
              && EconomicSets.add(seenKeys, currentMembers.getKey())) return true;
        }
        allMembersIndex -= 1;
        if (allMembersIndex < 0) return false;
        currentMembers = allMembers[allMembersIndex];
      }
    }

    @Override
    public final int getLength() {
      return -1;
    }

    @Override
    public Object key() {
      return currentMembers.getKey();
    }
  }

  abstract static sealed class AbstractUnorderedMemberCursor extends VmObjectCursor {
    private static final VmTyped dynamicPrototype = BaseModule.getDynamicClass().getPrototype();

    protected final VmDynamic iteratee;
    private VmObject currentObject;
    private @LateInit UnmodifiableMapCursor<Object, ObjectMember> currentMember;
    private final EconomicSet<Object> seenKeys = EconomicSets.create();

    public AbstractUnorderedMemberCursor(VmDynamic iteratee) {
      this.iteratee = iteratee;
      currentObject = iteratee;
    }

    @Override
    protected VmObject iteratee() {
      return iteratee;
    }

    protected abstract boolean shouldVisit(ObjectMember member);

    @Override
    public final boolean advance() {
      while (true) {
        while (currentMember.advance()) {
          if (shouldVisit(currentMember.getValue())
              && EconomicSets.add(seenKeys, currentMember.getKey())) return true;
        }
        var parent = currentObject.parent;
        if (parent == dynamicPrototype) return false;
        assert parent != null;
        currentObject = parent;
        currentMember = currentObject.members.getEntries();
      }
    }

    @Override
    public final Object key() {
      return currentMember.getKey();
    }

    @Override
    public final Object value(IndirectCallNode callNode) {
      var key = key();
      var value = iteratee.getCachedValue(key);
      if (value != null) return value;
      return VmUtils.doReadMember(iteratee, currentObject, key, member(), true, callNode);
    }

    @Override
    public final @Nullable Object cachedValueOrNull() {
      return iteratee.getCachedValue(key());
    }

    @Override
    public final ObjectMember member() {
      return currentMember.getValue();
    }

    @Override
    public final int getLength() {
      return -1;
    }
  }
}
