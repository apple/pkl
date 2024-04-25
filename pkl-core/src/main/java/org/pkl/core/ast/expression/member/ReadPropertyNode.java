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
package org.pkl.core.ast.expression.member;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.MemberLookupMode;
import org.pkl.core.ast.member.ClassProperty;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Nullable;

@NodeInfo(shortName = ".")
@ImportStatic(BaseModule.class)
@NodeChild(value = "receiverNode", type = ExpressionNode.class)
public abstract class ReadPropertyNode extends ExpressionNode {
  protected final Identifier propertyName;
  private final MemberLookupMode lookupMode;
  private final boolean needsConst;
  @CompilationFinal private boolean isConstChecked;

  protected ReadPropertyNode(
      SourceSection sourceSection,
      Identifier propertyName,
      MemberLookupMode lookupMode,
      boolean needsConst) {

    super(sourceSection);
    this.propertyName = propertyName;
    this.lookupMode = lookupMode;
    this.needsConst = needsConst;

    assert !propertyName.isLocalProp() : "Must use ReadLocalPropertyNode for local properties.";
  }

  protected ReadPropertyNode(
      SourceSection sourceSection, Identifier propertyName, boolean needsConst) {
    this(sourceSection, propertyName, MemberLookupMode.EXPLICIT_RECEIVER, needsConst);
  }

  protected ReadPropertyNode(SourceSection sourceSection, Identifier propertyName) {
    this(sourceSection, propertyName, MemberLookupMode.EXPLICIT_RECEIVER, false);
  }

  // This method effectively covers `VmObject receiver` but is implemented in a more
  // efficient way. See:
  // https://www.graalvm.org/22.0/graalvm-as-a-platform/language-implementation-framework/TruffleLibraries/#strategy-2-java-interfaces
  @Specialization(guards = "receiver.getClass() == cachedClass", limit = "99")
  protected Object evalObject(
      Object receiver,
      @Cached("getVmObjectSubclassOrNull(receiver)") Class<? extends VmObjectLike> cachedClass,
      @Cached("create()") IndirectCallNode callNode) {

    var object = cachedClass.cast(receiver);
    checkConst(object);
    var result = VmUtils.readMemberOrNull(object, propertyName, true, callNode);
    if (result != null) return result;

    CompilerDirectives.transferToInterpreter();
    throw cannotFindProperty(object);
  }

  // specializations for all other types
  @Specialization(guards = "receiver.getClass() == cachedClass", limit = "99")
  protected Object evalOther(
      Object receiver,
      @Cached("receiver.getClass()") @SuppressWarnings("unused") Class<?> cachedClass,
      @Cached("resolveProperty(receiver)") ClassProperty resolvedProperty,
      @Cached("createCallNode(resolvedProperty)") DirectCallNode callNode) {

    return callNode.call(receiver, resolvedProperty.getOwner(), resolvedProperty.getName());
  }

  protected static @Nullable Class<? extends VmObjectLike> getVmObjectSubclassOrNull(Object value) {
    // OK to perform slow cast here (not a guard)
    return value instanceof VmObjectLike objectLike ? objectLike.getClass() : null;
  }

  protected ClassProperty resolveProperty(Object value) {
    var clazz = VmUtils.getClass(value);
    var propertyDef = clazz.getProperty(propertyName);
    if (propertyDef != null) return propertyDef;

    CompilerDirectives.transferToInterpreter();
    throw cannotFindProperty(clazz.getPrototype());
  }

  // This method should only be used for standard library properties implemented as nodes.
  protected static DirectCallNode createCallNode(ClassProperty resolvedProperty) {
    var callTarget = resolvedProperty.getInitializer().getCallTarget();
    return DirectCallNode.create(callTarget);
  }

  @TruffleBoundary
  private VmException cannotFindProperty(VmObjectLike receiver) {
    return exceptionBuilder()
        .cannotFindProperty(
            receiver, propertyName, true, lookupMode != MemberLookupMode.EXPLICIT_RECEIVER)
        .build();
  }

  private void checkConst(VmObjectLike receiver) {
    if (needsConst && !isConstChecked) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      var property = receiver.getVmClass().getProperty(propertyName);
      if (property == null) {
        // fall through; `cannotFindProperty` gets thrown when we attempt to read the property.
        return;
      }
      if (!property.isConst()) {
        throw exceptionBuilder().evalError("propertyMustBeConst", propertyName.toString()).build();
      }
      isConstChecked = true;
    }
  }
}
