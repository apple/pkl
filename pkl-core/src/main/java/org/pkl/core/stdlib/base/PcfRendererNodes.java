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
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.PklConverter;

public final class PcfRendererNodes {
  private PcfRendererNodes() {}

  public abstract static class renderDocument extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self, Object value) {
      var builder = new StringBuilder();
      createRenderer(self, builder).renderDocument(value);
      return builder.toString();
    }
  }

  public abstract static class renderValue extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self, Object value) {
      var builder = new StringBuilder();
      createRenderer(self, builder).renderValue(value);
      return builder.toString();
    }
  }

  private static PcfRenderer createRenderer(VmTyped self, StringBuilder builder) {
    var indent = (String) VmUtils.readMember(self, Identifier.INDENT);
    var converters = (VmMapping) VmUtils.readMember(self, Identifier.CONVERTERS);
    var omitNullProperties = (boolean) VmUtils.readMember(self, Identifier.OMIT_NULL_PROPERTIES);
    var useCustomStringDelimiters =
        (boolean) VmUtils.readMember(self, Identifier.USE_CUSTOM_STRING_DELIMITERS);
    var converter = new PklConverter(converters);
    return new PcfRenderer(
        builder, indent, converter, omitNullProperties, useCustomStringDelimiters);
  }
}
