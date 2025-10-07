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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.MemberLookupMode;
import org.pkl.core.ast.expression.primary.GetEnclosingReceiverNode;
import org.pkl.core.ast.expression.primary.GetReceiverNode;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmUtils;

/**
 * Reads either a local, or a non-local property.
 *
 * <p>Sometimes, it is not possible to determine whether a property is local or not at parse time.
 * This happens for members declared inside generator bodies. For example:
 *
 * <pre>{@code
 * foo {
 *   when (cond) {
 *     local res = 1
 *   } else {
 *     res = 2
 *   }
 *   // Depending on how the when generator is executed, `res` is either a local property,
 *   // or a non-local
 *   bar = res
 * }
 * }</pre>
 */
public final class ReadAmbiguousLocalityPropertyNode extends ExpressionNode {

  private final Identifier name;
  private final int levelsUp;
  private final boolean needsConst;
  private @Child ExpressionNode readLocalPropertyNode;
  private @Child ExpressionNode readPropertyNode;

  public ReadAmbiguousLocalityPropertyNode(
      SourceSection sourceSection, Identifier name, int levelsUp, boolean needsConst) {
    super(sourceSection);

    this.name = name;
    this.levelsUp = levelsUp;
    this.needsConst = needsConst;
  }

  private ExpressionNode getReadLocalPropertyNode() {
    if (readLocalPropertyNode == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      readLocalPropertyNode =
          insert(
              new ReadLocalPropertyNode(
                  sourceSection, name.toLocalProperty(), levelsUp, needsConst));
    }
    return readLocalPropertyNode;
  }

  private ExpressionNode getReadPropertyNode() {
    if (readPropertyNode == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      readPropertyNode =
          insert(
              ReadPropertyNodeGen.create(
                  sourceSection,
                  name,
                  MemberLookupMode.IMPLICIT_LEXICAL,
                  needsConst,
                  levelsUp == 0 ? new GetReceiverNode() : new GetEnclosingReceiverNode(levelsUp)));
    }
    return readPropertyNode;
  }

  private ExpressionNode getUnderlying(VirtualFrame frame) {
    var receiver = VmUtils.getObjectReceiver(frame, levelsUp);
    return receiver.hasMember(name) ? getReadPropertyNode() : getReadLocalPropertyNode();
  }

  // NOTE: `replace()` is actually incorrect (we can't know that the next eval of this node will
  // continue resolving to the same node).
  // However, Pkl <= 0.31 works this way (ResolveVariableNode always calls `replace()`).
  // Also, it's likely that a future version of Pkl will _not_ have generator members be visible
  // to the enclosing object body.
  // If that scoping rule is implemented, `ReadAmbiguousLocalityPropertyNode` will go away entirely.
  // See pkl-core/src/test/files/LanguageSnippetTests/input/basic/localProperty5.pkl
  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return replace(getUnderlying(frame)).executeGeneric(frame);
  }
}
