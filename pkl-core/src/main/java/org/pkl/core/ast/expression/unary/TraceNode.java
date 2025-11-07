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
package org.pkl.core.ast.expression.unary;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.*;

public final class TraceNode extends ExpressionNode {
  @Child private ExpressionNode valueNode;

  private static final int MAX_RENDERER_LENGTH = 1000000;

  private final VmValueRenderer compactRenderer = VmValueRenderer.singleLine(MAX_RENDERER_LENGTH);
  private final VmValueRenderer prettyRenderer = VmValueRenderer.multiLine(Integer.MAX_VALUE);

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
    VmValue.force(value, true);
    var sourceSection = valueNode.getSourceSection();
    var lhs = sourceSection.isAvailable() ? sourceSection.getCharacters().toString() : "<value>";
    var message =
        switch (context.getTraceMode()) {
          case COMPACT -> lhs + " = " + compactRenderer.render(value);
          case PRETTY -> {
            var rhs = prettyRenderer.render(value);
            yield (lhs.contains("\n") ? "\n" + addIndent(lhs, "  ") + "\n=" : lhs + " =")
                + (rhs.contains("\n") ? "\n" + addIndent(rhs, "  ") : " " + rhs)
                + "\n";
          }
        };
    context.getLogger().trace(message, VmUtils.createStackFrame(sourceSection, null));
  }

  private static String addIndent(String s, String indent) {
    return Arrays.stream(s.split("\n")).map((it) -> indent + it).collect(Collectors.joining("\n"));
  }
}
