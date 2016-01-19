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
package org.pkl.core.ast;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.runtime.VmTypesGen;
import org.pkl.core.runtime.VmUtils;

public abstract class ExpressionNode extends PklNode {
  protected ExpressionNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  protected ExpressionNode() {
    this(VmUtils.unavailableSourceSection());
  }

  public abstract Object executeGeneric(VirtualFrame frame);

  public long executeInt(VirtualFrame frame) throws UnexpectedResultException {
    return VmTypesGen.expectLong(executeGeneric(frame));
  }

  public double executeFloat(VirtualFrame frame) throws UnexpectedResultException {
    return VmTypesGen.expectDouble(executeGeneric(frame));
  }

  public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
    return VmTypesGen.expectBoolean(executeGeneric(frame));
  }
}
