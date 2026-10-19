/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.pkl.core.Member.SourceLocation;
import org.pkl.core.PClass;
import org.pkl.core.PType;
import org.pkl.core.TypeParameter;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.TypeParameterNode;
import org.pkl.core.runtime.*;
import org.pkl.core.runtime.VmType.UnknownType;
import org.pkl.core.util.CollectionUtils;
import org.pkl.core.util.Pair;

public final class FunctionNode extends RegularMemberNode {
  // Every function (and property) call passes three implicit arguments at positions
  // frame.getArguments()[0], [1]:
  // - the receiver (target) of the call, of type Object (see VmUtils.getReceiver())
  // - the owner (lexically enclosing object) of the function/property definition, of type VmTyped
  // (see VmUtils.getOwner())
  private static final int IMPLICIT_PARAM_COUNT = 2;

  private final int paramCount;
  private final int totalParamCount;
  @Children private TypeParameterNode[] typeParameters;

  @Children private final TypeNode[] parameterTypeNodes;
  private @Nullable TypeNode checkedReturnTypeNode;
  @Child private @Nullable TypeNode returnTypeNode;

  @TruffleBoundary
  public FunctionNode(
      VmLanguage language,
      FrameDescriptor descriptor,
      Member member,
      int paramCount,
      TypeNode[] parameterTypeNodes,
      @Nullable TypeNode returnTypeNode,
      boolean isReturnTypeChecked,
      TypeParameterNode[] typeParameters,
      ExpressionNode bodyNode) {

    super(language, descriptor, member, bodyNode);

    assert member instanceof ClassMethod
        || member instanceof ObjectMember // local object method
        || member instanceof Lambda;

    this.paramCount = paramCount;
    this.typeParameters = typeParameters;
    this.parameterTypeNodes = parameterTypeNodes;
    this.checkedReturnTypeNode = isReturnTypeChecked ? returnTypeNode : null;
    this.returnTypeNode = returnTypeNode;

    totalParamCount = Math.addExact(IMPLICIT_PARAM_COUNT, paramCount);
  }

  public int getParameterCount() {
    return paramCount;
  }

  public int getTypeParameterCount() {
    return typeParameters.length;
  }

  public @Nullable TypeNode getParameterTypeNode(int idx) {
    if (idx >= paramCount) return null;
    return parameterTypeNodes[idx];
  }

  public VmType getReturnType() {
    return returnTypeNode == null ? UnknownType.INSTANCE : returnTypeNode.getType();
  }

  public @Nullable TypeNode getReturnTypeNode() {
    return returnTypeNode;
  }

  @TruffleBoundary
  public String getCallSignature() {
    var sb = new StringBuilder(member.getName().toString());
    sb.append('(');
    for (var i = 0; i < Math.min(getFrameDescriptor().getNumberOfSlots(), paramCount); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(getFrameDescriptor().getSlotName(i));
    }
    sb.append(')');
    return sb.toString();
  }

  @Override
  @ExplodeLoop
  protected Object executeImpl(VirtualFrame frame) {
    var args = frame.getArguments();
    var totalArgCount = args.length;
    if (totalArgCount != totalParamCount + typeParameters.length) {
      CompilerDirectives.transferToInterpreter();
      throw wrongArgumentCount(totalArgCount - typeParameters.length - IMPLICIT_PARAM_COUNT);
    }

    for (var i = 0; i < typeParameters.length; i++) {
      var typeArg = args[IMPLICIT_PARAM_COUNT + i];
      typeParameters[i].executeAndSet(frame, typeArg);
    }

    for (var i = 0; i < parameterTypeNodes.length; i++) {
      var argument = args[IMPLICIT_PARAM_COUNT + typeParameters.length + i];
      parameterTypeNodes[i].executeAndSet(frame, argument);
    }

    var result = bodyNode.executeGeneric(frame);

    if (checkedReturnTypeNode != null) {
      return checkedReturnTypeNode.execute(frame, result);
    } else if (returnTypeNode != null && args[totalArgCount - 1] != null) {
      // Return types are not typically checked for stdlib methods (isReturnTypeChecked == false)
      // but for generic stdlib methods (e.g. `List.map()`) when they are called with type args,
      // this behavior needs to be overridden so the type arg check on return type still runs.
      // otherwise, this code would succeed instead of failing:
      // ```
      // res = List("foo", "hello", "a").map::<String>((it) -> it.length)
      // ```
      // Assumption: this does not need to account for type args in enclosing frames because
      // no type parameters exist in the stdlib that would require this.
      // Assumption-breaking pattern example: snippet `callWithTypeArguments.pkl` property
      // `lexical.nestedInMethodParam`.
      return returnTypeNode.execute(frame, result);
    }

    return result;
  }

  public VmMap getParameterMirrors() {
    var builder = VmMap.builder();
    for (var i = 0; i < paramCount; i++) {
      var parameterName = getFrameDescriptor().getSlotName(i).toString();
      builder.add(
          parameterName,
          MirrorFactories.methodParameterFactory.create(
              Pair.of(parameterName, parameterTypeNodes[i].getMirror())));
    }
    return builder.build();
  }

  public VmTyped getReturnTypeMirror() {
    return TypeNode.getMirror(returnTypeNode);
  }

  public PClass.Method export(
      PClass owner,
      SourceSection @Nullable [] docComment,
      List<VmTyped> annotations,
      int modifiers,
      List<TypeParameter> typeParameters) {

    var parameters = CollectionUtils.<String, PType>newLinkedHashMap(paramCount);
    for (var i = 0; i < paramCount; i++) {
      var slotName = getFrameDescriptor().getSlotName(i);
      var paramName = slotName == Identifier.ILLEGAL ? "_#" + i : slotName.toString();
      parameters.put(paramName, TypeNode.export(parameterTypeNodes[i]));
    }

    var result =
        new PClass.Method(
            owner,
            VmUtils.exportDocComment(docComment),
            new SourceLocation(getHeaderSection().getStartLine(), getSourceSection().getEndLine()),
            VmModifier.export(modifiers, false),
            VmUtils.exportAnnotations(annotations),
            member.getName().toString(),
            typeParameters,
            parameters,
            TypeNode.export(returnTypeNode));

    for (var parameter : typeParameters) {
      // works because export() is called just once per FunctionNode (because PClass is cached)
      parameter.initOwner(result);
    }

    return result;
  }

  private VmException wrongArgumentCount(int argCount) {
    assert argCount != paramCount;

    return exceptionBuilder()
        .evalError("wrongFunctionArgumentCount", paramCount, argCount)
        .withSourceSection(member.getHeaderSection())
        .build();
  }
}
