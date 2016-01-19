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
package org.pkl.core.ast.expression.generator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.BaseModule;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmDynamic;
import org.pkl.core.runtime.VmListing;
import org.pkl.core.util.EconomicMaps;

@ImportStatic(BaseModule.class)
public abstract class GeneratorElementNode extends GeneratorMemberNode {
  private final ObjectMember element;

  protected GeneratorElementNode(ObjectMember element) {
    super(element.getSourceSection());
    this.element = element;
  }

  @Specialization
  @SuppressWarnings("unused")
  protected void evalDynamic(VmDynamic parent, ObjectData data) {
    addElement(data);
  }

  @Specialization
  @SuppressWarnings("unused")
  protected void evalListing(VmListing parent, ObjectData data) {
    addElement(data);
  }

  @SuppressWarnings("unused")
  @Specialization(guards = "parent == getDynamicClass()")
  protected void evalDynamicClass(VmClass parent, ObjectData data) {
    addElement(data);
  }

  @SuppressWarnings("unused")
  @Specialization(guards = "parent == getListingClass()")
  protected void evalListingClass(VmClass parent, ObjectData data) {
    addElement(data);
  }

  @Fallback
  @SuppressWarnings("unused")
  void fallback(Object parent, ObjectData data) {
    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder().evalError("objectCannotHaveElement", parent).build();
  }

  private void addElement(ObjectData data) {
    long index = data.length;
    EconomicMaps.put(data.members, index, element);
    data.length += 1;
    data.persistForBindings(index);
  }
}
