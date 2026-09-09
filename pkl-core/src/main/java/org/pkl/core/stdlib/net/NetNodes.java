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
package org.pkl.core.stdlib.net;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;

/** Backing nodes for {@code pkl:net}'s module-level members. */
public final class NetNodes {
  private NetNodes() {}

  public abstract static class encodeComponent extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(@SuppressWarnings("unused") VmTyped self, String value) {
      var out = new StringBuilder(value.length());
      PercentEncoder.encodeComponent(out, value);
      return out.toString();
    }
  }

  public abstract static class decodeComponent extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(@SuppressWarnings("unused") VmTyped self, String value) {
      return PercentEncoder.decode(value);
    }
  }

  // The constraints of `Url`. Each one is the same check the parser makes, so that a URL written or
  // amended by hand is held to exactly the parser's standard.

  public abstract static class isValidPercentEncoding extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected boolean eval(@SuppressWarnings("unused") VmTyped self, String value) {
      return UrlParser.hasValidPercentEncoding(value);
    }
  }

  public abstract static class isValidScheme extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected boolean eval(@SuppressWarnings("unused") VmTyped self, String value) {
      return UrlParser.isValidScheme(value);
    }
  }

  public abstract static class isValidHost extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected boolean eval(@SuppressWarnings("unused") VmTyped self, String value) {
      return UrlParser.isValidHost(value);
    }
  }

  public abstract static class isValidPath extends ExternalMethod2Node {
    @Specialization
    @TruffleBoundary
    protected boolean eval(
        @SuppressWarnings("unused") VmTyped self, String value, boolean hasAuthority) {
      return UrlParser.isValidPath(value, hasAuthority);
    }
  }
}
