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
package org.pkl.core.runtime;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import java.util.Deque;
import java.util.List;
import org.pkl.core.parser.Lexer;
import org.pkl.core.util.Nullable;

public final class VmUndefinedValueException extends VmEvalException {
  private final @Nullable Object receiver;

  public VmUndefinedValueException(
      String message,
      @Nullable Throwable cause,
      boolean isExternalMessage,
      Object[] messageArguments,
      List<ProgramValue> programValues,
      @Nullable Node location,
      @Nullable SourceSection sourceSection,
      @Nullable String memberName,
      @Nullable String hint,
      @Nullable Object receiver) {

    super(
        message,
        cause,
        isExternalMessage,
        messageArguments,
        programValues,
        location,
        sourceSection,
        memberName,
        hint);

    this.receiver = receiver;
  }

  public VmUndefinedValueException fillInHint(Deque<Object> path, Object topLevelValue) {
    if (hint != null) return this;
    var memberKey = getMessageArguments()[0];
    path.push(memberKey);
    var builder = new StringBuilder();
    builder.append("The above error occurred when rendering path `");
    renderPath(builder, path);
    builder.append('`');
    path.pop();
    if (topLevelValue instanceof VmTyped && ((VmTyped) topLevelValue).isModuleObject()) {
      var modl = (VmTyped) topLevelValue;
      builder
          .append(" of module `")
          .append(modl.getModuleInfo().getModuleSchema(modl).getModuleUri())
          .append('`');
    }
    builder.append('.');
    hint = builder.toString();
    return this;
  }

  private void renderPath(StringBuilder builder, Deque<Object> path) {
    var iter = path.descendingIterator();
    var isTrailingPath = false;
    while (iter.hasNext()) {
      var pathPart = iter.next();
      if (pathPart == VmValueConverter.TOP_LEVEL_VALUE) continue;
      if (pathPart instanceof Identifier) {
        if (isTrailingPath) {
          builder.append('.');
        } else {
          isTrailingPath = true;
        }
        builder.append(Lexer.maybeQuoteIdentifier(pathPart.toString()));
      } else {
        builder.append('[').append(pathPart).append(']');
      }
    }
  }

  public @Nullable Object getReceiver() {
    return receiver;
  }
}
