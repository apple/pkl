/**
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
package org.pkl.core.ast.member;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.ast.type.VmTypeMismatchException;
import org.pkl.core.runtime.VmException;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;

public final class LocalTypedPropertyNode extends RegularMemberNode {
  private final VmLanguage language;
  @Child private UnresolvedTypeNode unresolvedTypeNode;
  @Child @LateInit private TypeNode typeNode;
  private @Nullable Object defaultValue;
  private boolean defaultValueInitialized;

  public LocalTypedPropertyNode(
      VmLanguage language,
      FrameDescriptor descriptor,
      ObjectMember member,
      ExpressionNode bodyNode,
      UnresolvedTypeNode unresolvedTypeNode) {

    super(language, descriptor, member, bodyNode);

    this.language = language;
    this.unresolvedTypeNode = unresolvedTypeNode;
  }

  public @Nullable Object getDefaultValue() {
    if (!defaultValueInitialized) {
      defaultValue =
          typeNode.createDefaultValue(
              language, member.getHeaderSection(), member.getQualifiedName());
      defaultValueInitialized = true;
    }
    return defaultValue;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    try {
      if (typeNode == null) {
        CompilerDirectives.transferToInterpreter();
        typeNode = insert(unresolvedTypeNode.execute(frame));
        unresolvedTypeNode = null;
      }
      var result = bodyNode.executeGeneric(frame);
      return typeNode.execute(frame, result);
    } catch (VmTypeMismatchException e) {
      CompilerDirectives.transferToInterpreter();
      throw e.toVmException();
    } catch (Exception e) {
      CompilerDirectives.transferToInterpreter();
      if (e instanceof VmException) {
        throw e;
      } else {
        throw exceptionBuilder().bug(e.getMessage()).withCause(e).build();
      }
    }
  }
}
