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
package org.pkl.core.ast.member;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.PklRootNode;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmTyped;

public final class ModuleNode extends PklRootNode {
  private final SourceSection sourceSection;
  private final String moduleName;
  private @Child ExpressionNode moduleNode;

  public ModuleNode(
      VmLanguage language,
      SourceSection sourceSection,
      String moduleName,
      ExpressionNode moduleNode) {

    super(language, new FrameDescriptor());
    this.sourceSection = sourceSection;
    this.moduleName = moduleName;
    this.moduleNode = moduleNode;
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }

  @Override
  public String getName() {
    return moduleName;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    var module = executeBody(frame, moduleNode);
    if (module instanceof VmClass) {
      return ((VmClass) module).getPrototype();
    }

    assert module instanceof VmTyped;
    return module;
  }
}
