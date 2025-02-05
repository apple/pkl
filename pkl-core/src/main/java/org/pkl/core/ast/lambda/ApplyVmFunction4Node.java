/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.ast.lambda;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.pkl.core.ast.PklNode;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.runtime.VmUtils;

public abstract class ApplyVmFunction4Node extends PklNode {
  public abstract Object execute(
      VirtualFrame frame, VmFunction function, Object arg1, Object arg2, Object arg3, Object arg4);

  @Specialization(guards = "function.getCallTarget() == cachedCallTarget")
  protected Object evalDirect(
      VirtualFrame frame,
      VmFunction function,
      Object arg1,
      Object arg2,
      Object arg3,
      Object arg4,
      @SuppressWarnings("unused") @Cached("function.getCallTarget()")
          RootCallTarget cachedCallTarget,
      @Cached("create(cachedCallTarget)") DirectCallNode callNode) {

    return callNode.call(
        VmUtils.getMarkers(frame), function.getThisValue(), function, arg1, arg2, arg3, arg4);
  }

  @Specialization(replaces = "evalDirect")
  protected Object eval(
      VirtualFrame frame,
      VmFunction function,
      Object arg1,
      Object arg2,
      Object arg3,
      Object arg4,
      @Cached("create()") IndirectCallNode callNode) {

    return callNode.call(
        function.getCallTarget(),
        VmUtils.getMarkers(frame),
        function.getThisValue(),
        function,
        arg1,
        arg2,
        arg3,
        arg4);
  }
}
