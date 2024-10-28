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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.pkl.core.OutputFormatter;
import org.pkl.core.Release;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.Nullable;

public final class VmExceptionRenderer {
  private final @Nullable StackTraceRenderer stackTraceRenderer;
  private final OutputFormatter<?> outputFormatter;

  /**
   * Constructs an error renderer with the given stack trace renderer. If stack trace renderer is
   * {@code null}, stack traces will not be included in error output.
   */
  public VmExceptionRenderer(
      @Nullable StackTraceRenderer stackTraceRenderer, OutputFormatter<?> outputFormatter) {
    this.stackTraceRenderer = stackTraceRenderer;
    this.outputFormatter = outputFormatter;
  }

  @TruffleBoundary
  public String render(VmException exception) {
    var formatter = outputFormatter.createBlank();
    render(exception, formatter);
    return formatter.toString();
  }

  private void render(VmException exception, OutputFormatter<?> out) {
    if (exception instanceof VmBugException bugException) {
      renderBugException(bugException, out);
    } else {
      renderException(exception, out, true);
    }
  }

  private void renderBugException(VmBugException exception, OutputFormatter<?> out) {
    // if a cause exists, it's more useful to report just that
    var exceptionToReport = exception.getCause() != null ? exception.getCause() : exception;

    out.append("An unexpected error has occurred. Would you mind filing a bug report?\n")
        .append("Cmd+Double-click the link below to open an issue.\n")
        .append(
            "Please copy and paste the entire error output into the issue's description, provided you can share it.\n\n")
        .append("https://github.com/apple/pkl/issues/new\n\n");

    out.append(
        URLEncoder.encode(exceptionToReport.toString(), StandardCharsets.UTF_8)
            .replaceAll("\\+", "%20"));

    out.append("\n\n");
    renderException(exception, out, true);
    out.append('\n').append(Release.current().versionInfo()).append("\n\n");

    exceptionToReport.printStackTrace(out.toPrintWriter());
  }

  private void renderException(VmException exception, OutputFormatter<?> out, boolean withHeader) {
    String message;
    var hint = exception.getHint();
    if (exception.isExternalMessage()) {
      var totalMessage =
          ErrorMessages.create(exception.getMessage(), exception.getMessageArguments());
      // first paragraph is message, remainder is hint
      var index = totalMessage.indexOf("\n\n");
      if (index != -1) {
        message = totalMessage.substring(0, index);
        hint = totalMessage.substring(index + 2);
      } else {
        message = totalMessage;
      }
    } else if (exception.getMessage() != null && exception.getMessageArguments().length != 0) {
      message = String.format(exception.getMessage(), exception.getMessageArguments());
    } else {
      message = exception.getMessage();
    }

    if (withHeader) {
      out.errorHeader();
    }
    out.error(message).newline();

    // include cause's message unless it's the same as this exception's message
    if (exception.getCause() != null) {
      var cause = exception.getCause();
      var causeMessage = cause.getMessage();
      // null for Truffle's LazyStackTrace
      if (causeMessage != null && !causeMessage.equals(message)) {
        out.append(cause.getClass().getSimpleName()).append(": ").append(causeMessage).append('\n');
      }
    }

    var maxNameLength =
        exception.getProgramValues().stream().mapToInt(v -> v.name.length()).max().orElse(0);

    for (var value : exception.getProgramValues()) {
      out.text(value.name)
          .repeat(Math.max(0, maxNameLength - value.name.length()), ' ')
          .text(": ")
          .append(value)
          .newline();
    }

    if (stackTraceRenderer != null) {
      var frames = StackTraceGenerator.capture(exception);

      if (exception instanceof VmWrappedEvalException vmWrappedEvalException) {
        var sb = out.createBlank();
        renderException(vmWrappedEvalException.getWrappedException(), sb, false);
        hint = sb.toString().lines().map((it) -> ">\t" + it).collect(Collectors.joining("\n"));
      }

      if (!frames.isEmpty()) {
        stackTraceRenderer.render(frames, hint, out.newline());
      } else if (hint != null) {
        // render hint if there are no stack frames
        out.newline().hint(hint);
      }
    }
  }
}
