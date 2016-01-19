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
import org.pkl.core.runtime.*;
import org.pkl.core.util.EconomicMaps;

@ImportStatic({BaseModule.class, GeneratorObjectLiteralNode.class})
public abstract class GeneratorPropertyNode extends GeneratorMemberNode {
  protected final ObjectMember member;

  protected GeneratorPropertyNode(ObjectMember member) {
    super(member.getSourceSection());
    this.member = member;
    assert member.isProp();
  }

  @Specialization
  @SuppressWarnings("unused")
  protected void evalDynamic(VmDynamic parent, ObjectData data) {
    addProperty(data);
  }

  @SuppressWarnings("unused")
  @Specialization(guards = "checkIsValidTypedProperty(parent.getVmClass(), member)")
  protected void evalTyped(VmTyped parent, ObjectData data) {
    addProperty(data);
  }

  @SuppressWarnings("unused")
  @Specialization(guards = "checkIsValidMappingProperty()")
  protected void evalMapping(VmMapping parent, ObjectData data) {
    addProperty(data);
  }

  @SuppressWarnings("unused")
  @Specialization(guards = "checkIsValidListingProperty()")
  protected void evalListing(VmListing parent, ObjectData data) {
    addProperty(data);
  }

  @SuppressWarnings("unused")
  @Specialization(guards = "parent == getDynamicClass()")
  protected void evalDynamicClass(VmClass parent, ObjectData data) {
    addProperty(data);
  }

  @SuppressWarnings("unused")
  @Specialization(guards = {"parent == getMappingClass()", "checkIsValidMappingProperty()"})
  protected void evalMappingClass(VmClass parent, ObjectData data) {
    addProperty(data);
  }

  @SuppressWarnings("unused")
  @Specialization(guards = {"parent == getListingClass()", "checkIsValidListingProperty()"})
  protected void evalListingClass(VmClass parent, ObjectData data) {
    addProperty(data);
  }

  @SuppressWarnings("unused")
  @Specialization(
      guards = {"isTypedObjectClass(parent)", "checkIsValidTypedProperty(parent, member)"})
  protected void evalTypedObjectClass(VmClass parent, ObjectData data) {
    addProperty(data);
  }

  @Fallback
  @SuppressWarnings("unused")
  void fallback(Object parent, ObjectData data) {
    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder()
        .evalError(
            "objectCannotHaveProperty",
            parent instanceof VmClass ? parent : VmUtils.getClass(parent))
        .withSourceSection(member.getHeaderSection())
        .build();
  }

  protected boolean checkIsValidListingProperty() {
    if (member.isLocal() || member.getName() == Identifier.DEFAULT) return true;

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder()
        .evalError("objectCannotHaveProperty", BaseModule.getListingClass())
        .withSourceSection(member.getHeaderSection())
        .build();
  }

  protected boolean checkIsValidMappingProperty() {
    if (member.isLocal() || member.getName() == Identifier.DEFAULT) return true;

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder()
        .evalError("objectCannotHaveProperty", BaseModule.getMappingClass())
        .withSourceSection(member.getHeaderSection())
        .build();
  }

  private void addProperty(ObjectData data) {
    if (EconomicMaps.put(data.members, member.getName(), member) == null) return;

    CompilerDirectives.transferToInterpreter();
    throw duplicateDefinition(member.getName(), member);
  }
}
