/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Nullable;

@NodeInfo(language = "Pkl")
@TypeSystemReference(VmTypes.class)
public abstract class PklRootNode extends RootNode {
  protected PklRootNode(@Nullable VmLanguage language, FrameDescriptor descriptor) {
    super(language, descriptor);
  }

  public abstract SourceSection getSourceSection();

  public abstract @Nullable String getName();

  protected final Object executeBody(VirtualFrame frame, ExpressionNode bodyNode) {
    try {
      return bodyNode.executeGeneric(frame);
    } catch (VmException e) {
      CompilerDirectives.transferToInterpreter();
      throw e;
    } catch (StackOverflowError e) {
      CompilerDirectives.transferToInterpreter();
      throw new VmStackOverflowException(e);
    } catch (Exception e) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder().bug(e.getMessage()).withCause(e).build();
    }
  }

  protected VmExceptionBuilder exceptionBuilder() {
    return new VmExceptionBuilder().withLocation(this);
  }
}
