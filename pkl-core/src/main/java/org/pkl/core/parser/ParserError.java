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
package org.pkl.core.parser;

import org.pkl.core.parser.syntax.Module;
import org.pkl.core.util.Nullable;

public class ParserError extends RuntimeException {
  private final Span span;
  private @Nullable Module partialParseResult;

  public ParserError(String msg, Span span) {
    super(msg);
    this.span = span;
  }

  public Span span() {
    return span;
  }

  public void setPartialParseResult(@Nullable Module partialParseResult) {
    this.partialParseResult = partialParseResult;
  }

  public @Nullable Module getPartialParseResult() {
    return partialParseResult;
  }
}
