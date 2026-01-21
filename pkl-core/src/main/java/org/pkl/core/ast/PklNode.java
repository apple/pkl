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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmTypes;
import org.pkl.core.runtime.VmUtils;

@NodeInfo(language = "Pkl")
@TypeSystemReference(VmTypes.class)
public abstract class PklNode extends Node {
  protected final SourceSection sourceSection;

  protected PklNode(SourceSection sourceSection) {
    this.sourceSection = sourceSection;
  }

  protected PklNode() {
    this(VmUtils.unavailableSourceSection());
  }

  @Override
  public SourceSection getSourceSection() {
    if (this instanceof WrapperNode wrapperNode) {
      return wrapperNode.getDelegateNode().getSourceSection();
    }
    return sourceSection;
  }

  @TruffleBoundary
  protected VmExceptionBuilder exceptionBuilder() {
    return new VmExceptionBuilder().withLocation(this);
  }

  @TruffleBoundary
  protected final String getShortName() {
    return VmUtils.getNodeInfo(this).shortName();
  }

  @Override
  @TruffleBoundary
  public String toString() {
    return String.format(
        "(%s:%d) %s",
        sourceSection.getSource().getName(),
        sourceSection.getStartLine(),
        sourceSection.getCharacters());
  }
}
