/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.stdlib;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.VmUtils;

@NodeChild(value = "arg1Node", type = ExpressionNode.class)
@NodeChild(value = "arg2Node", type = ExpressionNode.class)
@NodeChild(value = "arg3Node", type = ExpressionNode.class)
@NodeChild(value = "arg4Node", type = ExpressionNode.class)
@NodeChild(value = "arg5Node", type = ExpressionNode.class)
public abstract class ExternalMethod5Node extends ExternalMethodNode {
  protected abstract ExpressionNode getArg1Node();

  protected abstract ExpressionNode getArg2Node();

  protected abstract ExpressionNode getArg3Node();

  protected abstract ExpressionNode getArg4Node();

  protected abstract ExpressionNode getArg5Node();

  @Fallback
  @TruffleBoundary
  protected Object fallback(
      @SuppressWarnings("unused") Object receiver,
      Object arg1,
      Object arg2,
      Object arg3,
      Object arg4,
      Object arg5) {
    throw exceptionBuilder()
        .evalError(
            "methodNotDefined5",
            getQualifiedMemberName(),
            VmUtils.getClass(arg1),
            VmUtils.getClass(arg2),
            VmUtils.getClass(arg3),
            VmUtils.getClass(arg4),
            VmUtils.getClass(arg5))
        .withProgramValue("Argument 1", arg1)
        .withProgramValue("Argument 2", arg2)
        .withProgramValue("Argument 3", arg3)
        .withProgramValue("Argument 4", arg4)
        .withProgramValue("Argument 5", arg5)
        .build();
  }

  public interface Factory {
    ExternalMethod5Node create(
        ExpressionNode receiverNode,
        ExpressionNode arg1Node,
        ExpressionNode arg2Node,
        ExpressionNode arg3Node,
        ExpressionNode arg4Node,
        ExpressionNode arg5Node);
  }
}
