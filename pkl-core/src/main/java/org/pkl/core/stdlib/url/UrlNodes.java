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
import org.pkl.core.runtime.VmMap;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod1Node;

/** Backing nodes for {@code pkl:url}'s module-level members. */
public final class UrlNodes {
  private UrlNodes() {}

  public abstract static class encodeComponent extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self, String value) {
      var out = new StringBuilder(value.length());
      value.codePoints().forEach(cp -> PercentEncoder.encode(cp, PercentEncoder.COMPONENT, out));
      return out.toString();
    }
  }

  public abstract static class decodeComponent extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self, String value) {
      return PercentEncoder.percentDecode(value);
    }
  }

  public abstract static class buildQuery extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(@SuppressWarnings("unused") VmTyped self, VmMap params) {
      return FormUrlEncoder.serialize(params);
    }

    @Specialization
    @TruffleBoundary
    protected String eval(@SuppressWarnings("unused") VmTyped self, VmList params) {
      return FormUrlEncoder.serialize(params);
    }
  }
}
