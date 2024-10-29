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
package org.pkl.core.ast.expression.generator;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.PklRootNode;
import org.pkl.core.ast.frame.WriteNthFrameSlotNode;
import org.pkl.core.ast.frame.WriteNthFrameSlotNodeGen;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.util.Nullable;

/** The iterable node of a for generator, or spread syntax. */
public class GeneratorIterableNode extends PklRootNode {

  private final int[] auxiliarySlots;
  private final SourceSection sourceSection;
  private final String qualifiedName;
  @Child private ExpressionNode bodyNode;
  @Child private WriteNthFrameSlotNode writeFrameSlotNode = WriteNthFrameSlotNodeGen.create();

  public GeneratorIterableNode(
      VmLanguage language,
      FrameDescriptor descriptor,
      int[] auxiliarySlots,
      SourceSection sourceSection,
      String qualifiedName,
      ExpressionNode bodyNode) {

    super(language, descriptor);
    this.auxiliarySlots = auxiliarySlots;
    this.sourceSection = sourceSection;
    this.qualifiedName = qualifiedName;
    this.bodyNode = bodyNode;
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }

  @Override
  public @Nullable String getName() {
    return qualifiedName;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    var forGeneratorVariables = (Object[]) frame.getArguments()[2];
    assert forGeneratorVariables.length == auxiliarySlots.length;
    for (var i = 0; i < auxiliarySlots.length; i++) {
      frame.setAuxiliarySlot(auxiliarySlots[i], forGeneratorVariables[i]);
    }
    var frameSlotValues = (Object[]) frame.getArguments()[3];
    for (var i = 0; i < frameSlotValues.length; i++) {
      if (i >= frame.getFrameDescriptor().getNumberOfSlots()) {
        break;
      }
      if (frameSlotValues[i] != null) {
        writeFrameSlotNode.executeWithSlotAndValue(frame, i, frameSlotValues[i]);
      }
    }
    return bodyNode.executeGeneric(frame);
  }
}
