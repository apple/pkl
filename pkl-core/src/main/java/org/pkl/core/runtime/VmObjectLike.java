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

import com.oracle.truffle.api.frame.MaterializedFrame;
import java.util.function.BiFunction;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.util.MapCursor;
import org.pkl.core.util.Nullable;

/**
 * Corresponds to `pkl.base#Object|pkl.base#Function`. The lexical scope is a chain of
 * `VmObjectLike` instances.
 */
public interface VmObjectLike extends VmValue {
  /** The frame that was active when this object was instantiated. * */
  MaterializedFrame getEnclosingFrame();

  @Nullable
  Object getExtraStorage();

  void setExtraStorage(@Nullable Object extraStorage);

  default boolean hasExtraStorage() {
    return getExtraStorage() != null;
  }

  default @Nullable Object getEnclosingReceiver() {
    return VmUtils.getReceiverOrNull(getEnclosingFrame());
  }

  default @Nullable VmObjectLike getEnclosingOwner() {
    return VmUtils.getOwnerOrNull(getEnclosingFrame());
  }

  default boolean isModuleObject() {
    return false;
  }

  /**
   * Returns the parent object in the prototype chain. For each concrete subclass X of VmObjectLike,
   * the exact return type of this method is `X|VmTyped`.
   */
  @Nullable
  VmObjectLike getParent();

  /** Always prefer this method over `getMembers().containsKey(key)`. */
  boolean hasMember(Object key);

  /** Always prefer this method over `getMembers().get(key)`. */
  @Nullable
  ObjectMember getMember(Object key);

  /** Returns the declared members of this object. */
  UnmodifiableEconomicMap<Object, ObjectMember> getMembers();

  /**
   * Reads from the properties cache for this object. The cache contains the values of all members
   * defined in this object or an ancestor thereof which have been requested with this object as the
   * receiver.
   */
  @Nullable
  Object getCachedValue(Object key);

  /**
   * Writes to the properties cache for this object. The cache contains the values of all members
   * defined in this object or an ancestor thereof which have been requested with this object as the
   * receiver.
   */
  void setCachedValue(Object key, Object value);

  /**
   * Prefer this method over {@link #getCachedValue} if the value is not required. (There is no
   * point in calling this method to determine whether to call {@link #getCachedValue}.)
   */
  boolean hasCachedValue(Object key);

  /** Returns a cursor for iterating over all cached values in this object. */
  MapCursor<Object, Object> getCachedValueEntries();

  /** Returns the number of cached values in this object. */
  int getCachedValueCount();

  /**
   * Iterates over member definitions and their values in order of their definition, from the top of
   * the prototype chain downwards. If a member value has not yet been evaluated, a `null` `value`
   * is passed to `consumer`. If a member is defined in multiple objects in the prototype chain,
   * i.e., is overridden along the way, it is visited only once, with the initial (i.e., upmost)
   * `member` and the final (i.e., downmost) `value`. (This peculiar behavior serves two purposes in
   * the current implementation: it guarantees that a `hidden` property is still recognized as such
   * when overridden, and that an element is still recognized as such when overridden with entry
   * syntax. It also means that members are visited in order of (first) definition.) Local, hidden,
   * and external properties are not visited. If an invocation of `consumer` returns `false`, the
   * remaining members are not visited, and `false` is returned. Otherwise, all members are visited,
   * and `true` is returned.
   */
  boolean iterateMemberValues(MemberValueConsumer consumer);

  /**
   * Same as {@link #iterateMemberValues} except that it first performs a shallow {@link #force}. As
   * a consequence, values passed to {@code consumer} are guaranteed to be non-null.
   */
  boolean forceAndIterateMemberValues(ForcedMemberValueConsumer consumer);

  boolean iterateAlreadyForcedMemberValues(ForcedMemberValueConsumer consumer);

  /**
   * Iterates over member definitions in order of their definition, from the top of the prototype
   * chain downwards. If a member is defined multiple times, each occurrence is visited. Local
   * properties are not visited. If an invocation of `consumer` returns `false`, the remaining
   * members are not visited, and `false` is returned. Otherwise, all members are visited, and
   * `true` is returned.
   */
  boolean iterateMembers(BiFunction<Object, ObjectMember, Boolean> consumer);

  /** Forces shallow or recursive (deep) evaluation of this object. */
  void force(boolean allowUndefinedValues, boolean recurse);

  /**
   * Exports this object to an external representation. Does not export local, hidden, or external
   * properties
   */
  @Override
  Object export();

  @FunctionalInterface
  interface MemberValueConsumer {
    /**
     * Returns true if {@link #iterateMemberValues} should continue calling this method for the
     * remaining members, and false otherwise.
     */
    boolean accept(Object key, ObjectMember member, @Nullable Object value);
  }

  @FunctionalInterface
  interface ForcedMemberValueConsumer {
    /**
     * Returns true if {@link #forceAndIterateMemberValues} should continue calling this method for
     * the remaining members, and false otherwise.
     */
    boolean accept(Object key, ObjectMember member, Object value);
  }
}
