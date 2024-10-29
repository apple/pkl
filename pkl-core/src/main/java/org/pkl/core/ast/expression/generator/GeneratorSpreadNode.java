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

import static org.pkl.core.runtime.BaseModule.getListingClass;
import static org.pkl.core.runtime.BaseModule.getMappingClass;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.BaseModule;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmCollection;
import org.pkl.core.runtime.VmDynamic;
import org.pkl.core.runtime.VmException.ProgramValue;
import org.pkl.core.runtime.VmIntSeq;
import org.pkl.core.runtime.VmListing;
import org.pkl.core.runtime.VmMap;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmObject;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.MutableLong;

@ImportStatic(BaseModule.class)
public abstract class GeneratorSpreadNode extends GeneratorIteratingNode {
  private final boolean nullable;

  public GeneratorSpreadNode(
      SourceSection sourceSection, GeneratorIterableNode iterableNode, boolean nullable) {
    super(sourceSection, iterableNode);
    this.nullable = nullable;
  }

  protected abstract void executeWithIterable(
      VirtualFrame frame, Object parent, ObjectData data, Object iterable);

  @Override
  public final void execute(VirtualFrame frame, Object parent, ObjectData data) {
    executeWithIterable(frame, parent, data, evalIterable(frame, data));
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
  protected void eval(VmDynamic parent, ObjectData data, VmObject iterable) {
    doEvalDynamic(data, iterable);
  }

  @Specialization(guards = "!iterable.isTyped()")
  @SuppressWarnings("unused")
  protected void eval(VmListing parent, ObjectData data, VmObject iterable) {
    doEvalListing(data, iterable);
  }

  @Specialization(guards = "!iterable.isTyped()")
  @SuppressWarnings("unused")
  protected void eval(VmMapping parent, ObjectData data, VmObject iterable) {
    doEvalMapping(data, iterable);
  }

  @Specialization(guards = {"parent == getDynamicClass()", "!iterable.isTyped()"})
  @SuppressWarnings("unused")
  protected void evalDynamicClass(VmClass parent, ObjectData data, VmObject iterable) {
    doEvalDynamic(data, iterable);
  }

  @Specialization(guards = {"parent == getListingClass()", "!iterable.isTyped()"})
  @SuppressWarnings("unused")
  protected void evalListingClass(VmClass parent, ObjectData data, VmObject iterable) {
    doEvalListing(data, iterable);
  }

  @Specialization(guards = {"parent == getMappingClass()", "!iterable.isTyped()"})
  @SuppressWarnings("unused")
  protected void evalMappingClass(VmClass parent, ObjectData data, VmObject iterable) {
    doEvalMapping(data, iterable);
  }

  @Specialization(guards = {"isTypedObjectClass(parent)", "!iterable.isTyped()"})
  protected void evalTypedClass(VmClass parent, ObjectData data, VmObject iterable) {
    doEvalTyped(parent, data, iterable);
  }

  @Specialization(guards = {"!iterable.isTyped()"})
  protected void eval(VmTyped parent, ObjectData data, VmObject iterable) {
    doEvalTyped(parent.getVmClass(), data, iterable);
  }

  @Specialization
  protected void eval(VmObject parent, ObjectData data, VmMap iterable) {
    doEvalMap(parent.getVmClass(), data, iterable);
  }

  @Specialization
  protected void eval(VmClass parent, ObjectData data, VmMap iterable) {
    doEvalMap(parent, data, iterable);
  }

  @Specialization
  protected void eval(VmObject parent, ObjectData data, VmCollection iterable) {
    doEvalCollection(parent.getVmClass(), data, iterable);
  }

  @Specialization
  protected void eval(VmClass parent, ObjectData data, VmCollection iterable) {
    doEvalCollection(parent, data, iterable);
  }

  @Specialization
  protected void eval(VmObject parent, ObjectData data, VmIntSeq iterable) {
    doEvalIntSeq(parent.getVmClass(), data, iterable);
  }

  @Specialization
  protected void eval(VmClass parent, ObjectData data, VmIntSeq iterable) {
    doEvalIntSeq(parent, data, iterable);
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

  protected void doEvalDynamic(ObjectData data, VmObject iterable) {
    var length = new MutableLong(data.length);
    iterable.forceAndIterateMemberValues(
        (key, member, value) -> {
          if (member.isElement()) {
            EconomicMaps.put(data.members, length.getAndIncrement(), createMember(member, value));
          } else {
            if (EconomicMaps.put(data.members, key, createMember(member, value)) != null) {
              duplicateMember(key, member);
            }
          }
          return true;
        });
    data.length = (int) length.get();
  }

  private void doEvalMapping(ObjectData data, VmObject iterable) {
    iterable.forceAndIterateMemberValues(
        (key, member, value) -> {
          if (member.isElement() || member.isProp()) {
            cannotHaveMember(BaseModule.getMappingClass(), member);
          }
          if (EconomicMaps.put(data.members, key, createMember(member, value)) != null) {
            duplicateMember(key, member);
          }
          return true;
        });
  }

  private void doEvalListing(ObjectData data, VmObject iterable) {
    var length = new MutableLong(data.length);
    iterable.forceAndIterateMemberValues(
        (key, member, value) -> {
          if (member.isEntry() || member.isProp()) {
            cannotHaveMember(getListingClass(), member);
          }
          EconomicMaps.put(data.members, length.getAndIncrement(), createMember(member, value));
          return true;
        });
    data.length = (int) length.get();
  }

  private void doEvalTyped(VmClass clazz, ObjectData data, VmObject iterable) {
    iterable.forceAndIterateMemberValues(
        (key, member, value) -> {
          if (member.isElement() || member.isEntry()) {
            cannotHaveMember(clazz, member);
          }
          checkTypedProperty(clazz, member);
          if (EconomicMaps.put(data.members, key, createMember(member, value)) != null) {
            duplicateMember(key, member);
          }
          return true;
        });
  }

  // handles both `List` and `Set`
  private void doEvalCollection(VmClass parent, ObjectData data, VmCollection iterable) {
    if (isTypedObjectClass(parent) || parent == getMappingClass()) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("cannotSpreadObject", iterable.getVmClass(), parent)
          .withHint(
              "`List` and `Set` can only be spread into objects of type `Dynamic` and `Listing`.")
          .withProgramValue("Value", iterable)
          .build();
    }
    spreadIterable(data, iterable);
  }

  private void doEvalMap(VmClass parent, ObjectData data, VmMap iterable) {
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
      if (EconomicMaps.put(data.members, VmUtils.getKey(entry), member) != null) {
        duplicateMember(VmUtils.getKey(entry), member);
      }
    }
  }

  private void doEvalIntSeq(VmClass parent, ObjectData data, VmIntSeq iterable) {
    if (isTypedObjectClass(parent) || parent == getMappingClass()) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("cannotSpreadObject", iterable.getVmClass(), parent)
          .withHint("`IntSeq` can only be spread into objects of type `Dynamic` and `Listing`.")
          .withProgramValue("Value", iterable)
          .build();
    }
    spreadIterable(data, iterable);
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

  private void duplicateMember(Object key, ObjectMember member) {
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

  @TruffleBoundary
  private void spreadIterable(ObjectData data, Iterable<?> iterable) {
    var length = data.length;
    for (var elem : iterable) {
      var index = length++;
      var member = VmUtils.createSyntheticObjectElement(String.valueOf(index), elem);
      EconomicMaps.put(data.members, (long) index, member);
    }
    data.length = length;
  }

  protected void checkTypedProperty(VmClass clazz, ObjectMember member) {
    if (member.isLocal()) return;

    var memberName = member.getName();
    var classProperty = clazz.getProperty(memberName);
    if (classProperty == null) {
      CompilerDirectives.transferToInterpreter();
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

    if (classProperty.isConstOrFixed()) {
      CompilerDirectives.transferToInterpreter();
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
}
