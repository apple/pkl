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
package org.pkl.core.ast.expression.literal;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.runtime.VmException.ProgramValue;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.Nullable;

/**
 * Base class for object literal nodes specialized for a certain mix of property/entry/element
 * definitions. The motivation for this specialization is to do as much object construction and
 * validation work as possible only once or a few times (via `@Cached`) per object literal, instead
 * of repeating it for every object instantiation.
 */
public abstract class SpecializedObjectLiteralNode extends ObjectLiteralNode {
  public SpecializedObjectLiteralNode(
      SourceSection sourceSection,
      VmLanguage language,
      String qualifiedScopeName,
      boolean isCustomThisScope,
      @Nullable FrameDescriptor parametersDescriptor,
      UnresolvedTypeNode[] parameterTypes,
      UnmodifiableEconomicMap<Object, ObjectMember> members) {

    super(
        sourceSection,
        language,
        qualifiedScopeName,
        isCustomThisScope,
        parametersDescriptor,
        parameterTypes);
    this.members = members;
  }

  protected final UnmodifiableEconomicMap<Object, ObjectMember> members;

  @CompilationFinal protected long maxListingMemberIndex = Long.MIN_VALUE;
  @CompilationFinal private boolean checkedIsValidMappingAmendment;

  // only runs once per VmClass (which often means once per PropertiesLiteralNode)
  // unless an XYZUncached specialization is active
  @SuppressWarnings("ExtractMethodRecommender")
  @TruffleBoundary
  @Idempotent
  protected boolean checkIsValidTypedAmendment(Object parent) {
    var parentClass = parent instanceof VmClass vmClass ? vmClass : VmUtils.getClass(parent);
    VmUtils.checkIsInstantiable(parentClass, getParentNode());

    for (var member : EconomicMaps.getValues(members)) {
      if (member.isLocal()) continue;

      var memberName = member.getName();
      if (!parentClass.hasProperty(memberName)) {
        throw exceptionBuilder()
            .cannotFindProperty(parentClass.getPrototype(), memberName, false, false)
            .withSourceSection(member.getHeaderSection())
            .build();
      }
      var classProperty = parentClass.getProperty(memberName);
      if (classProperty != null && classProperty.isConstOrFixed()) {
        // tailor error message based on whether an amends declaration is used or not
        // (i.e. `friends {}` vs. `friends = new {}`)
        // an amends declaration's body section includes the header section, whereas normal property
        // assignment's body section is the section after the equal sign.
        var isAmendsDeclaration =
            member.getHeaderSection().getCharIndex() == member.getBodySection().getCharIndex();
        var errMsg = isAmendsDeclaration ? "cannotAmendFixedProperty" : "cannotAssignFixedProperty";
        if (classProperty.isConst()) {
          errMsg = isAmendsDeclaration ? "cannotAmendConstProperty" : "cannotAssignConstProperty";
        }
        throw exceptionBuilder()
            .evalError(errMsg, memberName)
            .withSourceSection(member.getHeaderSection())
            .build();
      }
    }

    if (parametersDescriptor != null) {
      throw exceptionBuilder()
          .evalError("objectAmendmentCannotHaveParameters")
          .withLocation(getParentNode())
          .build();
    }

    return true;
  }

  @SuppressWarnings("SameReturnValue")
  @TruffleBoundary
  @Idempotent
  protected final boolean checkIsValidListingAmendment() {
    if (maxListingMemberIndex != Long.MIN_VALUE) return true;

    var cursor = EconomicMaps.getEntries(members);
    long maxIndex = -1;

    while (cursor.advance()) {
      var member = cursor.getValue();
      if (member.isLocal()) continue;

      var memberName = member.getNameOrNull();
      if (memberName == null) {
        var key = cursor.getKey();

        if (!(key instanceof Long)) {
          CompilerDirectives.transferToInterpreter();
          throw exceptionBuilder()
              .evalError("wrongListingKeyType", new ProgramValue("", VmUtils.getClass(key)))
              .withSourceSection(member.getHeaderSection())
              .build();
        }

        long index = (long) key;
        if (index < 0) {
          // defer handling of negative index to checkMaxListingMemberIndex() (gives more uniform
          // error messages)
          maxIndex = Long.MAX_VALUE;
          break;
        } else if (index > maxIndex) {
          maxIndex = index;
        }
      } else if (memberName != Identifier.DEFAULT) {
        throw exceptionBuilder()
            .evalError("objectCannotHaveProperty", BaseModule.getListingClass())
            .withSourceSection(member.getHeaderSection())
            .build();
      }
    }

    if (parametersDescriptor != null) {
      throw exceptionBuilder()
          .evalError("listingAmendmentCannotHaveParameters")
          .withSourceSection(getParentNode().getSourceSection())
          .build();
    }

    maxListingMemberIndex = maxIndex;
    return true;
  }

  @SuppressWarnings("SameReturnValue")
  @TruffleBoundary
  @Idempotent
  protected final boolean checkIsValidMappingAmendment() {
    if (checkedIsValidMappingAmendment) return true;

    for (var member : EconomicMaps.getValues(members)) {
      if (member.isLocal()) continue;

      var memberName = member.getNameOrNull();
      if (memberName == null) continue;

      if (memberName != Identifier.DEFAULT) {
        throw exceptionBuilder()
            .evalError("objectCannotHaveProperty", BaseModule.getMappingClass())
            .withSourceSection(member.getHeaderSection())
            .build();
      }
    }

    if (parametersDescriptor != null) {
      throw exceptionBuilder()
          .evalError("mappingAmendmentCannotHaveParameters")
          .withSourceSection(getParentNode().getSourceSection())
          .build();
    }

    checkedIsValidMappingAmendment = true;
    return true;
  }

  @Idempotent
  protected final boolean checkMaxListingMemberIndex(int parentLength) {
    assert maxListingMemberIndex != Long.MIN_VALUE;
    if (maxListingMemberIndex < parentLength) return true;

    CompilerDirectives.transferToInterpreter();
    var cursor = EconomicMaps.getEntries(members);
    while (cursor.advance()) {
      var key = cursor.getKey();
      if (!(key instanceof Long)) continue;

      var index = (long) key;
      if (index < 0 || index >= parentLength) {
        throw exceptionBuilder()
            .evalError("elementIndexOutOfRange", index, 0, parentLength - 1)
            .withSourceSection(cursor.getValue().getHeaderSection())
            .build();
      }
    }

    throw exceptionBuilder().unreachableCode().build();
  }

  @ExplodeLoop
  protected void addListEntries(
      VirtualFrame frame,
      int parentLength,
      EconomicMap<Object, ObjectMember> result,
      ExpressionNode[] keyNodes,
      ObjectMember[] values) {

    for (var i = 0; i < keyNodes.length; i++) {
      long index;
      try {
        index = keyNodes[i].executeInt(frame);
      } catch (UnexpectedResultException e) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder()
            .evalError("wrongListingKeyType", new ProgramValue("", VmUtils.getClass(e.getResult())))
            .withSourceSection(keyNodes[i].getSourceSection())
            .build();
      }

      var value = values[i];

      // use same error messages as in checkIsValidListingAmendment and checkMaxListingMemberIndex
      if (index < 0 || index >= parentLength) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder()
            .evalError("elementIndexOutOfRange", index, 0, parentLength - 1)
            .withSourceSection(value.getHeaderSection())
            .build();
      }

      if (EconomicMaps.put(result, index, value) != null) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder()
            .evalError("duplicateDefinition", new ProgramValue("", index))
            .withSourceSection(value.getHeaderSection())
            .build();
      }
    }
  }

  @TruffleBoundary
  protected @Nullable ObjectMember findFirstNonProperty(
      UnmodifiableEconomicMap<Object, ObjectMember> members) {
    var cursor = EconomicMaps.getEntries(members);
    while (cursor.advance()) {
      var member = cursor.getValue();
      if (member.getNameOrNull() == null) return member;
    }
    return null;
  }

  @TruffleBoundary
  protected @Nullable ObjectMember findFirstNonDefaultProperty(
      UnmodifiableEconomicMap<Object, ObjectMember> members) {
    var cursor = EconomicMaps.getEntries(members);
    while (cursor.advance()) {
      var member = cursor.getValue();
      if (member.getNameOrNull() != null && member.getNameOrNull() != Identifier.DEFAULT)
        return member;
    }
    return null;
  }

  @TruffleBoundary
  protected Object elementsEntriesFallback(
      Object parent, @Nullable ObjectMember firstElemOrEntry, boolean isElementsOnly) {
    var parentIsClass = parent instanceof VmClass;
    var parentClass = parentIsClass ? (VmClass) parent : VmUtils.getClass(parent);
    VmUtils.checkIsInstantiable(parentClass, getParentNode());

    var property = findFirstNonDefaultProperty(members);
    if (property != null
        && (parentClass == BaseModule.getListingClass()
            || parentClass == BaseModule.getMappingClass())) {
      throw exceptionBuilder()
          .evalError("objectCannotHaveProperty", parentClass)
          .withSourceSection(property.getHeaderSection())
          .build();
    }

    assert firstElemOrEntry != null;

    if (isTypedObjectClass(parentClass)
        || (isElementsOnly && parentClass == BaseModule.getMappingClass())) {
      throw exceptionBuilder()
          .evalError(
              isElementsOnly ? "objectCannotHaveElement" : "objectCannotHaveEntry", parentClass)
          .withSourceSection(firstElemOrEntry.getHeaderSection())
          .build();
    }

    throw exceptionBuilder().unreachableCode().build();
  }
}
