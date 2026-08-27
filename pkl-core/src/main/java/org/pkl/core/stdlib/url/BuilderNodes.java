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
import java.util.ArrayList;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmMap;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod0Node;

/** Backing nodes for {@code pkl:url}'s {@code Builder} class. */
public final class BuilderNodes {
  private BuilderNodes() {}

  public abstract static class build extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self) {
      var host = host(self);
      if (!UrlBuilder.isAssemblableHost(host)) {
        throw exceptionBuilder().evalError("invalidUrlBuilderHost", host).build();
      }
      var assembled = assemble(self, host);
      var record = UrlParser.parse(assembled, false);
      if (record == null) {
        throw exceptionBuilder().evalError("cannotBuildUrl", assembled).build();
      }
      return UrlFactory.create(record);
    }
  }

  public abstract static class buildOrNull extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self) {
      var host = host(self);
      if (!UrlBuilder.isAssemblableHost(host)) {
        return VmNull.withoutDefault();
      }
      var record = UrlParser.parse(assemble(self, host), false);
      return record == null ? VmNull.withoutDefault() : UrlFactory.create(record);
    }
  }

  private static String host(VmTyped self) {
    return (String) VmUtils.readMember(self, Identifier.HOST);
  }

  private static String assemble(VmTyped self, String host) {
    var port = (Long) VmNull.unwrap(VmUtils.readMember(self, Identifier.PORT));
    return UrlBuilder.assemble(
        (String) VmUtils.readMember(self, Identifier.SCHEME),
        (String) VmUtils.readMember(self, Identifier.USERNAME),
        (String) VmUtils.readMember(self, Identifier.PASSWORD),
        host,
        port == null ? null : port.intValue(),
        segments(self),
        query(self),
        (String) VmNull.unwrap(VmUtils.readMember(self, Identifier.FRAGMENT)));
  }

  private static ArrayList<String> segments(VmTyped self) {
    var members = (VmList) VmUtils.readMember(self, Identifier.SEGMENTS);
    var result = new ArrayList<String>(members.getLength());
    for (var segment : members) {
      result.add((String) segment);
    }
    return result;
  }

  private static String query(VmTyped self) {
    var params = VmUtils.readMember(self, Identifier.PARAMS);
    return params instanceof VmMap map
        ? FormUrlEncoder.serialize(map)
        : FormUrlEncoder.serialize((VmList) params);
  }
}
