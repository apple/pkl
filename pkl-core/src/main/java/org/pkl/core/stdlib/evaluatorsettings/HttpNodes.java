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
package org.pkl.core.stdlib.evaluatorsettings;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.util.List;
import java.util.Map;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmMap;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.util.Netrc;

public class HttpNodes {
  private HttpNodes() {}

  public abstract static class netRcHeaders extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmMapping eval(VmTyped self, String text) {
      return doParse(text);
    }

    @Specialization
    @TruffleBoundary
    protected VmMapping eval(
        VmTyped self, VmTyped resource, @Cached("create()") IndirectCallNode callNode) {
      var text = (String) VmUtils.readMember(resource, Identifier.TEXT, callNode);
      return doParse(text);
    }

    private VmMapping doParse(String text) {
      List<Netrc.Entry> entries = Netrc.parse(text);
      Map<String, Map<String, List<String>>> headersMap = Netrc.toHeadersMap(entries);
      return toMapping(headersMap);
    }

    private static VmMapping toMapping(Map<String, Map<String, List<String>>> headersMap) {
      var outerBuilder = VmMap.builder();
      for (var entry : headersMap.entrySet()) {
        var globPattern = entry.getKey();
        var innerMap = entry.getValue();

        var innerBuilder = VmMap.builder();
        for (var headerEntry : innerMap.entrySet()) {
          var headerName = headerEntry.getKey();
          var headerValuesList = headerEntry.getValue();

          innerBuilder.add(headerName, VmList.create(headerValuesList).toListing());
        }

        outerBuilder.add(globPattern, innerBuilder.build().toMapping());
      }

      return outerBuilder.build().toMapping();
    }
  }
}
