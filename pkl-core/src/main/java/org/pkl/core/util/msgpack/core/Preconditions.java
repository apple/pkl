/*
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
package org.pkl.core.util.msgpack.core;

import org.pkl.core.util.Nullable;

/**
 * Simple static methods to be called at the start of your own methods to verify correct arguments
 * and state. This allows constructs such as
 *
 * <pre>{@code
 * if (count <= 0) {
 *   throw new IllegalArgumentException("must be positive: " + count);
 * }
 * }</pre>
 *
 * to be replaced with the more compact
 *
 * <pre>
 *     checkArgument(count > 0, "must be positive: %s", count);</pre>
 *
 * Note that the sense of the expression is inverted; with {@code Preconditions} you declare what
 * you expect to be <i>true</i>, just as you do with an <a
 * href="http://java.sun.com/j2se/1.5.0/docs/guide/language/assert.html">{@code assert}</a> or a
 * JUnit {@code assertTrue} call.
 *
 * <p><b>Warning:</b> only the {@code "%s"} specifier is recognized as a placeholder in these
 * messages, not the full range of {@link String#format(String, Object[])} specifiers.
 *
 * <p>Take care not to confuse precondition checking with other similar types of checks!
 * Precondition exceptions -- including those provided here, but also {@link
 * IndexOutOfBoundsException}, {@link java.util.NoSuchElementException}, {@link
 * UnsupportedOperationException} and others -- are used to signal that the <i>calling method</i>
 * has made an error. This tells the caller that it should not have invoked the method when it did,
 * with the arguments it did, or perhaps ever. Postcondition or other invariant failures should not
 * throw these types of exceptions.
 *
 * <p>See the Guava User Guide on <a href=
 * "http://code.google.com/p/guava-libraries/wiki/PreconditionsExplained"> using {@code
 * Preconditions}</a>.
 *
 * @author Kevin Bourrillion
 * @since 2.0 (imported from Google Collections Library)
 */
public final class Preconditions {
  private Preconditions() {}

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @throws IllegalArgumentException if {@code expression} is false
   */
  public static void checkArgument(boolean expression) {
    if (!expression) {
      throw new IllegalArgumentException();
    }
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   *     string using {@link String#valueOf(Object)}
   * @throws IllegalArgumentException if {@code expression} is false
   */
  public static void checkArgument(boolean expression, @Nullable Object errorMessage) {
    if (!expression) {
      throw new IllegalArgumentException(String.valueOf(errorMessage));
    }
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessageTemplate a template for the exception message should the check fail. The
   *     message is formed by replacing each {@code %s} placeholder in the template with an
   *     argument. These are matched by position - the first {@code %s} gets {@code
   *     errorMessageArgs[0]}, etc. Unmatched arguments will be appended to the formatted message in
   *     square braces. Unmatched placeholders will be left as-is.
   * @param errorMessageArgs the arguments to be substituted into the message template. Arguments
   *     are converted to strings using {@link String#valueOf(Object)}.
   * @throws IllegalArgumentException if {@code expression} is false
   * @throws NullPointerException if the check fails and either {@code errorMessageTemplate} or
   *     {@code errorMessageArgs} is null (don't let this happen)
   */
  public static void checkArgument(
      boolean expression,
      @Nullable String errorMessageTemplate,
      @Nullable Object... errorMessageArgs) {
    if (!expression) {
      throw new IllegalArgumentException(format(errorMessageTemplate, errorMessageArgs));
    }
  }

  /**
   * Ensures that an object reference passed as a parameter to the calling method is not null.
   *
   * @param reference an object reference
   * @return the non-null reference that was validated
   * @throws NullPointerException if {@code reference} is null
   */
  public static <T> T checkNotNull(T reference) {
    if (reference == null) {
      throw new NullPointerException();
    }
    return reference;
  }

  /**
   * Ensures that an object reference passed as a parameter to the calling method is not null.
   *
   * @param reference an object reference
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   *     string using {@link String#valueOf(Object)}
   * @return the non-null reference that was validated
   * @throws NullPointerException if {@code reference} is null
   */
  public static <T> T checkNotNull(T reference, @Nullable Object errorMessage) {
    if (reference == null) {
      throw new NullPointerException(String.valueOf(errorMessage));
    }
    return reference;
  }

  /**
   * Ensures that an object reference passed as a parameter to the calling method is not null.
   *
   * @param reference an object reference
   * @param errorMessageTemplate a template for the exception message should the check fail. The
   *     message is formed by replacing each {@code %s} placeholder in the template with an
   *     argument. These are matched by position - the first {@code %s} gets {@code
   *     errorMessageArgs[0]}, etc. Unmatched arguments will be appended to the formatted message in
   *     square braces. Unmatched placeholders will be left as-is.
   * @param errorMessageArgs the arguments to be substituted into the message template. Arguments
   *     are converted to strings using {@link String#valueOf(Object)}.
   * @return the non-null reference that was validated
   * @throws NullPointerException if {@code reference} is null
   */
  public static <T> T checkNotNull(
      T reference, @Nullable String errorMessageTemplate, @Nullable Object... errorMessageArgs) {
    if (reference == null) {
      // If either of these parameters is null, the right thing happens anyway
      throw new NullPointerException(format(errorMessageTemplate, errorMessageArgs));
    }
    return reference;
  }

  /*
   * All recent hotspots (as of 2009) *really* like to have the natural code
   *
   * if (guardExpression) {
   *    throw new BadException(messageExpression);
   * }
   *
   * refactored so that messageExpression is moved to a separate
   * String-returning method.
   *
   * if (guardExpression) {
   *    throw new BadException(badMsg(...));
   * }
   *
   * The alternative natural refactorings into void or Exception-returning
   * methods are much slower.  This is a big deal - we're talking factors of
   * 2-8 in microbenchmarks, not just 10-20%.  (This is a hotspot optimizer
   * bug, which should be fixed, but that's a separate, big project).
   *
   * The coding pattern above is heavily used in java.util, e.g. in ArrayList.
   * There is a RangeCheckMicroBenchmark in the JDK that was used to test this.
   *
   * But the methods in this class want to throw different exceptions,
   * depending on the args, so it appears that this pattern is not directly
   * applicable.  But we can use the ridiculous, devious trick of throwing an
   * exception in the middle of the construction of another exception.
   * Hotspot is fine with that.
   */

  /**
   * Substitutes each {@code %s} in {@code template} with an argument. These are matched by position
   * - the first {@code %s} gets {@code args[0]}, etc. If there are more arguments than
   * placeholders, the unmatched arguments will be appended to the end of the formatted message in
   * square braces.
   *
   * @param template a non-null string containing 0 or more {@code %s} placeholders.
   * @param args the arguments to be substituted into the message template. Arguments are converted
   *     to strings using {@link String#valueOf(Object)}. Arguments can be null.
   */
  static String format(String template, @Nullable Object... args) {
    template = String.valueOf(template); // null -> "null"

    // start substituting the arguments into the '%s' placeholders
    StringBuilder builder = new StringBuilder(template.length() + 16 * args.length);
    int templateStart = 0;
    int i = 0;
    while (i < args.length) {
      int placeholderStart = template.indexOf("%s", templateStart);
      if (placeholderStart == -1) {
        break;
      }
      builder.append(template.substring(templateStart, placeholderStart));
      builder.append(args[i++]);
      templateStart = placeholderStart + 2;
    }
    builder.append(template.substring(templateStart));

    // if we run out of placeholders, append the extra args in square braces
    if (i < args.length) {
      builder.append(" [");
      builder.append(args[i++]);
      while (i < args.length) {
        builder.append(", ");
        builder.append(args[i++]);
      }
      builder.append(']');
    }

    return builder.toString();
  }
}
