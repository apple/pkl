/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.runtime.PklTags;
import org.pkl.core.runtime.VmTypesGen;
import org.pkl.core.runtime.VmUtils;

@GenerateWrapper
public abstract class ExpressionNode extends PklNode implements InstrumentableNode {
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

  @Override
  public boolean hasTag(Class<? extends Tag> tag) {
    return tag == PklTags.Expression.class;
  }

  @Override
  public boolean isInstrumentable() {
    return true;
  }

  @Override
  public WrapperNode createWrapper(ProbeNode probe) {
    return new ExpressionNodeWrapper(this, probe);
  }
}
