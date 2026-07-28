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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.expression.primary.GetModuleNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmTyped;

/** Read a class by name from the current receiver's module */
public final class ReadClassNode extends ExpressionNode {

  private final Identifier className;
  private final boolean isLocal;
  private @Child ExpressionNode getModuleNode;
  @Child private @Nullable DirectCallNode callNode;

  public ReadClassNode(SourceSection sourceSection, Identifier className, boolean isLocal) {
    super(sourceSection);
    this.getModuleNode = new GetModuleNode(sourceSection);
    this.className = className;
    this.isLocal = isLocal;
  }

  public Object executeGeneric(VirtualFrame frame) {
    var module = (VmTyped) getModuleNode.executeGeneric(frame);

    if (!isLocal) {
      var clazz = module.getCachedValue(className);
      if (clazz != null) return clazz;
    }

    var member = module.getMember(className);
    assert member != null;
    assert member.isClass();

    if (isLocal) {
      var clazz = module.getCachedValue(member);
      if (clazz != null) return clazz;
    }

    var clazz = getCallNode(member).call(module, module, member.getName());
    module.setCachedValue(isLocal ? member : className, clazz);
    return clazz;
  }

  private DirectCallNode getCallNode(ObjectMember member) {
    if (callNode == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      callNode = DirectCallNode.create(member.getCallTarget());
      insert(callNode);
    }
    assert callNode != null;
    return callNode;
  }
}
