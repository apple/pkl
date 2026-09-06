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
package org.pkl.core.ast.expression.member;

import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.member.Method;
import org.pkl.core.ast.member.ObjectMethodNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.runtime.VmLanguage;

public abstract class AbstractInferParentFromMethodNode extends AbstractInferParentNode {

  public AbstractInferParentFromMethodNode(SourceSection sourceSection, VmLanguage language) {
    super(sourceSection, language);
  }

  protected abstract Method getMethod(VirtualFrame frame);

  protected abstract @Nullable TypeNode getTypeNode(VirtualFrame frame, Method method);

  @Idempotent
  protected boolean isFinalType(Method method, @Nullable TypeNode typeNode) {
    return method instanceof ObjectMethodNode || (typeNode != null && typeNode.isFinalType());
  }
}
