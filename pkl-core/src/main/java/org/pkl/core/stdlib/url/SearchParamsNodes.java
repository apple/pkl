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
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmMap;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmPair;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.url.UrlRecord.QueryParam;

/** Backing nodes for {@code pkl:url}'s {@code SearchParams} class. */
public final class SearchParamsNodes {
  private SearchParamsNodes() {}

  public abstract static class toList extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected VmList eval(VmTyped self) {
      var params = paramsOf(self);
      var pairs = new Object[params.length];
      for (var i = 0; i < params.length; i++) {
        pairs[i] = new VmPair(params[i].name(), params[i].value());
      }
      return VmList.create(pairs);
    }
  }

  public abstract static class get extends ExternalMethod1Node {
    @Specialization
    protected Object eval(VmTyped self, String name) {
      var params = paramsOf(self);
      for (QueryParam param : params) {
        if (param.name().equals(name)) {
          return param.value();
        }
      }
      return VmNull.withoutDefault();
    }
  }

  public abstract static class has extends ExternalMethod1Node {
    @Specialization
    protected boolean eval(VmTyped self, String name) {
      var params = paramsOf(self);
      for (QueryParam param : params) {
        if (param.name().equals(name)) {
          return true;
        }
      }
      return false;
    }
  }

  public abstract static class toMap extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected VmMap eval(VmTyped self) {
      var builder = VmMap.builder();
      for (var param : paramsOf(self)) {
        // duplicate names collapse; the first value wins, as in `get()`
        if (builder.get(param.name()) == null) {
          builder.add(param.name(), param.value());
        }
      }
      return builder.build();
    }
  }

  private static QueryParam[] paramsOf(VmTyped self) {
    return (QueryParam[]) self.getExtraStorage();
  }
}
