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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
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
    @Specialization
    protected Object eval(VmTyped self, @Cached("create()") IndirectCallNode callNode) {
      return VmNull.lift(UrlFactory.readAuthority(self, callNode));
    }
  }

  public abstract static class pathSegments extends ExternalPropertyNode {
    @Specialization
    protected VmList eval(VmTyped self) {
      return VmList.create(UrlParser.segments(UrlFactory.readPath(self)));
    }
  }

  public abstract static class queryParameters extends ExternalPropertyNode {
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
    @Specialization
    @TruffleBoundary
    protected Object evalString(
        VmTyped self,
        String ref,
        @Cached("create()") @Shared("callNode") IndirectCallNode callNode) {
      return resolve(self, UrlFactory.parseOrThrow(ref, this), callNode);
    }

    @Specialization
    @TruffleBoundary
    protected Object eval(
        VmTyped self,
        VmTyped ref,
        @Cached("create()") @Shared("callNode") IndirectCallNode callNode) {
      return resolve(self, UrlFactory.read(ref, callNode), callNode);
    }

    @SuppressWarnings("MethodNameSameAsClassName")
    private Object resolve(VmTyped self, Parsed ref, IndirectCallNode callNode) {
      var base = UrlFactory.read(self, callNode);
      if (base.scheme() == null) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder()
            .evalError("cannotResolveAgainstRelativeUrl", base.serialize())
            .build();
      }
      return UrlFactory.create(UrlParser.resolve(base, ref));
    }
  }

  public abstract static class normalize extends ExternalMethod0Node {
    @Specialization
    protected VmTyped eval(VmTyped self, @Cached("create()") IndirectCallNode callNode) {
      return UrlFactory.create(UrlParser.normalize(UrlFactory.read(self, callNode)));
    }
  }

  public abstract static class equals extends ExternalMethod1Node {
    @Specialization
    protected boolean eval(
        VmTyped self, VmTyped other, @Cached("create()") IndirectCallNode callNode) {
      return UrlParser.isEquivalent(
          UrlFactory.read(self, callNode), UrlFactory.read(other, callNode));
    }
  }

  // The constraints of `Url`. Each one is the same check the parser makes, so that a URL written or
  // amended by hand is held to exactly the parser's standard.

  public abstract static class isValidPercentEncoding extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected boolean eval(@SuppressWarnings("unused") VmTyped self, String value) {
      return UrlParser.hasValidPercentEncoding(value);
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
