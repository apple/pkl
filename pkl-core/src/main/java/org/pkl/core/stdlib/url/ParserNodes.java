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
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod1Node;

/** Backing nodes for {@code pkl:url}'s {@code Parser} class. */
public final class ParserNodes {
  private ParserNodes() {}

  /**
   * Parses {@code Parser.base}, or returns {@code null} if it is unset or does not parse.
   *
   * <p>The base is always parsed leniently.
   */
  private static @Nullable UrlRecord parseBase(@Nullable String base) {
    return base == null ? null : UrlParser.parse(base, false);
  }

  public abstract static class parse extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self, String input) {
      var base = (String) VmNull.unwrap(VmUtils.readMember(self, Identifier.BASE));
      var baseRecord = parseBase(base);
      if (base != null && baseRecord == null) {
        throw exceptionBuilder().evalError("invalidUrlParserBase", base).build();
      }
      var record = UrlParser.parse(input, baseRecord, false);
      return record == null ? VmNull.withoutDefault() : UrlFactory.create(record);
    }
  }

  public abstract static class parseStrict extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self, String input) {
      var base = (String) VmNull.unwrap(VmUtils.readMember(self, Identifier.BASE));
      var baseRecord = parseBase(base);
      if (base != null && baseRecord == null) {
        throw exceptionBuilder().evalError("invalidUrlParserBase", base).build();
      }
      var record = UrlParser.parse(input, baseRecord, true);
      return record == null ? VmNull.withoutDefault() : UrlFactory.create(record);
    }
  }
}
