/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.ast.type;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.util.Nullable;

@NodeInfo(shortName = "is")
public final class TypeTestNode extends ExpressionNode {
  @Child private ExpressionNode valueNode;
  @Child private UnresolvedTypeNode unresolvedTypeNode;
  @Child private @Nullable TypeNode typeNode;

  public TypeTestNode(
      SourceSection sourceSection,
      ExpressionNode valueNode,
      UnresolvedTypeNode unresolvedTypeNode) {
    super(sourceSection);
    this.valueNode = valueNode;
    this.unresolvedTypeNode = unresolvedTypeNode;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return executeBoolean(frame);
  }

  @Override
  public boolean executeBoolean(VirtualFrame frame) {
    if (typeNode == null) {
      // don't compile unresolvedTypeNode.execute()
      // invalidation is done by insert()
      CompilerDirectives.transferToInterpreter();
      typeNode = insert(unresolvedTypeNode.execute(frame));
      unresolvedTypeNode = null;
    }

    Object value = valueNode.executeGeneric(frame);
    try {
      typeNode.executeEagerly(frame, value);
      return true;
    } catch (VmTypeMismatchException e) {
      return false;
    }
  }
}
