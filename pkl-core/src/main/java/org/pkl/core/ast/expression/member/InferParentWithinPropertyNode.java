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
package org.pkl.core.ast.expression.member;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.*;

/** Infers the parent to amend in `x = new { ... }`. */
@NodeChild(value = "ownerNode", type = ExpressionNode.class)
public abstract class InferParentWithinPropertyNode extends ExpressionNode {
  private final Identifier ownPropertyName;
  private final boolean isLocalProperty;

  protected InferParentWithinPropertyNode(SourceSection sourceSection, Identifier ownPropertyName) {
    super(sourceSection);
    this.ownPropertyName = ownPropertyName;
    isLocalProperty = ownPropertyName.isLocalProp();
  }

  @Specialization(guards = "!owner.isPrototype()")
  protected Object evalTypedObject(VmTyped owner) {
    if (isLocalProperty) {
      return getLocalPropertyDefaultValue(owner);
    }

    try {
      var result = VmUtils.readMemberOrNull(owner.getPrototype(), ownPropertyName, false);
      assert result != null : "every property has a default";
      return result;
    } catch (VmUndefinedValueException e) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder().evalError("cannotInferParent").build();
    }
  }

  @Specialization(guards = "owner.isPrototype()")
  protected Object evalPrototype(VmTyped owner) {
    var property =
        isLocalProperty
            ?
            // only getDeclaredProperty() returns local properties
            owner.getVmClass().getDeclaredProperty(ownPropertyName)
            :
            // only getProperty() returns aggregated type information if property type is specified
            // in a superclass
            owner.getVmClass().getProperty(ownPropertyName);
    assert property != null;

    var typeNode = property.getTypeNode();
    if (typeNode == null || typeNode.isUnknownType()) return VmDynamic.empty();

    var result = typeNode.getDefaultValue();
    if (result != null) return result;

    // no default exists for this property type

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder().evalError("cannotInferParent").build();
  }

  @Specialization
  protected Object eval(@SuppressWarnings("unused") VmDynamic owner) {
    if (isLocalProperty) {
      return getLocalPropertyDefaultValue(owner);
    }

    return VmDynamic.empty();
  }

  @Specialization
  protected Object eval(@SuppressWarnings("unused") VmListing owner) {
    if (isLocalProperty) {
      return getLocalPropertyDefaultValue(owner);
    }

    assert ownPropertyName == Identifier.DEFAULT;
    // return `VmListing.default`
    return VmUtils.readMember(BaseModule.getListingClass().getPrototype(), ownPropertyName);
  }

  @Specialization
  protected Object eval(@SuppressWarnings("unused") VmMapping owner) {
    if (isLocalProperty) {
      return getLocalPropertyDefaultValue(owner);
    }

    assert ownPropertyName == Identifier.DEFAULT;
    // return `VmMapping.default`
    return VmUtils.readMember(BaseModule.getMappingClass().getPrototype(), ownPropertyName);
  }

  private Object getLocalPropertyDefaultValue(VmObjectLike owner) {
    assert isLocalProperty;

    var member = owner.getMember(ownPropertyName);
    assert member != null;

    var defaultValue = member.getLocalPropertyDefaultValue();
    if (defaultValue != null) return defaultValue;

    // no default exists for this property type

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder().evalError("cannotInferParent").build();
  }
}
