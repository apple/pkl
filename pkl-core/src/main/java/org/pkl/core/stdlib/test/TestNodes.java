/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.stdlib.test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import java.util.stream.Collectors;
import org.pkl.core.ast.lambda.ApplyVmFunction0Node;
import org.pkl.core.ast.lambda.ApplyVmFunction0NodeGen;
import org.pkl.core.runtime.*;
import org.pkl.core.runtime.VmExceptionRenderer;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.PklName;

public final class TestNodes {
  private static final VmExceptionRenderer testNodeRenderer =
      new VmExceptionRenderer(null, false, false);

  private TestNodes() {}

  @PklName("catch")
  public abstract static class catchMethod extends ExternalMethod1Node {
    @Child private ApplyVmFunction0Node applyLambdaNode = ApplyVmFunction0NodeGen.create();

    @Specialization
    protected String eval(@SuppressWarnings("unused") VmTyped self, VmFunction function) {
      try {
        applyLambdaNode.execute(function);
      } catch (VmException e) {
        return render(e);
      }
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder().evalError("expectedAnException").build();
    }
  }

  public abstract static class catchOrNull extends ExternalMethod1Node {
    @Child private ApplyVmFunction0Node applyLambdaNode = ApplyVmFunction0NodeGen.create();

    @Specialization
    protected Object eval(@SuppressWarnings("unused") VmTyped self, VmFunction function) {
      try {
        applyLambdaNode.execute(function);
      } catch (VmException e) {
        return render(e);
      }
      return VmNull.withoutDefault();
    }
  }

  @TruffleBoundary
  private static String render(VmException e) {
    return testNodeRenderer
        .render(e)
        .lines()
        .skip(1) // remove meaningless header line
        .collect(Collectors.joining(" ")) // turn into single line
        .strip();
  }
}
