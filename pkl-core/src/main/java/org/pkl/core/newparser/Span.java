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
package org.pkl.core.newparser;

public record Span(int charIndex, int length) {

  /** Returns a span that starts with this span and ends with {@code other}. */
  public Span endWith(Span other) {
    return new Span(charIndex, other.charIndex - charIndex + other.length);
  }

  /** Checks wheter {@code other} starts directly after this span ends */
  public boolean adjacent(Span other) {
    return charIndex + length == other.charIndex;
  }

  public int stopIndex() {
    return charIndex + length - 1;
  }
}
