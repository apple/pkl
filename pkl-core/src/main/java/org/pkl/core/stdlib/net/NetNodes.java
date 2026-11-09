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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import org.pkl.core.ast.internal.ReadCursorValueNode;
import org.pkl.core.runtime.VmListing;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.util.url.PercentEncoder;
import org.pkl.core.util.url.UrlFactory;

/** Backing nodes for {@code pkl:net}'s module-level members. */
public final class NetNodes {
  private NetNodes() {}

  public abstract static class Url extends ExternalMethod1Node {
    @Specialization
    protected Object eval(@SuppressWarnings("unused") VmTyped self, String input) {
      return UrlFactory.create(UrlFactory.parseOrThrow(input, this));
    }
  }

  public abstract static class encodeUrlComponent extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(@SuppressWarnings("unused") VmTyped self, String value) {
      var out = new StringBuilder(value.length());
      PercentEncoder.encodeComponent(out, value);
      return out.toString();
    }
  }

  public abstract static class decodeUrlComponent extends ExternalMethod1Node {
    @Specialization
    protected String eval(@SuppressWarnings("unused") VmTyped self, String value) {
      UrlFactory.checkPercentEncoding(value, "cannotDecodeUrlComponent", this);
      return PercentEncoder.decode(value);
    }
  }

  public abstract static class buildQuery extends ExternalMethod1Node {
    @Specialization
    protected String eval(
        VirtualFrame frame,
        @SuppressWarnings("unused") VmTyped self,
        VmMapping parameters,
        @Cached("create()") ReadCursorValueNode readCursorValueNode,
        @Cached("create()") LoopConditionProfile outerLoopProfile,
        @Cached("create()") LoopConditionProfile innerLoopProfile) {
      var out = VmUtils.createBuilder();
      var count = 0;
      var isFirst = true;
      // don't use `inject()` for outer loop because mapping length can possibly transfer to
      // interpreter.
      for (var cursor = parameters.members(); outerLoopProfile.profile(cursor.advance()); ) {
        var values = (VmListing) readCursorValueNode.execute(frame, cursor);
        innerLoopProfile.profileCounted(values.getLength());
        for (var listingCursor = values.members();
            innerLoopProfile.inject(listingCursor.advance()); ) {
          count++;
          if (isFirst) {
            isFirst = false;
          } else {
            VmUtils.appendToBuilder(out, '&');
          }
          PercentEncoder.encodeForm(out, cursor.keyToString());
          VmUtils.appendToBuilder(out, '=');
          var value = (String) readCursorValueNode.execute(frame, listingCursor);
          PercentEncoder.encodeForm(out, value);
        }
      }
      LoopNode.reportLoopCount(this, count);
      return VmUtils.builderToString(out);
    }
  }

  public abstract static class parseQuery extends ExternalMethod1Node {
    @Specialization
    protected VmMapping eval(@SuppressWarnings("unused") VmTyped self, String query) {
      UrlFactory.checkPercentEncoding(query, "cannotParseQuery", this);
      return UrlFactory.createQueryParameters(query);
    }
  }
}
