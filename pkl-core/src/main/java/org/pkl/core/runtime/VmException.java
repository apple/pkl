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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.pkl.core.*;
import org.pkl.core.util.AnsiStringBuilder;
import org.pkl.core.util.Nullable;

public abstract class VmException extends AbstractTruffleException {
  private final boolean isExternalMessage;
  private final Object[] messageArguments;
  private final List<ProgramValue> programValues;
  private final @Nullable SourceSection sourceSection;
  private final @Nullable String memberName;
  protected @Nullable String hint;
  private final Map<CallTarget, StackFrame> insertedStackFrames;
  @Nullable private final BiConsumer<AnsiStringBuilder, Boolean> messageBuilder;

  public VmException(
      @Nullable String message,
      @Nullable Throwable cause,
      boolean isExternalMessage,
      Object[] messageArguments,
      List<ProgramValue> programValues,
      @Nullable Node location,
      @Nullable SourceSection sourceSection,
      @Nullable String memberName,
      @Nullable String hint,
      Map<CallTarget, StackFrame> insertedStackFrames,
      @Nullable BiConsumer<AnsiStringBuilder, Boolean> messageBuilder) {
    super(message, cause, UNLIMITED_STACK_TRACE, location);
    this.isExternalMessage = isExternalMessage;
    this.messageArguments = messageArguments;
    this.programValues = programValues;
    this.sourceSection = sourceSection;
    this.memberName = memberName;
    this.hint = hint;
    this.insertedStackFrames = insertedStackFrames;
    this.messageBuilder = messageBuilder;
  }

  public final boolean isExternalMessage() {
    return isExternalMessage;
  }

  public final Object[] getMessageArguments() {
    return messageArguments;
  }

  public final List<ProgramValue> getProgramValues() {
    return programValues;
  }

  public final @Nullable SourceSection getSourceSection() {
    return sourceSection;
  }

  public final @Nullable String getMemberName() {
    return memberName;
  }

  public @Nullable String getHint() {
    return hint;
  }

  public void setHint(@Nullable String hint) {
    this.hint = hint;
  }

  /**
   * Stack frames to insert into the stack trace presented to the user. For each entry `(target,
   * frame)`, `frame` will be inserted below the top-most frame associated with `target`.
   */
  public final Map<CallTarget, StackFrame> getInsertedStackFrames() {
    return insertedStackFrames;
  }

  public @Nullable BiConsumer<AnsiStringBuilder, Boolean> getMessageBuilder() {
    return messageBuilder;
  }

  public enum Kind {
    EVAL_ERROR,
    UNDEFINED_VALUE,
    WRAPPED,
    BUG
  }

  public record ProgramValue(String name, Object value) {
    private static final VmValueRenderer valueRenderer = VmValueRenderer.singleLine(80);

    @Override
    @TruffleBoundary
    public String toString() {
      return valueRenderer.render(value);
    }
  }

  @TruffleBoundary
  public PklException toPklException(StackFrameTransformer transformer, boolean color) {
    var renderer = new VmExceptionRenderer(new StackTraceRenderer(transformer), color, true);
    var rendered = renderer.render(this);
    return new PklException(rendered);
  }
}
