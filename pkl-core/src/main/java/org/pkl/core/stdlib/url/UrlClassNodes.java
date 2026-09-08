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
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.stdlib.PklName;

@PklName("Url")
public final class UrlClassNodes {
  private UrlClassNodes() {}

  public abstract static class portOrDefault extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self) {
      var port = recordOf(self).portOrDefault();
      return port == null ? VmNull.withoutDefault() : port.longValue();
    }
  }

  public abstract static class origin extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self) {
      return VmNull.lift(recordOf(self).origin());
    }
  }

  public abstract static class segments extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected VmList eval(VmTyped self) {
      return VmList.create(recordOf(self).segments());
    }
  }

  public abstract static class searchParams extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self) {
      return SearchParamsFactory.create(recordOf(self).queryParams());
    }
  }

  public abstract static class resolve extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self, String ref) {
      return lift(UrlParser.parse(ref, recordOf(self), false));
    }
  }

  public abstract static class withScheme extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self, String scheme) {
      return lift(recordOf(self).withScheme(scheme));
    }
  }

  public abstract static class withHost extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self, Object host) {
      return lift(recordOf(self).withHost((String) VmNull.unwrap(host)));
    }
  }

  public abstract static class withPort extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self, Object port) {
      var value = (Long) VmNull.unwrap(port);
      return lift(recordOf(self).withPort(value == null ? null : value.intValue()));
    }
  }

  public abstract static class withPath extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self, String path) {
      return lift(recordOf(self).withPath(path));
    }
  }

  public abstract static class withQuery extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self, Object query) {
      var value = VmNull.unwrap(query);
      var serialized = value instanceof VmMap map ? FormUrlEncoder.serialize(map) : (String) value;
      return UrlFactory.create(recordOf(self).withQuery(serialized));
    }
  }

  public abstract static class withFragment extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self, Object fragment) {
      return UrlFactory.create(recordOf(self).withFragment((String) VmNull.unwrap(fragment)));
    }
  }

  public abstract static class toString extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self) {
      return recordOf(self).serialize();
    }
  }

  private static Object lift(@Nullable UrlRecord record) {
    return record == null ? VmNull.withoutDefault() : UrlFactory.create(record);
  }

  private static UrlRecord recordOf(VmTyped self) {
    return (UrlRecord) self.getExtraStorage();
  }
}
