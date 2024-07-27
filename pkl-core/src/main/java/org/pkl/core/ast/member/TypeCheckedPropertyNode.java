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
package org.pkl.core.ast.member;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Executed;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.expression.primary.GetOwnerNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Nullable;

/** A property definition that does not have a type annotation but should be type-checked. */
public abstract class TypeCheckedPropertyNode extends RegularMemberNode {
  @Child @Executed protected ExpressionNode ownerNode = new GetOwnerNode();

  protected TypeCheckedPropertyNode(
      @Nullable VmLanguage language,
      FrameDescriptor descriptor,
      ObjectMember member,
      ExpressionNode bodyNode) {

    super(language, descriptor, member, bodyNode);

    assert member.isProp();
  }

  @SuppressWarnings("unused")
  @Specialization(guards = "owner.getVmClass() == cachedOwnerClass")
  protected Object evalTypedObjectCached(
      VirtualFrame frame,
      VmTyped owner,
      @Cached("owner.getVmClass()") VmClass cachedOwnerClass,
      @Cached("getProperty(cachedOwnerClass)") ClassProperty property,
      @Cached("createTypeCheckCallNode(property)") @Nullable DirectCallNode callNode) {

    var result = executeBody(frame);

    // TODO: propagate SUPER_CALL_MARKER to disable constraint (but not type) check
    if (callNode != null && VmUtils.shouldRunTypeCheck(frame)) {
      return callNode.call(VmUtils.getReceiverOrNull(frame), property.getOwner(), result);
    }

    return result;
  }

  @Specialization(guards = "!owner.isDynamic()")
  protected Object eval(
      VirtualFrame frame, VmObjectLike owner, @Cached("create()") IndirectCallNode callNode) {

    var result = executeBody(frame);

    if (VmUtils.shouldRunTypeCheck(frame)) {
      var property = getProperty(owner.getVmClass());
      var typeAnnNode = property.getTypeNode();
      if (typeAnnNode != null) {
        return callNode.call(
            typeAnnNode.getCallTarget(),
            VmUtils.getReceiverOrNull(frame),
            property.getOwner(),
            result);
      }
    }

    return result;
  }

  @Specialization
  protected Object eval(VirtualFrame frame, @SuppressWarnings("unused") VmDynamic owner) {
    return executeBody(frame);
  }

  protected ClassProperty getProperty(VmClass ownerClass) {
    ClassProperty result = ownerClass.getProperty(member.getName());
    assert result != null;
    return result;
  }

  protected @Nullable DirectCallNode createTypeCheckCallNode(ClassProperty property) {
    var typeCheckNode = property.getTypeNode();
    return typeCheckNode == null ? null : DirectCallNode.create(typeCheckNode.getCallTarget());
  }
}
