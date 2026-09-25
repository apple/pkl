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
package org.pkl.core.util.url;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import org.jspecify.annotations.Nullable;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.NetModule;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmObjectBuilder;
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.VmObjectFactory;
import org.pkl.core.util.url.UrlParser.Parsed;
import org.pkl.core.util.url.UrlParser.Result;

/** Converts between a parsed URL and {@code pkl:net}'s {@code Url}. */
public final class UrlFactory {
  private UrlFactory() {}

  private static final VmObjectFactory<Parsed> factory =
      new VmObjectFactory<Parsed>(NetModule::getUrlClass)
          .addProperty("scheme", parsed -> VmNull.lift(parsed.scheme()))
          .addProperty("rawUserInfo", parsed -> VmNull.lift(parsed.userInfo()))
          .addProperty("rawHost", parsed -> VmNull.lift(parsed.host()))
          .addProperty(
              "port",
              parsed -> parsed.port() == null ? VmNull.withoutDefault() : parsed.port().longValue())
          .addStringProperty("rawPath", Parsed::path)
          .addProperty("rawQuery", parsed -> VmNull.lift(parsed.query()))
          .addProperty("rawFragment", parsed -> VmNull.lift(parsed.fragment()));

  public static VmTyped create(Parsed parsed) {
    return factory.create(parsed);
  }

  /** Parses {@code input} as a URI reference. */
  @TruffleBoundary
  public static Parsed parseOrThrow(String input, Node node) {
    var result = UrlParser.parse(input);
    if (result instanceof Result.Failure failure) {
      throw new VmExceptionBuilder()
          .withLocation(node)
          .evalError("cannotParseUrl", input)
          .withHint(failure.hint())
          .build();
    }
    return ((Result.Success) result).url();
  }

  @TruffleBoundary
  public static void checkPercentEncoding(String value, String messageKey, Node node) {
    var failure = UrlParser.percentEncodingFailure(value);
    if (failure != null) {
      throw new VmExceptionBuilder()
          .withLocation(node)
          .evalError(messageKey, value)
          .withHint(failure.hint())
          .build();
    }
  }

  /** Reads the components back off {@code url}. */
  public static Parsed read(VmTyped url, IndirectCallNode callNode) {
    if (url.hasExtraStorage()) {
      return (Parsed) url.getExtraStorage();
    }
    var port = (Long) VmNull.unwrap(VmUtils.readMember(url, Identifier.PORT, callNode));
    var parsed =
        new Parsed(
            readNullableString(url, Identifier.SCHEME, callNode),
            readNullableString(url, Identifier.RAW_USER_INFO, callNode),
            readNullableString(url, Identifier.RAW_HOST, callNode),
            port == null ? null : port.intValue(),
            (String) VmUtils.readMember(url, Identifier.RAW_PATH),
            readNullableString(url, Identifier.RAW_QUERY, callNode),
            readNullableString(url, Identifier.RAW_FRAGMENT, callNode));
    url.setExtraStorage(parsed);
    return parsed;
  }

  private static @Nullable String readNullableString(
      VmObjectLike url, Identifier name, IndirectCallNode callNode) {
    return (String) VmNull.unwrap(VmUtils.readMember(url, name, callNode));
  }

  public static String readPath(VmObjectLike url, IndirectCallNode callNode) {
    return (String) VmUtils.readMember(url, Identifier.RAW_PATH, callNode);
  }

  public static @Nullable String readHost(VmObjectLike url, IndirectCallNode callNode) {
    return readNullableString(url, Identifier.RAW_HOST, callNode);
  }

  public static @Nullable String readQuery(VmObjectLike url, IndirectCallNode callNode) {
    return readNullableString(url, Identifier.RAW_QUERY, callNode);
  }

  /** The parameters of {@code query}, as {@code Mapping<String, Listing<String>>}. */
  @TruffleBoundary
  public static VmMapping createQueryParameters(String query) {
    var builder = new VmObjectBuilder();
    for (var parameter : UrlParser.queryParameters(query).entrySet()) {
      var values = new VmObjectBuilder(parameter.getValue().size());
      for (var value : parameter.getValue()) {
        values.addElement(value);
      }
      builder.addEntry(parameter.getKey(), values.toListing());
    }
    return builder.toMapping();
  }

  /** Reads the authority off {@code url}, or {@code null} if it has none. */
  public static @Nullable String readAuthority(VmObjectLike url, IndirectCallNode callNode) {
    var host = readNullableString(url, Identifier.RAW_HOST, callNode);
    if (host == null) {
      return null;
    }
    var port = (Long) VmNull.unwrap(VmUtils.readMember(url, Identifier.PORT));
    return UrlParser.serializeAuthority(
        readNullableString(url, Identifier.RAW_USER_INFO, callNode),
        host,
        port == null ? null : port.intValue());
  }
}
