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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.*;
import org.pkl.core.runtime.VmException.ProgramValue;
import org.pkl.core.util.EconomicMaps;

@ImportStatic(BaseModule.class)
public abstract class GeneratorEntryNode extends GeneratorMemberNode {
  @Child private ExpressionNode keyNode;
  private final ObjectMember member;

  protected GeneratorEntryNode(ExpressionNode keyNode, ObjectMember member) {
    super(member.getSourceSection());
    this.keyNode = keyNode;
    this.member = member;
  }

  @Specialization
  @SuppressWarnings("unused")
  protected void evalDynamic(VirtualFrame frame, VmDynamic parent, ObjectData data) {
    addRegularEntry(frame, data);
  }

  @Specialization
  @SuppressWarnings("unused")
  protected void evalMapping(VirtualFrame frame, VmMapping parent, ObjectData data) {
    addRegularEntry(frame, data);
  }

  @Specialization
  @SuppressWarnings("unused")
  protected void evalListing(VirtualFrame frame, VmListing parent, ObjectData data) {
    addListingEntry(frame, data, parent.getLength());
  }

  @SuppressWarnings("unused")
  @Specialization(guards = "parent == getDynamicClass()")
  protected void evalDynamicClass(VirtualFrame frame, VmClass parent, ObjectData data) {
    addRegularEntry(frame, data);
  }

  @SuppressWarnings("unused")
  @Specialization(guards = "parent == getMappingClass()")
  protected void evalMappingClass(VirtualFrame frame, VmClass parent, ObjectData data) {
    addRegularEntry(frame, data);
  }

  @SuppressWarnings("unused")
  @Specialization(guards = "parent == getListingClass()")
  protected void evalListingClass(VirtualFrame frame, VmClass parent, ObjectData data) {
    // always throws
    addListingEntry(frame, data, 0);
  }

  @Fallback
  @SuppressWarnings("unused")
  void fallback(Object parent, ObjectData data) {
    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder().evalError("objectCannotHaveEntry", parent).build();
  }

  private void addRegularEntry(VirtualFrame frame, ObjectData data) {
    var key = keyNode.executeGeneric(frame);
    doAdd(key, data);
  }

  private void addListingEntry(VirtualFrame frame, ObjectData data, int parentLength) {
    long index;
    try {
      index = keyNode.executeInt(frame);
    } catch (UnexpectedResultException e) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("wrongListingKeyType", new ProgramValue("", VmUtils.getClass(e.getResult())))
          .withLocation(keyNode)
          .build();
    }

    // use same error messages as in checkIsValidListingAmendment and checkMaxListingMemberIndex
    if (index < 0 || index >= parentLength) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("elementIndexOutOfRange", index, 0, parentLength - 1)
          .withLocation(keyNode)
          .build();
    }

    doAdd(index, data);
  }

  private void doAdd(Object key, ObjectData data) {
    if (EconomicMaps.put(data.members, key, member) != null) {
      CompilerDirectives.transferToInterpreter();
      throw duplicateDefinition(key, member);
    }

    data.persistForBindings(key);
  }
}
