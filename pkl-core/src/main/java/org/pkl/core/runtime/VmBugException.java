/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;
import org.pkl.core.*;
import org.pkl.core.util.AnsiStringBuilder;

public final class VmBugException extends VmException {
  public VmBugException(
      @Nullable String message,
      @Nullable Throwable cause,
      boolean isExternalMessage,
      @Nullable Object[] messageArguments,
      @Nullable BiConsumer<AnsiStringBuilder, Boolean> messageBuilder,
      List<ProgramValue> programValues,
      @Nullable Node location,
      @Nullable SourceSection sourceSection,
      @Nullable String memberName,
      @Nullable BiConsumer<AnsiStringBuilder, Boolean> hintBuilder,
      Map<CallTarget, StackFrame> insertedStackFrames,
      List<StackFrame> leadingStackFrames) {

    super(
        message,
        cause,
        isExternalMessage,
        messageArguments,
        messageBuilder,
        programValues,
        location,
        sourceSection,
        memberName,
        hintBuilder,
        insertedStackFrames,
        leadingStackFrames);
  }

  @Override
  @TruffleBoundary
  public String getLocalizedMessage() {
    return String.format(getMessage(), getMessageArguments());
  }

  @Override
  @TruffleBoundary
  public PklException toPklException(StackFrameTransformer transformer, boolean color) {
    var renderer = new VmExceptionRenderer(new StackTraceRenderer(transformer), color, true);
    var rendered = renderer.render(this);
    return new PklBugException(rendered, this);
  }
}
