/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.*;

/** Object literal that contains properties but not elements or entries. */
// IDEA: don't materialize frame when all members are constants
@ImportStatic({BaseModule.class, VmUtils.class})
public abstract class PropertiesLiteralNode extends SpecializedObjectLiteralNode {
  public PropertiesLiteralNode(
      SourceSection sourceSection,
      VmLanguage language,
      String qualifiedScopeName,
      boolean isCustomThisScope,
      @Nullable FrameDescriptor parametersDescriptor,
      UnresolvedTypeNode[] parameterTypes,
      UnmodifiableEconomicMap<Object, ObjectMember> properties) {

    super(
        sourceSection,
        language,
        qualifiedScopeName,
        isCustomThisScope,
        parametersDescriptor,
        parameterTypes,
        properties);
  }

  @Override
  public PropertiesLiteralNode copy(ExpressionNode newParentNode) {
    //noinspection ConstantConditions
    return PropertiesLiteralNodeGen.create(
        sourceSection,
        language,
        qualifiedScopeName,
        isCustomThisScope,
        null, // copied node no longer has parameters
        new UnresolvedTypeNode[0], // ditto
        members,
        newParentNode);
  }

  @Specialization(
      guards = {"parentClass == parent.getVmClass()", "checkIsValidTypedAmendment(parentClass)"})
  protected Object evalTypedObjectCached(
      VirtualFrame frame, VmTyped parent, @Cached("parent.getVmClass()") VmClass parentClass) {

    assert isTypedObjectClass(parentClass);

    return new VmTyped(frame.materialize(), parent, parentClass, members);
  }

  @Specialization(guards = {"checkIsValidTypedAmendment(parent)"})
  protected Object evalTypedObjectUncached(VirtualFrame frame, VmTyped parent) {
    assert isTypedObjectClass(parent.getVmClass());

    return new VmTyped(frame.materialize(), parent, parent.getVmClass(), members);
  }

  @SuppressWarnings("unused")
  @Specialization(
      guards = {
        "parentClass == getClass(defaultValue)",
        "isTypedObjectClass(parentClass)",
        "checkIsValidTypedAmendment(parentClass)"
      })
  protected Object evalNullWithTypedObjectDefaultCached(
      VirtualFrame frame,
      VmNull parent,
      @Bind("getNullDefaultValue(parent)") Object defaultValue,
      @Cached("getClass(defaultValue)") VmClass parentClass) {
    var parentTyped = (VmTyped) defaultValue;
    return new VmTyped(frame.materialize(), parentTyped, parentTyped.getVmClass(), members);
  }

  @SuppressWarnings("unused")
  @Specialization(
      guards = {
        "isTypedObjectClass(getClass(defaultValue))",
        "checkIsValidTypedAmendment(defaultValue)"
      })
  protected Object evalNullWithTypedObjectDefaultUncached(
      VirtualFrame frame,
      VmNull parent,
      @Bind(value = "getNullDefaultValue(parent)") Object defaultValue) {
    var parentTyped = (VmTyped) defaultValue;
    return new VmTyped(frame.materialize(), parentTyped, parentTyped.getVmClass(), members);
  }

  @Specialization
  protected Object evalDynamic(VirtualFrame frame, VmDynamic parent) {
    return new VmDynamic(frame.materialize(), parent, members, parent.getLength());
  }

  @SuppressWarnings("unused")
  @Specialization(guards = "getClass(defaultValue).isDynamicClass()")
  protected Object evalNullWithDynamicDefault(
      VirtualFrame frame, VmNull parent, @Bind("getNullDefaultValue(parent)") Object defaultValue) {
    return evalDynamic(frame, (VmDynamic) defaultValue);
  }

  @Specialization(guards = "checkIsValidListingAmendment()")
  protected Object evalListing(VirtualFrame frame, VmListing parent) {
    return new VmListing(frame.materialize(), parent, members, parent.getLength());
  }

  @SuppressWarnings("unused")
  @Specialization(
      guards = {"getClass(defaultValue).isListingClass()", "checkIsValidListingAmendment()"})
  protected Object evalNullWithListingDefault(
      VirtualFrame frame,
      VmNull parent,
      @Bind(value = "getNullDefaultValue(parent)") Object defaultValue) {
    return evalListing(frame, (VmListing) defaultValue);
  }

  @ExplodeLoop
  @Specialization(guards = "checkIsValidMappingAmendment()")
  protected Object evalMapping(VirtualFrame frame, VmMapping parent) {
    return new VmMapping(frame.materialize(), parent, members);
  }

  @SuppressWarnings("unused")
  @Specialization(
      guards = {"getClass(defaultValue).isMappingClass()", "checkIsValidMappingAmendment()"})
  protected Object evalNullWithMappingDefault(
      VirtualFrame frame,
      VmNull parent,
      @Bind(value = "getNullDefaultValue(parent)") Object defaultValue) {
    return evalMapping(frame, (VmMapping) defaultValue);
  }

  // Ultimately, this lambda or a lambda returned from it will call one of the other
  // specializations,
  // which will perform the "isValidXYZ" guard check.
  // That said, to flag nonsensical amendments early, this specialization could have a guard to
  // check
  // that this amendment is at least one of a valid listing, mapping, or object amendment (modulo
  // parameters).
  @Specialization(guards = "checkIsValidFunctionAmendment(parent)")
  protected VmFunction evalFunction(
      VirtualFrame frame,
      VmFunction parent,
      @Cached(value = "createAmendFunctionNode(frame)", neverDefault = true)
          AmendFunctionNode amendFunctionNode) {

    return amendFunctionNode.execute(frame, parent);
  }

  @SuppressWarnings("unused")
  @Specialization(
      guards = {"isFunction(defaultValue)", "checkIsValidFunctionAmendment(defaultValue)"})
  protected Object evalNullWithFunctionDefault(
      VirtualFrame frame,
      VmNull parent,
      @Bind("getNullDefaultValue(parent)") Object defaultValue,
      @Cached(value = "createAmendFunctionNode(frame)", neverDefault = true)
          AmendFunctionNode amendFunctionNode) {
    return evalFunction(frame, (VmFunction) defaultValue, amendFunctionNode);
  }

  @Specialization(
      guards = {
        "parent == cachedParent",
        "isTypedObjectClass(cachedParent)",
        "checkIsValidTypedAmendment(cachedParent)"
      })
  protected VmTyped evalTypedObjectClassCached(
      VirtualFrame frame,
      VmClass parent,
      @Cached("parent") @SuppressWarnings("unused") VmClass cachedParent) {
    return new VmTyped(frame.materialize(), parent.getPrototype(), parent, members);
  }

  @Specialization(
      guards = {
        "parent == cachedParent",
        "cachedParent.isListingClass()",
        "checkIsValidListingAmendment()"
      })
  protected VmListing evalListingClass(
      VirtualFrame frame,
      @SuppressWarnings("unused") VmClass parent,
      @Cached("parent") @SuppressWarnings("unused") VmClass cachedParent) {

    return new VmListing(
        frame.materialize(), BaseModule.getListingClass().getPrototype(), members, 0);
  }

  @Specialization(
      guards = {
        "parent == cachedParent",
        "cachedParent.isMappingClass()",
        "checkIsValidMappingAmendment()"
      })
  protected VmMapping evalMappingClass(
      VirtualFrame frame,
      @SuppressWarnings("unused") VmClass parent,
      @Cached("parent") @SuppressWarnings("unused") VmClass cachedParent) {

    return new VmMapping(frame.materialize(), BaseModule.getMappingClass().getPrototype(), members);
  }

  @Specialization(guards = {"parent == cachedParent", "cachedParent.isDynamicClass()"})
  protected VmDynamic evalDynamicClass(
      VirtualFrame frame,
      @SuppressWarnings("unused") VmClass parent,
      @Cached("parent") @SuppressWarnings("unused") VmClass cachedParent) {

    return new VmDynamic(
        frame.materialize(), BaseModule.getDynamicClass().getPrototype(), members, 0);
  }

  // slow but very unlikely to occur in practice
  @Specialization
  protected Object evalClassUncached(VirtualFrame frame, VmClass parent) {
    if (parent.isListingClass()) {
      checkIsValidListingAmendment();
      checkMaxListingMemberIndex(0);
      return evalListingClass(frame, parent, parent);
    }

    if (parent.isMappingClass()) {
      checkIsValidMappingAmendment();
      return evalMappingClass(frame, parent, parent);
    }

    if (parent.isDynamicClass()) {
      return new VmDynamic(
          frame.materialize(), BaseModule.getDynamicClass().getPrototype(), members, 0);
    }

    checkIsValidTypedAmendment(parent);
    return new VmTyped(frame.materialize(), parent.getPrototype(), parent, members);
  }

  @Specialization
  @TruffleBoundary
  protected void fallback(Object parent) {
    var value = parent;
    // blame the non-null type (e.g. blame `Duration` instead of `Null` in the case of `Duration?`)
    if (value instanceof VmNull vmNull) {
      value = getNullDefaultValue(vmNull);
    }
    // should always throw
    checkIsValidTypedAmendment(value);

    throw exceptionBuilder().unreachableCode().build();
  }
}
