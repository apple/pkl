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
package org.pkl.core.ast.internal;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.MemberLookupMode;
import org.pkl.core.ast.expression.member.InvokeMethodVirtualNode;
import org.pkl.core.ast.expression.member.InvokeMethodVirtualNodeGen;
import org.pkl.core.ast.expression.unary.UnaryExpressionNode;
import org.pkl.core.runtime.*;

@SuppressWarnings("unused")
public abstract class ToStringNode extends UnaryExpressionNode {
  protected ToStringNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  @Specialization
  protected String evalString(String value) {
    return value;
  }

  @Specialization
  @TruffleBoundary
  protected String evalInt(long value) {
    return String.valueOf(value);
  }

  @Specialization
  @TruffleBoundary
  protected String evalFloat(double value) {
    return String.valueOf(value);
  }

  @Specialization
  protected String evalBoolean(boolean value) {
    return String.valueOf(value);
  }

  @Specialization
  protected String evalTyped(
      VirtualFrame frame,
      VmTyped value,
      @Cached("createInvokeNode()") InvokeMethodVirtualNode invokeNode) {

    return (String) invokeNode.executeWith(frame, value, value.getVmClass());
  }

  @Fallback
  @Override
  @TruffleBoundary
  protected Object fallback(Object value) {
    return value.toString();
  }

  protected InvokeMethodVirtualNode createInvokeNode() {
    //noinspection ConstantConditions
    return InvokeMethodVirtualNodeGen.create(
        sourceSection,
        Identifier.TO_STRING,
        new ExpressionNode[] {},
        MemberLookupMode.EXPLICIT_RECEIVER,
        null,
        null);
  }
}
