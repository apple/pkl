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
package org.pkl.core.runtime;

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.RootNode;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.expression.primary.ExecuteTypeArgumentCheckNode;
import org.pkl.core.ast.member.FunctionNode;

/**
 * Type argument information passed from a type argument (in a method call) as an argument to a
 * {@link FunctionNode}.
 */
public class VmTypeArgument {

  private final @Nullable MaterializedFrame enclosingFrame;
  private final RootNode rootNode;

  public VmTypeArgument(RootNode rootNode, @Nullable MaterializedFrame enclosingFrame) {
    this.enclosingFrame =
        enclosingFrame != null ? enclosingFrame : VmUtils.createEmptyMaterializedFrame();
    this.rootNode = rootNode;
  }

  public Object check(Object value) {
    if (enclosingFrame == null) {
      return rootNode.getCallTarget().call(null, null, null, value);
    }

    return rootNode
        .getCallTarget()
        .call(
            enclosingFrame.getArguments()[0],
            enclosingFrame.getArguments()[1],
            enclosingFrame.getArguments()[2],
            value);
  }

  public VmType resolveType() {
    // assumption: ExecuteTypeArgumentCheckNode is the only child of rootNode
    var typeNode =
        ((ExecuteTypeArgumentCheckNode) rootNode.getChildren().iterator().next()).getTypeNode();
    var type = typeNode.getType();
    var frame = enclosingFrame != null ? enclosingFrame : VmUtils.createEmptyMaterializedFrame();
    var newType = type.reify(frame);
    while (type != newType) {
      type = newType;
      newType = type.reify(frame);
    }
    return newType;
  }
}
