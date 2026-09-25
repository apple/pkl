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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.internal.GetParsedUrlNode;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.util.url.UrlFactory;
import org.pkl.core.util.url.UrlParser;
import org.pkl.core.util.url.UrlParser.Parsed;

/** Backing nodes for {@code pkl:net}'s {@code Url} class. */
public final class UrlNodes {
  private UrlNodes() {}

  public abstract static class authority extends ExternalPropertyNode {
    @Specialization(guards = "self.hasExtraStorage()")
    protected Object evalCached(VmTyped self) {
      var parsed = (Parsed) self.getExtraStorage();
      return VmNull.lift(parsed.authority());
    }

    @Specialization
    protected Object eval(VmTyped self, @Cached("create()") IndirectCallNode callNode) {
      return VmNull.lift(UrlFactory.readAuthority(self, callNode));
    }
  }

  public abstract static class hostKind extends ExternalPropertyNode {
    @Specialization(guards = "self.hasExtraStorage()")
    protected Object evalCached(VmTyped self) {
      var parsed = (Parsed) self.getExtraStorage();
      return getHostKind(parsed.host());
    }

    @Specialization
    protected Object eval(VmTyped self, @Cached("create()") IndirectCallNode callNode) {
      return getHostKind(UrlFactory.readHost(self, callNode));
    }

    private static Object getHostKind(@Nullable String host) {
      return host == null ? VmNull.withoutDefault() : UrlParser.hostKind(host);
    }
  }

  public abstract static class zoneId extends ExternalPropertyNode {
    @Specialization(guards = "self.hasExtraStorage()")
    protected Object evalCached(VmTyped self) {
      var parsed = (Parsed) self.getExtraStorage();
      return getZoneId(parsed.host());
    }

    @Specialization
    protected Object eval(VmTyped self, @Cached("create()") IndirectCallNode callNode) {
      return getZoneId(UrlFactory.readHost(self, callNode));
    }

    private static Object getZoneId(@Nullable String host) {
      return VmNull.lift(host == null ? null : UrlParser.zoneId(host));
    }
  }

  public abstract static class pathSegments extends ExternalPropertyNode {
    @Specialization(guards = "self.hasExtraStorage()")
    protected VmList evalCached(VmTyped self) {
      var parsed = (Parsed) self.getExtraStorage();
      return VmList.create(UrlParser.segments(parsed.path()));
    }

    @Specialization
    protected VmList eval(VmTyped self, @Cached("create()") IndirectCallNode callNode) {
      return VmList.create(UrlParser.segments(UrlFactory.readPath(self, callNode)));
    }
  }

  public abstract static class queryParameters extends ExternalPropertyNode {
    @Specialization(guards = "self.hasExtraStorage()")
    protected VmMapping evalCached(VmTyped self) {
      var parsed = (Parsed) self.getExtraStorage();
      return UrlFactory.createQueryParameters(parsed.query());
    }

    @Specialization
    protected VmMapping eval(VmTyped self, @Cached("create()") IndirectCallNode callNode) {
      return UrlFactory.createQueryParameters(UrlFactory.readQuery(self, callNode));
    }
  }

  public abstract static class toString extends ExternalMethod0Node {
    @Specialization(guards = "self.hasExtraStorage()")
    protected String evalCached(VmTyped self) {
      var parsed = (Parsed) self.getExtraStorage();
      return parsed.serialize();
    }

    @Specialization
    protected String eval(VmTyped self, @Cached("create()") IndirectCallNode callNode) {
      var parsed = UrlFactory.read(self, callNode);
      return parsed.serialize();
    }
  }

  public abstract static class resolve extends ExternalMethod1Node {
    @Child private GetParsedUrlNode getSelfNode = GetParsedUrlNode.create();

    @Specialization
    protected Object evalString(VmTyped self, String ref) {
      return resolve(getSelfNode.execute(self), UrlFactory.parseOrThrow(ref, this));
    }

    @Specialization
    protected Object eval(
        VmTyped self, VmTyped ref, @Cached("create()") GetParsedUrlNode getRefNode) {
      return resolve(getSelfNode.execute(self), getRefNode.execute(ref));
    }

    @SuppressWarnings("MethodNameSameAsClassName")
    @TruffleBoundary
    private Object resolve(Parsed base, Parsed ref) {
      if (base.scheme() == null) {
        throw exceptionBuilder()
            .evalError("cannotResolveAgainstRelativeUrl", base.serialize())
            .build();
      }
      return UrlFactory.create(UrlParser.resolve(base, ref));
    }
  }

  public abstract static class normalize extends ExternalMethod0Node {
    @Child private GetParsedUrlNode getSelfNode = GetParsedUrlNode.create();

    @Specialization
    protected VmTyped eval(VmTyped self) {
      return UrlFactory.create(UrlParser.normalize(getSelfNode.execute(self)));
    }
  }

  public abstract static class equals extends ExternalMethod1Node {
    @Child private GetParsedUrlNode getSelfNode = GetParsedUrlNode.create();
    @Child private GetParsedUrlNode getOtherNode = GetParsedUrlNode.create();

    @Specialization
    protected boolean eval(VmTyped self, VmTyped other) {
      return UrlParser.isEquivalent(getSelfNode.execute(self), getOtherNode.execute(other));
    }
  }

  // The constraints of `Url`. Each one only accepts a component that is already percent-encoded,
  // and is otherwise the same check the parser makes, so that a URL written or amended by hand is
  // held to exactly the standard of the parser's output.

  public abstract static class isValidUserInfo extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected boolean eval(@SuppressWarnings("unused") VmTyped self, String value) {
      return UrlParser.isValidUserInfo(value);
    }
  }

  public abstract static class isValidQuery extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected boolean eval(@SuppressWarnings("unused") VmTyped self, String value) {
      return UrlParser.isValidQueryOrFragment(value);
    }
  }

  public abstract static class isValidFragment extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected boolean eval(@SuppressWarnings("unused") VmTyped self, String value) {
      return UrlParser.isValidQueryOrFragment(value);
    }
  }

  public abstract static class isValidScheme extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected boolean eval(@SuppressWarnings("unused") VmTyped self, String value) {
      return UrlParser.isValidScheme(value);
    }
  }

  public abstract static class isValidHost extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected boolean eval(@SuppressWarnings("unused") VmTyped self, String value) {
      return UrlParser.isValidHost(value);
    }
  }

  public abstract static class isValidPath extends ExternalMethod2Node {
    @Specialization
    @TruffleBoundary
    protected boolean eval(
        @SuppressWarnings("unused") VmTyped self, String value, boolean hasAuthority) {
      return UrlParser.isValidPath(value, hasAuthority);
    }
  }
}
