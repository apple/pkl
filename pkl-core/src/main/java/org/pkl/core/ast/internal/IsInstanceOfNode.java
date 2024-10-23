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
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.ast.PklNode;
import org.pkl.core.runtime.BaseModule;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmValue;

public abstract class IsInstanceOfNode extends PklNode {
  public abstract boolean executeBoolean(Object value, VmClass clazz);

  @Specialization
  protected boolean eval(@SuppressWarnings("unused") String left, VmClass right) {
    return right == BaseModule.getStringClass() || right == BaseModule.getAnyClass();
  }

  @Specialization
  protected boolean eval(@SuppressWarnings("unused") long left, VmClass right) {
    return right == BaseModule.getIntClass()
        || right == BaseModule.getNumberClass()
        || right == BaseModule.getAnyClass();
  }

  @Specialization
  protected boolean eval(@SuppressWarnings("unused") double left, VmClass right) {
    return right == BaseModule.getFloatClass()
        || right == BaseModule.getNumberClass()
        || right == BaseModule.getAnyClass();
  }

  @Specialization
  protected boolean eval(@SuppressWarnings("unused") boolean left, VmClass right) {
    return right == BaseModule.getBooleanClass() || right == BaseModule.getAnyClass();
  }

  /**
   * This method effectively covers `VmValue value` but is implemented in a <a
   * href="https://www.graalvm.org/22.0/graalvm-as-a-platform/language-implementation-framework/TruffleLibraries/#strategy-2-java-interfaces">more
   * efficient way</a>.
   */
  @Specialization(guards = "value.getClass() == valueJavaClass", limit = "99")
  protected boolean evalVmValue(
      Object value,
      VmClass vmClass,
      @Cached("getJavaClass(value)") Class<? extends VmValue> valueJavaClass) {

    VmClass valueVmClass = valueJavaClass.cast(value).getVmClass();
    return vmClass.isSuperclassOf(valueVmClass);
  }

  protected Class<? extends VmValue> getJavaClass(Object value) {
    // OK to perform slow cast here (not a guard).
    // `value instanceof VmValue` is guaranteed because `evalVmValue()`
    // - comes after String/long/double/boolean specializations
    // - has a guard that triggers per-class respecialization
    return ((VmValue) value).getClass();
  }
}
