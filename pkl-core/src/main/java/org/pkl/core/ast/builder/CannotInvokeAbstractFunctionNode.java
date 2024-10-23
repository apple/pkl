/*
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
package org.pkl.core.ast.builder;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;

public final class CannotInvokeAbstractFunctionNode extends ExpressionNode {
  private final String functionName;

  public CannotInvokeAbstractFunctionNode(SourceSection section, String functionName) {
    super(section);
    this.functionName = functionName;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder().evalError("cannotInvokeAbstractMethod", functionName).build();
  }
}
