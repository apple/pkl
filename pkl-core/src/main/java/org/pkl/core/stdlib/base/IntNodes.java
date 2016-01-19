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
package org.pkl.core.stdlib.base;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.DataSizeUnit;
import org.pkl.core.DurationUnit;
import org.pkl.core.runtime.VmDataSize;
import org.pkl.core.runtime.VmDuration;
import org.pkl.core.runtime.VmSafeMath;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.ExternalPropertyNode;

public final class IntNodes {
  private IntNodes() {}

  public abstract static class ns extends ExternalPropertyNode {
    @Specialization
    protected VmDuration eval(long self) {
      return new VmDuration(self, DurationUnit.NANOS);
    }
  }

  public abstract static class us extends ExternalPropertyNode {
    @Specialization
    protected VmDuration eval(long self) {
      return new VmDuration(self, DurationUnit.MICROS);
    }
  }

  public abstract static class ms extends ExternalPropertyNode {
    @Specialization
    protected VmDuration eval(long self) {
      return new VmDuration(self, DurationUnit.MILLIS);
    }
  }

  public abstract static class s extends ExternalPropertyNode {
    @Specialization
    protected VmDuration eval(long self) {
      return new VmDuration(self, DurationUnit.SECONDS);
    }
  }

  public abstract static class min extends ExternalPropertyNode {
    @Specialization
    protected VmDuration eval(long self) {
      return new VmDuration(self, DurationUnit.MINUTES);
    }
  }

  public abstract static class h extends ExternalPropertyNode {
    @Specialization
    protected VmDuration eval(long self) {
      return new VmDuration(self, DurationUnit.HOURS);
    }
  }

  public abstract static class d extends ExternalPropertyNode {
    @Specialization
    protected VmDuration eval(long self) {
      return new VmDuration(self, DurationUnit.DAYS);
    }
  }

  public abstract static class b extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(long self) {
      return new VmDataSize(self, DataSizeUnit.BYTES);
    }
  }

  public abstract static class kb extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(long self) {
      return new VmDataSize(self, DataSizeUnit.KILOBYTES);
    }
  }

  public abstract static class kib extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(long self) {
      return new VmDataSize(self, DataSizeUnit.KIBIBYTES);
    }
  }

  public abstract static class mb extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(long self) {
      return new VmDataSize(self, DataSizeUnit.MEGABYTES);
    }
  }

  public abstract static class mib extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(long self) {
      return new VmDataSize(self, DataSizeUnit.MEBIBYTES);
    }
  }

  public abstract static class gb extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(long self) {
      return new VmDataSize(self, DataSizeUnit.GIGABYTES);
    }
  }

  public abstract static class gib extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(long self) {
      return new VmDataSize(self, DataSizeUnit.GIBIBYTES);
    }
  }

  public abstract static class tb extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(long self) {
      return new VmDataSize(self, DataSizeUnit.TERABYTES);
    }
  }

  public abstract static class tib extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(long self) {
      return new VmDataSize(self, DataSizeUnit.TEBIBYTES);
    }
  }

  public abstract static class pb extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(long self) {
      return new VmDataSize(self, DataSizeUnit.PETABYTES);
    }
  }

  public abstract static class pib extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(long self) {
      return new VmDataSize(self, DataSizeUnit.PEBIBYTES);
    }
  }

  public abstract static class sign extends ExternalPropertyNode {
    @Specialization
    protected long eval(long self) {
      return Long.signum(self);
    }
  }

  public abstract static class abs extends ExternalPropertyNode {
    @Specialization
    protected long eval(long self) {
      return VmSafeMath.abs(self);
    }
  }

  public abstract static class ceil extends ExternalPropertyNode {
    @Specialization
    protected long eval(long self) {
      return self;
    }
  }

  public abstract static class floor extends ExternalPropertyNode {
    @Specialization
    protected long eval(long self) {
      return self;
    }
  }

  public abstract static class toRadixString extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(long self, long radix) {
      return (self < 0 ? "-" : "") + Long.toString(Math.abs(self), (int) radix);
    }
  }

  public abstract static class round extends ExternalMethod0Node {
    @Specialization
    protected long eval(long self) {
      return self;
    }
  }

  public abstract static class truncate extends ExternalMethod0Node {
    @Specialization
    protected long eval(long self) {
      return self;
    }
  }

  public abstract static class toInt extends ExternalMethod0Node {
    @Specialization
    protected long eval(long self) {
      return self;
    }
  }

  public abstract static strictfp class toFloat extends ExternalMethod0Node {
    @Specialization
    protected double eval(long self) {
      return self;
    }
  }

  public abstract static class toString extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected String eval(long self) {
      return Long.toString(self);
    }
  }

  public abstract static class toFixed extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(long self, long fractionDigits) {
      // see comment in FloatNodes.toFixed()
      assert fractionDigits >= 0 && fractionDigits <= 20;

      var format = VmUtils.createDecimalFormat((int) fractionDigits);
      return format.format(self);
    }
  }

  public abstract static class toDuration extends ExternalMethod1Node {
    @Specialization
    protected VmDuration eval(long self, String str) {
      DurationUnit unit = DurationUnit.parse(str);
      assert unit != null; // guaranteed by Pkl type check
      return new VmDuration(self, unit);
    }
  }

  public abstract static class toDataSize extends ExternalMethod1Node {
    @Specialization
    protected VmDataSize eval(long self, String str) {
      DataSizeUnit unit = DataSizeUnit.parse(str);
      assert unit != null; // guaranteed by Pkl type check
      return new VmDataSize(self, unit);
    }
  }

  public abstract static class shl extends ExternalMethod1Node {
    @Specialization
    protected long eval(long self, long n) {
      return self << n;
    }
  }

  public abstract static class shr extends ExternalMethod1Node {
    @Specialization
    protected long eval(long self, long n) {
      return self >> n;
    }
  }

  public abstract static class ushr extends ExternalMethod1Node {
    @Specialization
    protected long eval(long self, long n) {
      return self >>> n;
    }
  }

  public abstract static class and extends ExternalMethod1Node {
    @Specialization
    protected long eval(long self, long n) {
      return self & n;
    }
  }

  public abstract static class or extends ExternalMethod1Node {
    @Specialization
    protected long eval(long self, long n) {
      return self | n;
    }
  }

  public abstract static class xor extends ExternalMethod1Node {
    @Specialization
    protected long eval(long self, long n) {
      return self ^ n;
    }
  }

  public abstract static class inv extends ExternalPropertyNode {
    @Specialization
    protected long eval(long self) {
      return ~self;
    }
  }

  public abstract static class isPositive extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(long self) {
      return self >= 0;
    }
  }

  public abstract static class isFinite extends ExternalPropertyNode {
    @Specialization
    @SuppressWarnings("UnusedParameters")
    protected boolean eval(long self) {
      return true;
    }
  }

  public abstract static class isInfinite extends ExternalPropertyNode {
    @Specialization
    @SuppressWarnings("UnusedParameters")
    protected boolean eval(long self) {
      return false;
    }
  }

  public abstract static class isNaN extends ExternalPropertyNode {
    @Specialization
    @SuppressWarnings("UnusedParameters")
    protected boolean eval(long self) {
      return false;
    }
  }

  public abstract static class isEven extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(long self) {
      return (self & 1) == 0;
    }
  }

  public abstract static class isOdd extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(long self) {
      return (self & 1) != 0;
    }
  }

  public abstract static class isNonZero extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(long self) {
      return self != 0;
    }
  }

  public abstract static class isBetween extends ExternalMethod2Node {
    @Specialization
    protected boolean evalIntInt(long self, long start, long inclusiveEnd) {
      return self >= start && self <= inclusiveEnd;
    }

    @Specialization
    protected boolean evalIntFloat(long self, long start, double inclusiveEnd) {
      return self >= start && self <= inclusiveEnd;
    }

    @Specialization
    protected boolean evalFloatInt(long self, double start, long inclusiveEnd) {
      return self >= start && self <= inclusiveEnd;
    }

    @Specialization
    protected boolean evalFloatFloat(long self, double start, double inclusiveEnd) {
      return self >= start && self <= inclusiveEnd;
    }
  }

  public abstract static class toChar extends ExternalMethod0Node {
    @Specialization
    protected String eval(long self) {
      var codePoint = (int) self;
      if (codePoint == self && Character.isValidCodePoint(codePoint)) {
        return Character.toString(codePoint);
      }

      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder().evalError("invalidCodePoint", self).build();
    }
  }
}
