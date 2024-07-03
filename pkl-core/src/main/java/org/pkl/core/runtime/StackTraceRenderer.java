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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import org.pkl.core.StackFrame;
import org.pkl.core.util.Nullable;

public final class StackTraceRenderer {
  private final Function<StackFrame, StackFrame> frameTransformer;

  private static final Ansi.Color frameColor = Color.YELLOW;
  private static final Ansi.Color lineNumColor = Color.BLUE;
  private static final Ansi.Color repetitionColor = Color.MAGENTA;

  public StackTraceRenderer(Function<StackFrame, StackFrame> frameTransformer) {
    this.frameTransformer = frameTransformer;
  }

  public void render(List<StackFrame> frames, @Nullable String hint, StringBuilder builder) {
    var compressed = compressFrames(frames);
    doRender(compressed, hint, builder, "", true);
  }

  // non-private for testing
  void doRender(
      List<Object /*StackFrame|StackFrameLoop*/> frames,
      @Nullable String hint,
      StringBuilder builder,
      String leftMargin,
      boolean isFirstElement) {
    var out = ansi(builder);
    for (var frame : frames) {
      if (frame instanceof StackFrameLoop loop) {
        // ensure a cycle of length 1 doesn't get rendered as a loop
        if (loop.count == 1) {
          doRender(loop.frames, null, builder, leftMargin, isFirstElement);
        } else {
          if (!isFirstElement) {
            out.fgBright(frameColor).a(leftMargin).reset().a("\n");
          }
          out.fgBright(frameColor)
              .a(leftMargin)
              .a("┌─ ")
              .reset()
              .bold()
              .fg(repetitionColor)
              .a(Integer.toString(loop.count))
              .reset()
              .a(" repetitions of:\n");
          var newLeftMargin = leftMargin + "│ ";
          doRender(loop.frames, null, builder, newLeftMargin, isFirstElement);
          if (isFirstElement) {
            renderHint(hint, builder, newLeftMargin);
            isFirstElement = false;
          }
          out.fgBright(frameColor).a(leftMargin).a("└─").reset().a("\n");
        }
      } else {
        if (!isFirstElement) {
          out.fgBright(frameColor).a(leftMargin).reset().a('\n');
        }
        renderFrame((StackFrame) frame, builder, leftMargin);
      }

      if (isFirstElement) {
        renderHint(hint, builder, leftMargin);
        isFirstElement = false;
      }
    }
  }

  private void renderFrame(StackFrame frame, StringBuilder builder, String leftMargin) {
    var transformed = frameTransformer.apply(frame);
    renderSourceLine(transformed, builder, leftMargin);
    renderSourceLocation(transformed, builder, leftMargin);
  }

  private void renderHint(@Nullable String hint, StringBuilder builder, String leftMargin) {
    if (hint == null || hint.isEmpty()) return;
    var out = ansi(builder);

    out.a('\n');
    out.fgBright(frameColor).a(leftMargin);
    out.fgBright(frameColor).bold().a(hint).reset();
    out.a('\n');
  }

  private void renderSourceLine(StackFrame frame, StringBuilder builder, String leftMargin) {
    var out = ansi(builder);
    var originalSourceLine = frame.getSourceLines().get(0);
    var leadingWhitespace = VmUtils.countLeadingWhitespace(originalSourceLine);
    var sourceLine = originalSourceLine.strip();
    var startColumn = frame.getStartColumn() - leadingWhitespace;
    var endColumn =
        frame.getStartLine() == frame.getEndLine()
            ? frame.getEndColumn() - leadingWhitespace
            : sourceLine.length();

    var prefix = frame.getStartLine() + " | ";
    out.fgBright(frameColor)
        .a(leftMargin)
        .fgBright(lineNumColor)
        .a(prefix)
        .reset()
        .a(sourceLine)
        .a('\n');
    out.fgBright(frameColor).a(leftMargin).reset();
    //noinspection StringRepeatCanBeUsed
    for (int i = 1; i < prefix.length() + startColumn; i++) {
      out.append(' ');
    }

    out.fgRed();
    //noinspection StringRepeatCanBeUsed
    for (int i = startColumn; i <= endColumn; i++) {
      out.a('^');
    }
    out.reset().a('\n');
  }

  private void renderSourceLocation(StackFrame frame, StringBuilder builder, String leftMargin) {
    var out = ansi(builder);
    out.fgBright(frameColor).a(leftMargin).reset().a("at ");
    if (frame.getMemberName() != null) {
      out.a(frame.getMemberName());
    } else {
      out.a("<unknown>");
    }
    out.a(" (").a(frame.getModuleUri()).a(')').a('\n');
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
