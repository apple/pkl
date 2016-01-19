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
package org.pkl.core.ast.expression.literal;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.VmUtils;

@NodeInfo(shortName = "istr")
public final class InterpolatedStringLiteralNode extends ExpressionNode {
  @Children private final ExpressionNode[] parts;

  public InterpolatedStringLiteralNode(SourceSection sourceSection, ExpressionNode[] parts) {
    super(sourceSection);
    this.parts = parts;
  }

  @Override
  @ExplodeLoop
  public String executeGeneric(VirtualFrame frame) {
    var builder = VmUtils.createBuilder();
    for (var part : parts) {
      VmUtils.appendToBuilder(builder, (String) part.executeGeneric(frame));
    }
    return VmUtils.builderToString(builder);
  }
}
