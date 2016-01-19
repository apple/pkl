/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.util.Set;
import org.pkl.core.PClassInfo;
import org.pkl.core.PType;

/** Utilities shared across Java dnd Kotlin code generators. */
public final class CodeGeneratorUtils {
  private static final int UNDERSCORE = '_';

  private CodeGeneratorUtils() {}

  public static boolean isRepresentableAsEnum(PType type, @Nullable Set<String> collector) {
    if (type instanceof PType.StringLiteral) {
      if (collector != null) {
        collector.add(((PType.StringLiteral) type).getLiteral());
      }
      return true;
    }
    if (type instanceof PType.Alias) {
      return isRepresentableAsEnum(((PType.Alias) type).getAliasedType(), collector);
    }
    if (type instanceof PType.Union) {
      for (var elementType : ((PType.Union) type).getElementTypes()) {
        if (!isRepresentableAsEnum(elementType, collector)) return false;
      }
      return true;
    }
    return false;
  }

  public static boolean isRepresentableAsString(PType type) {
    if (type instanceof PType.StringLiteral) {
      return true;
    }
    if (type instanceof PType.Class) {
      return ((PType.Class) type).getPClass().getInfo() == PClassInfo.String;
    }
    if (type instanceof PType.Alias) {
      return isRepresentableAsString(((PType.Alias) type).getAliasedType());
    }
    if (type instanceof PType.Union) {
      for (var elementType : ((PType.Union) type).getElementTypes()) {
        if (!isRepresentableAsString(elementType)) return false;
      }
      return true;
    }
    return false;
  }

  /**
   * Converts the given Pkl string literal to an idiomatic enum constant name. Returns null if and
   * only if the string literal could not be converted.
   */
  public static @Nullable String toEnumConstantName(String pklStringLiteral) {
    if (pklStringLiteral.isEmpty()) return null;

    var builder = new StringBuilder();

    var firstCodePoint = pklStringLiteral.codePointAt(0);
    if (Character.getType(firstCodePoint) == Character.DECIMAL_DIGIT_NUMBER) {
      // prepend digit with underscore
      builder.appendCodePoint(UNDERSCORE);
    }

    var iterator = pklStringLiteral.codePoints().iterator();
    var seenPotentialWordEnd = false;

    while (iterator.hasNext()) {
      var codePoint = iterator.nextInt();
      var category = Character.getType(codePoint);

      if (isPunctuationOrSpacing(category)) {
        // replace with underscore
        builder.appendCodePoint(UNDERSCORE);
        seenPotentialWordEnd = false;
        continue;
      }

      if (!isValidIdentifierPart(codePoint, category)) {
        // give up rather than trying to mangle
        return null;
      }

      var isUpperCase = Character.isUpperCase(codePoint);
      if (seenPotentialWordEnd && isUpperCase) {
        // separate words with underscore
        builder.appendCodePoint(UNDERSCORE).appendCodePoint(codePoint);
        seenPotentialWordEnd = false;
        continue;
      }

      builder.appendCodePoint(isUpperCase ? codePoint : Character.toUpperCase(codePoint));
      seenPotentialWordEnd = !isUpperCase;
    }

    return builder.toString();
  }

  /**
   * Tells whether the given Unicode code point is valid when used in the name of an identifier in
   * generated code.
   */
  private static boolean isValidIdentifierPart(int codePoint, int category) {
    switch (category) {
        // NOT Character.CURRENCY_SYMBOL, which is valid in Java, but invalid in Kotlin
      case Character.LOWERCASE_LETTER:
      case Character.UPPERCASE_LETTER:
      case Character.MODIFIER_LETTER:
      case Character.OTHER_LETTER:
      case Character.TITLECASE_LETTER:
      case Character.LETTER_NUMBER:
      case Character.DECIMAL_DIGIT_NUMBER:
        return true;
      default:
        return codePoint == UNDERSCORE;
    }
  }

  private static boolean isPunctuationOrSpacing(int category) {
    switch (category) {
        // Punctuation
      case Character.CONNECTOR_PUNCTUATION: // Pc
      case Character.DASH_PUNCTUATION: // Pd
      case Character.START_PUNCTUATION: // Ps
      case Character.END_PUNCTUATION: // Pe
      case Character.INITIAL_QUOTE_PUNCTUATION: // Pi
      case Character.FINAL_QUOTE_PUNCTUATION: // Pf
      case Character.OTHER_PUNCTUATION: // Po
        // Spacing
      case Character.SPACE_SEPARATOR: // Zs
      case Character.LINE_SEPARATOR: // Zl
      case Character.PARAGRAPH_SEPARATOR: // Zp
        return true;
      default:
        return false;
    }
  }
}
