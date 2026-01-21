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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.pkl.core.StackFrame;
import org.pkl.core.util.AnsiStringBuilder;
import org.pkl.core.util.Nullable;

public class VmWrappedEvalException extends VmEvalException {

  private final VmException wrappedException;

  public VmWrappedEvalException(
      @Nullable String message,
      @Nullable Throwable cause,
      boolean isExternalMessage,
      Object[] messageArguments,
      @Nullable BiConsumer<AnsiStringBuilder, Boolean> messageBuilder,
      List<ProgramValue> programValues,
      @Nullable Node location,
      @Nullable SourceSection sourceSection,
      @Nullable String memberName,
      @Nullable BiConsumer<AnsiStringBuilder, Boolean> hintBuilder,
      Map<CallTarget, StackFrame> insertedStackFrames,
      VmException wrappedException) {
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
        insertedStackFrames);
    this.wrappedException = wrappedException;
  }

  public VmException getWrappedException() {
    return wrappedException;
  }
}
