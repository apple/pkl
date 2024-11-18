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
package org.pkl.core.stdlib.benchmark;

import static org.pkl.core.stdlib.benchmark.BenchmarkUtils.runBenchmark;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.PklRootNode;
import org.pkl.core.ast.internal.BlackholeNode;
import org.pkl.core.ast.internal.BlackholeNodeGen;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmException;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.util.Nullable;

public final class MicrobenchmarkNodes {
  public abstract static class run extends ExternalMethod0Node {
    @TruffleBoundary
    @Specialization
    protected VmTyped eval(VmTyped self) {
      var codeMember = VmUtils.findMember(self, Identifier.EXPRESSION);
      assert codeMember != null;
      var codeMemberNode = codeMember.getMemberNode();
      if (codeMemberNode == null) {
        throw exceptionBuilder().evalError("constantMicrobenchmark").build();
      }
      var runIterationsNode =
          new RunIterationsNode(
              VmLanguage.get(this),
              new FrameDescriptor(),
              (ExpressionNode) codeMemberNode.getBodyNode().deepCopy());
      var callTarget = runIterationsNode.getCallTarget();
      return runBenchmark(self, (iterations) -> callTarget.call(self, self, iterations));
    }
  }

  public static final class RunIterationsNode extends PklRootNode {
    private @Child BlackholeNode blackholeNode;

    public RunIterationsNode(
        VmLanguage language, FrameDescriptor descriptor, ExpressionNode iterationNode) {
      super(language, descriptor);

      this.blackholeNode = BlackholeNodeGen.create(iterationNode);
    }

    @Override
    public boolean isInternal() {
      return true;
    }

    @Override
    public SourceSection getSourceSection() {
      return VmUtils.unavailableSourceSection();
    }

    @Override
    public @Nullable String getName() {
      return null;
    }

    @Override
    public @Nullable Object execute(VirtualFrame frame) {
      try {
        var repetitions = (long) frame.getArguments()[2];
        for (long i = 0; i < repetitions; i++) {
          blackholeNode.executeGeneric(frame);
        }
        LoopNode.reportLoopCount(
            this, repetitions > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) repetitions);
        return null;
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
}
