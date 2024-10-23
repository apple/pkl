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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import java.util.Arrays;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.ast.PklNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmException;
import org.pkl.core.runtime.VmException.ProgramValue;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.Nullable;

public abstract class GeneratorMemberNode extends PklNode {
  protected GeneratorMemberNode(SourceSection sourceSection) {
    super(sourceSection);
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
  protected boolean checkIsValidTypedProperty(VmClass clazz, ObjectMember member) {
    if (member.isLocal() || clazz.hasProperty(member.getName())) return true;

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder()
        .cannotFindProperty(clazz.getPrototype(), member.getName(), false, false)
        .withSourceSection(member.getHeaderSection())
        .build();
  }

  /**
   * <code>
   * x = new Mapping { for (i in IntSeq(1, 3)) for (key, value in Map(4, "Pigeon", 6, "Barn Owl")) [i *
   * key] = value.reverse() }
   * </code>
   *
   * <p>The above code results in - 1 MemberNode for `value.reverse()` - 1 ObjectMember for `[i *
   * key] = value.reverse()` - 1 ObjectData.members map with 6 identical ObjectMember values keyed
   * by `i * key` - 1 ObjectData.forBindings map with 6 distinct arrays keyed by `i * key` Each
   * array contains three elements, namely the current values for `i`, `key`, and `value`. - 1
   * VmMapping whose `members` field holds `ObjectData.members` and whose `extraStorage` field holds
   * `ObjectData.forBindings`. - 3 `FrameSlot`s for `i`, `key`, and `value`
   */
  @ValueType
  public static final class ObjectData {
    // member count is exact iff every for/when body has exactly one member
    ObjectData(int minMemberCount, int length) {
      this.members = EconomicMaps.create(minMemberCount);
      this.length = length;
    }

    final EconomicMap<Object, ObjectMember> members;

    // For-bindings keyed by object member key.
    // (There is only one ObjectMember instance per lexical member definition,
    // hence can't store a member's for-bindings there.)
    final EconomicMap<Object, Object[]> forBindings = EconomicMap.create();

    int length;

    private Object @Nullable [] currentForBindings;

    @TruffleBoundary
    Object @Nullable [] addForBinding(Object value) {
      var result = currentForBindings;
      if (currentForBindings == null) {
        currentForBindings = new Object[] {value};
      } else {
        currentForBindings = Arrays.copyOf(currentForBindings, currentForBindings.length + 1);
        currentForBindings[currentForBindings.length - 1] = value;
      }
      return result;
    }

    @TruffleBoundary
    Object @Nullable [] addForBinding(Object key, Object value) {
      var result = currentForBindings;
      if (currentForBindings == null) {
        currentForBindings = new Object[] {key, value};
      } else {
        currentForBindings = Arrays.copyOf(currentForBindings, currentForBindings.length + 2);
        currentForBindings[currentForBindings.length - 2] = key;
        currentForBindings[currentForBindings.length - 1] = value;
      }
      return result;
    }

    void persistForBindings(Object key) {
      EconomicMaps.put(forBindings, key, currentForBindings);
    }

    void resetForBindings(Object @Nullable [] bindings) {
      currentForBindings = bindings;
    }
  }
}
