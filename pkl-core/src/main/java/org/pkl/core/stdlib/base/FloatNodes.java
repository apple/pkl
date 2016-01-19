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
package org.pkl.core.stdlib.base;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import org.pkl.core.DataSizeUnit;
import org.pkl.core.DurationUnit;
import org.pkl.core.runtime.VmDataSize;
import org.pkl.core.runtime.VmDuration;
import org.pkl.core.runtime.VmException.ProgramValue;
import org.pkl.core.runtime.VmSafeMath;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.util.MathUtils;

public final class FloatNodes {
  private FloatNodes() {}

  public abstract static class ns extends ExternalPropertyNode {
    @Specialization
    protected VmDuration eval(double self) {
      return new VmDuration(self, DurationUnit.NANOS);
    }
  }

  public abstract static class us extends ExternalPropertyNode {
    @Specialization
    protected VmDuration eval(double self) {
      return new VmDuration(self, DurationUnit.MICROS);
    }
  }

  public abstract static class ms extends ExternalPropertyNode {
    @Specialization
    protected VmDuration eval(double self) {
      return new VmDuration(self, DurationUnit.MILLIS);
    }
  }

  public abstract static class s extends ExternalPropertyNode {
    @Specialization
    protected VmDuration eval(double self) {
      return new VmDuration(self, DurationUnit.SECONDS);
    }
  }

  public abstract static class min extends ExternalPropertyNode {
    @Specialization
    protected VmDuration eval(double self) {
      return new VmDuration(self, DurationUnit.MINUTES);
    }
  }

  public abstract static class h extends ExternalPropertyNode {
    @Specialization
    protected VmDuration eval(double self) {
      return new VmDuration(self, DurationUnit.HOURS);
    }
  }

  public abstract static class d extends ExternalPropertyNode {
    @Specialization
    protected VmDuration eval(double self) {
      return new VmDuration(self, DurationUnit.DAYS);
    }
  }

  public abstract static class b extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(double self) {
      return new VmDataSize(self, DataSizeUnit.BYTES);
    }
  }

  public abstract static class kb extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(double self) {
      return new VmDataSize(self, DataSizeUnit.KILOBYTES);
    }
  }

  public abstract static class kib extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(double self) {
      return new VmDataSize(self, DataSizeUnit.KIBIBYTES);
    }
  }

  public abstract static class mb extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(double self) {
      return new VmDataSize(self, DataSizeUnit.MEGABYTES);
    }
  }

  public abstract static class mib extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(double self) {
      return new VmDataSize(self, DataSizeUnit.MEBIBYTES);
    }
  }

  public abstract static class gb extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(double self) {
      return new VmDataSize(self, DataSizeUnit.GIGABYTES);
    }
  }

  public abstract static class gib extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(double self) {
      return new VmDataSize(self, DataSizeUnit.GIBIBYTES);
    }
  }

  public abstract static class tb extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(double self) {
      return new VmDataSize(self, DataSizeUnit.TERABYTES);
    }
  }

  public abstract static class tib extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(double self) {
      return new VmDataSize(self, DataSizeUnit.TEBIBYTES);
    }
  }

  public abstract static class pb extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(double self) {
      return new VmDataSize(self, DataSizeUnit.PETABYTES);
    }
  }

  public abstract static class pib extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(double self) {
      return new VmDataSize(self, DataSizeUnit.PEBIBYTES);
    }
  }

  public abstract static class sign extends ExternalPropertyNode {
    @Specialization
    protected double eval(double self) {
      return StrictMath.signum(self);
    }
  }

  public abstract static class abs extends ExternalPropertyNode {
    @Specialization
    protected double eval(double self) {
      return StrictMath.abs(self);
    }
  }

  public abstract static class ceil extends ExternalPropertyNode {
    @Specialization
    protected double eval(double self) {
      return StrictMath.ceil(self);
    }
  }

  public abstract static class floor extends ExternalPropertyNode {
    @Specialization
    protected double eval(double self) {
      return StrictMath.floor(self);
    }
  }

  public abstract static class round extends ExternalMethod0Node {
    @Specialization
    protected double eval(double self) {
      return StrictMath.rint(self);
    }
  }

  public abstract static class truncate extends ExternalMethod0Node {
    @Specialization
    protected double eval(double self) {
      return VmSafeMath.truncate(self);
    }
  }

  public abstract static class toInt extends ExternalMethod0Node {
    @Specialization
    protected long eval(double self) {
      try {
        return MathUtils.roundToLong(self, RoundingMode.DOWN);
      } catch (ArithmeticException e) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder()
            .evalError(
                Double.isFinite(self) ? "cannotConvertLargeFloat" : "cannotConvertNonFiniteFloat",
                new ProgramValue("Float", self))
            .build();
      }
    }
  }

  public abstract static class toFloat extends ExternalMethod0Node {
    @Specialization
    protected double eval(double self) {
      return self;
    }
  }

  public abstract static class toString extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected String eval(double self) {
      return Double.toString(self);
    }
  }

  public abstract static class toFixed extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(double self, long fractionDigits) {
      // Guaranteed by Pkl method parameter constraint.
      // 0..20 is what JS implementations of `Number.prototype.toFixed()`
      // and Dart implementations of `num.toStringAsFixed()` must support and seems sufficient.
      // `java.text.DecimalFormat` silently clamps the value to 0..340.
      // `java.lang.String.format()` supports a larger range.
      assert fractionDigits >= 0 && fractionDigits <= 20;

      if (Double.isFinite(self)) {
        // specialization that caches DecimalFormat in ThreadLocal
        // (only) gives 3x speedup in microbenchmark
        DecimalFormat format = VmUtils.createDecimalFormat((int) fractionDigits);
        return format.format(self);
      }

      return Double.toString(self);
    }
  }

  public abstract static class toDuration extends ExternalMethod1Node {
    @Specialization
    protected VmDuration eval(double self, String str) {
      DurationUnit unit = DurationUnit.parse(str);
      assert unit != null; // guaranteed by Pkl type check
      return new VmDuration(self, unit);
    }
  }

  public abstract static class toDataSize extends ExternalMethod1Node {
    @Specialization
    protected VmDataSize eval(double self, String str) {
      DataSizeUnit unit = DataSizeUnit.parse(str);
      assert unit != null; // guaranteed by Pkl type check
      return new VmDataSize(self, unit);
    }
  }

  public abstract static class isPositive extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(double self) {
      return self >= 0;
    }
  }

  public abstract static class isFinite extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(double self) {
      return Double.isFinite(self);
    }
  }

  public abstract static class isInfinite extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(double self) {
      return Double.isInfinite(self);
    }
  }

  public abstract static class isNaN extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(double self) {
      return Double.isNaN(self);
    }
  }

  public abstract static class isNonZero extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(double self) {
      return self != 0;
    }
  }

  public abstract static class isBetween extends ExternalMethod2Node {
    @Specialization
    protected boolean evalIntInt(double self, long start, long inclusiveEnd) {
      return self >= start && self <= inclusiveEnd;
    }

    @Specialization
    protected boolean evalIntFloat(double self, long start, double inclusiveEnd) {
      return self >= start && self <= inclusiveEnd;
    }

    @Specialization
    protected boolean evalFloatInt(double self, double start, long inclusiveEnd) {
      return self >= start && self <= inclusiveEnd;
    }

    @Specialization
    protected boolean evalFloatFloat(double self, double start, double inclusiveEnd) {
      return self >= start && self <= inclusiveEnd;
    }
  }
}
