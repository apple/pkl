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
package org.pkl.core.util.yaml;

import org.pkl.core.util.AbstractCharEscaper;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;

// https://yaml.org/spec/1.2.2/#57-escaped-characters
public final class YamlEscaper extends AbstractCharEscaper {
  private static final String[] REPLACEMENTS;

  static {
    REPLACEMENTS = new String[0x22 + 1];
    for (var i = 0; i < 0x20; i++) {
      REPLACEMENTS[i] = IoUtils.toHexEscape(i);
    }
    REPLACEMENTS[0x00] = "\\0";
    REPLACEMENTS[0x07] = "\\a";
    REPLACEMENTS[0x08] = "\\b";
    REPLACEMENTS[0x09] = "\\t";
    REPLACEMENTS[0x0A] = "\\n";
    REPLACEMENTS[0x0B] = "\\v";
    REPLACEMENTS[0x0C] = "\\f";
    REPLACEMENTS[0x0D] = "\\r";
    REPLACEMENTS[0x1B] = "\\e";
    // we don't ever need to escape 0x20 because we don't generate quoted multiline strings
    REPLACEMENTS[0x22] = "\\\"";
  }

  @Override
  protected @Nullable String findReplacement(char ch) {
    return ch <= '\u0022'
        ? REPLACEMENTS[ch]
        : ch == '\u2028' ? "\\L" : ch == '\u2029' ? "\\P" : null;
  }
}
