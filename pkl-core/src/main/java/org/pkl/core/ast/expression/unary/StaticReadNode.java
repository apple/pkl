/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.ast.expression.unary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import java.net.URI;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.LateInit;

/** Used by {@link ReadGlobNode}. */
public class StaticReadNode extends UnaryExpressionNode {
  private final URI resourceUri;

  @CompilationFinal @LateInit private Object readResult;

  public StaticReadNode(URI resourceUri) {
    super(VmUtils.unavailableSourceSection());
    assert resourceUri.isAbsolute();
    this.resourceUri = resourceUri;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    if (readResult == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      var context = VmContext.get(this);
      readResult = context.getResourceManager().read(resourceUri, this).orElse(null);
      if (readResult == null) {
        throw exceptionBuilder().evalError("cannotFindResource", resourceUri).build();
      }
    }
    return readResult;
  }
}
