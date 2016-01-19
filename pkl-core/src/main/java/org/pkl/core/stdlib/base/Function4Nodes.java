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
package org.pkl.core.stdlib.base;

import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.ast.lambda.ApplyVmFunction4Node;
import org.pkl.core.ast.lambda.ApplyVmFunction4NodeGen;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.stdlib.ExternalMethod4Node;

public final class Function4Nodes {
  private Function4Nodes() {}

  public abstract static class apply extends ExternalMethod4Node {
    @Child private ApplyVmFunction4Node applyLambdaNode = ApplyVmFunction4NodeGen.create();

    @Specialization
    protected Object eval(VmFunction self, Object arg1, Object arg2, Object arg3, Object arg4) {
      return applyLambdaNode.execute(self, arg1, arg2, arg3, arg4);
    }
  }
}
