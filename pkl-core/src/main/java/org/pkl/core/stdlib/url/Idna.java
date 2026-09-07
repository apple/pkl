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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.pkl.core.stdlib.url.IdnaTable.BidiClass;
import org.pkl.core.stdlib.url.IdnaTable.Status;

/** UTS-46 {@code Processing} and {@code ToASCII}. */
final class Idna {
  private Idna() {}

  /** The ACE prefix marking a Punycode-encoded label. */
  private static final String ACE_PREFIX = "xn--";

  /**
   * The ASCII form of {@code domain}, or {@code null} if UTS-46 rejects it. An empty result is a
   * failure too.
   */
  static @Nullable String domainToAscii(String domain) {
    if (isAscii(domain)) {
      return domain.isEmpty() ? null : domain.toLowerCase(Locale.ROOT);
    }
    return process(domain);
  }

  /** UTS-46 {@code Processing} followed by {@code ToASCII}'s conversion back to ASCII. */
  private static @Nullable String process(String domain) {
    var mapped = map(domain);
    if (mapped == null) {
      return null;
    }
    var labels = split(Normalizer.normalize(mapped, Normalizer.Form.NFC));
    for (var i = 0; i < labels.size(); i++) {
      var label = labels.get(i);
      if (!hasAcePrefix(label)) {
        continue;
      }
      var decoded = Punycode.decode(label.substring(ACE_PREFIX.length()));
      if (decoded == null) {
        return null;
      }
      labels.set(i, decoded);
    }
    var isBidiDomain = labels.stream().anyMatch(Idna::isRtlLabel);
    var out = new StringBuilder(domain.length() + ACE_PREFIX.length());
    for (var i = 0; i < labels.size(); i++) {
      var label = labels.get(i);
      if (!isValid(label, isBidiDomain)) {
        return null;
      }
      if (i > 0) {
        out.append('.');
      }
      if (isAscii(label)) {
        out.append(label);
        continue;
      }
      var encoded = Punycode.encode(label);
      if (encoded == null) {
        return null;
      }
      out.append(ACE_PREFIX).append(encoded);
    }
    return out.isEmpty() ? null : out.toString();
  }

  /** Applies the UTS-46 status of each code point, or returns {@code null} on a disallowed one. */
  private static @Nullable String map(String domain) {
    var out = new StringBuilder(domain.length());
    for (var i = 0; i < domain.length(); ) {
      var codePoint = domain.codePointAt(i);
      i += Character.charCount(codePoint);
      switch (IdnaTable.status(codePoint)) {
        case VALID -> out.appendCodePoint(codePoint);
        case IGNORED -> {}
        case MAPPED -> {
          var mapping = IdnaTable.mapping(codePoint);
          assert mapping != null;
          out.append(mapping);
        }
        case DISALLOWED -> {
          return null;
        }
      }
    }
    return out.toString();
  }

  /** Splits a domain into labels on U+002E FULL STOP, keeping the empty ones. */
  private static List<String> split(String domain) {
    var labels = new ArrayList<String>();
    var start = 0;
    while (true) {
      var end = domain.indexOf('.', start);
      if (end < 0) {
        labels.add(domain.substring(start));
        return labels;
      }
      labels.add(domain.substring(start, end));
      start = end + 1;
    }
  }

  private static boolean isValid(String label, boolean isBidiDomain) {
    if (label.isEmpty()) {
      return true;
    }
    if (!Normalizer.isNormalized(label, Normalizer.Form.NFC)) {
      return false;
    }
    if (hasAcePrefix(label)) {
      return false;
    }
    if (IdnaTable.isMark(label.codePointAt(0))) {
      return false;
    }
    for (var i = 0; i < label.length(); ) {
      var codePoint = label.codePointAt(i);
      i += Character.charCount(codePoint);
      if (codePoint == '.') {
        return false;
      }
      if (IdnaTable.status(codePoint) != Status.VALID) {
        return false;
      }
    }
    return !isBidiDomain || satisfiesBidiRule(label);
  }

  /** Whether a label has a code point of Bidi_Class {@code R}, {@code AL} or {@code AN}. */
  private static boolean isRtlLabel(String label) {
    return label
        .codePoints()
        .anyMatch(
            codePoint ->
                switch (IdnaTable.bidiClass(codePoint)) {
                  case R, AL, AN -> true;
                  default -> false;
                });
  }

  /**
   * The six conditions of the RFC 5893 Bidi rule, which apply to every label of a Bidi domain name.
   */
  private static boolean satisfiesBidiRule(String label) {
    var codePoints = label.codePoints().toArray();
    // condition 1, which is also what decides the direction of the label
    var first = IdnaTable.bidiClass(codePoints[0]);
    if (first != BidiClass.L && first != BidiClass.R && first != BidiClass.AL) {
      return false;
    }
    var isRtl = first != BidiClass.L;

    var hasEuropeanNumber = false;
    var hasArabicNumber = false;
    for (var codePoint : codePoints) {
      var bidiClass = IdnaTable.bidiClass(codePoint);
      // conditions 2 and 5
      if (!(isRtl ? isAllowedInRtlLabel(bidiClass) : isAllowedInLtrLabel(bidiClass))) {
        return false;
      }
      hasEuropeanNumber |= bidiClass == BidiClass.EN;
      hasArabicNumber |= bidiClass == BidiClass.AN;
    }
    // condition 4
    if (isRtl && hasEuropeanNumber && hasArabicNumber) {
      return false;
    }

    // conditions 3 and 6
    var last = codePoints.length - 1;
    while (IdnaTable.bidiClass(codePoints[last]) == BidiClass.NSM) {
      last--;
    }
    var lastClass = IdnaTable.bidiClass(codePoints[last]);
    if (isRtl) {
      // condition 3
      return switch (lastClass) {
        case R, AL, EN, AN -> true;
        default -> false;
      };
    }
    // condition 6
    return lastClass == BidiClass.L || lastClass == BidiClass.EN;
  }

  /** The Bidi_Class values RFC 5893's condition 2 allows in an RTL label. */
  private static boolean isAllowedInRtlLabel(BidiClass bidiClass) {
    return switch (bidiClass) {
      case R, AL, AN, EN, ES, CS, ET, ON, BN, NSM -> true;
      case L, OTHER -> false;
    };
  }

  /** The Bidi_Class values RFC 5893's condition 5 allows in an LTR label. */
  private static boolean isAllowedInLtrLabel(BidiClass bidiClass) {
    return switch (bidiClass) {
      case L, EN, ES, CS, ET, ON, BN, NSM -> true;
      case R, AL, AN, OTHER -> false;
    };
  }

  static boolean isAscii(String value) {
    return value.chars().allMatch(c -> c < 0x80);
  }

  private static boolean hasAcePrefix(String label) {
    return label.regionMatches(true, 0, ACE_PREFIX, 0, ACE_PREFIX.length());
  }
}
