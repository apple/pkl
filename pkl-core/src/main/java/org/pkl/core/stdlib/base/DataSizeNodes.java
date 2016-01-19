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

import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.DataSizeUnit;
import org.pkl.core.runtime.VmDataSize;
import org.pkl.core.stdlib.*;
import org.pkl.core.util.MathUtils;

@SuppressWarnings("unused")
public final class DataSizeNodes {
  private DataSizeNodes() {}

  @ImportStatic(MathUtils.class)
  public abstract static class value extends ExternalPropertyNode {
    @Specialization(guards = "isMathematicalInteger(self.getValue())")
    protected long evalInt(VmDataSize self) {
      return (long) self.getValue();
    }

    @Specialization(guards = "!isMathematicalInteger(self.getValue())")
    protected double evalFloat(VmDataSize self) {
      return self.getValue();
    }
  }

  public abstract static class unit extends ExternalPropertyNode {
    @Specialization
    protected String eval(VmDataSize self) {
      return self.getUnit().getSymbol();
    }
  }

  public abstract static class isPositive extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(VmDataSize self) {
      return self.getValue() >= 0;
    }
  }

  public abstract static class isBetween extends ExternalMethod2Node {
    @Specialization
    protected boolean eval(VmDataSize self, VmDataSize start, VmDataSize inclusiveEnd) {
      return self.compareTo(start) >= 0 && self.compareTo(inclusiveEnd) <= 0;
    }
  }

  public abstract static class toUnit extends ExternalMethod1Node {
    @Specialization
    protected VmDataSize eval(VmDataSize self, String str) {
      var unit = DataSizeUnit.parse(str);
      assert unit != null; // guaranteed by Pkl type check
      return self.convertTo(unit);
    }
  }

  public abstract static class isBinaryUnit extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(VmDataSize self) {
      var ordinal = self.getUnit().ordinal();
      return ordinal % 2 == 0;
    }
  }

  public abstract static class isDecimalUnit extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(VmDataSize self) {
      var ordinal = self.getUnit().ordinal();
      return ordinal == 0 || ordinal % 2 == 1;
    }
  }

  public abstract static class toBinaryUnit extends ExternalMethod0Node {
    @Specialization
    protected VmDataSize eval(VmDataSize self) {
      switch (self.getUnit()) {
        case KILOBYTES:
          return self.convertTo(DataSizeUnit.KIBIBYTES);
        case MEGABYTES:
          return self.convertTo(DataSizeUnit.MEBIBYTES);
        case GIGABYTES:
          return self.convertTo(DataSizeUnit.GIBIBYTES);
        case TERABYTES:
          return self.convertTo(DataSizeUnit.TEBIBYTES);
        case PETABYTES:
          return self.convertTo(DataSizeUnit.PEBIBYTES);
        default:
          return self;
      }
    }
  }

  public abstract static class toDecimalUnit extends ExternalMethod0Node {
    @Specialization
    protected VmDataSize eval(VmDataSize self) {
      switch (self.getUnit()) {
        case KIBIBYTES:
          return self.convertTo(DataSizeUnit.KILOBYTES);
        case MEBIBYTES:
          return self.convertTo(DataSizeUnit.MEGABYTES);
        case GIBIBYTES:
          return self.convertTo(DataSizeUnit.GIGABYTES);
        case TEBIBYTES:
          return self.convertTo(DataSizeUnit.TERABYTES);
        case PEBIBYTES:
          return self.convertTo(DataSizeUnit.PETABYTES);
        default:
          return self;
      }
    }
  }
}
