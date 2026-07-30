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
import org.jspecify.annotations.Nullable;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmMap;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmPair;
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
      var out = new StringBuilder();
      for (var param : params) {
        appendParam(out, (String) param.getKey(), (String) VmNull.unwrap(param.getValue()));
      }
      return out.toString();
    }

    @Specialization
    @TruffleBoundary
    protected String eval(@SuppressWarnings("unused") VmTyped self, VmList params) {
      var out = new StringBuilder();
      for (var param : params) {
        var pair = (VmPair) param;
        appendParam(out, (String) pair.getFirst(), (String) VmNull.unwrap(pair.getSecond()));
      }
      return out.toString();
    }

    /**
     * https://url.spec.whatwg.org/#concept-urlencoded-serializer - appends {@code name=value} to
     * {@code out}, separated from any preceding parameter by {@code &}.
     *
     * <p>A {@code null} value emits a bare name.
     */
    @SuppressWarnings("JavadocLinkAsPlainText")
    private static void appendParam(StringBuilder out, String name, @Nullable String value) {
      if (!out.isEmpty()) {
        out.append('&');
      }
      appendEncoded(out, name);
      if (value != null) {
        out.append('=');
        appendEncoded(out, value);
      }
    }

    private static void appendEncoded(StringBuilder out, String value) {
      value.codePoints().forEach(cp -> PercentEncoder.encodeForm(cp, out));
    }
  }
}
