/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class Netrc {
  private static final String NULL_LINE = "\0null_line\0";

  public static final class NetrcParseException extends Exception {
    public NetrcParseException(String message) {
      super(message);
    }
  }

  private Netrc() {}

  public record Entry(
      @Nullable String machine,
      boolean isDefault,
      @Nullable String login,
      @Nullable String password,
      @Nullable String account) {}

  /**
   * Parses the content of a .netrc file into a list of {@link Entry}.
   *
   * @throws NetrcParseException if the content contains an unclosed quote or invalid escape.
   */
  public static List<Entry> parse(String content) throws NetrcParseException {
    var tokens = tokenize(content);
    var entries = new ArrayList<Entry>();

    String currentMachine = null;
    var isDefault = false;
    String currentLogin = null;
    String currentPassword = null;
    String currentAccount = null;

    var i = 0;
    while (i < tokens.size()) {
      var token = tokens.get(i++);
      switch (token.toLowerCase(Locale.ROOT)) {
        case "machine" -> {
          if (currentMachine != null) {
            entries.add(
                new Entry(
                    currentMachine, isDefault, currentLogin, currentPassword, currentAccount));
          }
          isDefault = false;
          currentLogin = null;
          currentPassword = null;
          currentAccount = null;
          currentMachine = i < tokens.size() ? tokens.get(i++) : null;
        }
        case "default" -> {
          if (currentMachine != null) {
            entries.add(
                new Entry(
                    currentMachine, isDefault, currentLogin, currentPassword, currentAccount));
          }
          currentLogin = null;
          currentPassword = null;
          currentAccount = null;
          currentMachine = "default";
          isDefault = true;
        }
        case "login" -> {
          if (i < tokens.size()) {
            currentLogin = tokens.get(i++);
          }
        }
        case "password" -> {
          if (i < tokens.size()) {
            currentPassword = tokens.get(i++);
          }
        }
        case "account" -> {
          if (i < tokens.size()) {
            currentAccount = tokens.get(i++);
          }
        }
        case "macdef" -> {
          while (i < tokens.size()) {
            if (tokens.get(i).equals(NULL_LINE)) {
              i++;
              break;
            }
            i++;
          }
        }
      }
    }
    if (currentMachine != null || isDefault) {
      entries.add(
          new Entry(currentMachine, isDefault, currentLogin, currentPassword, currentAccount));
    }
    return entries;
  }

  /**
   * Converts a list of {@link Entry} into a map of host glob pattern to header map (header name to
   * list of header values).
   */
  public static Map<String, Map<String, List<String>>> toHeadersMap(List<Entry> entries) {
    var result = new LinkedHashMap<String, Map<String, List<String>>>();
    for (var entry : entries) {
      var authHeaderValue = computeAuthHeaderValue(entry.login(), entry.password());
      if (authHeaderValue == null) {
        continue;
      }
      var headerMap = Map.of("Authorization", List.of(authHeaderValue));
      if (entry.isDefault()) {
        continue;
      }
      if (entry.machine() != null && !entry.machine().contains("/")) {
        result.putIfAbsent("http{,s}://" + escapeGlobPattern(entry.machine()) + "/**", headerMap);
      }
    }
    return result;
  }

  private static String escapeGlobPattern(String value) {
    var sb = new StringBuilder();
    for (var i = 0; i < value.length(); i++) {
      var c = value.charAt(i);
      if (c == '?' || c == '*' || c == '[' || c == '{' || c == '\\') {
        sb.append('[').append(c).append(']');
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private static @Nullable String computeAuthHeaderValue(
      @Nullable String login, @Nullable String password) {
    if (password == null && login == null) {
      return null;
    }
    var user = login == null ? "" : login;
    var pass = password == null ? "" : password;
    var credentials = user + ":" + pass;
    var encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    return "Basic " + encoded;
  }

  public static List<String> tokenize(String content) throws NetrcParseException {
    var tokens = new ArrayList<String>();
    var len = content.length();
    var i = 0;
    var atLineStart = true;

    while (i < len) {
      var c = content.charAt(i);

      if (c == '\n' || (c == '\r' && i < len - 1 && content.charAt(i + 1) == '\n')) {
        if (atLineStart) {
          tokens.add(NULL_LINE);
        }
        atLineStart = true;
        if (c == '\r') {
          i += 2;
        } else {
          i++;
        }
        continue;
      } else if (isWhitespace(c)) {
        i++;
        continue;
      }

      // lines starting with `#` (after any leading blanks) are treated as comments
      if (c == '#' && atLineStart) {
        // Skip comment until end of line
        i = consumeLineComment(i, content, len);
        continue;
      }

      // Non-whitespace character encountered
      atLineStart = false;
      if (c == '"') {
        i = consumeQuotedToken(i, content, len, tokens);
      } else {
        i = consumeUnquotedToken(i, content, len, tokens);
      }
    }
    return tokens;
  }

  private static int consumeLineComment(int i, String content, int len) {
    while (i < len && content.charAt(i) != '\n') {
      i++;
    }
    return i;
  }

  private static int consumeQuotedToken(int i, String content, int len, List<String> tokens)
      throws NetrcParseException {
    i++; // skip opening quote
    var escape = false;
    var sb = new StringBuilder();
    while (i < len) {
      var ch = content.charAt(i);
      if (escape) {
        var escapedChar =
            switch (ch) {
              case 'n' -> '\n';
              case 't' -> '\t';
              case 'r' -> '\r';
              default -> ch;
            };
        sb.append(escapedChar);
        escape = false;
        i++;
        continue;
      }
      switch (ch) {
        case '"':
          {
            tokens.add(sb.toString());
            return i + 1;
          }
        case '\\':
          {
            escape = true;
            break;
          }
        default:
          {
            sb.append(ch);
          }
      }
      i++;
    }
    var reason = escape ? "invalid escape" : "unclosed quote";
    throw new NetrcParseException(reason);
  }

  private static int consumeUnquotedToken(int i, String content, int len, List<String> tokens) {
    var sb = new StringBuilder();
    while (i < len) {
      var ch = content.charAt(i);
      if (isWhitespace(ch)) {
        tokens.add(sb.toString());
        return i;
      }
      sb.append(ch);
      i++;
    }
    tokens.add(sb.toString());
    return i;
  }

  private static boolean isWhitespace(char c) {
    return c == ' ' || c == '\n' || c == '\t' || c == '\r';
  }
}
