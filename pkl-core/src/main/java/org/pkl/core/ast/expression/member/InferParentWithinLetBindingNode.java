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

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.expression.binary.LetExprNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.runtime.VmLanguage;

public abstract class InferParentWithinLetBindingNode extends AbstractInferParentNode {
  public InferParentWithinLetBindingNode(SourceSection sourceSection, VmLanguage language) {
    super(sourceSection, language);
  }

  protected LetExprNode getLetNode(VirtualFrame frame) {
    Node child = this;
    LetExprNode letNode = null;
    for (var node = getParent(); node != null; node = node.getParent()) {
      if (node instanceof LetExprNode let && let.getBindingNode() == child) {
        letNode = let;
        break;
      }
      child = node;
    }
    assert letNode != null
        : "AstBuilder created an InferParentWithinLetBindingNode outside of a let binding";
    return letNode;
  }

  // keep specializations in sync with other AbstractInferParentNode subclasses

  @Specialization(guards = {"typeNode.isFinalType()"})
  protected final Object evalCached(
      @SuppressWarnings("unused") VirtualFrame frame,
      @Bind("getLetNode(frame)") LetExprNode letNode,
      @Bind("letNode.getTypeNode(frame)") @SuppressWarnings("unused") TypeNode typeNode,
      @Cached(
              value =
                  "getDefaultValue(frame, typeNode, letNode.getSourceSection(), letNode.getQualifiedName())",
              neverDefault = true)
          Object defaultValue) {
    return defaultValue;
  }

  @Specialization(replaces = "evalCached")
  protected final Object eval(
      VirtualFrame frame,
      @Bind("getLetNode(frame)") LetExprNode letNode,
      @Bind("letNode.getTypeNode(frame)") TypeNode typeNode) {
    return getDefaultValue(frame, typeNode, letNode.getSourceSection(), letNode.getQualifiedName());
  }
}
