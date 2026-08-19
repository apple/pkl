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
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.pkl.core.runtime.VmEvalException;
import org.pkl.core.runtime.VmExceptionBuilder;

public final class Netrc {
  private static final String NULL_LINE = "\0null_line\0";

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
   * @throws VmEvalException if the content contains an unclosed quote or invalid escape.
   */
  public static List<Entry> parse(String content) {
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
      switch (token) {
        case "machine" -> {
          if (currentMachine != null || isDefault) {
            entries.add(
                new Entry(
                    currentMachine, isDefault, currentLogin, currentPassword, currentAccount));
            currentMachine = null;
            isDefault = false;
            currentLogin = null;
            currentPassword = null;
            currentAccount = null;
          }
          if (i < tokens.size()) {
            currentMachine = tokens.get(i++);
            isDefault = false;
          }
        }
        case "default" -> {
          if (currentMachine != null || isDefault) {
            entries.add(
                new Entry(
                    currentMachine, isDefault, currentLogin, currentPassword, currentAccount));
            currentMachine = null;
            isDefault = false;
            currentLogin = null;
            currentPassword = null;
            currentAccount = null;
          }
          currentMachine = "default";
          isDefault = true;
        }
        case "login", "user" -> {
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
          // Macro definition: skip macro name and macro body lines until the next null line or EOF
          if (i < tokens.size()) {
            i++; // skip macro name
          }
          while (i < tokens.size() && !tokens.get(i).equals(NULL_LINE)) {
            i++;
          }
          if (i < tokens.size() && tokens.get(i).equals(NULL_LINE)) {
            i++;
          }
        }
        case NULL_LINE -> {
          // Ignore null lines outside macdef
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
        result.putIfAbsent("**", headerMap);
      } else if (entry.machine() != null && !entry.machine().contains("/")) {
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

  public static List<String> tokenize(String content) {
    var tokens = new ArrayList<String>();
    var len = content.length();
    var i = 0;
    var consecutiveNewlines = 0;

    while (i < len) {
      var c = content.charAt(i);

      if (c == '\n') {
        consecutiveNewlines++;
        if (consecutiveNewlines >= 2) {
          tokens.add(NULL_LINE);
          consecutiveNewlines = 0;
        }
        i++;
        continue;
      }

      if (Character.isWhitespace(c) || c == ',') {
        i++;
        continue;
      }

      // Non-whitespace character encountered
      consecutiveNewlines = 0;

      if (c == '#') {
        // Skip comment until end of line
        while (i < len && content.charAt(i) != '\n') {
          i++;
        }
        continue;
      }

      if (c == '"') {
        // Quoted token
        i++; // skip opening quote
        var sb = new StringBuilder();
        var closed = false;
        while (i < len) {
          var qc = content.charAt(i);
          if (qc == '\n' || qc == '\r') {
            break;
          }
          if (qc == '\\') {
            i++;
            if (i >= len || content.charAt(i) == '\n' || content.charAt(i) == '\r') {
              throw new VmExceptionBuilder()
                  .evalError("cannotParseNetrc", "unclosed quote")
                  .build();
            }
            sb.append(content.charAt(i++));
          } else if (qc == '"') {
            closed = true;
            i++; // skip closing quote
            break;
          } else {
            sb.append(qc);
            i++;
          }
        }
        if (!closed) {
          throw new VmExceptionBuilder().evalError("cannotParseNetrc", "unclosed quote").build();
        }
        tokens.add(sb.toString());

        // If followed immediately by another quote (e.g. login "foo""bar"), discard subsequent
        // quoted text
        if (i < len && content.charAt(i) == '"') {
          i++;
          while (i < len
              && content.charAt(i) != '"'
              && content.charAt(i) != '\n'
              && content.charAt(i) != '\r') {
            if (content.charAt(i) == '\\') {
              i++;
            }
            i++;
          }
          if (i < len && content.charAt(i) == '"') {
            i++;
          }
        }
        continue;
      }

      // Unquoted token
      var sb = new StringBuilder();
      while (i < len) {
        var uc = content.charAt(i);
        if (Character.isWhitespace(uc) || uc == ',' || uc == '#') {
          break;
        }
        sb.append(uc);
        i++;
      }
      tokens.add(sb.toString());
    }
    return tokens;
  }
}
