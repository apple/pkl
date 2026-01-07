/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

public record Span(int charIndex, int length) {

  /** Returns a span that starts with this span and ends with {@code end}. */
  public Span endWith(Span end) {
    return new Span(charIndex, end.charIndex - charIndex + end.length);
  }

  /** Checks wheter {@code other} starts directly after this span ends */
  public boolean adjacent(Span other) {
    return charIndex + length == other.charIndex;
  }

  public int stopIndex() {
    return charIndex + length - 1;
  }

  public int stopIndexExclusive() {
    return charIndex + length;
  }

  public Span stopSpan() {
    return new Span(charIndex + length - 1, 1);
  }

  public Span move(int amount) {
    return new Span(charIndex + amount, length);
  }

  public Span grow(int amount) {
    return new Span(charIndex, length + amount);
  }

  /** Tells if {@code other} is entirely within this span. */
  public boolean contains(Span other) {
    var offset = other.charIndex - charIndex;
    if (offset < 0) {
      return false;
    }
    var maxLength = length - offset;
    return other.length <= maxLength;
  }
}
