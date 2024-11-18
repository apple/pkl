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
package org.pkl.core.ast.internal;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.*;

// NOTE: needs to be kept in sync with VmUtils.getClass()
@NodeChild(value = "valueNode", type = ExpressionNode.class)
public abstract class GetClassNode extends ExpressionNode {
  protected GetClassNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  protected GetClassNode() {
    super();
  }

  /**
   * When only using this execute method, pass `null` for `valueNode` to {@link
   * GetClassNodeGen#create}.
   */
  public abstract VmClass executeWith(VirtualFrame frame, Object value);

  @Specialization
  protected VmClass evalString(@SuppressWarnings("unused") String value) {
    return BaseModule.getStringClass();
  }

  @Specialization
  protected VmClass evalInt(@SuppressWarnings("unused") long value) {
    return BaseModule.getIntClass();
  }

  @Specialization
  protected VmClass evalFloat(@SuppressWarnings("unused") double value) {
    return BaseModule.getFloatClass();
  }

  @Specialization
  protected VmClass evalBoolean(@SuppressWarnings("unused") boolean value) {
    return BaseModule.getBooleanClass();
  }

  // This method effectively covers `VmValue value` but is implemented in a more efficient way.
  // See:
  // https://www.graalvm.org/22.0/graalvm-as-a-platform/language-implementation-framework/TruffleLibraries/#strategy-2-java-interfaces
  @Specialization(guards = "value.getClass() == cachedClass", limit = "99")
  protected VmClass evalVmValue(
      Object value, @Cached("getValueClass(value)") Class<? extends VmValue> cachedClass) {
    return cachedClass.cast(value).getVmClass();
  }

  protected static Class<? extends VmValue> getValueClass(Object value) {
    // OK to perform slow cast here (not a guard).
    // `value instanceof VmValue` is guaranteed because `evalVmValue()`
    // - comes after String/long/double/boolean specializations
    // - has a guard that triggers per-class respecialization
    return ((VmValue) value).getClass();
  }
}
