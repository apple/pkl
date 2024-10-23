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
package org.pkl.core.stdlib.base;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.DurationUnit;
import org.pkl.core.runtime.VmDuration;
import org.pkl.core.stdlib.*;
import org.pkl.core.util.DurationUtils;
import org.pkl.core.util.MathUtils;

@SuppressWarnings("unused")
public final class DurationNodes {
  private DurationNodes() {}

  @ImportStatic(MathUtils.class)
  public abstract static class value extends ExternalPropertyNode {
    @Specialization(guards = "isMathematicalInteger(self.getValue())")
    protected long evalInt(VmDuration self) {
      return (long) self.getValue();
    }

    @Specialization(guards = "!isMathematicalInteger(self.getValue())")
    protected double evalFloat(VmDuration self) {
      return self.getValue();
    }
  }

  public abstract static class isoString extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected String eval(VmDuration self) {
      try {
        return DurationUtils.toIsoString(self.getValue(), self.getUnit());
      } catch (ArithmeticException e) {
        throw exceptionBuilder().evalError("cannotConvertToIsoDuration", self).build();
      }
    }
  }

  public abstract static class unit extends ExternalPropertyNode {
    @Specialization
    protected String eval(VmDuration self) {
      return self.getUnit().getSymbol();
    }
  }

  public abstract static class isPositive extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(VmDuration self) {
      return self.getValue() >= 0;
    }
  }

  public abstract static class isBetween extends ExternalMethod2Node {
    @Specialization
    protected boolean eval(VmDuration self, VmDuration start, VmDuration inclusiveEnd) {
      return self.compareTo(start) >= 0 && self.compareTo(inclusiveEnd) <= 0;
    }
  }

  public abstract static class toUnit extends ExternalMethod1Node {
    @Specialization
    protected VmDuration eval(VmDuration self, String str) {
      DurationUnit unit = DurationUnit.parse(str);
      assert unit != null; // guaranteed by Pkl type check
      return self.convertTo(unit);
    }
  }
}
