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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.PklNode;
import org.pkl.core.runtime.VmTypeArgument;

public abstract class CallTypeArgumentNode extends PklNode {

  public CallTypeArgumentNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  public abstract Object execute(VirtualFrame frame, VmTypeArgument typeArgument, Object value);

  @Specialization(guards = {"typeArgument.getCallTarget() == cachedCallTarget"})
  protected Object evalDirect(
      VirtualFrame frame,
      @SuppressWarnings("unused") VmTypeArgument typeArgument,
      Object value,
      @Cached("typeArgument.getCallTarget()") @SuppressWarnings("unused")
          CallTarget cachedCallTarget,
      @Cached("create(cachedCallTarget)") DirectCallNode callNode) {
    var enclosingFrame = typeArgument.getEnclosingFrame();
    if (enclosingFrame == null) {
      return callNode.call(null, null, null, null, value);
    }

    var arguments = enclosingFrame.getArguments();
    return callNode.call(arguments[0], arguments[1], arguments[2], enclosingFrame, value);
  }

  @Specialization(replaces = "evalDirect")
  protected Object eval(
      VirtualFrame frame,
      VmTypeArgument typeArgument,
      Object value,
      @Cached("create()") IndirectCallNode callNode) {
    var enclosingFrame = typeArgument.getEnclosingFrame();
    if (enclosingFrame == null) {
      return callNode.call(typeArgument.getCallTarget(), null, null, null, null, value);
    }

    var arguments = enclosingFrame.getArguments();
    return callNode.call(
        typeArgument.getCallTarget(),
        arguments[0],
        arguments[1],
        arguments[2],
        enclosingFrame,
        value);
  }
}
