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

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.runtime.VmDataSize;
import org.pkl.core.runtime.VmDuration;
import org.pkl.core.runtime.VmSafeMath;

@NodeInfo(shortName = "**")
public abstract class ExponentiationNode extends BinaryExpressionNode {
  public ExponentiationNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  @Specialization(guards = "y >= 0")
  protected long evalPositive(long x, long y) {
    return VmSafeMath.pow(x, y);
  }

  @Specialization(guards = "y < 0")
  protected double evalNegative(long x, long y) {
    return StrictMath.pow(x, y);
  }

  @Specialization
  protected double eval(long x, double y) {
    return StrictMath.pow(x, y);
  }

  @Specialization
  protected double eval(double x, long y) {
    return StrictMath.pow(x, y);
  }

  @Specialization
  protected double eval(double x, double y) {
    return StrictMath.pow(x, y);
  }

  @Specialization
  protected VmDuration eval(VmDuration x, long y) {
    return x.pow(y);
  }

  @Specialization
  protected VmDuration eval(VmDuration x, double y) {
    return x.pow(y);
  }

  @Specialization
  protected VmDataSize eval(VmDataSize x, long y) {
    return x.pow(y);
  }

  @Specialization
  protected VmDataSize eval(VmDataSize x, double y) {
    return x.pow(y);
  }
}
