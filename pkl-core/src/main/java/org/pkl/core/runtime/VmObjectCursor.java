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
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.member.ObjectMember;

/**
 * A cursor for iterating over the keys and values of {@link VmObject} members.
 *
 * <p>To obtain a cursor, call one of the following methods:
 *
 * <ul>
 *   <li>{@link VmObjectLike#properties}
 *   <li>{@link VmObjectLike#elements}
 *   <li>{@link VmObjectLike#entries}
 *   <li>{@link VmObjectLike#members}
 * </ul>
 *
 * To customize cursor behavior, the above methods optionally accept a {@link CursorOption}.
 *
 * <p>A typical cursor iteration looks as follows:
 *
 * <pre><code>
 *   for (var cursor = object.properties(); cursor.advance(); ) {
 *     var key = cursor.key();
 *     var value = cursor.value();
 *     ...
 *   }
 * </code></pre>
 *
 * <p>Cursors do not visit {@code local}, {@code external}, and {@code hidden} properties. Only
 * {@link VmTyped#members} visits {@linkplain ObjectMember#isClass() classes} and {@linkplain
 * ObjectMember#isTypeAlias() type aliases} of {@linkplain VmObjectLike#isModuleObject() module
 * objects}.
 */
public abstract class VmObjectCursor {
  protected static final VmTyped objectPrototype = BaseModule.getObjectClass().getPrototype();

  public enum CursorOption {
    /**
     * Indicates that a cursor may choose any iteration order.
     *
     * <p>Specifying this option may increase cursor performance.
     *
     * <p>If this option is not specified, members are visited in declaration order. For elements,
     * declaration order is equivalent to ascending order.
     */
    ANY_ORDER,
    /**
     * Indicates that the caller will request every value of this cursor unless it encounters an
     * unexpected error.
     *
     * <p>Specifying this option may increase cursor performance.
     */
    ALL_VALUES,
    /**
     * Indicates that a cursor must not evaluate any member before its value is requested. This
     * option should only be specified if a lazyness guarantee is required for correctness.
     */
    LAZY_REQUIRED
  }

  /**
   * Advances this cursor to the next object member.
   *
   * @return {@code true} if a next member exists, {@code false} otherwise
   */
  public abstract boolean advance();

  /**
   * Returns the key of the current object member.
   *
   * <p>The returned key has one of the following types:
   *
   * <ul>
   *   <li>{@link Identifier} for a property
   *   <li>{@link Long} for an element
   *   <li>{@link Boolean}, {@link Long}, {@link Double}, {@link String} or {@link VmValue} for an
   *       entry
   * </ul>
   *
   * @throws RuntimeException if this method is called before the first call to {@link #advance()}
   *     or after {@link #advance()} returned {@code false}
   */
  public abstract Object key();

  /**
   * Returns the value of the current object member.
   *
   * <p>If the member's value is not already cached, the member is evaluated.
   *
   * <p>If calling from a node, use {@link org.pkl.core.ast.internal.ReadCursorValueNode} instead of
   * calling this directly.
   *
   * @throws RuntimeException if this method is called before the first call to {@link #advance()}
   *     or after {@link #advance()} returned {@code false}
   */
  public abstract Object value(IndirectCallNode callNode);

  /** Returns the cached value for this member, if it exists. */
  public abstract @Nullable Object cachedValueOrNull();

  /** Returns the cached value for this member, asserting that it exists. */
  public final Object cachedValue() {
    var value = cachedValueOrNull();
    assert value != null;
    return value;
  }

  public boolean isProperty() {
    return false;
  }

  public boolean isElement() {
    return false;
  }

  public boolean isEntry() {
    return false;
  }

  protected abstract VmObject iteratee();

  /**
   * Returns the current member.
   *
   * <p>If a member is overridden in the prototype chain, this method returns the original
   * definition, whereas {@link #value(IndirectCallNode)} returns the value corresponding to the
   * overriding definition. As a consequence, {@code member().isElement()} will correctly identify
   * elements overridden with entry syntax.
   *
   * <p>This method is supported by the following cursors:
   *
   * <ul>
   *   <li>{@link VmObjectLike#members}
   * </ul>
   */
  public ObjectMember member() {
    var key = key();
    var iteratee = iteratee();
    var ret = iteratee.getRootFirstMember(key);
    assert ret != null;
    return ret;
  }

  @TruffleBoundary
  public final String keyToString() {
    return key().toString();
  }

  /**
   * The length of the members to be iterated.
   *
   * <p>Returns {@code -1} if the length is not statically known (requires evaluation to compute).
   */
  public abstract int getLength();

  static final class EmptyCursor extends VmObjectCursor {
    @Override
    public boolean advance() {
      return false;
    }

    @Override
    public Object key() {
      throw new IllegalStateException("empty cursor");
    }

    @Override
    public Object value(IndirectCallNode ignored) {
      throw new IllegalStateException("empty cursor");
    }

    @Override
    public @Nullable Object cachedValueOrNull() {
      throw new IllegalStateException("empty cursor");
    }

    @Override
    public boolean isProperty() {
      throw new IllegalStateException("empty cursor");
    }

    @Override
    public boolean isElement() {
      throw new IllegalStateException("empty cursor");
    }

    @Override
    public boolean isEntry() {
      throw new IllegalStateException("empty cursor");
    }

    @Override
    protected VmObject iteratee() {
      throw new IllegalStateException("empty cursor");
    }

    @Override
    public int getLength() {
      return 0;
    }
  }

  abstract static class AbstractOrderedCursor<T extends VmObject> extends VmObjectCursor {
    protected final T iteratee;

    public AbstractOrderedCursor(T iteratee) {
      this.iteratee = iteratee;
    }

    @Override
    public final Object value(@Nullable IndirectCallNode callNode) {
      var key = key();
      var value = iteratee.getCachedValue(key);
      if (value != null) return value;
      // find and evaluate bottom-most member for key (simplified version of VmUtils.readMember())
      for (VmObject object = iteratee;
          object != null && object.parent != objectPrototype;
          object = object.parent) {
        var member = object.getMember(key);
        if (member != null) {
          if (callNode == null) {
            throw new IllegalStateException("Received null for callNode but member was not forced");
          }
          return VmUtils.doReadMember(iteratee, object, key, member, true, callNode);
        }
      }
      throw unreachableCode();
    }

    @Override
    public final @Nullable Object cachedValueOrNull() {
      return iteratee.getCachedValue(key());
    }

    @TruffleBoundary
    private VmException unreachableCode() {
      return new VmExceptionBuilder().unreachableCode().build();
    }

    @Override
    protected VmObject iteratee() {
      return iteratee;
    }
  }

  abstract static class AbstractCachedMemberCursor<T extends VmObject> extends VmObjectCursor {
    protected final T iteratee;
    private final UnmodifiableMapCursor<Object, Object> cachedValues;
    @CompilationFinal private int length = -1;

    @TruffleBoundary
    AbstractCachedMemberCursor(T iteratee) {
      this.iteratee = iteratee;
      cachedValues = iteratee.cachedValues.getEntries();
    }

    protected abstract boolean shouldVisit(Object key);

    @Override
    @TruffleBoundary
    public boolean advance() {
      while (true) {
        if (!cachedValues.advance()) return false;
        if (shouldVisit(cachedValues.getKey())) return true;
      }
    }

    @Override
    @TruffleBoundary
    public Object key() {
      return cachedValues.getKey();
    }

    @Override
    @TruffleBoundary
    public final Object value(IndirectCallNode ignored) {
      return cachedValues.getValue();
    }

    @Override
    public final Object cachedValueOrNull() {
      return cachedValues.getValue();
    }

    @Override
    protected VmObject iteratee() {
      return iteratee;
    }

    public final int getLength() {
      if (length == -1) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        length = computeLength();
      }
      return length;
    }

    private int computeLength() {
      var ret = 0;
      var cursor = iteratee.cachedValues.getEntries();
      while (cursor.advance()) {
        if (shouldVisit(cursor.getKey())) {
          ret++;
        }
      }
      return ret;
    }
  }
}
