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
package org.pkl.core.ast.expression.generator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.PklNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmException;
import org.pkl.core.runtime.VmException.ProgramValue;
import org.pkl.core.runtime.VmUtils;

public abstract class GeneratorMemberNode extends PklNode {
  final boolean needsStoredFrame;

  protected GeneratorMemberNode(SourceSection sourceSection, boolean needsStoredFrame) {
    super(sourceSection);
    this.needsStoredFrame = needsStoredFrame;
  }

  public abstract void execute(VirtualFrame frame, Object parent, ObjectData data);

  protected VmException duplicateDefinition(Object key, ObjectMember member) {
    return exceptionBuilder()
        .evalError(
            "duplicateDefinition", key instanceof Identifier ? key : new ProgramValue("", key))
        .withSourceSection(member.getHeaderSection())
        .build();
  }

  @Idempotent
  protected static boolean isTypedObjectClass(VmClass clazz) {
    assert clazz.isInstantiable();
    return !(clazz.isListingClass() || clazz.isMappingClass() || clazz.isDynamicClass());
  }

  @Idempotent
  @SuppressWarnings("SameReturnValue")
  protected final boolean checkIsValidTypedProperty(VmClass clazz, ObjectMember member) {
    if (member.isLocal()) return true;
    var memberName = member.getName();
    var classProperty = clazz.getProperty(memberName);
    if (classProperty != null && !classProperty.isConstOrFixed()) return true;

    CompilerDirectives.transferToInterpreter();
    if (classProperty == null) {
      var exception =
          exceptionBuilder()
              .cannotFindProperty(clazz.getPrototype(), memberName, false, false)
              .build();
      if (member.getHeaderSection().isAvailable()) {
        exception
            .getInsertedStackFrames()
            .put(
                getRootNode().getCallTarget(),
                VmUtils.createStackFrame(member.getHeaderSection(), member.getQualifiedName()));
      }
      throw exception;
    }
    assert classProperty.isConstOrFixed();
    var errMsg =
        classProperty.isConst() ? "cannotAssignConstProperty" : "cannotAssignFixedProperty";
    var exception = exceptionBuilder().evalError(errMsg, memberName).build();
    if (member.getHeaderSection().isAvailable()) {
      exception
          .getInsertedStackFrames()
          .put(
              getRootNode().getCallTarget(),
              VmUtils.createStackFrame(member.getHeaderSection(), member.getQualifiedName()));
    }
    throw exception;
  }
}
