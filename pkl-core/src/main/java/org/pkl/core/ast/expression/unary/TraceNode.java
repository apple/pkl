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
package org.pkl.core.ast.expression.unary;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.*;

public final class TraceNode extends ExpressionNode {
  @Child private ExpressionNode valueNode;

  private final VmValueRenderer renderer = VmValueRenderer.singleLine(1000000);

  public TraceNode(SourceSection sourceSection, ExpressionNode valueNode) {
    super(sourceSection);
    this.valueNode = valueNode;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    var value = valueNode.executeGeneric(frame);
    doTrace(value, VmContext.get(this));
    return value;
  }

  @TruffleBoundary
  private void doTrace(Object value, VmContext context) {
    if (value instanceof VmObjectLike objectLike) {
      try {
        objectLike.force(true, true);
      } catch (VmException ignored) {
      }
    }

    var sourceSection = valueNode.getSourceSection();
    var renderedValue = renderer.render(value);
    var message =
        (sourceSection.isAvailable() ? sourceSection.getCharacters() : "<value")
            + " = "
            + renderedValue;

    context.getLogger().trace(message, VmUtils.createStackFrame(sourceSection, null));
  }
}
