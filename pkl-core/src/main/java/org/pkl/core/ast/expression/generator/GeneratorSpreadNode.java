/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import static org.pkl.core.runtime.BaseModule.getListingClass;
import static org.pkl.core.runtime.BaseModule.getMappingClass;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.BaseModule;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.Iterators.TruffleIterator;
import org.pkl.core.runtime.VmBytes;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmCollection;
import org.pkl.core.runtime.VmDynamic;
import org.pkl.core.runtime.VmException;
import org.pkl.core.runtime.VmException.ProgramValue;
import org.pkl.core.runtime.VmIntSeq;
import org.pkl.core.runtime.VmListing;
import org.pkl.core.runtime.VmMap;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmObject;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;

@ImportStatic(BaseModule.class)
public abstract class GeneratorSpreadNode extends GeneratorMemberNode {
  @Child private ExpressionNode iterableNode;
  private final boolean nullable;

  public GeneratorSpreadNode(
      SourceSection sourceSection, ExpressionNode iterableNode, boolean nullable) {
    super(sourceSection, false);
    this.iterableNode = iterableNode;
    this.nullable = nullable;
  }

  protected abstract void executeWithIterable(
      VirtualFrame frame, Object parent, ObjectData data, Object iterable);

  @Override
  public final void execute(VirtualFrame frame, Object parent, ObjectData data) {
    executeWithIterable(frame, parent, data, iterableNode.executeGeneric(frame));
  }

  @Specialization
  @SuppressWarnings("unused")
  protected void eval(VmObject parent, ObjectData data, VmNull iterable) {
    if (nullable) {
      return;
    }
    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder()
        .evalError("cannotIterateOverThisValue", BaseModule.getNullClass())
        .withLocation(iterableNode)
        .withHint(
            "To guard against a nullable value, use `...?` instead of `...`.\n"
                + "Try: `...?"
                + iterableNode.getSourceSection().getCharacters()
                + "`")
        .build();
  }

  @Specialization(guards = "!iterable.isTyped()")
  @SuppressWarnings("unused")
  protected void eval(VirtualFrame frame, VmDynamic parent, ObjectData data, VmObject iterable) {
    doEvalDynamic(frame, data, iterable);
  }

  @Specialization(guards = "!iterable.isTyped()")
  @SuppressWarnings("unused")
  protected void eval(VirtualFrame frame, VmListing parent, ObjectData data, VmObject iterable) {
    doEvalListing(frame, data, iterable);
  }

  @Specialization(guards = "!iterable.isTyped()")
  @SuppressWarnings("unused")
  protected void eval(VirtualFrame frame, VmMapping parent, ObjectData data, VmObject iterable) {
    doEvalMapping(frame, data, iterable);
  }

  @Specialization(guards = {"parent == getDynamicClass()", "!iterable.isTyped()"})
  @SuppressWarnings("unused")
  protected void evalDynamicClass(
      VirtualFrame frame, VmClass parent, ObjectData data, VmObject iterable) {
    doEvalDynamic(frame, data, iterable);
  }

  @Specialization(guards = {"parent == getListingClass()", "!iterable.isTyped()"})
  @SuppressWarnings("unused")
  protected void evalListingClass(
      VirtualFrame frame, VmClass parent, ObjectData data, VmObject iterable) {
    doEvalListing(frame, data, iterable);
  }

  @Specialization(guards = {"parent == getMappingClass()", "!iterable.isTyped()"})
  @SuppressWarnings("unused")
  protected void evalMappingClass(
      VirtualFrame frame, VmClass parent, ObjectData data, VmObject iterable) {
    doEvalMapping(frame, data, iterable);
  }

  @Specialization(guards = {"isTypedObjectClass(parent)", "!iterable.isTyped()"})
  protected void evalTypedClass(
      VirtualFrame frame, VmClass parent, ObjectData data, VmObject iterable) {
    doEvalTyped(frame, parent, data, iterable);
  }

  @Specialization(guards = {"!iterable.isTyped()"})
  protected void eval(VirtualFrame frame, VmTyped parent, ObjectData data, VmObject iterable) {
    doEvalTyped(frame, parent.getVmClass(), data, iterable);
  }

  @Specialization
  protected void eval(VirtualFrame frame, VmObject parent, ObjectData data, VmMap iterable) {
    doEvalMap(frame, parent.getVmClass(), data, iterable);
  }

  @Specialization
  protected void eval(VirtualFrame frame, VmClass parent, ObjectData data, VmMap iterable) {
    doEvalMap(frame, parent, data, iterable);
  }

  @Specialization
  protected void eval(VirtualFrame frame, VmObject parent, ObjectData data, VmCollection iterable) {
    doEvalCollection(frame, parent.getVmClass(), data, iterable);
  }

  @Specialization
  protected void eval(VirtualFrame frame, VmClass parent, ObjectData data, VmCollection iterable) {
    doEvalCollection(frame, parent, data, iterable);
  }

  @Specialization
  protected void eval(VirtualFrame frame, VmObject parent, ObjectData data, VmIntSeq iterable) {
    doEvalIntSeq(frame, parent.getVmClass(), data, iterable);
  }

  @Specialization
  protected void eval(VirtualFrame frame, VmClass parent, ObjectData data, VmIntSeq iterable) {
    doEvalIntSeq(frame, parent, data, iterable);
  }

  @Specialization
  protected void eval(VirtualFrame frame, VmObject parent, ObjectData data, VmBytes iterable) {
    doEvalBytes(frame, parent.getVmClass(), data, iterable);
  }

  @Specialization
  protected void eval(VirtualFrame frame, VmClass parent, ObjectData data, VmBytes iterable) {
    doEvalBytes(frame, parent, data, iterable);
  }

  @Fallback
  @SuppressWarnings("unused")
  protected void fallback(VirtualFrame frame, Object parent, ObjectData data, Object iterable) {
    CompilerDirectives.transferToInterpreter();
    var builder =
        exceptionBuilder()
            .evalError("cannotIterateOverThisValue", VmUtils.getClass(iterable))
            .withLocation(iterableNode)
            .withProgramValue("Value", iterable);
    if (iterable instanceof VmObject vmObject && vmObject.isTyped()) {
      builder.withHint(
          "`Typed` values are not iterable. If you mean to spread its members, convert it to `Dynamic` using `toDynamic()`.");
    }
    throw builder.build();
  }

  protected void doEvalDynamic(VirtualFrame frame, ObjectData data, VmObject iterable) {
    iterable.forceAndIterateMemberValues(
        (key, member, value) -> {
          if (member.isElement()) {
            data.addElement(frame, createMember(member, value), this);
          } else {
            data.addMember(frame, key, createMember(member, value), this);
          }
          return true;
        });
  }

  private void doEvalMapping(VirtualFrame frame, ObjectData data, VmObject iterable) {
    iterable.forceAndIterateMemberValues(
        (key, member, value) -> {
          if (member.isElement() || member.isProp()) {
            cannotHaveMember(BaseModule.getMappingClass(), member);
          }
          data.addMember(frame, key, createMember(member, value), this);
          return true;
        });
  }

  private void doEvalListing(VirtualFrame frame, ObjectData data, VmObject iterable) {
    iterable.forceAndIterateMemberValues(
        (key, member, value) -> {
          if (member.isEntry() || member.isProp()) {
            cannotHaveMember(getListingClass(), member);
          }
          data.addElement(frame, createMember(member, value), this);
          return true;
        });
  }

  private void doEvalTyped(VirtualFrame frame, VmClass clazz, ObjectData data, VmObject iterable) {
    iterable.forceAndIterateMemberValues(
        (key, member, value) -> {
          if (member.isElement() || member.isEntry()) {
            cannotHaveMember(clazz, member);
          }
          checkIsValidTypedProperty(clazz, member);
          data.addProperty(frame, createMember(member, value), this);
          return true;
        });
  }

  // handles both `List` and `Set`
  private void doEvalCollection(
      VirtualFrame frame, VmClass parent, ObjectData data, VmCollection iterable) {
    if (isTypedObjectClass(parent) || parent == getMappingClass()) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("cannotSpreadObject", iterable.getVmClass(), parent)
          .withHint(
              "`List` and `Set` can only be spread into objects of type `Dynamic` and `Listing`.")
          .withProgramValue("Value", iterable)
          .build();
    }
    spreadIterable(frame, data, iterable);
  }

  private void doEvalMap(VirtualFrame frame, VmClass parent, ObjectData data, VmMap iterable) {
    if (isTypedObjectClass(parent) || parent == getListingClass()) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("cannotSpreadObject", iterable.getVmClass(), parent)
          .withHint("`Map` can only be spread into objects of type `Dynamic` and `Mapping`.")
          .withProgramValue("Value", iterable)
          .build();
    }
    for (var entry : iterable) {
      var member = VmUtils.createSyntheticObjectEntry("", VmUtils.getValue(entry));
      data.addMember(frame, VmUtils.getKey(entry), member, this);
    }
  }

  private void doEvalIntSeq(
      VirtualFrame frame, VmClass parent, ObjectData data, VmIntSeq iterable) {
    if (isTypedObjectClass(parent) || parent == getMappingClass()) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("cannotSpreadObject", iterable.getVmClass(), parent)
          .withHint("`IntSeq` can only be spread into objects of type `Dynamic` and `Listing`.")
          .withProgramValue("Value", iterable)
          .build();
    }
    spreadIterable(frame, data, iterable);
  }

  private void doEvalBytes(VirtualFrame frame, VmClass parent, ObjectData data, VmBytes iterable) {
    if (isTypedObjectClass(parent) || parent == getMappingClass()) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("cannotSpreadObject", iterable.getVmClass(), parent)
          .withHint("`Bytes` can only be spread into objects of type `Dynamic` and `Listing`.")
          .withProgramValue("Value", iterable)
          .build();
    }
    spreadIterable(frame, data, iterable);
  }

  private void cannotHaveMember(VmClass clazz, ObjectMember member) {
    CompilerDirectives.transferToInterpreter();
    var builder = exceptionBuilder();
    if (member.isEntry()) {
      builder.evalError("objectCannotHaveSpreadEntry", clazz);
    } else if (member.isElement()) {
      builder.evalError("objectCannotHaveSpreadElement", clazz);
    } else {
      builder.evalError("objectCannotHaveSpreadProperty", clazz);
    }
    var exception = builder.build();
    // add the member's source section to the stack trace if it exists.
    if (member.getHeaderSection().isAvailable()) {
      exception
          .getInsertedStackFrames()
          .put(
              getRootNode().getCallTarget(),
              VmUtils.createStackFrame(member.getHeaderSection(), member.getQualifiedName()));
    }
    throw exception;
  }

  @Override
  protected VmException duplicateDefinition(Object key, ObjectMember member) {
    CompilerDirectives.transferToInterpreter();
    var exception =
        exceptionBuilder()
            .evalError(
                member.isProp() ? "objectSpreadDuplicateProperty" : "objectSpreadDuplicateEntry",
                key instanceof Identifier ? key : new ProgramValue("", key))
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

  private ObjectMember createMember(ObjectMember prototype, Object value) {
    // If there is a constant value that is equal to the target value, we can use as-is.
    // Otherwise, initialize a new member and initialize a constant value for it
    // (effectively cached).
    if (prototype.getConstantValue() == value) {
      return prototype;
    }
    var result =
        new ObjectMember(
            prototype.getSourceSection(),
            prototype.getHeaderSection(),
            prototype.getModifiers(),
            prototype.getNameOrNull(),
            prototype.getQualifiedName());
    result.initConstantValue(value);
    return result;
  }

  private void spreadIterable(VirtualFrame frame, ObjectData data, Iterable<?> iterable) {
    var iterator = new TruffleIterator<>(iterable);
    while (iterator.hasNext()) {
      var elem = iterator.next();
      var member = VmUtils.createSyntheticObjectElement(String.valueOf(data.length()), elem);
      data.addElement(frame, member, this);
    }
  }
}
