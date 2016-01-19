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
package org.pkl.core.ast.expression.binary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import java.math.RoundingMode;
import org.pkl.core.runtime.VmDataSize;
import org.pkl.core.runtime.VmDuration;
import org.pkl.core.runtime.VmException.ProgramValue;
import org.pkl.core.util.MathUtils;

@NodeInfo(shortName = "~/")
@SuppressWarnings("SuspiciousNameCombination")
public abstract class TruncatingDivisionNode extends BinaryExpressionNode {
  protected TruncatingDivisionNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  @Specialization
  protected long eval(long left, long right) {
    if (right == 0) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder().evalError("divisionByZero").build();
    }

    var result = left / right;

    // use same check as com.oracle.truffle.sl.nodes.expression.SLDivNode
    if ((left & right & result) < 0) {
      CompilerDirectives.transferToInterpreter();
      assert left == Long.MIN_VALUE && right == -1;
      throw exceptionBuilder().evalError("integerOverflow").build();
    }
    return result;
  }

  @Specialization
  protected long eval(long left, double right) {
    return doTruncatingDivide(left, right);
  }

  @Specialization
  protected long eval(double left, long right) {
    return doTruncatingDivide(left, right);
  }

  @Specialization
  protected long eval(double left, double right) {
    return doTruncatingDivide(left, right);
  }

  @Specialization
  protected VmDuration eval(VmDuration left, long right) {
    var newValue = doTruncatingDivide(left.getValue(), right);
    return new VmDuration(newValue, left.getUnit());
  }

  @Specialization
  protected VmDuration eval(VmDuration left, double right) {
    var newValue = doTruncatingDivide(left.getValue(), right);
    return new VmDuration(newValue, left.getUnit());
  }

  @Specialization
  protected long eval(VmDuration left, VmDuration right) {
    // use same conversion strategy as add/subtract
    if (left.getUnit().ordinal() <= right.getUnit().ordinal()) {
      return doTruncatingDivide(left.getValue(right.getUnit()), right.getValue());
    }
    return doTruncatingDivide(left.getValue(), right.getValue(left.getUnit()));
  }

  @Specialization
  protected VmDataSize eval(VmDataSize left, long right) {
    var value = doTruncatingDivide(left.getValue(), right);
    return new VmDataSize(value, left.getUnit());
  }

  @Specialization
  protected VmDataSize eval(VmDataSize left, double right) {
    var newValue = doTruncatingDivide(left.getValue(), right);
    return new VmDataSize(newValue, left.getUnit());
  }

  @Specialization
  protected long eval(VmDataSize left, VmDataSize right) {
    // use same conversion strategy as add/subtract
    if (left.getUnit().ordinal() <= right.getUnit().ordinal()) {
      var leftValue = left.convertTo(right.getUnit()).getValue();
      return doTruncatingDivide(leftValue, right.getValue());
    }
    var rightValue = right.convertTo(left.getUnit()).getValue();
    return doTruncatingDivide(left.getValue(), rightValue);
  }

  private long doTruncatingDivide(double x, double y) {
    try {
      return MathUtils.roundToLong(x / y, RoundingMode.DOWN);
    } catch (ArithmeticException e) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError(
              Double.isFinite(x) ? "cannotConvertLargeFloat" : "cannotConvertNonFiniteFloat",
              new ProgramValue("Float", x))
          .build();
    }
  }
}
