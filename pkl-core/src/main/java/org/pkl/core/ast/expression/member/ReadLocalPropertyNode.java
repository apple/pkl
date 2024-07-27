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
package org.pkl.core.ast.expression.member;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.runtime.VmUtils;

/** Reads a local non-constant property that is known to exist in the lexical scope of this node. */
public final class ReadLocalPropertyNode extends ExpressionNode {
  private final ObjectMember property;
  private final int levelsUp;
  @Child private DirectCallNode callNode;

  public ReadLocalPropertyNode(SourceSection sourceSection, ObjectMember property, int levelsUp) {

    super(sourceSection);
    CompilerAsserts.neverPartOfCompilation();

    this.property = property;
    this.levelsUp = levelsUp;

    assert property.getNameOrNull() != null;
    assert property.getConstantValue() == null : "Use a ConstantNode instead.";

    callNode = DirectCallNode.create(property.getCallTarget());
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    var owner = VmUtils.getOwner(frame);
    Object receiver;

    if (levelsUp == 0) {
      receiver = VmUtils.getReceiver(frame);
    } else {
      for (int i = 1; i < levelsUp; i++) {
        owner = owner.getEnclosingOwner();
        assert owner != null;
      }

      receiver = owner.getEnclosingReceiver();
      owner = owner.getEnclosingOwner();
    }

    assert receiver instanceof VmObjectLike
        : "Assumption: This node isn't used in Truffle ASTs of `external` pkl.base classes whose values aren't VmObject's.";

    var objReceiver = (VmObjectLike) receiver;
    var result = objReceiver.getCachedValue(property);

    if (result == null) {
      result = callNode.call(objReceiver, owner, property.getName());
      objReceiver.setCachedValue(property, result, property);
    }

    return result;
  }
}
