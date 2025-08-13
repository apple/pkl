/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.parser;

public record Span(
    int charIndex, int length, int lineBegin, int colBegin, int lineEnd, int colEnd) {

  /** Returns a span that starts with this span and ends with {@code end}. */
  public Span endWith(Span end) {
    return new Span(
        charIndex,
        end.charIndex - charIndex + end.length,
        lineBegin,
        colBegin,
        end.lineEnd,
        end.colEnd);
  }

  /** Checks wheter {@code other} starts directly after this span ends */
  public boolean adjacent(Span other) {
    return charIndex + length == other.charIndex;
  }

  /** Returns true if this span comes before `other`. */
  public boolean before(Span other) {
    return charIndex < other.charIndex;
  }
  
  public boolean after(Span other) {
    return other.charIndex + other.length < charIndex;
  }

  /** Returns true if this span trails `other``: comes after it in the same line. */
  public boolean trails(Span other) {
    return other.lineEnd == lineBegin && other.colEnd <= colBegin;
  }

  /** Returns true if this span in inside `other`. */
  public boolean inside(Span other) {
    var thisEnd = charIndex + length;
    var otherEnd = other.charIndex + other.length;
    return charIndex > other.charIndex && thisEnd < otherEnd;
  }

  public int stopIndex() {
    return charIndex + length - 1;
  }
  
  public int linesBetween(Span next) {
    return next.lineBegin - lineEnd;
  }

  // the functions below should only be used for error reporting, as they don't
  // recalculate lines and columns

  public Span stopSpan() {
    return new Span(charIndex + length - 1, 1, lineEnd, colEnd, lineEnd, colEnd);
  }

  public Span move(int amount) {
    return new Span(charIndex + amount, length, lineEnd, colEnd, lineEnd, colEnd);
  }

  public Span grow(int amount) {
    return new Span(charIndex, length + amount, lineEnd, colEnd, lineEnd, colEnd);
  }
}
