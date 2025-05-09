/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.TypeNode.UInt8TypeAliasTypeNode;
import org.pkl.core.ast.type.VmTypeMismatchException;
import org.pkl.core.runtime.VmBytes;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.LateInit;

@NodeInfo(shortName = "Bytes()")
public final class BytesLiteralNode extends ExpressionNode {
  @Children private final ExpressionNode[] elements;

  @Child @LateInit private TypeNode typeNode;

  public BytesLiteralNode(SourceSection sourceSection, ExpressionNode[] elements) {
    super(sourceSection);
    this.elements = elements;
  }

  private TypeNode getTypeNode() {
    if (typeNode == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      typeNode = new UInt8TypeAliasTypeNode();
    }
    return typeNode;
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    var bytes = new byte[elements.length];
    var typeNode = getTypeNode();
    for (var i = 0; i < elements.length; i++) {
      var elem = elements[i];
      try {
        var result = (Long) typeNode.execute(frame, elem.executeGeneric(frame));
        bytes[i] = result.byteValue();
      } catch (VmTypeMismatchException err) {
        // optimization: don't create a new stack frame to check the type, but pretend that one
        // exists.
        err.putInsertedStackFrame(
            getRootNode().getCallTarget(),
            VmUtils.createStackFrame(elem.getSourceSection(), getRootNode().getName()));
        throw err.toVmException();
      }
    }
    return new VmBytes(bytes);
  }
}
