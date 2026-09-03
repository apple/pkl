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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmUtils;

public abstract class AbstractInvokeLexicalMethodNode
    extends AbstractInvokeLexicalOrQualifiedMethodNode {
  private final int levelsUp;

  public AbstractInvokeLexicalMethodNode(
      SourceSection sourceSection,
      Identifier methodName,
      int levelsUp,
      UnresolvedTypeNode @Nullable [] unresolvedTypeArgumentNodes,
      ExpressionNode[] argumentNodes,
      boolean needsConst,
      boolean argsRequireInference) {
    super(
        sourceSection,
        methodName,
        unresolvedTypeArgumentNodes,
        argumentNodes,
        needsConst,
        argsRequireInference);
    this.levelsUp = levelsUp;
  }

  protected VirtualFrame getEffectiveFrame(VirtualFrame frame) {
    var owner = VmUtils.getOwner(frame);
    return levelsUp == 0 && !owner.isParseTimeInvisibleScope()
        ? frame
        : VmUtils.getEnclosingFrame(owner, levelsUp);
  }
}
