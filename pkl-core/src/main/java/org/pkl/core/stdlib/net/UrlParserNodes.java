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
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod1Node;

/** Backing nodes for {@code pkl:net}'s {@code UrlParser} class. */
public final class UrlParserNodes {
  private UrlParserNodes() {}

  public abstract static class parse extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self, String input) {
      var base = (String) VmNull.unwrap(VmUtils.readMember(self, Identifier.BASE));
      UrlParser.Parsed parsedBase = null;
      if (base != null) {
        parsedBase = UrlParser.parse(base);
        if (parsedBase == null || parsedBase.scheme() == null) {
          throw exceptionBuilder().evalError("invalidUrlParserBase", base).build();
        }
      }
      var parsed = UrlParser.parse(input);
      if (parsed == null) {
        return VmNull.withoutDefault();
      }
      if (parsedBase == null || parsed.scheme() != null) {
        // an absolute URL stands on its own, and resolving it would only remove its dot segments;
        // without a base, a relative reference is kept as one
        return UrlFactory.create(parsed);
      }
      return UrlFactory.create(UrlParser.resolve(parsedBase, parsed));
    }
  }
}
