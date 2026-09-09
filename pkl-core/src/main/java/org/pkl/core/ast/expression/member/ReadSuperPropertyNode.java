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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Pair;

@ImportStatic(VmUtils.class)
public abstract class ReadSuperPropertyNode extends ExpressionNode {

  private final Identifier propertyName;
  private final boolean needsConst;

  @Child private IndirectCallNode callNode = IndirectCallNode.create();

  public ReadSuperPropertyNode(
      SourceSection sourceSection, Identifier propertyName, boolean needsConst) {
    super(sourceSection);
    this.propertyName = propertyName;
    this.needsConst = needsConst;
  }

  @SuppressWarnings("unused")
  @Specialization(guards = "getObjectReceiver(frame) == receiver")
  public Object evalCached(
      VirtualFrame frame,
      @Cached("getObjectReceiver(frame)") VmObjectLike receiver,
      @Cached("getPropertyAndOwner(frame)")
          @Nullable Pair<VmObjectLike, ObjectMember> ownerAndProperty,
      @Cached("doEval(receiver, ownerAndProperty)") Object result) {
    return result;
  }

  @Specialization(replaces = "evalCached")
  public Object evalUncached(VirtualFrame frame) {
    return doEval(VmUtils.getObjectReceiver(frame), getPropertyAndOwner(frame));
  }

  protected Object doEval(
      VmObjectLike receiver, @Nullable Pair<VmObjectLike, ObjectMember> ownerAndProperty) {
    if (ownerAndProperty == null) {
      // TODO: refine when to return VmDynamic.empty() and when to fail
      return VmDynamic.empty();
    }
    var owner = ownerAndProperty.getFirst();
    var property = ownerAndProperty.getSecond();
    var constantValue = property.getConstantValue();
    if (constantValue != null) return constantValue; // TODO: type check

    return callNode.call(
        property.getCallTarget(),
        // TODO: should the marker only turn off constraint checking, not overall type checking?
        receiver,
        owner,
        propertyName,
        VmUtils.SKIP_TYPECHECK_MARKER);
  }

  protected @Nullable Pair<VmObjectLike, ObjectMember> getPropertyAndOwner(VirtualFrame frame) {
    var initialOwner = VmUtils.getOwner(frame);
    while (initialOwner instanceof VmFunction) {
      initialOwner = initialOwner.getEnclosingOwner();
    }
    assert initialOwner != null : "VmFunction always has a parent";
    initialOwner = initialOwner.getParent();

    for (var owner = initialOwner; owner != null; owner = owner.getParent()) {
      var property = owner.getMember(propertyName);
      if (property == null) continue;
      if (needsConst && !property.isConst()) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder().evalError("propertyMustBeConst", propertyName.toString()).build();
      }
      return Pair.of(owner, property);
    }
    return null;
  }
}
