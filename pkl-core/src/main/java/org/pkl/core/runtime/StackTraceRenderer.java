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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.pkl.core.StackFrame;
import org.pkl.core.util.ColorTheme;
import org.pkl.core.util.Nullable;

public final class StackTraceRenderer {
  private final Function<StackFrame, StackFrame> frameTransformer;

  public StackTraceRenderer(Function<StackFrame, StackFrame> frameTransformer) {
    this.frameTransformer = frameTransformer;
  }

  public void render(
      List<StackFrame> frames, @Nullable String hint, TextFormattingStringBuilder out) {
    var compressed = compressFrames(frames);
    doRender(compressed, hint, out, "", true);
  }

  // non-private for testing
  void doRender(
      List<Object /*StackFrame|StackFrameLoop*/> frames,
      @Nullable String hint,
      TextFormattingStringBuilder out,
      String leftMargin,
      boolean isFirstElement) {
    for (var frame : frames) {
      if (frame instanceof StackFrameLoop loop) {
        // ensure a cycle of length 1 doesn't get rendered as a loop
        if (loop.count == 1) {
          doRender(loop.frames, null, out, leftMargin, isFirstElement);
        } else {
          if (!isFirstElement) {
            out.append(ColorTheme.STACK_TRACE_MARGIN, leftMargin).appendLine();
          }
          out.append(ColorTheme.STACK_TRACE_MARGIN, leftMargin)
              .append(ColorTheme.STACK_TRACE_MARGIN, "┌─ ")
              .append(ColorTheme.STACK_TRACE_LOOP_COUNT, loop.count)
              .append(" repetitions of:\n");
          var newLeftMargin = leftMargin + "│ ";
          doRender(loop.frames, null, out, newLeftMargin, isFirstElement);
          if (isFirstElement) {
            renderHint(hint, out, newLeftMargin);
            isFirstElement = false;
          }
          out.append(ColorTheme.STACK_TRACE_MARGIN, leftMargin + "└─").appendLine();
        }
      } else {
        if (!isFirstElement) {
          out.append(ColorTheme.STACK_TRACE_MARGIN, leftMargin).appendLine();
        }
        renderFrame((StackFrame) frame, out, leftMargin);
      }

      if (isFirstElement) {
        renderHint(hint, out, leftMargin);
        isFirstElement = false;
      }
    }
  }

  private void renderFrame(StackFrame frame, TextFormattingStringBuilder out, String leftMargin) {
    var transformed = frameTransformer.apply(frame);
    renderSourceLine(transformed, out, leftMargin);
    renderSourceLocation(transformed, out, leftMargin);
  }

  private void renderHint(
      @Nullable String hint, TextFormattingStringBuilder out, String leftMargin) {
    if (hint == null || hint.isEmpty()) return;

    out.appendLine()
        .append(ColorTheme.STACK_TRACE_MARGIN, leftMargin)
        .append(ColorTheme.ERROR_MESSAGE_HINT, hint)
        .appendLine();
  }

  private void renderSourceLine(
      StackFrame frame, TextFormattingStringBuilder out, String leftMargin) {
    var originalSourceLine = frame.getSourceLines().get(0);
    var leadingWhitespace = VmUtils.countLeadingWhitespace(originalSourceLine);
    var sourceLine = originalSourceLine.strip();
    var startColumn = frame.getStartColumn() - leadingWhitespace;
    var endColumn =
        frame.getStartLine() == frame.getEndLine()
            ? frame.getEndColumn() - leadingWhitespace
            : sourceLine.length();

    var prefix = frame.getStartLine() + " | ";
    out.append(ColorTheme.STACK_TRACE_MARGIN, leftMargin)
        .append(ColorTheme.STACK_TRACE_LINE_NUMBER, prefix)
        .append(sourceLine)
        .appendLine()
        .append(ColorTheme.STACK_TRACE_MARGIN, leftMargin)
        .append(" ".repeat(prefix.length() + startColumn - 1))
        .append(ColorTheme.STACK_TRACE_CARET, "^".repeat(endColumn - startColumn + 1))
        .appendLine();
  }

  private void renderSourceLocation(
      StackFrame frame, TextFormattingStringBuilder out, String leftMargin) {
    out.append(ColorTheme.STACK_TRACE_MARGIN, leftMargin)
        .append(
            ColorTheme.STACK_FRAME,
            () ->
                out.append("at ")
                    .append(frame.getMemberName() != null ? frame.getMemberName() : "<unknown>")
                    .append(" (")
                    .appendUntrusted(frame.getModuleUri())
                    .append(")")
                    .appendLine());
  }

  /**
   * `StackFrame` and `StackFrameLoop` don't currently have a common base interface because the
   * former is public API and the latter isn't.
   */
  // non-private for testing
  static class StackFrameLoop {
    final List<Object /*StackFrame|StackFrameLoop*/> frames;
    final int count;

    StackFrameLoop(List<Object> frames, int count) {
      this.count = count;
      this.frames = frames;
    }
  }

  // non-private for testing
  static List<Object /*StackFrame|StackFrameLoop*/> compressFrames(List<StackFrame> frames) {
    return doCompressFrames(frames, new int[frames.size()], new ArrayList<>(), 0, frames.size());
  }

  private static List<Object /*StackFrame|StackFrameLoop*/> doCompressFrames(
      List<StackFrame> frames, int[] lpps, List<Object> result, int beginning, int ending) {
    // Algorithm was written with reversed `frames` in mind.
    // Instead of reversing `frames`, we invert indices passed to `frames.get()`.
    var framesLastIndex = frames.size() - 1;

    var totalSize = ending - beginning;

    var maxLength = -1;
    var patternStart = -2;
    var patternWidth = -2;
    var matchEnd = -2;

    loopSearch:
    for (int i = beginning; i < ending; i++) {
      var best = i;
      var len = 0;
      lpps[i] = 0;

      var j = i + 1;
      while (j < ending) {
        var frame1 = frames.get(framesLastIndex - j);
        var frame2 = frames.get(framesLastIndex - (len + i));
        var match = frame1.equals(frame2);
        if (!match && len != 0) {
          len = lpps[len - 1];
        } else {
          len += match ? 1 : 0;
          lpps[j] = len;
          if (len > lpps[best]) {
            best = j;
          } else if (len > 0 && len == lpps[j - 1]) {
            // Degenerative; e.g. ABAAB; we don't care for "prefixes that are suffixes" for a
            // non-empty infix. In other words, we only look for regex `P+P` and not `P+IP`
            continue loopSearch;
          }
          j++;
        }
      }

      var length = best - i + 1;
      if (length > 1 && maxLength < length) {
        maxLength = length;
        matchEnd = best;
        patternStart = i;
      }
      if (maxLength > ending - i || maxLength * 2 > totalSize) {
        // There are no better matches to be found.
        break;
      }
    }

    // Write to result in reverse order.
    if (maxLength > 1) {
      patternWidth = matchEnd - lpps[matchEnd] - patternStart + 1;
      doCompressFrames(frames, lpps, result, matchEnd + 1, ending);
      result.add(
          new StackFrameLoop(
              doCompressFrames(
                  frames, lpps, new ArrayList<>(), patternStart, patternStart + patternWidth),
              maxLength / patternWidth));
      doCompressFrames(frames, lpps, result, beginning, patternStart);
    } else {
      // No patterns found in frames[beginning...ending].
      for (int i = ending - 1; i >= beginning; i--) {
        result.add(frames.get(framesLastIndex - i));
      }
    }
    return result;
  }
}
