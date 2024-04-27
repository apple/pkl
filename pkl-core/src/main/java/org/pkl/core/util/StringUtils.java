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
package org.pkl.core.util;

import java.util.function.Consumer;

// Some code in this class was taken from the following Google Guava classes:
// * com.google.common.base.CharMatcher
// * com.google.common.base.Strings
public final class StringUtils {
  // TABLE is a precomputed hashset of whitespace characters. MULTIPLIER serves as a hash function
  // whose key property is that it maps 25 characters into the 32-slot table without collision.
  // Basically this is an opportunistic fast implementation as opposed to "good code". For most
  // other use-cases, the reduction in readability isn't worth it.
  @SuppressWarnings("UnnecessaryUnicodeEscape")
  private static final String TABLE =
      "\u2002\u3000\r\u0085\u200A\u2005\u2000\u3000"
          + "\u2029\u000B\u3000\u2008\u2003\u205F\u3000\u1680"
          + "\u0009\u0020\u2006\u2001\u202F\u00A0\u000C\u2009"
          + "\u3000\u2004\u3000\u3000\u2028\n\u2007\u3000";

  private static final int MULTIPLIER = 1682554634;
  private static final int SHIFT = Integer.numberOfLeadingZeros(TABLE.length() - 1);

  private StringUtils() {}

  /** Tells if the given Unicode character has Unicode property "White_Space". */
  public static boolean isWhitespace(int codePoint) {
    return Character.isBmpCodePoint(codePoint) && isWhitespace((char) codePoint);
  }

  /** Tells if the given Java character has Unicode property "White_Space". */
  public static boolean isWhitespace(char ch) {
    return TABLE.charAt((MULTIPLIER * ch) >>> SHIFT) == ch;
  }

  /** Tells if all characters of the given string have Unicode property "White_Space". */
  public static boolean isBlank(String str) {
    for (var i = 0; i < str.length(); i++) {
      if (!isWhitespace(str.charAt(i))) return false;
    }
    return true;
  }

  /** Removes any leading and trailing characters with Unicode property "White_Space". */
  public static String trim(String str) {
    var index = indexOfNonWhitespace(str);
    if (index == str.length()) return "";

    var lastIndex = lastIndexOfNonWhitespace(str);
    return str.substring(index, lastIndex);
  }

  /** Removes any leading characters with Unicode property "White_Space". */
  public static String trimStart(String str) {
    return str.substring(indexOfNonWhitespace(str));
  }

  /** Removes any trailing characters with Unicode property "White_Space". */
  public static String trimEnd(String str) {
    return str.substring(0, lastIndexOfNonWhitespace(str));
  }

  public static <T> void joinToStringBuilder(
      StringBuilder builder, Iterable<T> coll, String delimiter, Consumer<T> eachFn) {
    int i = 0;
    for (var v : coll) {
      if (i++ != 0) {
        builder.append(delimiter);
      }
      eachFn.accept(v);
    }
  }

  public static <T> void joinToStringBuilder(
      StringBuilder builder, Iterable<T> coll, String delimiter) {
    joinToStringBuilder(builder, coll, delimiter, builder::append);
  }

  public static <T> void joinToStringBuilder(
      StringBuilder builder, Iterable<T> coll, Consumer<T> eachFn) {
    joinToStringBuilder(builder, coll, ", ", eachFn);
  }

  private static int indexOfNonWhitespace(String str) {
    var length = str.length();
    for (var i = 0; i < length; i++) {
      if (!isWhitespace(str.charAt(i))) return i;
    }
    return length;
  }

  private static int lastIndexOfNonWhitespace(String str) {
    var length = str.length();
    for (var i = length; i > 0; i--) {
      if (!isWhitespace(str.charAt(i - 1))) return i;
    }
    return 0;
  }
}
