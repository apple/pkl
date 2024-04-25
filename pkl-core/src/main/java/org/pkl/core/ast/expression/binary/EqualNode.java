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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Nullable;

@NodeInfo(shortName = "==")
@NodeChild(value = "leftNode", type = ExpressionNode.class)
@NodeChild(value = "rightNode", type = ExpressionNode.class)
// not extending BinaryExpressionNode because we don't want the latter's fallback
public abstract class EqualNode extends ExpressionNode {
  protected EqualNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  @Specialization
  protected boolean eval(String left, String right) {
    return left.equals(right);
  }

  @Specialization
  protected boolean eval(long left, long right) {
    return left == right;
  }

  @Specialization
  protected boolean eval(long left, double right) {
    return left == right;
  }

  @Specialization
  protected boolean eval(double left, long right) {
    return left == right;
  }

  @Specialization
  protected boolean eval(double left, double right) {
    return left == right;
  }

  @Specialization
  protected boolean eval(boolean left, boolean right) {
    return left == right;
  }

  /**
   * This method effectively covers `VmValue left, VmValue right` but is implemented in a more
   * efficient way. See:
   * https://www.graalvm.org/22.0/graalvm-as-a-platform/language-implementation-framework/TruffleLibraries/#strategy-2-java-interfaces
   */
  @Specialization(
      guards = {"left.getClass() == leftJavaClass", "right.getClass() == leftJavaClass"},
      limit = "99")
  protected boolean eval(
      Object left,
      Object right,
      @SuppressWarnings("unused") @Cached("getVmValueJavaClassOrNull(left)")
          Class<? extends VmValue> leftJavaClass) {
    return equals(left, right);
  }

  // TODO: Putting the equals call behind a boundary make the above optimization moot.
  // Without the boundary, native-image 22.0 complains that Object.equals is reachable for
  // runtime compilation, but with the above optimization, this isn't actually a problem.
  @TruffleBoundary
  private boolean equals(Object left, Object right) {
    return left.equals(right);
  }

  protected static @Nullable Class<? extends VmValue> getVmValueJavaClassOrNull(Object value) {
    // OK to perform slow cast here (not a guard)
    return value instanceof VmValue vmValue ? vmValue.getClass() : null;
  }

  // covers all remaining cases (else it's a bug)
  @Specialization(guards = "isIncompatibleTypes(left, right)")
  protected boolean eval(
      @SuppressWarnings("unused") Object left, @SuppressWarnings("unused") Object right) {
    return false;
  }

  protected static boolean isIncompatibleTypes(Object left, Object right) {
    var leftClass = left.getClass();
    var rightClass = right.getClass();

    return leftClass == Long.class || leftClass == Double.class
        ? rightClass != Long.class && rightClass != Double.class
        : leftClass != rightClass;
  }
}
