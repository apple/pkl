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
package org.pkl.core.stdlib;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.function.Supplier;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.member.TypeCheckedPropertyNodeGen;
import org.pkl.core.ast.member.UntypedObjectMemberNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;

public final class VmObjectFactory<E> {
  private final Supplier<VmClass> classSupplier;
  private final EconomicMap<Object, ObjectMember> members = EconomicMaps.create();
  // not static to avoid compile-time evaluation by native-image
  private final boolean isPropertyTypeChecked = IoUtils.isTestMode();

  public VmObjectFactory(Supplier<VmClass> classSupplier) {
    this.classSupplier = classSupplier;
  }

  public VmObjectFactory<E> addIntProperty(String name, IntProperty<E> impl) {
    return doAddProperty(name, new IntPropertyNode<>(impl));
  }

  public VmObjectFactory<E> addBooleanProperty(String name, BooleanProperty<E> impl) {
    return doAddProperty(name, new BooleanPropertyNode<>(impl));
  }

  public VmObjectFactory<E> addStringProperty(String name, Property<E, String> impl) {
    return doAddProperty(name, new PropertyNode<>(impl));
  }

  public VmObjectFactory<E> addValueProperty(String name, Property<E, VmValue> impl) {
    return doAddProperty(name, new PropertyNode<>(impl));
  }

  public VmObjectFactory<E> addDurationProperty(String name, Property<E, VmDuration> impl) {
    return doAddProperty(name, new PropertyNode<>(impl));
  }

  public VmObjectFactory<E> addTypedProperty(String name, Property<E, VmTyped> impl) {
    return doAddProperty(name, new PropertyNode<>(impl));
  }

  public VmObjectFactory<E> addListProperty(String name, Property<E, VmList> impl) {
    return doAddProperty(name, new PropertyNode<>(impl));
  }

  public VmObjectFactory<E> addSetProperty(String name, Property<E, VmSet> impl) {
    return doAddProperty(name, new PropertyNode<>(impl));
  }

  public VmObjectFactory<E> addMapProperty(String name, Property<E, VmMap> impl) {
    return doAddProperty(name, new PropertyNode<>(impl));
  }

  public VmObjectFactory<E> addClassProperty(String name, Property<E, VmClass> impl) {
    return doAddProperty(name, new PropertyNode<>(impl));
  }

  public <T> VmObjectFactory<E> addProperty(String name, Property<E, T> impl) {
    return doAddProperty(name, new PropertyNode<>(impl));
  }

  @TruffleBoundary
  private VmObjectFactory<E> doAddProperty(String name, ExpressionNode bodyNode) {
    var section = VmUtils.unavailableSourceSection();
    var identifier = Identifier.get(name);
    var member = new ObjectMember(section, section, VmModifier.NONE, identifier, name, false);
    var node =
        isPropertyTypeChecked
            ? TypeCheckedPropertyNodeGen.create(null, new FrameDescriptor(), member, bodyNode)
            : new UntypedObjectMemberNode(null, new FrameDescriptor(), member, bodyNode);
    member.initMemberNode(node);
    if (members.put(identifier, member) != null) {
      throw new VmExceptionBuilder()
          .bug(
              "Duplicate definition of property `"
                  + name
                  + "` for object of type `"
                  + classSupplier.get().getDisplayName()
                  + "`.")
          .build();
    }
    return this;
  }

  @TruffleBoundary
  public VmTyped create(@Nullable E extraStorage) {
    var clazz = classSupplier.get();
    assert clazz != null;

    var result =
        new VmTyped(VmUtils.createEmptyMaterializedFrame(), clazz.getPrototype(), clazz, members);
    result.setExtraStorage(extraStorage);
    return result;
  }

  @FunctionalInterface
  public interface IntProperty<E> {
    long evaluate(E extraStorage);
  }

  @FunctionalInterface
  public interface BooleanProperty<E> {
    boolean evaluate(E extraStorage);
  }

  @FunctionalInterface
  public interface Property<E, T> {
    T evaluate(E extraStorage);

    static <T> Property<T, T> identity() {
      return t -> t;
    }
  }

  private static final class IntPropertyNode<E> extends ExpressionNode {
    private final IntProperty<E> impl;

    public IntPropertyNode(IntProperty<E> impl) {
      this.impl = impl;
    }

    @Override
    public long executeInt(VirtualFrame frame) {
      return doExecute(VmUtils.getOwner(frame));
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
      return doExecute(VmUtils.getOwner(frame));
    }

    @TruffleBoundary
    @SuppressWarnings("unchecked")
    private long doExecute(VmObjectLike owner) {
      return impl.evaluate((E) owner.getExtraStorage());
    }
  }

  private static final class BooleanPropertyNode<E> extends ExpressionNode {
    private final BooleanProperty<E> impl;

    public BooleanPropertyNode(BooleanProperty<E> impl) {
      this.impl = impl;
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame) {
      return doExecute(VmUtils.getOwner(frame));
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
      return doExecute(VmUtils.getOwner(frame));
    }

    @TruffleBoundary
    @SuppressWarnings("unchecked")
    private boolean doExecute(VmObjectLike owner) {
      return impl.evaluate((E) owner.getExtraStorage());
    }
  }

  private static final class PropertyNode<E, T> extends ExpressionNode {
    private final Property<E, T> impl;

    public PropertyNode(Property<E, T> impl) {
      this.impl = impl;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
      return doExecute(VmUtils.getOwner(frame));
    }

    @TruffleBoundary
    @SuppressWarnings("unchecked")
    private T doExecute(VmObjectLike owner) {
      return impl.evaluate((E) owner.getExtraStorage());
    }
  }
}
