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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.runtime.VmDataSize;
import org.pkl.core.runtime.VmDuration;

@NodeInfo(shortName = ">")
public abstract class GreaterThanNode extends ComparatorNode {
  protected GreaterThanNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  @Specialization
  @TruffleBoundary
  protected boolean eval(String left, String right) {
    return left.compareTo(right) > 0;
  }

  @Specialization
  protected boolean eval(long left, long right) {
    return left > right;
  }

  @Specialization
  protected boolean eval(long left, double right) {
    return left > right;
  }

  @Specialization
  protected boolean eval(double left, long right) {
    return left > right;
  }

  @Specialization
  protected boolean eval(double left, double right) {
    return left > right;
  }

  @Specialization
  protected boolean eval(VmDuration left, VmDuration right) {
    return left.compareTo(right) > 0;
  }

  @Specialization
  protected boolean eval(VmDataSize left, VmDataSize right) {
    return left.compareTo(right) > 0;
  }
}
