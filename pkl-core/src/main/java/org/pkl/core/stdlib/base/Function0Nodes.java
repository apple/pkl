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
package org.pkl.core.stdlib.base;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.pkl.core.ast.lambda.ApplyVmFunction0Node;
import org.pkl.core.ast.lambda.ApplyVmFunction0NodeGen;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.stdlib.ExternalMethod0Node;

public final class Function0Nodes {
  private Function0Nodes() {}

  public abstract static class apply extends ExternalMethod0Node {
    @Child private ApplyVmFunction0Node applyLambdaNode = ApplyVmFunction0NodeGen.create();

    @Specialization
    protected Object eval(VirtualFrame frame, VmFunction self) {
      return applyLambdaNode.execute(frame, self);
    }
  }
}
