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

import org.pkl.parser.syntax.generic.FullSpan;

public class GenericParserError extends RuntimeException {
  private final FullSpan span;

  public GenericParserError(String msg, FullSpan span) {
    super(msg);
    this.span = span;
  }

  public FullSpan getSpan() {
    return span;
  }

  @Override
  public String toString() {
    return getMessage() + " at " + span;
  }
}
