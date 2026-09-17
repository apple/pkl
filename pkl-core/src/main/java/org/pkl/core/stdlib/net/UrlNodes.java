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
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.ExternalPropertyNode;

/** Backing nodes for {@code pkl:net}'s {@code Url} class. */
public final class UrlNodes {
  private UrlNodes() {}

  public abstract static class authority extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self) {
      return VmNull.lift(UrlFactory.readAuthority(self));
    }
  }

  public abstract static class pathSegments extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected VmList eval(VmTyped self) {
      return VmList.create(UrlParser.segments(UrlFactory.readPath(self)));
    }
  }

  public abstract static class queryParameters extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected VmMapping eval(VmTyped self) {
      return UrlFactory.createQueryParameters(UrlFactory.readQuery(self));
    }
  }

  public abstract static class toString extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self) {
      return UrlFactory.read(self).serialize();
    }
  }

  public abstract static class resolve extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self, String ref) {
      return resolve(self, ref);
    }

    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self, VmTyped ref) {
      return resolve(self, UrlFactory.read(ref).serialize());
    }

    @SuppressWarnings("MethodNameSameAsClassName")
    private Object resolve(VmTyped self, String ref) {
      var base = UrlFactory.read(self);
      if (base.scheme() == null) {
        throw exceptionBuilder()
            .evalError("cannotResolveAgainstRelativeUrl", base.serialize())
            .build();
      }
      var parsedRef = UrlFactory.parseOrThrow(ref, exceptionBuilder());
      return UrlFactory.create(UrlParser.resolve(base, parsedRef));
    }
  }

  public abstract static class normalize extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self) {
      return UrlFactory.create(UrlParser.normalize(UrlFactory.read(self)));
    }
  }

  public abstract static class equals extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected boolean eval(VmTyped self, VmTyped other) {
      return UrlParser.isEquivalent(UrlFactory.read(self), UrlFactory.read(other));
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
