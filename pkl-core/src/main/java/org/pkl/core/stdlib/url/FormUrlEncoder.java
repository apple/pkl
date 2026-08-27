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

import org.jspecify.annotations.Nullable;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmMap;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmPair;

/**
 * https://url.spec.whatwg.org/#concept-urlencoded-serializer - serializes name/value pairs as an
 * {@code application/x-www-form-urlencoded} string, without a leading {@code ?}.
 */
@SuppressWarnings("JavadocLinkAsPlainText")
final class FormUrlEncoder {
  private FormUrlEncoder() {}

  static String serialize(VmMap params) {
    var out = new StringBuilder();
    for (var param : params) {
      appendParam(out, (String) param.getKey(), (String) VmNull.unwrap(param.getValue()));
    }
    return out.toString();
  }

  static String serialize(VmList params) {
    var out = new StringBuilder();
    for (var param : params) {
      var pair = (VmPair) param;
      appendParam(out, (String) pair.getFirst(), (String) VmNull.unwrap(pair.getSecond()));
    }
    return out.toString();
  }

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
