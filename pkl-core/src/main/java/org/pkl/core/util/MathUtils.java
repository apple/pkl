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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.math.RoundingMode;

public final class MathUtils {
  private static final long FLOOR_SQRT_MAX_LONG = 3037000499L;

  // The mask for the significand, according to the {@link
  // Double#doubleToRawLongBits(double)} spec.
  private static final long SIGNIFICAND_MASK = 0x000fffffffffffffL;

  /** The implicit 1 bit that is omitted in significands of normal doubles. */
  private static final long IMPLICIT_BIT = SIGNIFICAND_MASK + 1;

  private static final double MIN_LONG_AS_DOUBLE = -0x1p63;
  /*
   * We cannot store Long.MAX_VALUE as a double without losing precision. Instead, we store
   * Long.MAX_VALUE + 1 == -Long.MIN_VALUE, and then offset all comparisons by 1.
   */
  private static final double MAX_LONG_AS_DOUBLE_PLUS_ONE = 0x1p63;

  private static final int SIGNIFICAND_BITS = 52;

  private MathUtils() {}

  @TruffleBoundary
  public static long roundToLong(double x, RoundingMode mode) {
    var z = roundIntermediate(x, mode);
    checkInRange(MIN_LONG_AS_DOUBLE - z < 1.0 & z < MAX_LONG_AS_DOUBLE_PLUS_ONE);
    return (long) z;
  }

  @TruffleBoundary
  public static boolean isMathematicalInteger(double x) {
    return isFinite(x)
        && (x == 0.0
            || SIGNIFICAND_BITS - Long.numberOfTrailingZeros(getSignificand(x))
                <= StrictMath.getExponent(x));
  }

  /**
   * Returns {@code true} if {@code x} represents a power of two.
   *
   * <p>
   *
   * <p>This differs from {@code Long.bitCount(x) == 1}, because {@code
   * Long.bitCount(Long.MIN_VALUE) == 1}, but {@link Long#MIN_VALUE} is not a power of two.
   */
  public static boolean isPowerOfTwo(long x) {
    return x > 0 && (x & (x - 1)) == 0;
  }

  /**
   * Returns {@code true} if {@code x} is exactly equal to {@code 2^k} for some finite integer
   * {@code k}.
   */
  @TruffleBoundary
  public static boolean isPowerOfTwo(double x) {
    if (x > 0.0 && isFinite(x)) {
      long significand = getSignificand(x);
      return (significand & (significand - 1)) == 0;
    }
    return false;
  }

  /**
   * Returns the {@code b} to the {@code k}th power, provided it does not overflow.
   *
   * @throws ArithmeticException if {@code b} to the {@code k}th power overflows in signed {@code
   *     long} arithmetic
   */
  @TruffleBoundary
  public static long checkedPow(long b, int k) {
    checkNonNegative("exponent", k);
    if (b >= -2 & b <= 2) {
      return switch ((int) b) {
        case 0 -> (k == 0) ? 1 : 0;
        case 1 -> 1;
        case (-1) -> ((k & 1) == 0) ? 1 : -1;
        case 2 -> {
          checkNoOverflow(k < Long.SIZE - 1);
          yield 1L << k;
        }
        case (-2) -> {
          checkNoOverflow(k < Long.SIZE);
          yield ((k & 1) == 0) ? (1L << k) : (-1L << k);
        }
        default -> throw new AssertionError();
      };
    }
    long accum = 1;
    while (true) {
      switch (k) {
        case 0 -> {
          return accum;
        }
        case 1 -> {
          return checkedMultiply(accum, b);
        }
        default -> {
          if ((k & 1) != 0) {
            accum = checkedMultiply(accum, b);
          }
          k >>= 1;
          if (k > 0) {
            checkNoOverflow(-FLOOR_SQRT_MAX_LONG <= b && b <= FLOOR_SQRT_MAX_LONG);
            b *= b;
          }
        }
      }
    }
  }

  /**
   * Returns the greatest common divisor of {@code a, b}. Returns {@code 0} if {@code a == 0 && b ==
   * 0}.
   *
   * @throws IllegalArgumentException if {@code a < 0} or {@code b < 0}
   */
  @TruffleBoundary
  public static long gcd(long a, long b) {
    /*
     * The reason we require both arguments to be >= 0 is because otherwise, what do you return on
     * gcd(0, Long.MIN_VALUE)? BigInteger.gcd would return positive 2^63, but positive 2^63 isn't an
     * int.
     */
    checkNonNegative("a", a);
    checkNonNegative("b", b);
    if (a == 0) {
      // 0 % b == 0, so b divides a, but the converse doesn't hold.
      // BigInteger.gcd is consistent with this decision.
      return b;
    } else if (b == 0) {
      return a; // similar logic
    }
    /*
     * Uses the binary GCD algorithm; see http://en.wikipedia.org/wiki/Binary_GCD_algorithm. This is
     * >60% faster than the Euclidean algorithm in benchmarks.
     */
    var aTwos = Long.numberOfTrailingZeros(a);
    a >>= aTwos; // divide out all 2s
    var bTwos = Long.numberOfTrailingZeros(b);
    b >>= bTwos; // divide out all 2s
    while (a != b) { // both a, b are odd
      // The key to the binary GCD algorithm is as follows:
      // Both a and b are odd. Assume a > b; then gcd(a - b, b) = gcd(a, b).
      // But in gcd(a - b, b), a - b is even and b is odd, so we can divide out powers of two.

      // We bend over backwards to avoid branching, adapting a technique from
      // http://graphics.stanford.edu/~seander/bithacks.html#IntegerMinOrMax

      var delta = a - b; // can't overflow, since a and b are nonnegative

      var minDeltaOrZero = delta & (delta >> (Long.SIZE - 1));
      // equivalent to Math.min(delta, 0)

      a = delta - minDeltaOrZero - minDeltaOrZero; // sets a to Math.abs(a - b)
      // a is now nonnegative and even

      b += minDeltaOrZero; // sets b to min(old a, b)
      a >>= Long.numberOfTrailingZeros(a); // divide out all 2s, since 2 doesn't divide b
    }
    return a << Math.min(aTwos, bTwos);
  }

  /**
   * Returns the product of {@code a} and {@code b}, provided it does not overflow.
   *
   * @throws ArithmeticException if {@code a * b} overflows in signed {@code long} arithmetic
   */
  @TruffleBoundary
  public static long checkedMultiply(long a, long b) {
    // Hacker's Delight, Section 2-12
    var leadingZeros =
        Long.numberOfLeadingZeros(a)
            + Long.numberOfLeadingZeros(~a)
            + Long.numberOfLeadingZeros(b)
            + Long.numberOfLeadingZeros(~b);
    /*
     * If leadingZeros > Long.SIZE + 1 it's definitely fine, if it's < Long.SIZE it's definitely
     * bad. We do the leadingZeros check to avoid the division below if at all possible.
     *
     * Otherwise, if b == Long.MIN_VALUE, then the only allowed values of a are 0 and 1. We take
     * care of all a < 0 with their own check, because in particular, the case a == -1 will
     * incorrectly pass the division check below.
     *
     * In all other cases, we check that either a is 0 or the result is consistent with division.
     */
    if (leadingZeros > Long.SIZE + 1) {
      return a * b;
    }
    checkNoOverflow(leadingZeros >= Long.SIZE);
    checkNoOverflow(a >= 0 | b != Long.MIN_VALUE);
    var result = a * b;
    checkNoOverflow(a == 0 || result / a == b);
    return result;
  }

  /*
   * This method returns a value y such that rounding y DOWN (towards zero) gives the same result as
   * rounding x according to the specified mode.
   */
  private static double roundIntermediate(double x, RoundingMode mode) {
    if (!isFinite(x)) {
      throw new ArithmeticException("input is infinite or NaN");
    }
    switch (mode) {
      case UNNECESSARY:
        checkRoundingUnnecessary(isMathematicalInteger(x));
        return x;

      case FLOOR:
        if (x >= 0.0 || isMathematicalInteger(x)) {
          return x;
        } else {
          return (long) x - 1;
        }

      case CEILING:
        if (x <= 0.0 || isMathematicalInteger(x)) {
          return x;
        } else {
          return (long) x + 1;
        }

      case DOWN:
        return x;

      case UP:
        if (isMathematicalInteger(x)) {
          return x;
        } else {
          return (long) x + (x > 0 ? 1 : -1);
        }

      case HALF_EVEN:
        return StrictMath.rint(x);

      case HALF_UP:
        {
          var z = StrictMath.rint(x);
          if (StrictMath.abs(x - z) == 0.5) {
            return x + StrictMath.copySign(0.5, x);
          } else {
            return z;
          }
        }

      case HALF_DOWN:
        {
          var z = StrictMath.rint(x);
          if (StrictMath.abs(x - z) == 0.5) {
            return x;
          } else {
            return z;
          }
        }

      default:
        throw new AssertionError();
    }
  }

  private static long getSignificand(double d) {
    checkArgument(isFinite(d), "not a normal value");
    var exponent = StrictMath.getExponent(d);
    var bits = Double.doubleToRawLongBits(d);
    bits &= SIGNIFICAND_MASK;
    return (exponent == Double.MIN_EXPONENT - 1) ? bits << 1 : bits | IMPLICIT_BIT;
  }

  private static boolean isFinite(double d) {
    return Math.getExponent(d) <= Double.MAX_EXPONENT;
  }

  private static void checkInRange(boolean condition) {
    if (!condition) {
      throw new ArithmeticException("not in range");
    }
  }

  private static void checkRoundingUnnecessary(boolean condition) {
    if (!condition) {
      throw new ArithmeticException("mode was UNNECESSARY, but rounding was necessary");
    }
  }

  private static void checkArgument(boolean expression, @Nullable Object errorMessage) {
    if (!expression) {
      throw new IllegalArgumentException(String.valueOf(errorMessage));
    }
  }

  private static void checkNonNegative(String role, int x) {
    if (x < 0) {
      throw new IllegalArgumentException(role + " (" + x + ") must be >= 0");
    }
  }

  private static void checkNonNegative(@Nullable String role, long x) {
    if (x < 0) {
      throw new IllegalArgumentException(role + " (" + x + ") must be >= 0");
    }
  }

  private static void checkNoOverflow(boolean condition) {
    if (!condition) {
      throw new ArithmeticException("overflow");
    }
  }
}
