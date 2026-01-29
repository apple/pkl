/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.stdlib.math;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.util.MathUtils;

// implementation notes:
// according to graal folks, it shouldn't be necessary to put
// java.lang.Math calls behind a @TruffleBoundary, and doing so
// could prevent Graal from applying its java.lang.Math intrinsics
// for now we assume the same holds for java.lang.StrictMath
@SuppressWarnings("unused")
public final class MathNodes {
  private static final double LN_2 = StrictMath.log(2);

  private MathNodes() {}

  public abstract static class minInt extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmTyped self) {
      return Long.MIN_VALUE;
    }
  }

  public abstract static class maxInt extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmTyped self) {
      return Long.MAX_VALUE;
    }
  }

  public abstract static class minInt8 extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmTyped self) {
      return Byte.MIN_VALUE;
    }
  }

  public abstract static class maxInt8 extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmTyped self) {
      return Byte.MAX_VALUE;
    }
  }

  public abstract static class minInt16 extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmTyped self) {
      return Short.MIN_VALUE;
    }
  }

  public abstract static class maxInt16 extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmTyped self) {
      return Short.MAX_VALUE;
    }
  }

  public abstract static class minInt32 extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmTyped self) {
      return Integer.MIN_VALUE;
    }
  }

  public abstract static class maxInt32 extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmTyped self) {
      return Integer.MAX_VALUE;
    }
  }

  public abstract static class maxUInt extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmTyped self) {
      return Long.MAX_VALUE;
    }
  }

  public abstract static class maxUInt8 extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmTyped self) {
      return 255;
    }
  }

  public abstract static class maxUInt16 extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmTyped self) {
      return 256L * 256 - 1;
    }
  }

  public abstract static class maxUInt32 extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmTyped self) {
      return 256L * 256 * 256 * 256 - 1;
    }
  }

  public abstract static class minFiniteFloat extends ExternalPropertyNode {
    @Specialization
    protected double eval(VmTyped self) {
      return -Double.MAX_VALUE;
    }
  }

  public abstract static class maxFiniteFloat extends ExternalPropertyNode {
    @Specialization
    protected double eval(VmTyped self) {
      return Double.MAX_VALUE;
    }
  }

  public abstract static class minPositiveFloat extends ExternalPropertyNode {
    @Specialization
    protected double eval(VmTyped self) {
      return Double.MIN_VALUE;
    }
  }

  public abstract static class e extends ExternalPropertyNode {
    @Specialization
    protected double eval(VmTyped self) {
      return StrictMath.E;
    }
  }

  public abstract static class pi extends ExternalPropertyNode {
    @Specialization
    protected double eval(VmTyped self) {
      return StrictMath.PI;
    }
  }

  public abstract static class exp extends ExternalMethod1Node {
    @Specialization
    protected double eval(VmTyped self, long x) {
      return StrictMath.exp(x);
    }

    @Specialization
    protected double eval(VmTyped self, double x) {
      return StrictMath.exp(x);
    }
  }

  public abstract static class sqrt extends ExternalMethod1Node {
    @Specialization
    protected double eval(VmTyped self, long x) {
      return StrictMath.sqrt(x);
    }

    @Specialization
    protected double eval(VmTyped self, double x) {
      return StrictMath.sqrt(x);
    }
  }

  public abstract static class cbrt extends ExternalMethod1Node {
    @Specialization
    protected double eval(VmTyped self, long x) {
      return StrictMath.cbrt(x);
    }

    @Specialization
    protected double eval(VmTyped self, double x) {
      return StrictMath.cbrt(x);
    }
  }

  public abstract static class log extends ExternalMethod1Node {
    @Specialization
    protected double eval(VmTyped self, long x) {
      return StrictMath.log(x);
    }

    @Specialization
    protected double eval(VmTyped self, double x) {
      return StrictMath.log(x);
    }
  }

  public abstract static class log2 extends ExternalMethod1Node {
    @Specialization
    protected double eval(VmTyped self, long x) {
      // based on com.google.common.math.DoubleMath.log2, but uses StrictMath
      return StrictMath.log(x) / LN_2;
    }

    @Specialization
    protected double eval(VmTyped self, double x) {
      // based on com.google.common.math.DoubleMath.log2, but uses StrictMath
      return StrictMath.log(x) / LN_2;
    }
  }

  public abstract static class log10 extends ExternalMethod1Node {
    @Specialization
    protected double eval(VmTyped self, long x) {
      return StrictMath.log10(x);
    }

    @Specialization
    protected double eval(VmTyped self, double x) {
      return StrictMath.log10(x);
    }
  }

  public abstract static class sin extends ExternalMethod1Node {
    @Specialization
    protected double eval(VmTyped self, long x) {
      return StrictMath.sin(x);
    }

    @Specialization
    protected double eval(VmTyped self, double x) {
      return StrictMath.sin(x);
    }
  }

  public abstract static class cos extends ExternalMethod1Node {
    @Specialization
    protected double eval(VmTyped self, long x) {
      return StrictMath.cos(x);
    }

    @Specialization
    protected double eval(VmTyped self, double x) {
      return StrictMath.cos(x);
    }
  }

  public abstract static class tan extends ExternalMethod1Node {
    @Specialization
    protected double eval(VmTyped self, long x) {
      return StrictMath.tan(x);
    }

    @Specialization
    protected double eval(VmTyped self, double x) {
      return StrictMath.tan(x);
    }
  }

  public abstract static class asin extends ExternalMethod1Node {
    @Specialization
    protected double eval(VmTyped self, long x) {
      return StrictMath.asin(x);
    }

    @Specialization
    protected double eval(VmTyped self, double x) {
      return StrictMath.asin(x);
    }
  }

  public abstract static class acos extends ExternalMethod1Node {
    @Specialization
    protected double eval(VmTyped self, long x) {
      return StrictMath.acos(x);
    }

    @Specialization
    protected double eval(VmTyped self, double x) {
      return StrictMath.acos(x);
    }
  }

  public abstract static class atan extends ExternalMethod1Node {
    @Specialization
    protected double eval(VmTyped self, long x) {
      return StrictMath.atan(x);
    }

    @Specialization
    protected double eval(VmTyped self, double x) {
      return StrictMath.atan(x);
    }
  }

  public abstract static class atan2 extends ExternalMethod2Node {
    @Specialization
    protected double eval(VmTyped self, long x, long y) {
      return StrictMath.atan2(x, y);
    }

    @Specialization
    protected double eval(VmTyped self, long x, double y) {
      return StrictMath.atan2(x, y);
    }

    @Specialization
    protected double eval(VmTyped self, double x, double y) {
      return StrictMath.atan2(x, y);
    }

    @Specialization
    protected double eval(VmTyped self, double x, long y) {
      return StrictMath.atan2(x, y);
    }
  }

  public abstract static class gcd extends ExternalMethod2Node {
    @TruffleBoundary
    @Specialization
    protected long eval(VmTyped self, long x, long y) {
      VmUtils.checkPositive(x);
      VmUtils.checkPositive(y);

      return MathUtils.gcd(x, y);
    }
  }

  public abstract static class lcm extends ExternalMethod2Node {
    @TruffleBoundary
    @Specialization
    protected long eval(VmTyped self, long x, long y) {
      VmUtils.checkPositive(x);
      VmUtils.checkPositive(y);

      if (x == 0 || y == 0) return 0;

      return VmSafeMath.abs(VmSafeMath.multiply(x / MathUtils.gcd(x, y), y));
    }
  }

  public abstract static class isPowerOfTwo extends ExternalMethod1Node {
    @Specialization
    protected boolean eval(VmTyped self, long x) {
      return MathUtils.isPowerOfTwo(x);
    }

    @TruffleBoundary
    @Specialization
    protected boolean eval(VmTyped self, double x) {
      return MathUtils.isPowerOfTwo(x);
    }
  }

  public abstract static class min extends ExternalMethod2Node {
    @Specialization
    protected long eval(VmTyped self, long x, long y) {
      return StrictMath.min(x, y);
    }

    @Specialization
    protected double eval(VmTyped self, long x, double y) {
      return StrictMath.min(x, y);
    }

    @Specialization
    protected double eval(VmTyped self, double x, long y) {
      return StrictMath.min(x, y);
    }

    @Specialization
    protected double eval(VmTyped self, double x, double y) {
      return StrictMath.min(x, y);
    }
  }

  public abstract static class max extends ExternalMethod2Node {
    @Specialization
    protected long eval(VmTyped self, long x, long y) {
      return StrictMath.max(x, y);
    }

    @Specialization
    protected double eval(VmTyped self, long x, double y) {
      return StrictMath.max(x, y);
    }

    @Specialization
    protected double eval(VmTyped self, double x, long y) {
      return StrictMath.max(x, y);
    }

    @Specialization
    protected double eval(VmTyped self, double x, double y) {
      return StrictMath.max(x, y);
    }
  }
}
