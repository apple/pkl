/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.nodes.Node;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.pkl.core.util.Nullable;

public final class VmValueTracker implements AutoCloseable {

  private final EventBinding<ExecutionEventNodeFactory> binding;
  private final VmLocalContext localContext;

  private final Map<Node, List<Object>> values = new IdentityHashMap<>();

  public VmValueTracker(Instrumenter instrumenter, VmLocalContext localContext) {
    this.localContext = localContext;
    localContext.enterTracker();
    binding =
        instrumenter.attachExecutionEventFactory(
            SourceSectionFilter.newBuilder().tagIs(PklTags.Expression.class).build(),
            context ->
                new ExecutionEventNode() {
                  @Override
                  @TruffleBoundary
                  protected void onReturnValue(VirtualFrame frame, @Nullable Object result) {
                    if (result == null) {
                      return;
                    }
                    var node = context.getInstrumentedNode();
                    var myValues = values.getOrDefault(node, new ArrayList<>());
                    myValues.add(result);
                    values.put(node, myValues);
                  }
                });
  }

  public Map<Node, List<Object>> values() {
    return values;
  }

  @Override
  public void close() {
    localContext.exitTracker();
    binding.dispose();
  }
}
