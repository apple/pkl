/*
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.ast.expression.primary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.VmUtils;

@NodeInfo(shortName = "module")
public final class GetModuleNode extends ExpressionNode {
  public GetModuleNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  public Object executeGeneric(VirtualFrame frame) {
    CompilerDirectives.transferToInterpreter();

    var levelsUp = 0;
    for (var current = VmUtils.getOwner(frame).getEnclosingOwner();
        current != null;
        current = current.getEnclosingOwner()) {
      levelsUp += 1;
    }

    return replace(levelsUp == 0 ? new GetReceiverNode() : new GetEnclosingReceiverNode(levelsUp))
        .executeGeneric(frame);
  }
}
