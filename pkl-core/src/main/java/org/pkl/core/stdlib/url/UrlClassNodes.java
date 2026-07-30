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
package org.pkl.core.stdlib.url;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.stdlib.PklName;

@PklName("Url")
public final class UrlClassNodes {
  private UrlClassNodes() {}

  public abstract static class origin extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self) {
      return VmNull.lift(recordOf(self).origin());
    }
  }

  public abstract static class segments extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected VmList eval(VmTyped self) {
      return VmList.create(recordOf(self).segments());
    }
  }

  public abstract static class searchParams extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self) {
      return SearchParamsFactory.create(recordOf(self).queryParams());
    }
  }

  public abstract static class toString extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self) {
      return recordOf(self).serialize();
    }
  }

  private static UrlRecord recordOf(VmTyped self) {
    return (UrlRecord) self.getExtraStorage();
  }
}
