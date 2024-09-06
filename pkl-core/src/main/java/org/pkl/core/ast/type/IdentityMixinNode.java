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
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.PklRootNode;
import org.pkl.core.runtime.VmException;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.util.Nullable;

/** Root node for a mixin used as default value for type `Mixin<Foo>`. */
public final class IdentityMixinNode extends PklRootNode {
  private final SourceSection sourceSection;
  private final String qualifiedName;
  @Child private @Nullable TypeNode argumentTypeNode;

  public IdentityMixinNode(
      VmLanguage language,
      FrameDescriptor descriptor,
      SourceSection sourceSection,
      String qualifiedName,
      @Nullable TypeNode argumentTypeNode) {
    super(language, descriptor);
    this.qualifiedName = qualifiedName;
    this.sourceSection = sourceSection;
    this.argumentTypeNode = argumentTypeNode;
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }

  @Override
  public String getName() {
    return qualifiedName;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    var arguments = frame.getArguments();
    if (arguments.length != 3) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("wrongFunctionArgumentCount", 1, arguments.length - 2)
          .withSourceSection(sourceSection)
          .build();
    }

    try {
      var argument = arguments[2];
      if (argumentTypeNode != null) {
        return argumentTypeNode.execute(frame, argument);
      }
      return argument;
    } catch (VmTypeMismatchException e) {
      CompilerDirectives.transferToInterpreter();
      throw e.toVmException();
    } catch (Exception e) {
      CompilerDirectives.transferToInterpreter();
      if (e instanceof VmException) {
        throw e;
      } else {
        throw exceptionBuilder().bug(e.getMessage()).withCause(e).build();
      }
    }
  }
}
