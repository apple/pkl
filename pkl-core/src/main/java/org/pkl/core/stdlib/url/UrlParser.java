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
package org.pkl.core.stdlib.url;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The WHATWG <a href="https://url.spec.whatwg.org/#concept-basic-url-parser">basic URL parser</a>.
 *
 * <p>A single forward pass over the input code points drives a state machine that materializes a
 * {@link UrlRecord}. Parsing is lenient (matching {@code new URL()}); it returns {@code null} only
 * on genuine failure. When {@code strict} is set, any WHATWG <em>validation error</em> is also
 * treated as failure.
 *
 * <p>IDNA/UTS-46 {@code ToASCII} is not yet implemented: a non-ASCII domain (the host of a special
 * scheme) is rejected rather than passed through. See {@code .ai/pkl-url.md}.
 */
@SuppressWarnings("JavadocLinkAsPlainText")
final class UrlParser {
  private static final int EOF = -1;

  private final int[] input;
  private final @Nullable UrlRecord base;
  private final boolean strict;

  private boolean validationError;
  private int pointer;
  private State state = State.SCHEME_START;
  private final StringBuilder buffer = new StringBuilder();
  private boolean atSignSeen;
  private boolean insideBrackets;
  private boolean passwordTokenSeen;

  // URL record under construction.
  private String scheme = "";
  private final StringBuilder username = new StringBuilder();
  private final StringBuilder password = new StringBuilder();
  private @Nullable String host;
  private @Nullable Integer port;
  private final List<String> path = new ArrayList<>();
  private @Nullable StringBuilder opaquePath;
  private @Nullable StringBuilder query;
  private @Nullable StringBuilder fragment;

  private enum State {
    SCHEME_START,
    SCHEME,
    NO_SCHEME,
    SPECIAL_RELATIVE_OR_AUTHORITY,
    PATH_OR_AUTHORITY,
    RELATIVE,
    RELATIVE_SLASH,
    SPECIAL_AUTHORITY_SLASHES,
    SPECIAL_AUTHORITY_IGNORE_SLASHES,
    AUTHORITY,
    HOST,
    PORT,
    FILE,
    FILE_SLASH,
    FILE_HOST,
    PATH_START,
    PATH,
    OPAQUE_PATH,
    QUERY,
    FRAGMENT
  }

  private UrlParser(String rawInput, @Nullable UrlRecord base, boolean strict) {
    this.base = base;
    this.strict = strict;

    // Remove leading/trailing C0 controls and spaces, then all ASCII tab/newline. Each removal is a
    // validation error.
    var trimmed = stripC0OrSpace(rawInput);
    if (trimmed.length() != rawInput.length()) {
      validationError = true;
    }
    var cleaned = removeTabsAndNewlines(trimmed);
    if (cleaned.length() != trimmed.length()) {
      validationError = true;
    }
    this.input = cleaned.codePoints().toArray();
  }

  /** Parses {@code input} as an absolute URL. Returns {@code null} on failure. */
  static @Nullable UrlRecord parse(String input, boolean strict) {
    return parse(input, null, strict);
  }

  /** Parses {@code input}, resolving relative references against {@code base}. */
  static @Nullable UrlRecord parse(String input, @Nullable UrlRecord base, boolean strict) {
    return new UrlParser(input, base, strict).run();
  }

  private @Nullable UrlRecord run() {
    for (pointer = 0; pointer <= input.length; pointer++) {
      var c = pointer < input.length ? input[pointer] : EOF;
      if (!step(c)) {
        return null;
      }
    }
    if (strict && validationError) {
      return null;
    }
    return build();
  }

  /** Runs one state-machine step for code point {@code c}. Returns {@code false} on failure. */
  @SuppressWarnings("fallthrough")
  private boolean step(int c) {
    switch (state) {
      case SCHEME_START -> {
        if (isAsciiAlpha(c)) {
          buffer.append((char) toLowerAscii(c));
          state = State.SCHEME;
        } else {
          state = State.NO_SCHEME;
          pointer--;
        }
      }
      case SCHEME -> {
        if (isAsciiAlphanumeric(c) || c == '+' || c == '-' || c == '.') {
          buffer.append((char) toLowerAscii(c));
        } else if (c == ':') {
          scheme = buffer.toString();
          buffer.setLength(0);
          if (scheme.equals("file")) {
            if (!(at(pointer + 1) == '/' && at(pointer + 2) == '/')) {
              validationError = true;
            }
            state = State.FILE;
          } else if (isSpecial() && base != null && base.scheme().equals(scheme)) {
            state = State.SPECIAL_RELATIVE_OR_AUTHORITY;
          } else if (isSpecial()) {
            state = State.SPECIAL_AUTHORITY_SLASHES;
          } else if (at(pointer + 1) == '/') {
            state = State.PATH_OR_AUTHORITY;
            pointer++;
          } else {
            opaquePath = new StringBuilder();
            state = State.OPAQUE_PATH;
          }
        } else {
          buffer.setLength(0);
          scheme = "";
          state = State.NO_SCHEME;
          pointer = -1;
        }
      }
      case NO_SCHEME -> {
        if (base == null || (base.hasOpaquePath() && c != '#')) {
          return false;
        } else if (base.hasOpaquePath()) {
          scheme = base.scheme();
          opaquePath = new StringBuilder(base.path().isEmpty() ? "" : base.path().get(0));
          query = base.query() == null ? null : new StringBuilder(base.query());
          fragment = new StringBuilder();
          state = State.FRAGMENT;
        } else if (!base.scheme().equals("file")) {
          state = State.RELATIVE;
          pointer--;
        } else {
          state = State.FILE;
          pointer--;
        }
      }
      case SPECIAL_RELATIVE_OR_AUTHORITY -> {
        if (c == '/' && at(pointer + 1) == '/') {
          state = State.SPECIAL_AUTHORITY_IGNORE_SLASHES;
          pointer++;
        } else {
          validationError = true;
          state = State.RELATIVE;
          pointer--;
        }
      }
      case PATH_OR_AUTHORITY -> {
        if (c == '/') {
          state = State.AUTHORITY;
        } else {
          state = State.PATH;
          pointer--;
        }
      }
      case RELATIVE -> {
        assert base != null;
        scheme = base.scheme();
        if (c == '/') {
          state = State.RELATIVE_SLASH;
        } else if (isSpecial() && c == '\\') {
          validationError = true;
          state = State.RELATIVE_SLASH;
        } else {
          copyAuthorityFrom(base);
          host = base.host();
          port = base.port();
          path.addAll(base.path());
          query = base.query() == null ? null : new StringBuilder(base.query());
          if (c == '?') {
            query = new StringBuilder();
            state = State.QUERY;
          } else if (c == '#') {
            fragment = new StringBuilder();
            state = State.FRAGMENT;
          } else if (c != EOF) {
            query = null;
            shortenPath();
            state = State.PATH;
            pointer--;
          }
        }
      }
      case RELATIVE_SLASH -> {
        assert base != null;
        if (isSpecial() && (c == '/' || c == '\\')) {
          if (c == '\\') {
            validationError = true;
          }
          state = State.SPECIAL_AUTHORITY_IGNORE_SLASHES;
        } else if (c == '/') {
          state = State.AUTHORITY;
        } else {
          copyAuthorityFrom(base);
          host = base.host();
          port = base.port();
          state = State.PATH;
          pointer--;
        }
      }
      case SPECIAL_AUTHORITY_SLASHES -> {
        if (c == '/' && at(pointer + 1) == '/') {
          state = State.SPECIAL_AUTHORITY_IGNORE_SLASHES;
          pointer++;
        } else {
          validationError = true;
          state = State.SPECIAL_AUTHORITY_IGNORE_SLASHES;
          pointer--;
        }
      }
      case SPECIAL_AUTHORITY_IGNORE_SLASHES -> {
        if (c != '/' && c != '\\') {
          state = State.AUTHORITY;
          pointer--;
        } else {
          validationError = true;
        }
      }
      case AUTHORITY -> {
        if (c == '@') {
          validationError = true;
          if (atSignSeen) {
            buffer.insert(0, "%40");
          }
          atSignSeen = true;
          for (var cp : buffer.toString().codePoints().toArray()) {
            if (cp == ':' && !passwordTokenSeen) {
              passwordTokenSeen = true;
              continue;
            }
            PercentEncoder.encode(
                cp, PercentEncoder.USERINFO, passwordTokenSeen ? password : username);
          }
          buffer.setLength(0);
        } else if (c == EOF || c == '/' || c == '?' || c == '#' || (isSpecial() && c == '\\')) {
          if (atSignSeen && buffer.isEmpty()) {
            validationError = true;
            return false;
          }
          pointer -= buffer.codePointCount(0, buffer.length()) + 1;
          buffer.setLength(0);
          state = State.HOST;
        } else {
          buffer.appendCodePoint(c);
        }
      }
      case HOST -> {
        if (c == ':' && !insideBrackets) {
          if (buffer.isEmpty()) {
            validationError = true;
            return false;
          }
          var parsedHost = parseHost(buffer.toString(), !isSpecial());
          if (parsedHost == null) {
            return false;
          }
          host = parsedHost;
          buffer.setLength(0);
          state = State.PORT;
        } else if (c == EOF || c == '/' || c == '?' || c == '#' || (isSpecial() && c == '\\')) {
          pointer--;
          if (isSpecial() && buffer.isEmpty()) {
            validationError = true;
            return false;
          }
          var parsedHost = parseHost(buffer.toString(), !isSpecial());
          if (parsedHost == null) {
            return false;
          }
          host = parsedHost;
          buffer.setLength(0);
          state = State.PATH_START;
        } else {
          if (c == '[') {
            insideBrackets = true;
          } else if (c == ']') {
            insideBrackets = false;
          }
          buffer.appendCodePoint(c);
        }
      }
      case PORT -> {
        if (isAsciiDigit(c)) {
          buffer.appendCodePoint(c);
        } else if (c == EOF || c == '/' || c == '?' || c == '#' || (isSpecial() && c == '\\')) {
          if (!buffer.isEmpty()) {
            long p = 0;
            for (var i = 0; i < buffer.length(); i++) {
              p = p * 10 + (buffer.charAt(i) - '0');
              if (p > 65535) {
                validationError = true;
                return false;
              }
            }
            var defaultPort = UrlRecord.defaultPort(scheme);
            port = (defaultPort != null && defaultPort == p) ? null : (int) p;
            buffer.setLength(0);
          }
          state = State.PATH_START;
          pointer--;
        } else {
          validationError = true;
          return false;
        }
      }
      case FILE -> {
        scheme = "file";
        host = "";
        if (c == '/' || c == '\\') {
          if (c == '\\') {
            validationError = true;
          }
          state = State.FILE_SLASH;
        } else if (base != null && base.scheme().equals("file")) {
          host = base.host();
          path.addAll(base.path());
          query = base.query() == null ? null : new StringBuilder(base.query());
          if (c == '?') {
            query = new StringBuilder();
            state = State.QUERY;
          } else if (c == '#') {
            fragment = new StringBuilder();
            state = State.FRAGMENT;
          } else if (c != EOF) {
            query = null;
            if (!startsWithWindowsDriveLetter(pointer)) {
              shortenPath();
            } else {
              validationError = true;
              path.clear();
            }
            state = State.PATH;
            pointer--;
          }
        } else {
          state = State.PATH;
          pointer--;
        }
      }
      case FILE_SLASH -> {
        if (c == '/' || c == '\\') {
          if (c == '\\') {
            validationError = true;
          }
          state = State.FILE_HOST;
        } else {
          if (base != null && base.scheme().equals("file")) {
            host = base.host();
            if (!startsWithWindowsDriveLetter(pointer)
                && !base.path().isEmpty()
                && isNormalizedWindowsDriveLetter(base.path().get(0))) {
              path.add(base.path().get(0));
            }
          }
          state = State.PATH;
          pointer--;
        }
      }
      case FILE_HOST -> {
        if (c == EOF || c == '/' || c == '\\' || c == '?' || c == '#') {
          pointer--;
          if (isWindowsDriveLetter(buffer.toString())) {
            validationError = true;
            state = State.PATH;
          } else if (buffer.isEmpty()) {
            host = "";
            state = State.PATH_START;
          } else {
            var parsedHost = parseHost(buffer.toString(), false);
            if (parsedHost == null) {
              return false;
            }
            host = parsedHost.equals("localhost") ? "" : parsedHost;
            buffer.setLength(0);
            state = State.PATH_START;
          }
        } else {
          buffer.appendCodePoint(c);
        }
      }
      case PATH_START -> {
        if (isSpecial()) {
          if (c == '\\') {
            validationError = true;
          }
          state = State.PATH;
          if (c != '/' && c != '\\') {
            pointer--;
          }
        } else if (c == '?') {
          query = new StringBuilder();
          state = State.QUERY;
        } else if (c == '#') {
          fragment = new StringBuilder();
          state = State.FRAGMENT;
        } else if (c != EOF) {
          state = State.PATH;
          if (c != '/') {
            pointer--;
          }
        }
      }
      case PATH -> {
        var isSlash = c == '/' || (isSpecial() && c == '\\');
        if (c == EOF || isSlash || c == '?' || c == '#') {
          if (isSpecial() && c == '\\') {
            validationError = true;
          }
          var segment = buffer.toString();
          if (isDoubleDotSegment(segment)) {
            shortenPath();
            if (!isSlash) {
              path.add("");
            }
          } else if (isSingleDotSegment(segment)) {
            if (!isSlash) {
              path.add("");
            }
          } else {
            if (scheme.equals("file") && path.isEmpty() && isWindowsDriveLetter(segment)) {
              buffer.setCharAt(1, ':');
              segment = buffer.toString();
            }
            path.add(segment);
          }
          buffer.setLength(0);
          if (c == '?') {
            query = new StringBuilder();
            state = State.QUERY;
          } else if (c == '#') {
            fragment = new StringBuilder();
            state = State.FRAGMENT;
          }
        } else {
          checkUrlUnit(c);
          PercentEncoder.encode(c, PercentEncoder.PATH, buffer);
        }
      }
      case OPAQUE_PATH -> {
        assert opaquePath != null;
        if (c == '?') {
          query = new StringBuilder();
          state = State.QUERY;
        } else if (c == '#') {
          fragment = new StringBuilder();
          state = State.FRAGMENT;
        } else if (c == ' ') {
          // a space in an opaque path is always a validation error, but is only percent-encoded
          // when it is the last one before the query or fragment
          validationError = true;
          if (at(pointer + 1) == '?' || at(pointer + 1) == '#') {
            opaquePath.append("%20");
          } else {
            opaquePath.append(' ');
          }
        } else if (c != EOF) {
          checkUrlUnit(c);
          PercentEncoder.encode(c, PercentEncoder.C0_CONTROL, opaquePath);
        }
      }
      case QUERY -> {
        if (c == EOF || c == '#') {
          assert query != null;
          var set = isSpecial() ? PercentEncoder.SPECIAL_QUERY : PercentEncoder.QUERY;
          for (var cp : buffer.toString().codePoints().toArray()) {
            PercentEncoder.encode(cp, set, query);
          }
          buffer.setLength(0);
          if (c == '#') {
            fragment = new StringBuilder();
            state = State.FRAGMENT;
          }
        } else {
          checkUrlUnit(c);
          buffer.appendCodePoint(c);
        }
      }
      case FRAGMENT -> {
        if (c != EOF) {
          assert fragment != null;
          checkUrlUnit(c);
          PercentEncoder.encode(c, PercentEncoder.FRAGMENT, fragment);
        }
      }
    }
    return true;
  }

  private UrlRecord build() {
    List<String> finalPath;
    boolean opaque;
    if (opaquePath != null) {
      finalPath = List.of(opaquePath.toString());
      opaque = true;
    } else {
      finalPath = List.copyOf(path);
      opaque = false;
    }
    return new UrlRecord(
        scheme,
        username.toString(),
        password.toString(),
        host,
        port,
        finalPath,
        opaque,
        query == null ? null : query.toString(),
        fragment == null ? null : fragment.toString());
  }

  private void copyAuthorityFrom(UrlRecord base) {
    username.setLength(0);
    username.append(base.username());
    password.setLength(0);
    password.append(base.password());
  }

  private boolean isSpecial() {
    return UrlRecord.isSpecialScheme(scheme);
  }

  /** https://url.spec.whatwg.org/#shorten-a-urls-path */
  private void shortenPath() {
    if (scheme.equals("file") && path.size() == 1 && isNormalizedWindowsDriveLetter(path.get(0))) {
      return;
    }
    if (!path.isEmpty()) {
      path.remove(path.size() - 1);
    }
  }

  // Host parsing (https://url.spec.whatwg.org/#host-parsing)

  private @Nullable String parseHost(String input, boolean isNotSpecial) {
    if (!input.isEmpty() && input.charAt(0) == '[') {
      if (input.charAt(input.length() - 1) != ']') {
        validationError = true;
        return null;
      }
      var address = parseIpv6(input.substring(1, input.length() - 1));
      if (address == null) {
        return null;
      }
      return "[" + serializeIpv6(address) + "]";
    }
    if (isNotSpecial) {
      return parseOpaqueHost(input);
    }
    var domain = PercentEncoder.percentDecode(input);
    var asciiDomain = domainToAscii(domain);
    if (asciiDomain == null) {
      return null;
    }
    for (var i = 0; i < asciiDomain.length(); i++) {
      if (isForbiddenDomainCodePoint(asciiDomain.charAt(i))) {
        validationError = true;
        return null;
      }
    }
    if (endsInNumber(asciiDomain)) {
      var address = parseIpv4(asciiDomain);
      return address < 0 ? null : serializeIpv4(address);
    }
    return asciiDomain;
  }

  private @Nullable String domainToAscii(String domain) {
    var allAscii = domain.chars().allMatch(c -> c < 0x80);
    if (!allAscii) {
      // IDNA/UTS-46 ToASCII is not yet implemented, return an error.
      return null;
    }
    return domain.toLowerCase(Locale.ROOT);
  }

  private @Nullable String parseOpaqueHost(String input) {
    for (var i = 0; i < input.length(); i++) {
      if (isForbiddenHostCodePoint(input.charAt(i))) {
        validationError = true;
        return null;
      }
    }
    checkUrlUnits(input);
    var out = new StringBuilder();
    input.codePoints().forEach(cp -> PercentEncoder.encode(cp, PercentEncoder.C0_CONTROL, out));
    return out.toString();
  }

  // URL units (https://url.spec.whatwg.org/#url-units)

  /**
   * Records an <a href="https://url.spec.whatwg.org/#invalid-url-unit">invalid-URL-unit</a>
   * validation error unless the code point at {@code pointer} is a URL unit.
   */
  private void checkUrlUnit(int c) {
    if (!isUrlUnit(c, at(pointer + 1), at(pointer + 2))) {
      validationError = true;
    }
  }

  private void checkUrlUnits(String input) {
    var codePoints = input.codePoints().toArray();
    for (var i = 0; i < codePoints.length; i++) {
      var next1 = i + 1 < codePoints.length ? codePoints[i + 1] : EOF;
      var next2 = i + 2 < codePoints.length ? codePoints[i + 2] : EOF;
      if (!isUrlUnit(codePoints[i], next1, next2)) {
        validationError = true;
        return;
      }
    }
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private static boolean isUrlUnit(int c, int next1, int next2) {
    return c == '%'
        ? PercentEncoder.isHexDigit(next1) && PercentEncoder.isHexDigit(next2)
        : isUrlCodePoint(c);
  }

  /** https://url.spec.whatwg.org/#url-code-points */
  private static boolean isUrlCodePoint(int c) {
    if (isAsciiAlphanumeric(c)) {
      return true;
    }
    return switch (c) {
      case '!',
          '$',
          '&',
          '\'',
          '(',
          ')',
          '*',
          '+',
          ',',
          '-',
          '.',
          '/',
          ':',
          ';',
          '=',
          '?',
          '@',
          '_',
          '~' ->
          true;
      default -> c >= 0xA0 && c <= 0x10FFFD && !isSurrogate(c) && !isNoncharacter(c);
    };
  }

  private static boolean isSurrogate(int c) {
    return c >= 0xD800 && c <= 0xDFFF;
  }

  /** https://infra.spec.whatwg.org/#noncharacter */
  private static boolean isNoncharacter(int c) {
    return (c >= 0xFDD0 && c <= 0xFDEF) || (c & 0xFFFF) >= 0xFFFE;
  }

  // IPv4 (https://url.spec.whatwg.org/#concept-ipv4-parser)

  private boolean endsInNumber(String input) {
    var parts = split(input, '.');
    if (parts.get(parts.size() - 1).isEmpty() && parts.size() > 1) {
      parts.remove(parts.size() - 1);
    }
    var last = parts.get(parts.size() - 1);
    if (last.isEmpty()) {
      return false;
    }
    if (last.chars().allMatch(UrlParser::isAsciiDigit)) {
      return true;
    }
    return parseIpv4Number(last) >= 0;
  }

  private long parseIpv4(String input) {
    var parts = split(input, '.');
    if (parts.get(parts.size() - 1).isEmpty() && parts.size() > 1) {
      validationError = true;
      parts.remove(parts.size() - 1);
    }
    if (parts.size() > 4) {
      return -1;
    }
    var numbers = new ArrayList<Long>();
    for (var part : parts) {
      if (part.isEmpty()) {
        return -1;
      }
      var n = parseIpv4Number(part);
      if (n < 0) {
        return -1;
      }
      numbers.add(n);
    }
    var size = numbers.size();
    long ipv4 = numbers.get(size - 1);
    if (ipv4 >= (1L << (8L * (5 - size)))) {
      return -1;
    }
    var counter = 0;
    for (var i = 0; i < size - 1; i++) {
      long n = numbers.get(i);
      if (n > 255) {
        return -1;
      }
      ipv4 += n << (8L * (3 - counter));
      counter++;
    }
    return ipv4;
  }

  /** Returns the parsed number, or a negative value on failure. */
  private static long parseIpv4Number(String input) {
    if (input.isEmpty()) {
      return -1;
    }
    var radix = 10;
    if (input.startsWith("0x") || input.startsWith("0X")) {
      input = input.substring(2);
      radix = 16;
    } else if (input.length() >= 2 && input.charAt(0) == '0') {
      input = input.substring(1);
      radix = 8;
    }
    if (input.isEmpty()) {
      return 0;
    }
    if (!isRadixDigits(input, radix)) {
      return -1;
    }
    try {
      return Long.parseLong(input, radix);
    } catch (NumberFormatException e) {
      // The spec computes an arbitrary-precision integer here, so a value that overflows a `long`
      // is valid input, not a parse failure. `endsInNumber` must still treat it as a number.
      return IPV4_NUMBER_TOO_LARGE;
    }
  }

  private static final long IPV4_NUMBER_TOO_LARGE = 1L << 32;

  /** Whether every code point of {@code input} is an ASCII digit in {@code radix}. */
  private static boolean isRadixDigits(String input, int radix) {
    for (var i = 0; i < input.length(); i++) {
      var c = input.charAt(i);
      if (c > 0x7F || Character.digit(c, radix) < 0) {
        return false;
      }
    }
    return true;
  }

  private static String serializeIpv4(long address) {
    var out = new StringBuilder();
    var n = address;
    for (var i = 1; i <= 4; i++) {
      out.insert(0, n % 256);
      if (i != 4) {
        out.insert(0, '.');
      }
      n = n / 256;
    }
    return out.toString();
  }

  // IPv6 (https://url.spec.whatwg.org/#concept-ipv6-parser)

  private int @Nullable [] parseIpv6(String input) {
    var pieces = new int[8];
    var pieceIndex = 0;
    var compress = -1;
    var chars = input.toCharArray();
    var p = 0;

    if (p < chars.length && chars[p] == ':') {
      if (p + 1 >= chars.length || chars[p + 1] != ':') {
        validationError = true;
        return null;
      }
      p += 2;
      pieceIndex++;
      compress = pieceIndex;
    }

    while (p < chars.length) {
      if (pieceIndex == 8) {
        validationError = true;
        return null;
      }
      if (chars[p] == ':') {
        if (compress != -1) {
          validationError = true;
          return null;
        }
        p++;
        pieceIndex++;
        compress = pieceIndex;
        continue;
      }
      var value = 0;
      var length = 0;
      while (length < 4 && p < chars.length && PercentEncoder.isHexDigit(chars[p])) {
        value = value * 16 + Character.digit(chars[p], 16);
        p++;
        length++;
      }
      if (p < chars.length && chars[p] == '.') {
        if (length == 0) {
          validationError = true;
          return null;
        }
        p -= length;
        if (pieceIndex > 6) {
          validationError = true;
          return null;
        }
        var numbersSeen = 0;
        while (p < chars.length) {
          var ipv4Piece = -1;
          if (numbersSeen > 0) {
            if (chars[p] == '.' && numbersSeen < 4) {
              p++;
            } else {
              validationError = true;
              return null;
            }
          }
          if (p >= chars.length || !isAsciiDigit(chars[p])) {
            validationError = true;
            return null;
          }
          while (p < chars.length && isAsciiDigit(chars[p])) {
            var number = chars[p] - '0';
            if (ipv4Piece == -1) {
              ipv4Piece = number;
            } else if (ipv4Piece == 0) {
              validationError = true;
              return null;
            } else {
              ipv4Piece = ipv4Piece * 10 + number;
            }
            if (ipv4Piece > 255) {
              validationError = true;
              return null;
            }
            p++;
          }
          pieces[pieceIndex] = pieces[pieceIndex] * 0x100 + ipv4Piece;
          numbersSeen++;
          if (numbersSeen == 2 || numbersSeen == 4) {
            pieceIndex++;
          }
        }
        if (numbersSeen != 4) {
          validationError = true;
          return null;
        }
        break;
      } else if (p < chars.length && chars[p] == ':') {
        p++;
        if (p >= chars.length) {
          validationError = true;
          return null;
        }
      } else if (p < chars.length) {
        validationError = true;
        return null;
      }
      pieces[pieceIndex] = value;
      pieceIndex++;
    }

    if (compress != -1) {
      var swaps = pieceIndex - compress;
      pieceIndex = 7;
      while (pieceIndex != 0 && swaps > 0) {
        var temp = pieces[pieceIndex];
        pieces[pieceIndex] = pieces[compress + swaps - 1];
        pieces[compress + swaps - 1] = temp;
        pieceIndex--;
        swaps--;
      }
    } else if (pieceIndex != 8) {
      validationError = true;
      return null;
    }
    return pieces;
  }

  private static String serializeIpv6(int[] pieces) {
    var out = new StringBuilder();
    var compress = longestZeroRun(pieces);
    var ignoreZero = false;
    for (var i = 0; i < 8; i++) {
      if (ignoreZero && pieces[i] == 0) {
        continue;
      }
      ignoreZero = false;
      if (compress == i) {
        out.append(i == 0 ? "::" : ":");
        ignoreZero = true;
        continue;
      }
      out.append(Integer.toHexString(pieces[i]));
      if (i != 7) {
        out.append(':');
      }
    }
    return out.toString();
  }

  /** Index at which the longest run (length > 1) of zero pieces starts, or -1 if none. */
  private static int longestZeroRun(int[] pieces) {
    var bestStart = -1;
    var bestLength = 1;
    var runStart = -1;
    var runLength = 0;
    for (var i = 0; i < 8; i++) {
      if (pieces[i] == 0) {
        if (runStart == -1) {
          runStart = i;
        }
        runLength++;
        if (runLength > bestLength) {
          bestLength = runLength;
          bestStart = runStart;
        }
      } else {
        runStart = -1;
        runLength = 0;
      }
    }
    return bestStart;
  }

  // Input helpers

  private int at(int index) {
    return index >= 0 && index < input.length ? input[index] : EOF;
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean startsWithWindowsDriveLetter(int from) {
    var length = input.length - from;
    return length >= 2
        && isAsciiAlpha(input[from])
        && (input[from + 1] == ':' || input[from + 1] == '|')
        && (length == 2
            || input[from + 2] == '/'
            || input[from + 2] == '\\'
            || input[from + 2] == '?'
            || input[from + 2] == '#');
  }

  private static List<String> split(String input, char separator) {
    var parts = new ArrayList<String>();
    var start = 0;
    for (var i = 0; i < input.length(); i++) {
      if (input.charAt(i) == separator) {
        parts.add(input.substring(start, i));
        start = i + 1;
      }
    }
    parts.add(input.substring(start));
    return parts;
  }

  private static String stripC0OrSpace(String input) {
    var start = 0;
    var end = input.length();
    while (start < end && input.charAt(start) <= 0x20) {
      start++;
    }
    while (end > start && input.charAt(end - 1) <= 0x20) {
      end--;
    }
    return input.substring(start, end);
  }

  private static String removeTabsAndNewlines(String input) {
    var out = new StringBuilder(input.length());
    for (var i = 0; i < input.length(); i++) {
      var c = input.charAt(i);
      if (c != '\t' && c != '\n' && c != '\r') {
        out.append(c);
      }
    }
    return out.toString();
  }

  private static boolean isSingleDotSegment(String s) {
    return s.equals(".") || s.equalsIgnoreCase("%2e");
  }

  private static boolean isDoubleDotSegment(String s) {
    return s.equals("..")
        || s.equalsIgnoreCase(".%2e")
        || s.equalsIgnoreCase("%2e.")
        || s.equalsIgnoreCase("%2e%2e");
  }

  private static boolean isWindowsDriveLetter(String s) {
    return s.length() == 2
        && isAsciiAlpha(s.charAt(0))
        && (s.charAt(1) == ':' || s.charAt(1) == '|');
  }

  private static boolean isNormalizedWindowsDriveLetter(String s) {
    return isWindowsDriveLetter(s) && s.charAt(1) == ':';
  }

  static boolean isForbiddenHostCodePoint(int c) {
    return c == 0x00 || c == 0x09 || c == 0x0A || c == 0x0D || c == ' ' || c == '#' || c == '/'
        || c == ':' || c == '<' || c == '>' || c == '?' || c == '@' || c == '[' || c == '\\'
        || c == ']' || c == '^' || c == '|';
  }

  private static boolean isForbiddenDomainCodePoint(int c) {
    return isForbiddenHostCodePoint(c) || c <= 0x1F || c == '%' || c == 0x7F;
  }

  private static boolean isAsciiAlpha(int c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  private static boolean isAsciiDigit(int c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isAsciiAlphanumeric(int c) {
    return isAsciiAlpha(c) || isAsciiDigit(c);
  }

  private static int toLowerAscii(int c) {
    return (c >= 'A' && c <= 'Z') ? c + 0x20 : c;
  }
}
