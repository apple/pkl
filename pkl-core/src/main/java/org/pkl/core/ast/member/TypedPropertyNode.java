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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmUtils;

/** A property definition that has a type annotation. */
public final class TypedPropertyNode extends RegularMemberNode {
  @Child private DirectCallNode typeCheckCallNode;

  @TruffleBoundary
  public TypedPropertyNode(
      VmLanguage language,
      FrameDescriptor descriptor,
      ObjectMember member,
      ExpressionNode bodyNode,
      PropertyTypeNode typeNode) {

    super(language, descriptor, member, bodyNode);

    assert member.isProp();

    typeCheckCallNode = DirectCallNode.create(typeNode.getCallTarget());
  }

  @Override
  public Object execute(VirtualFrame frame) {
    var propertyValue = executeBody(frame);
    if (VmUtils.shouldRunTypeCheck(frame)) {
      return typeCheckCallNode.call(
          VmUtils.getReceiver(frame), VmUtils.getOwner(frame), propertyValue);
    }
    return propertyValue;
  }
}
