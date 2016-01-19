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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.util.Pair;

public final class RegexNodes {
  private RegexNodes() {}

  public abstract static class pattern extends ExternalPropertyNode {
    @Specialization
    protected String eval(VmRegex self) {
      return self.getPattern().pattern();
    }
  }

  public abstract static class groupCount extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected long eval(VmRegex self) {
      return self.getPattern().matcher("").groupCount();
    }
  }

  public abstract static class findMatchesIn extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmList eval(VmRegex self, String str) {
      var matcher = self.getPattern().matcher(str);

      var builder = VmList.EMPTY.builder();
      while (matcher.find()) {
        // -1 indicates regex match instead of group match (see comment in RegexMatchNodes)
        builder.add(RegexMatchFactory.create(Pair.of(matcher.toMatchResult(), -1)));
      }

      return builder.build();
    }
  }

  public abstract static class matchEntire extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmValue eval(VmRegex self, String str) {
      var matcher = self.getPattern().matcher(str);
      if (!matcher.matches()) return VmNull.withoutDefault();

      // -1 indicates regex match instead of group match (see comment in RegexMatchNodes)
      return RegexMatchFactory.create(Pair.of(matcher.toMatchResult(), -1));
    }
  }
}
