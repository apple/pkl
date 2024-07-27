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
package org.pkl.core.ast.expression.generator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.builder.SymbolTable.CustomThisScope;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.*;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.EconomicSets;

public abstract class GeneratorPredicateMemberNode extends GeneratorMemberNode {
  @Child private ExpressionNode predicateNode;
  private final ObjectMember member;

  @CompilationFinal private int customThisSlot = -1;

  protected GeneratorPredicateMemberNode(ExpressionNode predicateNode, ObjectMember member) {
    super(member.getSourceSection());
    this.predicateNode = predicateNode;
    this.member = member;
  }

  @Specialization
  @SuppressWarnings("unused")
  protected void evalDynamic(VirtualFrame frame, VmDynamic parent, ObjectData data) {
    addMembers(frame, parent, data);
  }

  @Specialization
  @SuppressWarnings("unused")
  protected void evalMapping(VirtualFrame frame, VmMapping parent, ObjectData data) {
    addMembers(frame, parent, data);
  }

  @Specialization
  @SuppressWarnings("unused")
  protected void evalListing(VirtualFrame frame, VmListing parent, ObjectData data) {
    addMembers(frame, parent, data);
  }

  @Fallback
  @SuppressWarnings("unused")
  void fallback(Object parent, ObjectData data) {
    if (parent == BaseModule.getDynamicClass()
        || parent == BaseModule.getMappingClass()
        || parent == BaseModule.getListingClass()) {
      // nothing to do (parent is guaranteed to have zero elements/entries)
      return;
    }

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder()
        .evalError(
            "objectCannotHavePredicateMember",
            parent instanceof VmClass ? parent : VmUtils.getClass(parent))
        .withLocation(predicateNode)
        .build();
  }

  private void addMembers(VirtualFrame frame, VmObject parent, ObjectData data) {
    initThisSlot(frame);

    var previousValue = frame.getAuxiliarySlot(customThisSlot);
    var visitedKeys = EconomicSets.create();

    // do our own traversal instead of relying on `VmAbstractObject.force/iterateMemberValues`
    // (more efficient and we don't want to execute `predicateNode` behind Truffle boundary)
    for (var owner = parent; owner != null; owner = owner.getParent()) {
      var entries = EconomicMaps.getEntries(owner.getMembers());
      while (entries.advance()) {
        var key = entries.getKey();
        if (!EconomicSets.add(visitedKeys, key)) continue;

        var member = entries.getValue();
        if (member.isProp() || member.isLocal()) continue;

        var value = owner.getCachedValue(key);
        if (value == null) {
          var constantValue = member.getConstantValue();
          if (constantValue != null) {
            value = constantValue;
          } else {
            var callTarget = member.getCallTarget();
            value = callTarget.call(parent, owner, key);
          }
          owner.setCachedValue(key, value, member);
        }

        frame.setAuxiliarySlot(customThisSlot, value);

        try {
          var isApplicable = predicateNode.executeBoolean(frame);
          if (isApplicable) doAdd(key, data);
        } catch (UnexpectedResultException e) {
          CompilerDirectives.transferToInterpreter();
          throw exceptionBuilder()
              .typeMismatch(e.getResult(), BaseModule.getBooleanClass())
              .withLocation(predicateNode)
              .build();
        }
      }
    }

    // restore previous value
    // handles the (pathetic) case of a predicate containing an object with another predicate
    frame.setAuxiliarySlot(customThisSlot, previousValue);
  }

  private void initThisSlot(VirtualFrame frame) {
    if (customThisSlot == -1) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      // deferred until execution time s.t. nodes of inlined type aliases get the right frame slot
      customThisSlot =
          frame.getFrameDescriptor().findOrAddAuxiliarySlot(CustomThisScope.FRAME_SLOT_ID);
    }
  }

  private void doAdd(Object key, ObjectData data) {
    if (EconomicMaps.put(data.members, key, member) != null) {
      CompilerDirectives.transferToInterpreter();
      throw duplicateDefinition(key, member);
    }

    data.persistForBindings(key);
  }
}
