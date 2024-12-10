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
package org.pkl.core.ast.expression.generator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.BaseModule;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmDynamic;
import org.pkl.core.runtime.VmListing;

@ImportStatic(BaseModule.class)
public abstract class GeneratorElementNode extends GeneratorMemberNode {
  private final ObjectMember element;

  protected GeneratorElementNode(ObjectMember element, boolean isFrameStored) {
    super(element.getSourceSection(), isFrameStored);
    this.element = element;
  }

  @Specialization
  @SuppressWarnings("unused")
  protected void evalDynamic(VirtualFrame frame, VmDynamic parent, ObjectData data) {
    data.addElement(frame, element, this);
  }

  @Specialization
  @SuppressWarnings("unused")
  protected void evalListing(VirtualFrame frame, VmListing parent, ObjectData data) {
    data.addElement(frame, element, this);
  }

  @SuppressWarnings("unused")
  @Specialization(guards = "parent == getDynamicClass()")
  protected void evalDynamicClass(VirtualFrame frame, VmClass parent, ObjectData data) {
    data.addElement(frame, element, this);
  }

  @SuppressWarnings("unused")
  @Specialization(guards = "parent == getListingClass()")
  protected void evalListingClass(VirtualFrame frame, VmClass parent, ObjectData data) {
    data.addElement(frame, element, this);
  }

  @Fallback
  @SuppressWarnings("unused")
  void fallback(VirtualFrame frame, Object parent, ObjectData data) {
    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder().evalError("objectCannotHaveElement", parent).build();
  }
}
