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
package org.pkl.core.util.json;

import org.pkl.core.util.AbstractCharEscaper;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;

public final class JsonEscaper extends AbstractCharEscaper {
  /*
   * From RFC 7159, "All Unicode characters may be placed within the
   * quotation marks except for the characters that must be escaped:
   * quotation mark, reverse solidus, and the control characters
   * (U+0000 through U+001F)."
   *
   * We also escape '\u2028' and '\u2029', which JavaScript interprets as
   * newline characters. This prevents eval() from failing with a syntax
   * error. http://code.google.com/p/google-gson/issues/detail?id=341
   */
  private static final String[] REPLACEMENTS;
  private static final String[] HTML_SAFE_REPLACEMENTS;

  private final @Nullable String[] replacements;

  static {
    REPLACEMENTS = new String['\\' + 1];
    for (var i = 0; i < 0x20; i++) {
      REPLACEMENTS[i] = IoUtils.toUnicodeEscape(i);
    }
    REPLACEMENTS['"'] = "\\\"";
    REPLACEMENTS['\t'] = "\\t";
    REPLACEMENTS['\b'] = "\\b";
    REPLACEMENTS['\n'] = "\\n";
    REPLACEMENTS['\r'] = "\\r";
    REPLACEMENTS['\f'] = "\\f";
    REPLACEMENTS['\\'] = "\\\\";

    HTML_SAFE_REPLACEMENTS = REPLACEMENTS.clone();
    HTML_SAFE_REPLACEMENTS['<'] = "\\u003c";
    HTML_SAFE_REPLACEMENTS['>'] = "\\u003e";
    HTML_SAFE_REPLACEMENTS['&'] = "\\u0026";
    HTML_SAFE_REPLACEMENTS['='] = "\\u003d";
    HTML_SAFE_REPLACEMENTS['\''] = "\\u0027";
  }

  public JsonEscaper(boolean isHtmlSafe) {
    replacements = isHtmlSafe ? HTML_SAFE_REPLACEMENTS : REPLACEMENTS;
  }

  @Override
  protected @Nullable String findReplacement(char ch) {
    return ch <= '\\'
        ? replacements[ch]
        : ch == '\u2028' ? "\\u2028" : ch == '\u2029' ? "\\u2029" : null;
  }
}
