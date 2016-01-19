/**
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
package org.pkl.core.ast.expression.literal;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.VmMap;

@NodeInfo(shortName = "Map()")
public final class MapLiteralNode extends ExpressionNode {
  @Children private final ExpressionNode[] keysAndValues;

  public MapLiteralNode(SourceSection sourceSection, ExpressionNode[] keysAndValues) {
    super(sourceSection);
    this.keysAndValues = keysAndValues;
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    assert keysAndValues.length % 2 == 0;

    var builder = VmMap.builder();
    for (var i = 0; i < keysAndValues.length; i += 2) {
      var key = keysAndValues[i].executeGeneric(frame);
      var value = keysAndValues[i + 1].executeGeneric(frame);
      builder.add(key, value);
    }

    return builder.build();
  }
}
