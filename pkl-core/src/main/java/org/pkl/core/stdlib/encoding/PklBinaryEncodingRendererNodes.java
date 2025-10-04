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
package org.pkl.core.stdlib.encoding;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmBytes;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmPklBinaryEncoder;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.PklConverter;

public final class PklBinaryEncodingRendererNodes {
  public abstract static class renderDocument extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmBytes eval(VmTyped self, Object value) {
      var packer = MessagePack.newDefaultBufferPacker();
      createRenderer(self, packer).renderDocument(value);
      return new VmBytes(packer.toByteArray());
    }
  }

  public abstract static class renderValue extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmBytes eval(VmTyped self, Object value) {
      var packer = MessagePack.newDefaultBufferPacker();
      createRenderer(self, packer).renderValue(value);
      return new VmBytes(packer.toByteArray());
    }
  }

  private static VmPklBinaryEncoder createRenderer(VmTyped self, MessagePacker packer) {
    var converters = (VmMapping) VmUtils.readMember(self, Identifier.CONVERTERS);
    var converter = new PklConverter(converters);
    return new VmPklBinaryEncoder(packer, converter);
  }
}
