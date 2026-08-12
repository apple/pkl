/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.stdlib.syntax;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.formatter.Formatter;
import org.pkl.formatter.GrammarVersion;

public final class RendererNodes {
  private RendererNodes() {}

  public abstract static class render extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self, VmTyped nodeVm) {
      var grammarVersion = (String) VmUtils.readMember(self, Identifier.GRAMMAR_VERSION);
      var node = SyntaxNodes.convertVmToNode(nodeVm, SyntaxNodes.ZERO_SPAN);
      return new Formatter(GrammarVersion.valueOf(grammarVersion)).format(node);
    }
  }
}
