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
import java.util.Iterator;
import org.jspecify.annotations.Nullable;
import org.pkl.core.runtime.VmObjectCursor.AbstractOrderedCursor;

// Doesn't offer CachedPropertyCursor because filtering out local, hidden, and type properties
// while iterating over VmObject.cachedValues likely won't be any better than using PropertyCursor
// in the first place.
final class VmTypedCursors {
  private VmTypedCursors() {}

  static final class PropertyCursor extends AbstractOrderedCursor<VmTyped> {
    private final Iterator<Identifier> identifiers;
    private final boolean skipTypes;
    private @Nullable Object currentKey;
    @CompilationFinal int length = -1;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public PropertyCursor(VmTyped iteratee, boolean skipTypes) {
      super(iteratee);
      identifiers = (Iterator) iteratee.getVmClass().getAllRegularPropertyNames().iterator();
      this.skipTypes = skipTypes && iteratee.isModuleObject();
    }

    @Override
    @TruffleBoundary
    public boolean advance() {
      if (identifiers.hasNext()) {
        currentKey = identifiers.next();
        if (skipTypes) {
          while (member().isType()) {
            if (identifiers.hasNext()) {
              currentKey = identifiers.next();
            } else {
              currentKey = null;
              return false;
            }
          }
        }
        return true;
      }
      currentKey = null;
      return false;
    }

    @Override
    public Object key() {
      assert currentKey != null : "illegal state";
      return currentKey;
    }

    @Override
    public boolean isProperty() {
      return true;
    }

    @Override
    public int getLength() {
      if (length == -1) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        length = computeLength();
      }
      return length;
    }

    private int computeLength() {
      var length = 0;
      var clazz = iteratee.getVmClass();
      for (var identifier : iteratee.getVmClass().getAllRegularPropertyNames()) {
        var member = clazz.getProperty((Identifier) identifier);
        assert member != null;
        if (skipTypes && member.isType()) {
          continue;
        }
        length++;
      }
      return length;
    }
  }
}
