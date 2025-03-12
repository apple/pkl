/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.oracle.truffle.api.dsl.Specialization;
import java.io.UnsupportedEncodingException;
import org.pkl.core.PklBugException;
import org.pkl.core.runtime.VmBytes;
import org.pkl.core.runtime.VmList;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.util.ByteArrayUtils;

public final class BytesNodes {
  private BytesNodes() {}

  public abstract static class value extends ExternalPropertyNode {
    @Specialization
    protected VmList eval(VmBytes self) {
      return self.vmList();
    }
  }

  public abstract static class length extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmBytes self) {
      return self.getBytes().length;
    }
  }

  public abstract static class base64 extends ExternalPropertyNode {
    @Specialization
    protected String eval(VmBytes self) {
      return self.base64();
    }
  }

  public abstract static class hex extends ExternalPropertyNode {
    @Specialization
    protected String eval(VmBytes self) {
      return self.hex();
    }
  }

  public abstract static class md5 extends ExternalPropertyNode {
    @Specialization
    protected String eval(VmBytes self) {
      return ByteArrayUtils.md5(self.getBytes());
    }
  }

  public abstract static class sha1 extends ExternalPropertyNode {
    @Specialization
    protected String eval(VmBytes self) {
      return ByteArrayUtils.sha1(self.getBytes());
    }
  }

  public abstract static class sha256 extends ExternalPropertyNode {
    @Specialization
    protected String eval(VmBytes self) {
      return ByteArrayUtils.sha256(self.getBytes());
    }
  }

  public abstract static class sha256Int extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmBytes self) {
      return ByteArrayUtils.sha256Int(self.getBytes());
    }
  }

  public abstract static class encodeToStringWithCharset extends ExternalMethod1Node {
    @Specialization
    protected String eval(VmBytes self, String charset) {
      try {
        return new String(self.getBytes(), charset);
      } catch (UnsupportedEncodingException e) {
        throw PklBugException.unreachableCode();
      }
    }
  }
}
