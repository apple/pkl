/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.ast.internal;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.PklNode;
import org.pkl.core.runtime.VmObjectCursor;

public abstract class ReadCursorValueNode extends PklNode {
  protected ReadCursorValueNode() {}

  protected ReadCursorValueNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  public abstract Object execute(VirtualFrame frame, VmObjectCursor cursor);

  @Specialization(guards = "cachedValue != null")
  protected Object evalCached(
      @SuppressWarnings("unused") VmObjectCursor cursor,
      @Bind("cursor.cachedValueOrNull()") Object cachedValue) {
    return cachedValue;
  }

  @Specialization(replaces = "evalCached")
  protected Object eval(
      VmObjectCursor cursor,
      @Bind("cursor.cachedValueOrNull()") @SuppressWarnings("unused") @Nullable Object cachedValue,
      @Cached("create()") IndirectCallNode callNode) {
    if (cachedValue != null) {
      return cachedValue;
    }
    return cursor.value(callNode);
  }
}
