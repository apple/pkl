/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.ast.expression.literal;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Nullable;

public final class CheckIsAnnotationClassNode extends ExpressionNode {
  @Child private UnresolvedTypeNode unresolvedTypeNode;
  @Child private @Nullable TypeNode typeNode;

  public CheckIsAnnotationClassNode(UnresolvedTypeNode unresolvedTypeNode) {
    super(unresolvedTypeNode.getSourceSection());
    this.unresolvedTypeNode = unresolvedTypeNode;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    if (typeNode == null) {
      // invalidation is done by insert()
      CompilerDirectives.transferToInterpreter();
      typeNode = insert(unresolvedTypeNode.execute(frame));
      unresolvedTypeNode = null;
    }
    var clazz = typeNode.getVmClass();
    if (clazz != null && clazz.isSubclassOf(BaseModule.getAnnotationClass())) {
      return typeNode.getVmClass();
    }

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder().evalError("expectedAnnotationClass").build();
  }
}
