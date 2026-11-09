/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmObjectLike;

public abstract class AbstractReadLocalPropertyNode extends ExpressionNode {

  private final Identifier name;
  private final boolean needsConst;
  @Child private @Nullable DirectCallNode callNode;
  @CompilationFinal @Nullable private ObjectMember property;

  public AbstractReadLocalPropertyNode(
      SourceSection sourceSection, Identifier name, boolean needsConst) {
    super(sourceSection);
    this.name = name;
    this.needsConst = needsConst;
  }

  protected ObjectMember getProperty(VmObjectLike owner) {
    if (property == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      property = owner.getMember(name);
      if (property == null) {
        // should never happen
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder().bug("Couldn't find local variable `" + name + "`.").build();
      }
      if (needsConst && !property.isConst()) {
        throw exceptionBuilder().evalError("propertyMustBeConst", name.toString()).build();
      }
    }
    return property;
  }

  protected DirectCallNode getCallNode(ObjectMember property) {
    if (callNode == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      callNode = DirectCallNode.create(property.getCallTarget());
      insert(callNode);
    }
    assert callNode != null;
    return callNode;
  }
}
