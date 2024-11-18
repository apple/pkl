/*
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.stdlib.base;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import java.util.*;
import org.pkl.core.resource.Resource;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.util.ByteArrayUtils;

public final class ResourceNodes {
  private ResourceNodes() {}

  public abstract static class md5 extends ExternalPropertyNode {
    @TruffleBoundary
    @Specialization(guards = "self.hasExtraStorage()")
    protected String evalWithExtraStorage(VmTyped self) {
      var resource = (Resource) self.getExtraStorage();
      return ByteArrayUtils.md5(resource.bytes());
    }

    @TruffleBoundary
    @Specialization(guards = "!self.hasExtraStorage()")
    protected String evalWithoutExtraStorage(VmTyped self) {
      // `pkl.base#Resource` is designed to allow direct instantiation,
      // in which case it isn't backed by a `org.pkl.core.resource.Resource`.
      // It seems the best we can do here
      // is to expect `pkl.base#Resource.base64` to be set and decode it.
      var base64 = (String) VmUtils.readMember(self, Identifier.BASE64);
      var bytes = Base64.getDecoder().decode(base64);
      return ByteArrayUtils.md5(bytes);
    }
  }

  public abstract static class sha1 extends ExternalPropertyNode {
    @TruffleBoundary
    @Specialization(guards = "self.hasExtraStorage()")
    protected String evalWithExtraStorage(VmTyped self) {
      var resource = (Resource) self.getExtraStorage();
      return ByteArrayUtils.sha1(resource.bytes());
    }

    @TruffleBoundary
    @Specialization(guards = "!self.hasExtraStorage()")
    protected String evalWithoutExtraStorage(VmTyped self) {
      var base64 = (String) VmUtils.readMember(self, Identifier.BASE64);
      var bytes = Base64.getDecoder().decode(base64);
      return ByteArrayUtils.sha1(bytes);
    }
  }

  public abstract static class sha256 extends ExternalPropertyNode {
    @TruffleBoundary
    @Specialization(guards = "self.hasExtraStorage()")
    protected String evalWithExtraStorage(VmTyped self) {
      var resource = (Resource) self.getExtraStorage();
      return ByteArrayUtils.sha256(resource.bytes());
    }

    @TruffleBoundary
    @Specialization(guards = "!self.hasExtraStorage()")
    protected String evalWithoutExtraStorage(VmTyped self) {
      var base64 = (String) VmUtils.readMember(self, Identifier.BASE64);
      var bytes = Base64.getDecoder().decode(base64);
      return ByteArrayUtils.sha256(bytes);
    }
  }

  public abstract static class sha256Int extends ExternalPropertyNode {
    @TruffleBoundary
    @Specialization(guards = "self.hasExtraStorage()")
    protected long evalWithExtraStorage(VmTyped self) {
      var resource = (Resource) self.getExtraStorage();
      return ByteArrayUtils.sha256Int(resource.bytes());
    }

    @TruffleBoundary
    @Specialization(guards = "!self.hasExtraStorage()")
    protected long evalWithoutExtraStorage(VmTyped self) {
      var base64 = (String) VmUtils.readMember(self, Identifier.BASE64);
      var bytes = Base64.getDecoder().decode(base64);
      return ByteArrayUtils.sha256Int(bytes);
    }
  }
}
