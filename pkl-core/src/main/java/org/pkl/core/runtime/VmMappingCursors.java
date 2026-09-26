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

import java.util.Iterator;
import org.jspecify.annotations.Nullable;
import org.pkl.core.runtime.VmObjectCursor.AbstractCachedMemberCursor;
import org.pkl.core.runtime.VmObjectCursor.AbstractOrderedCursor;

final class VmMappingCursors {
  private VmMappingCursors() {}

  static final class EntryCursor extends AbstractOrderedCursor<VmMapping> {
    private final Iterator<Object> keys;
    private @Nullable Object currentKey;

    public EntryCursor(VmMapping iteratee) {
      super(iteratee);
      keys = iteratee.getAllKeys().iterator();
    }

    @Override
    public boolean advance() {
      if (keys.hasNext()) {
        currentKey = keys.next();
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
    public boolean isEntry() {
      return true;
    }

    @Override
    public int getLength() {
      return -1;
    }
  }

  static final class CachedEntryCursor extends AbstractCachedMemberCursor<VmMapping> {
    public CachedEntryCursor(VmMapping iteratee) {
      super(iteratee);
    }

    @Override
    protected boolean shouldVisit(Object key) {
      return !(key instanceof Identifier); // skip local and hidden properties
    }

    @Override
    public boolean isEntry() {
      return true;
    }
  }
}
