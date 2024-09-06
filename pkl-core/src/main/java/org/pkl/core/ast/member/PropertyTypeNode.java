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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.PType;
import org.pkl.core.ast.PklRootNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.TypeNode.UnknownTypeNode;
import org.pkl.core.ast.type.VmTypeMismatchException;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Nullable;

public final class PropertyTypeNode extends PklRootNode {
  private final String qualifiedPropertyName;
  @Child private TypeNode typeNode;

  private @Nullable Object defaultValue;
  private boolean defaultValueInitialized;

  @TruffleBoundary
  public PropertyTypeNode(
      VmLanguage language,
      FrameDescriptor descriptor,
      String qualifiedPropertyName,
      TypeNode childNode) {

    super(language, descriptor);
    this.qualifiedPropertyName = qualifiedPropertyName;
    this.typeNode = childNode;
  }

  public TypeNode getTypeNode() {
    return typeNode;
  }

  @Override
  public SourceSection getSourceSection() {
    return typeNode.getSourceSection();
  }

  @Override
  public String getName() {
    return qualifiedPropertyName;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    try {
      return typeNode.execute(frame, frame.getArguments()[2]);
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

  public @Nullable Object getDefaultValue() {
    if (!defaultValueInitialized) {
      defaultValue =
          typeNode.createDefaultValue(
              VmLanguage.get(this), getSourceSection(), qualifiedPropertyName);
      defaultValueInitialized = true;
    }
    return defaultValue;
  }

  public boolean isUnknownType() {
    return typeNode instanceof UnknownTypeNode;
  }

  public PType export() {
    return TypeNode.export(typeNode);
  }

  public VmTyped getMirror() {
    return TypeNode.getMirror(typeNode);
  }

  public static PType export(@Nullable PropertyTypeNode node) {
    return node != null ? node.export() : PType.UNKNOWN;
  }

  public static VmTyped getMirror(@Nullable PropertyTypeNode node) {
    return node != null ? node.getMirror() : MirrorFactories.unknownTypeFactory.create(null);
  }
}
