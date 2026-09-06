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
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmUtils;

public abstract sealed class AbstractInvokeLexicalMethodNode
    extends AbstractInvokeLexicalOrQualifiedMethodNode
    permits InvokeLexicalClassMethodNode, InvokeLexicalObjectMethodNode {
  private final int levelsUp;

  public AbstractInvokeLexicalMethodNode(
      SourceSection sourceSection,
      Identifier methodName,
      int levelsUp,
      ExpressionNode[] argumentNodes,
      boolean needsConst,
      boolean argsRequireInference) {
    super(sourceSection, methodName, argumentNodes, needsConst, argsRequireInference);
    this.levelsUp = levelsUp;
  }

  @Override
  public final Object executeGeneric(VirtualFrame frame) {
    var owner = VmUtils.getOwner(frame);
    if (levelsUp == 0 && !owner.isParseTimeInvisibleScope()) {
      return invoke(frame, owner, VmUtils.getReceiver(frame));
    }
    var enclosingFrame = VmUtils.getEnclosingFrame(owner, levelsUp);
    return invoke(frame, VmUtils.getOwner(enclosingFrame), VmUtils.getReceiver(enclosingFrame));
  }
}
