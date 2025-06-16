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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import org.pkl.core.runtime.VmBytes;
import org.pkl.core.runtime.VmDataSize;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.util.ByteArrayUtils;

public final class BytesNodes {
  private BytesNodes() {}

  public abstract static class toList extends ExternalMethod0Node {
    @Specialization
    protected VmList eval(VmBytes self) {
      return self.toList();
    }
  }

  public abstract static class length extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmBytes self) {
      return self.getLength();
    }
  }

  public abstract static class size extends ExternalPropertyNode {
    @Specialization
    protected VmDataSize eval(VmBytes self) {
      return self.getSize();
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

  public abstract static class getOrNull extends ExternalMethod1Node {
    @Specialization
    protected Object eval(VmBytes self, long index) {
      if (index < 0 || index >= self.getLength()) {
        return VmNull.withoutDefault();
      }
      return self.get(index);
    }
  }

  public abstract static class decodeToString extends ExternalMethod1Node {
    @TruffleBoundary
    private String doDecode(VmBytes self, String charset) throws CharacterCodingException {
    var byteBuffer = ByteBuffer.wrap(self.getBytes());
      var decoder = Charset.forName(charset).newDecoder();
      return decoder.decode(byteBuffer).toString();
    }

    @Specialization
    protected String eval(VmBytes self, String charset) {
      try {
        return doDecode(self, charset);
      } catch (CharacterCodingException e) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder().evalError("characterCodingException", charset).build();
      }
    }
  }
}
