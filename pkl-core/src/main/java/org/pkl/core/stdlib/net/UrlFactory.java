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

import org.jspecify.annotations.Nullable;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.NetModule;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.VmObjectFactory;
import org.pkl.core.stdlib.net.UrlParser.Parsed;

/** Converts between a parsed URL and {@code pkl:net}'s {@code Url}. */
final class UrlFactory {
  private UrlFactory() {}

  private static final VmObjectFactory<Parsed> factory =
      new VmObjectFactory<Parsed>(NetModule::getUrlClass)
          .addProperty("scheme", parsed -> VmNull.lift(parsed.scheme()))
          .addProperty("userInfo", parsed -> VmNull.lift(parsed.userInfo()))
          .addProperty("host", parsed -> VmNull.lift(parsed.host()))
          .addProperty(
              "port",
              parsed -> parsed.port() == null ? VmNull.withoutDefault() : parsed.port().longValue())
          .addStringProperty("path", Parsed::path)
          .addProperty("query", parsed -> VmNull.lift(parsed.query()))
          .addProperty("fragment", parsed -> VmNull.lift(parsed.fragment()));

  static VmTyped create(Parsed parsed) {
    return factory.create(parsed);
  }

  /** Reads the components back off {@code url}. */
  static Parsed read(VmObjectLike url) {
    if (url.hasExtraStorage()) {
      return (Parsed) url.getExtraStorage();
    }
    var port = (Long) VmNull.unwrap(VmUtils.readMember(url, Identifier.PORT));
    return new Parsed(
        readNullableString(url, Identifier.SCHEME),
        readNullableString(url, Identifier.USER_INFO),
        readNullableString(url, Identifier.HOST),
        port == null ? null : port.intValue(),
        (String) VmUtils.readMember(url, Identifier.PATH),
        readNullableString(url, Identifier.QUERY),
        readNullableString(url, Identifier.FRAGMENT));
  }

  private static @Nullable String readNullableString(VmObjectLike url, Identifier name) {
    return (String) VmNull.unwrap(VmUtils.readMember(url, name));
  }

  static String readPath(VmObjectLike url) {
    return url.hasExtraStorage()
        ? ((Parsed) url.getExtraStorage()).path()
        : (String) VmUtils.readMember(url, Identifier.PATH);
  }
}
