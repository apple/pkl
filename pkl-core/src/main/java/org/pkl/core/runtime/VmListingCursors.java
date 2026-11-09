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

import org.pkl.core.runtime.VmObjectCursor.AbstractCachedMemberCursor;
import org.pkl.core.runtime.VmObjectCursor.AbstractOrderedCursor;

final class VmListingCursors {
  private VmListingCursors() {}

  static final class ElementCursor extends AbstractOrderedCursor<VmListing> {
    private int currentIndex = -1;

    public ElementCursor(VmListing iteratee) {
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
    public boolean isElement() {
      return true;
    }

    @Override
    public int getLength() {
      return iteratee.getLength();
    }
  }

  static final class CachedElementCursor extends AbstractCachedMemberCursor<VmListing> {
    public CachedElementCursor(VmListing iteratee) {
      super(iteratee);
    }

    @Override
    protected boolean shouldVisit(Object key) {
      return key instanceof Long; // skip local and hidden properties
    }

    @Override
    public boolean isElement() {
      return true;
    }
  }
}
