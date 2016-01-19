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
package org.pkl.core.ast.expression.binary;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.runtime.*;

@NodeInfo(shortName = "/")
public abstract class DivisionNode extends BinaryExpressionNode {
  protected DivisionNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  @Specialization
  protected double eval(long left, long right) {
    return (double) left / (double) right;
  }

  @Specialization
  protected double eval(long left, double right) {
    return (double) left / right;
  }

  @Specialization
  protected double eval(double left, long right) {
    return left / right;
  }

  @Specialization
  protected double eval(double left, double right) {
    return left / right;
  }

  @Specialization
  protected VmDuration eval(VmDuration left, long right) {
    return left.divide(right);
  }

  @Specialization
  protected VmDuration eval(VmDuration left, double right) {
    return left.divide(right);
  }

  @Specialization
  protected double eval(VmDuration left, VmDuration right) {
    return left.divide(right);
  }

  @Specialization
  protected VmDataSize eval(VmDataSize left, long right) {
    return left.divide(right);
  }

  @Specialization
  protected VmDataSize eval(VmDataSize left, double right) {
    return left.divide(right);
  }

  @Specialization
  protected double eval(VmDataSize left, VmDataSize right) {
    return left.divide(right);
  }
}
