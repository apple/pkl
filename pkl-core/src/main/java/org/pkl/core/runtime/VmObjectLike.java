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
import java.util.function.BiFunction;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.util.Nullable;

/**
 * Corresponds to `pkl.base#Object|pkl.base#Function`. The lexical scope is a hierarchy of
 * `VmOwner`s.
 */
public abstract class VmObjectLike extends VmValue {
  /** The frame that was active when this object was instantiated. * */
  protected final MaterializedFrame enclosingFrame;

  protected @Nullable Object extraStorage;

  protected VmObjectLike(MaterializedFrame enclosingFrame) {
    this.enclosingFrame = enclosingFrame;
  }

  public final MaterializedFrame getEnclosingFrame() {
    return enclosingFrame;
  }

  public final @Nullable Object getEnclosingReceiver() {
    return VmUtils.getReceiverOrNull(enclosingFrame);
  }

  public final @Nullable VmObjectLike getEnclosingOwner() {
    return VmUtils.getOwnerOrNull(enclosingFrame);
  }

  public final boolean hasExtraStorage() {
    return extraStorage != null;
  }

  public Object getExtraStorage() {
    assert extraStorage != null;
    return extraStorage;
  }

  public final void setExtraStorage(@Nullable Object extraStorage) {
    this.extraStorage = extraStorage;
  }

  public boolean isModuleObject() {
    return false;
  }

  /**
   * Returns the parent object in the prototype chain. For each concrete subclass X of VmOwner, the
   * exact return type of this method is `X|VmTyped`.
   */
  public abstract @Nullable VmObjectLike getParent();

  /** Always prefer this method over `getMembers().containsKey(key)`. */
  @TruffleBoundary
  public abstract boolean hasMember(Object key);

  /** Always prefer this method over `getMembers().get(key)`. */
  @TruffleBoundary
  public abstract @Nullable ObjectMember getMember(Object key);

  /** Returns the declared members of this object. */
  public abstract UnmodifiableEconomicMap<Object, ObjectMember> getMembers();

  /**
   * Reads from the properties cache for this object. The cache contains the values of all members
   * defined in this object or an ancestor thereof which have been requested with this object as the
   * receiver.
   */
  @TruffleBoundary
  public abstract @Nullable Object getCachedValue(Object key);

  /**
   * Writes to the properties cache for this object. The cache contains the values of all members
   * defined in this object or an ancestor thereof which have been requested with this object as the
   * receiver.
   */
  @TruffleBoundary
  public abstract void setCachedValue(Object key, Object value, ObjectMember objectMember);

  /**
   * Prefer this method over {@link #getCachedValue} if the value is not required. (There is no
   * point in calling this method to determine whether to call {@link #getCachedValue}.)
   */
  @TruffleBoundary
  public abstract boolean hasCachedValue(Object key);

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
  public abstract boolean iterateMemberValues(MemberValueConsumer consumer);

  /**
   * Same as {@link #iterateMemberValues} except that it first performs a shallow {@link #force}. As
   * a consequence, values passed to {@code consumer} are guaranteed to be non-null.
   */
  public abstract boolean forceAndIterateMemberValues(ForcedMemberValueConsumer consumer);

  public abstract boolean iterateAlreadyForcedMemberValues(ForcedMemberValueConsumer consumer);

  /**
   * Iterates over member definitions in order of their definition, from the top of the prototype
   * chain downwards. If a member is defined multiple times, each occurrence is visited. Local
   * properties are not visited. If an invocation of `consumer` returns `false`, the remaining
   * members are not visited, and `false` is returned. Otherwise, all members are visited, and
   * `true` is returned.
   */
  public abstract boolean iterateMembers(BiFunction<Object, ObjectMember, Boolean> consumer);

  /** Forces shallow or recursive (deep) evaluation of this object. */
  public abstract void force(boolean allowUndefinedValues, boolean recurse);

  /**
   * Exports this object to an external representation. Does not export local, hidden, or external
   * properties
   */
  public abstract Object export();

  @FunctionalInterface
  public interface MemberValueConsumer {
    /**
     * Returns true if {@link #iterateMemberValues} should continue calling this method for the
     * remaining members, and false otherwise.
     */
    boolean accept(Object key, ObjectMember member, @Nullable Object value);
  }

  @FunctionalInterface
  public interface ForcedMemberValueConsumer {
    /**
     * Returns true if {@link #forceAndIterateMemberValues} should continue calling this method for
     * the remaining members, and false otherwise.
     */
    boolean accept(Object key, ObjectMember member, Object value);
  }
}
