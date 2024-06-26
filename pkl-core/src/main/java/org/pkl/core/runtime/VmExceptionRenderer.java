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
package org.pkl.core.runtime;

import static org.fusesource.jansi.Ansi.ansi;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import org.pkl.core.Release;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.StringBuilderWriter;

public final class VmExceptionRenderer {
  private final @Nullable StackTraceRenderer stackTraceRenderer;

  private static final Ansi.Color errorColor = Color.RED;

  /**
   * Constructs an error renderer with the given stack trace renderer. If stack trace renderer is
   * {@code null}, stack traces will not be included in error output.
   */
  public VmExceptionRenderer(@Nullable StackTraceRenderer stackTraceRenderer) {
    this.stackTraceRenderer = stackTraceRenderer;
  }

  @TruffleBoundary
  public String render(VmException exception) {
    var builder = new StringBuilder();
    render(exception, builder);
    return builder.toString();
  }

  private void render(VmException exception, StringBuilder builder) {
    if (exception instanceof VmBugException bugException) {
      renderBugException(bugException, builder);
    } else {
      renderException(exception, builder);
    }
  }

  private void renderBugException(VmBugException exception, StringBuilder builder) {
    // if a cause exists, it's more useful to report just that
    var exceptionToReport = exception.getCause() != null ? exception.getCause() : exception;

    builder
        .append("An unexpected error has occurred. Would you mind filing a bug report?\n")
        .append("Cmd+Double-click the link below to open an issue.\n")
        .append(
            "Please copy and paste the entire error output into the issue's description, provided you can share it.\n\n")
        .append("https://github.com/apple/pkl/issues/new\n\n");

    builder.append(
        URLEncoder.encode(exceptionToReport.toString(), StandardCharsets.UTF_8)
            .replaceAll("\\+", "%20"));

    builder.append("\n\n");
    renderException(exception, builder);
    builder.append('\n').append(Release.current().versionInfo()).append("\n\n");

    exceptionToReport.printStackTrace(new PrintWriter(new StringBuilderWriter(builder)));
  }

  private void renderException(VmException exception, StringBuilder builder) {
    var out = ansi(builder);
    out.fg(errorColor).a("–– Pkl Error ––").reset();

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

    out.a('\n').fgBright(errorColor).a(message).reset().a('\n');

    // include cause's message unless it's the same as this exception's message
    if (exception.getCause() != null) {
      var cause = exception.getCause();
      var causeMessage = cause.getMessage();
      // null for Truffle's LazyStackTrace
      if (causeMessage != null && !causeMessage.equals(message)) {
        builder
            .append(cause.getClass().getSimpleName())
            .append(": ")
            .append(causeMessage)
            .append('\n');
      }
    }

    var maxNameLength =
        exception.getProgramValues().stream().mapToInt(v -> v.name.length()).max().orElse(0);

    for (var value : exception.getProgramValues()) {
      builder.append(value.name);
      builder.append(" ".repeat(Math.max(0, maxNameLength - value.name.length())));
      builder.append(": ");
      builder.append(value);
      builder.append('\n');
    }

    if (stackTraceRenderer != null) {
      var frames = StackTraceGenerator.capture(exception);
      if (!frames.isEmpty()) {
        builder.append('\n');
        stackTraceRenderer.render(frames, hint, builder);
      }
    }
  }
}
