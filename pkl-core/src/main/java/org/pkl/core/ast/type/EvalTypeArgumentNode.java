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
package org.pkl.core.ast.type;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.PklNode;
import org.pkl.core.runtime.VmTypeArgument;

public abstract class EvalTypeArgumentNode extends PklNode {

  public EvalTypeArgumentNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  public abstract Object execute(VirtualFrame frame, VmTypeArgument typeArgument, Object value);

  @Specialization(
      guards = {"enclosingFrame == null", "typeArgument.getCallTarget() == cachedCallTarget"})
  protected Object evalDirect(
      @SuppressWarnings("unused") VmTypeArgument typeArgument,
      Object value,
      @Bind("typeArgument.getEnclosingFrame()") @SuppressWarnings("unused")
          @Nullable MaterializedFrame enclosingFrame,
      @Cached("typeArgument.getCallTarget()") @SuppressWarnings("unused")
          CallTarget cachedCallTarget,
      @Cached("create(cachedCallTarget)") DirectCallNode callNode) {
    return callNode.call(null, null, null, value);
  }

  @Specialization(
      guards = {"enclosingFrame != null", "typeArgument.getCallTarget() == cachedCallTarget"})
  protected Object evalDirectCapturing(
      @SuppressWarnings("unused") VmTypeArgument typeArgument,
      Object value,
      @Bind("typeArgument.getEnclosingFrame()") MaterializedFrame enclosingFrame,
      @Cached("typeArgument.getCallTarget()") @SuppressWarnings("unused")
          CallTarget cachedCallTarget,
      @Cached("create(cachedCallTarget)") DirectCallNode callNode) {
    return callNode.call(
        enclosingFrame.getArguments()[0],
        enclosingFrame.getArguments()[1],
        enclosingFrame.getArguments()[2],
        value);
  }

  @Specialization(replaces = {"evalDirect", "evalDirectCapturing"})
  protected Object eval(
      VmTypeArgument typeArgument, Object value, @Cached("create()") IndirectCallNode callNode) {
    var frame = typeArgument.getEnclosingFrame();
    return frame == null
        ? callNode.call(typeArgument.getCallTarget(), null, null, null, value)
        : callNode.call(
            typeArgument.getCallTarget(),
            frame.getArguments()[0],
            frame.getArguments()[1],
            frame.getArguments()[2],
            value);
  }
}
