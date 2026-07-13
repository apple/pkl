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
package org.pkl.core.ast.expression.member;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.internal.GetClassNode;
import org.pkl.core.ast.internal.GetClassNodeGen;
import org.pkl.core.ast.member.ObjectMethodNode;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmUtils;

public abstract class InferParentWithinMethodArgumentNode extends ExpressionNode {
  protected final VmLanguage language;
  protected final int argIndex;
  @CompilationFinal private @Nullable Object inferredParent;

  protected InferParentWithinMethodArgumentNode(
      SourceSection sourceSection, VmLanguage language, int argIndex) {
    super(sourceSection);
    this.language = language;
    this.argIndex = argIndex;
  }

  @Override
  public final Object executeGeneric(VirtualFrame frame) {
    if (inferredParent != null) return inferredParent;

    // remaining code only runs first time this node is executed
    // (assuming evaluation isn't continued despite errors)

    CompilerDirectives.transferToInterpreter();

    var parameterTypeDefaultValue = getParameterDefaultValue(frame);
    if (parameterTypeDefaultValue != null) {
      inferredParent = parameterTypeDefaultValue;
      return inferredParent;
    }

    throw exceptionBuilder().evalError("cannotInferParent").build();
  }

  protected abstract @Nullable Object getParameterDefaultValue(VirtualFrame frame);

  private abstract static class ClassMethod extends InferParentWithinMethodArgumentNode {

    protected ClassMethod(SourceSection sourceSection, VmLanguage language, int argIndex) {
      super(sourceSection, language, argIndex);
    }

    protected final @Nullable Object getParameterDefaultValue(VirtualFrame frame) {
      var method = getClassMethod(frame);
      var parameterTypeNode = method.getParameterTypeNode(argIndex);
      return parameterTypeNode.createDefaultValue(
          frame, language, method.getHeaderSection(), method.getQualifiedName());
    }

    protected abstract org.pkl.core.ast.member.ClassMethod getClassMethod(VirtualFrame frame);
  }

  /** Used for method calls within final/external classes that needn't be virtual */
  public static final class FinalClassMethod extends ClassMethod {
    private final Identifier methodName;
    private final int levelsUp;

    public FinalClassMethod(
        SourceSection sourceSection,
        VmLanguage language,
        int argIndex,
        Identifier methodName,
        int levelsUp) {
      super(sourceSection, language, argIndex);
      this.methodName = methodName;
      this.levelsUp = levelsUp;
    }

    protected org.pkl.core.ast.member.ClassMethod getClassMethod(VirtualFrame frame) {
      var capturedFrame = VmUtils.getFrame(frame, levelsUp);
      var owner = VmUtils.getOwner(capturedFrame);
      var method = owner.getVmClass().getDeclaredMethod(methodName);
      assert method != null;
      return method;
    }
  }

  public static final class ObjectMethod extends InferParentWithinMethodArgumentNode {
    private final Identifier methodName;
    private final int levelsUp;

    public ObjectMethod(
        SourceSection sourceSection,
        VmLanguage language,
        int argIndex,
        Identifier methodName,
        int levelsUp) {
      super(sourceSection, language, argIndex);
      this.methodName = methodName;
      this.levelsUp = levelsUp;
    }

    protected @Nullable Object getParameterDefaultValue(VirtualFrame frame) {
      var capturedFrame = VmUtils.getFrame(frame, levelsUp);
      var owner = VmUtils.getOwner(capturedFrame);
      var method = owner.getMember(methodName);
      assert method != null && method.isLocal();
      var objectMethodNode = method.getMemberNode();
      assert objectMethodNode instanceof ObjectMethodNode;
      var typeNode = ((ObjectMethodNode) objectMethodNode).getParameterTypeNode(argIndex);
      return typeNode.createDefaultValue(
          frame,
          language,
          objectMethodNode.getHeaderSection(),
          objectMethodNode.getQualifiedName());
    }
  }

  public static final class Virtual extends ClassMethod {
    private final Identifier methodName;
    @Child private GetClassNode receiverClassNode;

    public Virtual(
        SourceSection sourceSection,
        VmLanguage language,
        int argIndex,
        Identifier methodName,
        ExpressionNode receiverNode) {
      super(sourceSection, language, argIndex);
      this.methodName = methodName;
      this.receiverClassNode = GetClassNodeGen.create(receiverNode);
    }

    protected org.pkl.core.ast.member.ClassMethod getClassMethod(VirtualFrame frame) {
      var receiverClass = receiverClassNode.executeGeneric(frame);
      var method = ((VmClass) receiverClass).getMethod(methodName);
      assert method != null;
      return method;
    }
  }

  public static final class Super extends ClassMethod {
    private final Identifier methodName;

    public Super(
        SourceSection sourceSection, VmLanguage language, int argIndex, Identifier methodName) {
      super(sourceSection, language, argIndex);
      this.methodName = methodName;
    }

    @Override
    protected org.pkl.core.ast.member.ClassMethod getClassMethod(VirtualFrame frame) {
      var owner = VmUtils.getOwner(frame);
      while (owner instanceof VmFunction) {
        owner = owner.getEnclosingOwner();
      }
      assert owner != null;
      var superclass = owner.getVmClass().getSuperclass();
      assert superclass != null;
      var method = superclass.getMethod(methodName);
      assert method != null;
      return method;
    }
  }
}
