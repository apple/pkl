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
import org.pkl.core.runtime.VmListing;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod1Node;

/** Backing nodes for {@code pkl:net}'s module-level members. */
public final class NetNodes {
  private NetNodes() {}

  public abstract static class Url extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(@SuppressWarnings("unused") VmTyped self, String input) {
      return UrlFactory.create(UrlFactory.parseOrThrow(input, exceptionBuilder()));
    }
  }

  public abstract static class encodeUrlComponent extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(@SuppressWarnings("unused") VmTyped self, String value) {
      var out = new StringBuilder(value.length());
      PercentEncoder.encodeComponent(out, value);
      return out.toString();
    }
  }

  public abstract static class decodeUrlComponent extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(@SuppressWarnings("unused") VmTyped self, String value) {
      return PercentEncoder.decode(value);
    }
  }

  public abstract static class buildQuery extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(@SuppressWarnings("unused") VmTyped self, VmMapping parameters) {
      var out = new StringBuilder();
      parameters.forceAndIterateMemberValues(
          (name, def, values) -> {
            ((VmListing) values)
                .forceAndIterateMemberValues(
                    (index, valueDef, value) -> {
                      if (!out.isEmpty()) {
                        out.append('&');
                      }
                      PercentEncoder.encodeForm(out, (String) name);
                      out.append('=');
                      PercentEncoder.encodeForm(out, (String) value);
                      return true;
                    });
            return true;
          });
      return out.toString();
    }
  }

  public abstract static class parseQuery extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmMapping eval(@SuppressWarnings("unused") VmTyped self, String query) {
      return UrlFactory.createQueryParameters(query);
    }
  }
}
