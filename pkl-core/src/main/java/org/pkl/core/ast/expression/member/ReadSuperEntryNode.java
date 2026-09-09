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
package org.pkl.core.ast.expression.member;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Executed;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.lambda.ApplyVmFunction1Node;
import org.pkl.core.ast.lambda.ApplyVmFunction1NodeGen;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Pair;

/**
 * An expression of the form {@code super[key]}.
 *
 * <p>Note: Reading an entry ({@code object[key]}) is the subscript operator (SubscriptNode).
 */
@ImportStatic(VmUtils.class)
public abstract class ReadSuperEntryNode extends ExpressionNode {
  @Child @Executed protected ExpressionNode keyNode;
  @Child private IndirectCallNode callNode = IndirectCallNode.create();
  @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1NodeGen.create();

  public ReadSuperEntryNode(SourceSection sourceSection, ExpressionNode keyNode) {
    super(sourceSection);
    this.keyNode = keyNode;
  }

  @SuppressWarnings("unused")
  @Specialization(
      guards = {
        // don't use `Object.equals` here because it will deep-force the receiver
        "getObjectReceiver(frame) == receiver",
        // fine to use `Object.equals` here because an object key is deep-forced upon lookup anyway
        "key.equals(cachedKey)"
      })
  protected Object evalCached(
      VirtualFrame frame,
      Object key,
      @Cached("key") Object cachedKey,
      @Cached("getObjectReceiver(frame)") VmObjectLike receiver,
      @Cached("getOwnerAndMember(frame, key)")
          @Nullable Pair<VmObjectLike, ObjectMember> ownerAndMember,
      @Cached("doEval(key, receiver, ownerAndMember)") Object result) {
    return result;
  }

  @Specialization(replaces = "evalCached")
  protected Object evalUncached(VirtualFrame frame, Object key) {
    var receiver = VmUtils.getObjectReceiver(frame);
    var ownerAndMember = getOwnerAndMember(frame, key);
    return doEval(key, receiver, ownerAndMember);
  }

  protected final Object doEval(
      Object key,
      VmObjectLike receiver,
      @Nullable Pair<VmObjectLike, ObjectMember> ownerAndMember) {
    if (ownerAndMember == null) {
      // not found -> apply lambda contained in `default` property
      var defaultFunction =
          (VmFunction) VmUtils.readMemberOrNull(receiver, Identifier.DEFAULT, callNode);
      assert defaultFunction != null;
      return applyLambdaNode.execute(defaultFunction, key);
    }
    var owner = ownerAndMember.getFirst();
    var member = ownerAndMember.getSecond();
    var constantValue = member.getConstantValue();
    if (constantValue != null) return constantValue; // TODO: type check

    return callNode.call(
        member.getCallTarget(),
        // TODO: should the marker only turn off constraint checking, not overall type checking?
        receiver,
        owner,
        key,
        VmUtils.SKIP_TYPECHECK_MARKER);
  }

  protected @Nullable Pair<VmObjectLike, ObjectMember> getOwnerAndMember(
      VirtualFrame frame, Object key) {
    var initialOwner = VmUtils.getOwner(frame);
    while (initialOwner instanceof VmFunction) {
      initialOwner = initialOwner.getEnclosingOwner();
    }
    assert initialOwner != null : "VmFunction always has a parent";
    initialOwner = initialOwner.getParent();

    for (var owner = initialOwner; owner != null; owner = owner.getParent()) {
      var property = owner.getMember(key);
      if (property == null) continue;
      return Pair.of(owner, property);
    }
    return null;
  }
}
