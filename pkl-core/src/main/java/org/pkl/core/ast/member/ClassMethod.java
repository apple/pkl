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
package org.pkl.core.ast.member;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;
import java.util.List;
import org.pkl.core.PClass;
import org.pkl.core.TypeParameter;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Nullable;

public final class ClassMethod extends ClassMember {
  private final List<TypeParameter> typeParameters;

  // null = not deprecated, "" = no/empty message in the @Deprecated body
  private final @Nullable String deprecation;

  @CompilationFinal private FunctionNode functionNode;

  public ClassMethod(
      SourceSection sourceSection,
      SourceSection headerSection,
      int modifiers,
      Identifier name,
      String qualifiedName,
      @Nullable SourceSection docComment,
      List<VmTyped> annotations,
      VmTyped owner,
      List<TypeParameter> typeParameters,
      @Nullable String deprecation) {

    super(
        sourceSection,
        headerSection,
        modifiers,
        name,
        qualifiedName,
        docComment,
        annotations,
        owner);
    this.typeParameters = typeParameters;
    this.deprecation = deprecation;
  }

  public void initFunctionNode(FunctionNode functionNode) {
    assert this.functionNode == null;
    this.functionNode = functionNode;
  }

  public CallTarget getCallTarget() {
    return functionNode.getCallTarget();
  }

  @TruffleBoundary
  private void reportDeprecation(SourceSection callSite) {
    assert deprecation != null;

    var logger = VmContext.get(null).getLogger();
    logger.warn(
        "Method `"
            + qualifiedName
            + "` is deprecated"
            + (deprecation.isEmpty() ? "" : ": " + deprecation),
        VmUtils.createStackFrame(callSite, null));
  }

  public CallTarget getCallTarget(SourceSection callSite) {
    if (deprecation != null) {
      reportDeprecation(callSite);
    }
    return functionNode.getCallTarget();
  }

  public int getParameterCount() {
    return functionNode.getParameterCount();
  }

  public @Nullable TypeNode getReturnTypeNode() {
    return functionNode.getReturnTypeNode();
  }

  @Override
  public String getCallSignature() {
    return functionNode.getCallSignature();
  }

  public VmTyped getMirror() {
    return MirrorFactories.methodFactory.create(this);
  }

  public VmSet getModifierMirrors() {
    return VmModifier.getMirrors(modifiers, false);
  }

  public VmList getTypeParameterMirrors() {
    var builder = VmList.EMPTY.builder();
    for (var typeParameter : typeParameters) {
      builder.add(MirrorFactories.typeParameterFactory.create(typeParameter));
    }
    return builder.build();
  }

  public VmMap getParameterMirrors() {
    return functionNode.getParameterMirrors();
  }

  public VmTyped getReturnTypeMirror() {
    return functionNode.getReturnTypeMirror();
  }

  public PClass.Method export(PClass owner) {
    return functionNode.export(owner, docComment, annotations, modifiers, typeParameters);
  }
}
