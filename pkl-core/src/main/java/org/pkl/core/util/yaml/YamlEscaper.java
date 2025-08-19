/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

/**
 * Emits escape sequences for YAML. This is only used when emitting double-quoted strings.
 *
 * <p>Note: we don't need to escape space ({@code 0x20}) because we don't generate quoted multiline
 * strings. We also don't need to escape forward slash ({@code 0x2f}) because a normal forward slash
 * is also valid YAML.
 *
 * @see <a
 *     href="https://yaml.org/spec/1.2.2/#57-escaped-characters">https://yaml.org/spec/1.2.2/#57-escaped-characters</a>
 */
public final class YamlEscaper extends AbstractCharEscaper {
  private static final String[] REPLACEMENTS;

  static {
    REPLACEMENTS = new String[0xA0 + 1];
    for (var i = 0; i < 0x20; i++) {
      REPLACEMENTS[i] = IoUtils.toHexEscape(i);
    }
    // ns-esc-null
    REPLACEMENTS[0x00] = "\\0";
    // ns-esc-bell
    REPLACEMENTS[0x07] = "\\a";
    // ns-esc-backspace
    REPLACEMENTS[0x08] = "\\b";
    // ns-esc-horizontal-tab
    REPLACEMENTS[0x09] = "\\t";
    // ns-esc-line-feed
    REPLACEMENTS[0x0A] = "\\n";
    // ns-esc-vertical-tab
    REPLACEMENTS[0x0B] = "\\v";
    // ns-esc-form-feed
    REPLACEMENTS[0x0C] = "\\f";
    // ns-esc-carriage-return
    REPLACEMENTS[0x0D] = "\\r";
    // ns-esc-escape
    REPLACEMENTS[0x1B] = "\\e";
    // ns-esc-double-quote
    REPLACEMENTS[0x22] = "\\\"";
    // ns-esc-backslash
    REPLACEMENTS[0x5c] = "\\\\";
    // ns-esc-next-line
    REPLACEMENTS[0x85] = "\\N";
    // ns-esc-non-breaking-space
    REPLACEMENTS[0xA0] = "\\_";
  }

  @Override
  protected @Nullable String findReplacement(char ch) {
    //noinspection UnnecessaryUnicodeEscape
    return ch <= 0xA0 ? REPLACEMENTS[ch] : ch == '\u2028' ? "\\L" : ch == '\u2029' ? "\\P" : null;
  }
}
