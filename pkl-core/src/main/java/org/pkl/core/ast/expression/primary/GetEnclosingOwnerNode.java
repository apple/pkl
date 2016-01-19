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
package org.pkl.core.ast.expression.primary;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.VmUtils;

public final class GetEnclosingOwnerNode extends ExpressionNode {
  private final int levelsUp;

  public GetEnclosingOwnerNode(int levelsUp) {
    this.levelsUp = levelsUp;

    assert levelsUp > 0 : "shouldn't be using GetEnclosingOwnerNode for levelsUp == 0";
  }

  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    var owner = VmUtils.getOwner(frame);
    for (var i = 1; i < levelsUp; i++) {
      owner = owner.getEnclosingOwner();
      assert owner != null;
    }
    var result = owner.getEnclosingOwner();
    assert result != null;
    return result;
  }
}
