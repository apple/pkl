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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
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

/** Reads a local non-constant property that is known to exist in the lexical scope of this node. */
public final class ReadLocalPropertyNode extends ExpressionNode {
  private final Identifier name;
  private final int levelsUp;
  private final boolean needsConst;
  @Child private DirectCallNode callNode;
  @CompilationFinal private ObjectMember property;

  public ReadLocalPropertyNode(
      SourceSection sourceSection, Identifier name, int levelsUp, boolean needsConst) {

    super(sourceSection);
    CompilerAsserts.neverPartOfCompilation();

    this.name = name;
    this.levelsUp = levelsUp;
    this.needsConst = needsConst;
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    var owner = VmUtils.getOwner(frame, levelsUp);
    var property = getProperty(owner);
    var constantValue = property.getConstantValue();
    if (constantValue != null) {
      return constantValue;
    }

    var receiver = (VmObjectLike) VmUtils.getReceiver(frame, levelsUp);
    var result = receiver.getCachedValue(property);

    if (result == null) {
      result = getCallNode(property).call(receiver, owner, property.getName());
      receiver.setCachedValue(property, result);
    }

    return result;
  }

  private ObjectMember getProperty(VmObjectLike owner) {
    if (property == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      property = owner.getMember(name);
      if (property == null) {
        // should never happen
        CompilerDirectives.transferToInterpreter();
        throw new PklBugException("Couldn't find local variable `" + name + "`.");
      }
      if (needsConst && !property.isConst()) {
        throw exceptionBuilder().evalError("propertyMustBeConst", name.toString()).build();
      }
    }
    return property;
  }

  public DirectCallNode getCallNode(ObjectMember property) {
    if (callNode == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      callNode = DirectCallNode.create(property.getCallTarget());
      insert(callNode);
    }
    return callNode;
  }
}
