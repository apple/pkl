/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.PklBugException;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.Nullable;

/** Reads a local non-constant property that is known to exist in the lexical scope of this node. */
public final class ReadLocalPropertyNode extends ExpressionNode {
  private final Identifier name;
  private final int levelsUp;
  private ObjectMember property;
  @Child private DirectCallNode callNode;

  public ReadLocalPropertyNode(SourceSection sourceSection, Identifier name, int levelsUp) {

    super(sourceSection);
    CompilerAsserts.neverPartOfCompilation();

    this.name = name;
    this.levelsUp = levelsUp;
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
      assert owner != null;
    }
    var constantValue = getProperty(frame);
    if (constantValue != null) {
      return constantValue;
    }

    var property = owner.getMember(name);
    if (property == null) {
      // should never happen
      CompilerDirectives.transferToInterpreter();
      throw new PklBugException("Couldn't find local variable `" + name + "`.");
    }

    assert receiver instanceof VmObjectLike
        : "Assumption: This node isn't used in Truffle ASTs of `external` pkl.base classes whose values aren't VmObject's.";

    var objReceiver = (VmObjectLike) receiver;
    var result = objReceiver.getCachedValue(property);

    if (result == null) {
      result = callNode.call(objReceiver, owner, property.getName());
      objReceiver.setCachedValue(property, result);
    }

    return result;
  }

  private @Nullable Object getProperty(VirtualFrame frame) {
    if (property != null) return property.getConstantValue();

    var currFrame = frame;
    var currOwner = VmUtils.getOwner(currFrame);

    do {
      var localMember = currOwner.getMember(name);
      if (localMember != null) {
        assert localMember.isLocal();

        var value = localMember.getConstantValue();
        if (value != null) {
          return value;
        }

        property = localMember;
        callNode = DirectCallNode.create(property.getCallTarget());
        break;
      }

      currFrame = currOwner.getEnclosingFrame();
      currOwner = VmUtils.getOwnerOrNull(currFrame);
    } while (currOwner != null);
    return null;
  }
}
