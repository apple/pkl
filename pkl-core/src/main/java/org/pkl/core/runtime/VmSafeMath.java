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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import java.math.RoundingMode;
import org.pkl.core.runtime.VmException.ProgramValue;
import org.pkl.core.util.MathUtils;

/**
 * Uses methods from [java.lang.(Strict)Math] where appropriate, which may benefit from special
 * optimization by Graal. To control error messages in a single place (namely here),
 * [ArithmeticException]s thrown by [java.lang.StrictMath] are caught and rethrown.
 */
public final strictfp class VmSafeMath {
  private VmSafeMath() {}

  public static long negate(long x) {
    try {
      return Math.negateExact(x);
    } catch (ArithmeticException e) {
      CompilerDirectives.transferToInterpreter();
      throw intOverflow();
    }
  }

  public static double negate(double x) {
    return -x;
  }

  public static long add(long x, long y) {
    try {
      return StrictMath.addExact(x, y);
    } catch (ArithmeticException e) {
      CompilerDirectives.transferToInterpreter();
      throw intOverflow();
    }
  }

  public static double add(double x, double y) {
    return x + y;
  }

  public static long multiply(long x, long y) {
    try {
      return StrictMath.multiplyExact(x, y);
    } catch (ArithmeticException e) {
      CompilerDirectives.transferToInterpreter();
      throw intOverflow();
    }
  }

  public static double multiply(double x, double y) {
    return x * y;
  }

  public static long truncatingDivide(long x, long y) {
    // for some reason, detecting division by zero by catching ArithmeticException
    // does not work correctly in AOT mode, so let's do an explicit check for now
    if (y == 0) {
      CompilerDirectives.transferToInterpreter();
      throw divisionByZero();
    }

    var result = x / y;

    if ((x & y & result)
        < 0) { // use same check as com.oracle.truffle.sl.nodes.expression.SLDivNode
      CompilerDirectives.transferToInterpreter();
      assert x == Long.MIN_VALUE && y == -1;
      throw intOverflow();
    }

    return result;
  }

  public static long remainder(long x, long y) {
    return x % y;
  }

  public static double remainder(double x, double y) {
    return x % y;
  }

  public static int toInt32(long x) {
    try {
      return StrictMath.toIntExact(x);
    } catch (ArithmeticException e) {
      CompilerDirectives.transferToInterpreter();
      throw new VmExceptionBuilder().evalError("intValueTooLarge", x).build();
    }
  }

  public static double truncate(double x) {
    if (x < 0) {
      return StrictMath.ceil(x);
    }
    return StrictMath.floor(x);
  }

  @TruffleBoundary
  public static long toInt(double x, Node sourceNode) {
    try {
      return MathUtils.roundToLong(x, RoundingMode.DOWN);
    } catch (ArithmeticException e) {
      CompilerDirectives.transferToInterpreter();
      throw new VmExceptionBuilder()
          .withLocation(sourceNode)
          .evalError(
              Double.isFinite(x) ? "cannotConvertLargeFloat" : "cannotConvertNonFiniteFloat",
              new ProgramValue("Float", x))
          .build();
    }
  }

  public static long increment(long x) {
    try {
      return Math.incrementExact(x);
    } catch (ArithmeticException e) {
      CompilerDirectives.transferToInterpreter();
      throw intOverflow();
    }
  }

  public static long decrement(long x) {
    try {
      return Math.decrementExact(x);
    } catch (ArithmeticException e) {
      CompilerDirectives.transferToInterpreter();
      throw intOverflow();
    }
  }

  public static long abs(long x) {
    if (x == Long.MIN_VALUE) {
      CompilerDirectives.transferToInterpreter();
      throw intOverflow();
    }
    return StrictMath.abs(x);
  }

  public static long pow(long x, long y) {
    assert y >= 0;

    // handle cases not covered by GuavaMath.checkedPow
    if (y > Integer.MAX_VALUE) {
      if (x == 0 || x == 1) return x;
      if (x == -1) return y % 2 == 0 ? 1 : -1;

      CompilerDirectives.transferToInterpreter();
      throw intOverflow();
    }

    try {
      return MathUtils.checkedPow(x, (int) y);
    } catch (ArithmeticException e) {
      CompilerDirectives.transferToInterpreter();
      throw intOverflow();
    }
  }

  private static VmException intOverflow() {
    return new VmExceptionBuilder().evalError("integerOverflow").build();
  }

  private static VmException divisionByZero() {
    return new VmExceptionBuilder().evalError("divisionByZero").build();
  }
}
