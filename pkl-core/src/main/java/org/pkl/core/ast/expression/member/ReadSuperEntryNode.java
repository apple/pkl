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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.*;

/**
 * An expression of the form `super[key]`.
 *
 * <p>Note: Reading an entry (`object[key]`) is the subscript operator (SubscriptNode).
 */
public class ReadSuperEntryNode extends ExpressionNode {
  @Child private ExpressionNode keyNode;
  @Child private IndirectCallNode callNode = IndirectCallNode.create();

  public ReadSuperEntryNode(SourceSection sourceSection, ExpressionNode keyNode) {
    super(sourceSection);
    this.keyNode = keyNode;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    var key = keyNode.executeGeneric(frame);

    var receiver = VmUtils.getObjectReceiver(frame);

    var initialOwner = VmUtils.getOwner(frame);
    while (initialOwner instanceof VmFunction) {
      initialOwner = initialOwner.getEnclosingOwner();
    }
    assert initialOwner != null : "VmFunction always has a parent";
    initialOwner = initialOwner.getParent();

    for (var owner = initialOwner; owner != null; owner = owner.getParent()) {
      var member = owner.getMember(key);
      if (member == null) continue;

      var constantValue = member.getConstantValue();
      if (constantValue != null) return constantValue; // TODO: type check

      // caching the result of a super call is tricky (function of both receiver and owner)
      return callNode.call(
          member.getCallTarget(),
          // TODO: should the marker only turn off constraint checking, not overall type checking?
          receiver,
          owner,
          key,
          VmUtils.SKIP_TYPECHECK_MARKER);
    }

    // not found -> apply lambda contained in `default` property
    var defaultFunction =
        (VmFunction) VmUtils.readMemberOrNull(receiver, Identifier.DEFAULT, callNode);
    assert defaultFunction != null;
    return defaultFunction.apply(key);
  }
}
