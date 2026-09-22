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
package org.pkl.core.ast.type;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.pkl.core.PType;
import org.pkl.core.PklBugException;
import org.pkl.core.StackFrame;
import org.pkl.core.TypeParameter;
import org.pkl.core.ast.*;
import org.pkl.core.ast.expression.primary.GetModuleNode;
import org.pkl.core.ast.expression.primary.GetReceiverClassNode;
import org.pkl.core.ast.expression.primary.GetReceiverNode;
import org.pkl.core.ast.frame.WriteFrameSlotNode;
import org.pkl.core.ast.frame.WriteFrameSlotNodeGen;
import org.pkl.core.ast.internal.SyntheticNode;
import org.pkl.core.ast.member.DefaultPropertyBodyNode;
import org.pkl.core.ast.member.ListingOrMappingTypeCastNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.member.UntypedObjectMemberNode;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.VmObjectFactory;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.EconomicSets;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.MutableBoolean;
import org.pkl.core.util.MutableReference;

public abstract class TypeNode extends PklNode {
  @CompilationFinal private @Nullable VmType type;

  /** Type node that corresponds to a user-defined class (or module class). */
  public interface UserClassTypeNode {
    VmType getType();
  }

  protected TypeNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  protected abstract VmType doGetType();

  public VmType getType() {
    if (type == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      type = doGetType();
    }
    return type;
  }

  @Override
  public final Node deepCopy() {
    // Reset cached type after deepCopy
    // This avoids incorrect getType() returns in this case:
    // ```
    // typealias List2<E> = List<E>
    // res1: List2<Int>
    // ```
    // 1. Evaluator.evaluateSchema runs
    // 2. List2 alias is exported: VmTypeAlias -> TypeAlias, body is exported TypeNode -> VmType
    // (cached) -> PType
    // 3. res1 property is exported: TypeAliasTypeNode is exported -> aliasedType is exported
    // (post-instantiation deepCopy), but includes copied cached VmType
    var copy = (TypeNode) super.deepCopy();
    copy.type = null;
    return copy;
  }

  public boolean isNoopTypeCheck() {
    return false;
  }

  public abstract FrameSlotKind getFrameSlotKind();

  /**
   * Initializes this node's frame slot. Called if this node is a function/method parameter type.
   * Kept separate from constructor so that {@link TypeAliasTypeNode} can initialize frame slot of
   * its cloned child node.
   */
  public abstract TypeNode initWriteSlotNode(int slot);

  /**
   * Checks if {@code value} conforms to this type.
   *
   * <p>Possibly returns a new object with type-casted members, in the case of {@link
   * MappingTypeNode} or {@link ListingTypeNode}.
   *
   * <p>If {@link VmLocalContext#shouldEagerTypecheck()} is true, this method will always do an
   * eager check.
   *
   * <p>If not, throws a {@link VmTypeMismatchException}.
   */
  public final Object execute(VirtualFrame frame, Object value) {
    var localContext = VmLanguage.get(this).localContext.get();
    if (localContext.shouldEagerTypecheck()) {
      return executeEagerly(frame, value);
    }
    return executeLazily(frame, value);
  }

  /**
   * Checks if {@code value} conforms to this type, and possibly casts it in the case of {@link
   * MappingTypeNode} or {@link ListingTypeNode}.
   */
  protected abstract Object executeLazily(VirtualFrame frame, Object value);

  /**
   * Checks if {@code value} conforms to this type, and possibly casts its value.
   *
   * <p>If {@code value} is conforming, sets {@code slot} to {@code value}. Otherwise, throws a
   * {@link VmTypeMismatchException}.
   */
  public abstract Object executeAndSet(VirtualFrame frame, Object value);

  /**
   * Checks if {@code value} conforms to this type.
   *
   * <p>In the case of a parameterized {@link VmObject} (e.g. {@link VmListing}), shallow-force and
   * check its members.
   */
  public Object executeEagerly(VirtualFrame frame, Object value) {
    return executeLazily(frame, value);
  }

  // method arguments are used when default value contains a root node
  public @Nullable Object createDefaultValue(
      VirtualFrame frame,
      VmLanguage language,
      // header section of the property or method that carries the type annotation
      SourceSection headerSection,
      // qualified name of the property or method that carries the type annotation
      String qualifiedName) {
    return null;
  }

  @Idempotent
  public final boolean isFinalType() {
    var ret = new MutableBoolean(true);
    acceptTypeNode(
        true,
        typeNode -> {
          // assumption: don't need to worry about `NonFinalClassTypeNode`
          if (typeNode instanceof NonFinalSelfTypeNode) {
            ret.set(false);
            return false;
          }
          return true;
        });
    return ret.get();
  }

  public final boolean isSelfType() {
    var ret = new MutableBoolean(false);
    acceptTypeNode(
        true,
        typeNode -> {
          if (typeNode instanceof NonFinalSelfTypeNode || typeNode instanceof FinalSelfTypeNode) {
            ret.set(true);
            return false;
          }
          return true;
        });
    return ret.get();
  }

  /** Visit child type nodes of this type. */
  protected abstract boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer);

  protected VmTypeMismatchException constraintException(Object value, SourceSection sourceSection) {
    throw new VmTypeMismatchException.Constraint(
        sourceSection,
        value,
        sourceSection,
        Map.of(new SyntheticNode(sourceSection), List.of(false)));
  }

  public static TypeNode forClass(SourceSection sourceSection, VmClass clazz) {
    return clazz.isClosed()
        ? new FinalClassTypeNode(sourceSection, clazz)
        : TypeNodeFactory.NonFinalClassTypeNodeGen.create(sourceSection, clazz);
  }

  public static PType export(@Nullable TypeNode node) {
    return node != null ? node.getType().export() : PType.UNKNOWN;
  }

  public static VmTyped getMirror(@Nullable TypeNode node) {
    return node != null ? node.getMirror() : MirrorFactories.unknownTypeFactory.create(null);
  }

  public static VmList getMirrors(TypeNode[] nodes) {
    var builder = VmList.EMPTY.builder();
    for (var node : nodes) {
      builder.add(node.getMirror());
    }
    return builder.build();
  }

  public VmTyped getMirror() {
    return MirrorFactories.classTypeFactory.create(this);
  }

  public VmList getTypeArgumentMirrors() {
    return VmList.EMPTY;
  }

  protected final VmTypeMismatchException typeMismatch(Object actualValue, Object expectedType) {
    return new VmTypeMismatchException.Simple(sourceSection, actualValue, expectedType);
  }

  /**
   * Base class for types whose `executeAndSet` method assigns values to slots with
   * `frame.setXYZ(slot, value)`.
   */
  public abstract static class FrameSlotTypeNode extends TypeNode {
    @CompilationFinal protected int slot = -1;

    protected FrameSlotTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    public TypeNode initWriteSlotNode(int slot) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      this.slot = slot;
      return this;
    }
  }

  public abstract static class IntSlotTypeNode extends FrameSlotTypeNode {
    protected IntSlotTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    public final FrameSlotKind getFrameSlotKind() {
      return FrameSlotKind.Long;
    }

    @Override
    public final Object executeAndSet(VirtualFrame frame, Object value) {
      execute(frame, value);
      frame.setLong(slot, (long) value);
      return value;
    }
  }

  public abstract static class ObjectSlotTypeNode extends FrameSlotTypeNode {
    protected ObjectSlotTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    public final FrameSlotKind getFrameSlotKind() {
      return FrameSlotKind.Object;
    }

    @Override
    public final Object executeAndSet(VirtualFrame frame, Object value) {
      var result = execute(frame, value);
      frame.setObject(slot, result);
      return result;
    }
  }

  /**
   * Base class for types whose `executeAndSet` method assigns values to slots with a
   * `WriteFrameSlotNode`.
   */
  public abstract static class WriteFrameSlotTypeNode extends TypeNode {
    @Child @LateInit private WriteFrameSlotNode writeSlotNode;

    protected WriteFrameSlotTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    public final FrameSlotKind getFrameSlotKind() {
      return FrameSlotKind.Illegal;
    }

    @Override
    public TypeNode initWriteSlotNode(int slot) {
      writeSlotNode = WriteFrameSlotNodeGen.create(VmUtils.unavailableSourceSection(), slot, null);
      return this;
    }

    @Override
    public final Object executeAndSet(VirtualFrame frame, Object value) {
      var result = executeLazily(frame, value);
      writeSlotNode.executeWithValue(frame, result);
      return result;
    }
  }

  /** The `unknown` type. */
  public static final class UnknownTypeNode extends WriteFrameSlotTypeNode {
    public UnknownTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    protected VmType doGetType() {
      return VmType.UnknownType.INSTANCE;
    }

    @Override
    public boolean isNoopTypeCheck() {
      return true;
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      // do nothing
      return value;
    }

    public VmTyped getMirror() {
      return MirrorFactories.unknownTypeFactory.create(null);
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  /** The `nothing` type. */
  public static final class NothingTypeNode extends TypeNode {
    public NothingTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    protected VmType doGetType() {
      return VmType.NothingType.INSTANCE;
    }

    @Override
    public TypeNode initWriteSlotNode(int slot) {
      // do nothing
      return this;
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      CompilerDirectives.transferToInterpreter();
      throw new VmTypeMismatchException.Nothing(sourceSection, value);
    }

    @Override
    public Object executeAndSet(VirtualFrame frame, Object value) {
      executeLazily(frame, value);
      // guaranteed to never run (execute will always throw).
      CompilerDirectives.transferToInterpreter();
      throw PklBugException.unreachableCode();
    }

    @Override
    public FrameSlotKind getFrameSlotKind() {
      return FrameSlotKind.Illegal;
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.nothingTypeFactory.create(null);
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  /** The `module` or `this` type for a final module or class. */
  public static final class FinalSelfTypeNode extends ObjectSlotTypeNode {
    private final VmClass clazz;
    private final Function<VmClass, VmType> typeConstructor;
    private final VmObjectFactory<Void> mirrorFactory;

    private FinalSelfTypeNode(
        SourceSection sourceSection,
        VmClass clazz,
        Function<VmClass, VmType> typeConstructor,
        VmObjectFactory<Void> mirrorFactory) {
      super(sourceSection);
      this.clazz = clazz;
      this.typeConstructor = typeConstructor;
      this.mirrorFactory = mirrorFactory;
    }

    public static FinalSelfTypeNode moduleType(SourceSection sourceSection, VmClass clazz) {
      return new FinalSelfTypeNode(
          sourceSection, clazz, VmType.FinalModuleType::new, MirrorFactories.moduleTypeFactory);
    }

    public static FinalSelfTypeNode thisType(SourceSection sourceSection, VmClass clazz) {
      return new FinalSelfTypeNode(
          sourceSection, clazz, VmType.FinalThisType::new, MirrorFactories.thisTypeFactory);
    }

    @Override
    protected VmType doGetType() {
      return typeConstructor.apply(clazz);
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (VmUtils.getClass(value) == clazz) return value;
      throw typeMismatch(value, clazz);
    }

    @Override
    public VmTyped getMirror() {
      return mirrorFactory.create(null);
    }

    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }

    @Override
    public @Nullable Object createDefaultValue(
        VirtualFrame frame,
        VmLanguage language,
        SourceSection headerSection,
        String qualifiedName) {
      return TypeNode.createDefaultValue(clazz);
    }
  }

  /** The `module` or `this` type for an open module or class. */
  public static final class NonFinalSelfTypeNode extends ObjectSlotTypeNode {
    private final VmClass clazz; // only used by getVmClass()
    @Child private ExpressionNode getTargetNode;
    private final Function<VmClass, VmType> typeConstructor;
    private final VmObjectFactory<Void> mirrorFactory;

    private NonFinalSelfTypeNode(
        SourceSection sourceSection,
        VmClass clazz,
        ExpressionNode getTargetNode,
        Function<VmClass, VmType> typeConstructor,
        VmObjectFactory<Void> mirrorFactory) {
      super(sourceSection);
      this.clazz = clazz;
      this.getTargetNode = getTargetNode;
      this.typeConstructor = typeConstructor;
      this.mirrorFactory = mirrorFactory;
    }

    public static NonFinalSelfTypeNode moduleType(SourceSection sourceSection, VmClass clazz) {
      return new NonFinalSelfTypeNode(
          sourceSection,
          clazz,
          new GetModuleNode(sourceSection),
          VmType.NonFinalModuleType::new,
          MirrorFactories.moduleTypeFactory);
    }

    public static NonFinalSelfTypeNode thisType(SourceSection sourceSection, VmClass clazz) {
      return new NonFinalSelfTypeNode(
          sourceSection,
          clazz,
          new GetReceiverNode(),
          VmType.NonFinalThisType::new,
          MirrorFactories.thisTypeFactory);
    }

    @Override
    protected VmType doGetType() {
      return typeConstructor.apply(clazz);
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      var clazz = ((VmObjectLike) getTargetNode.executeGeneric(frame)).getVmClass();
      if (clazz.isSuperclassOf(VmUtils.getClass(value))) return value;
      throw typeMismatch(value, clazz);
    }

    @Override
    public VmTyped getMirror() {
      return mirrorFactory.create(null);
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }

    @Override
    public @Nullable Object createDefaultValue(
        VirtualFrame frame,
        VmLanguage language,
        SourceSection headerSection,
        String qualifiedName) {
      var clazz = ((VmObjectLike) getTargetNode.executeGeneric(frame)).getVmClass();
      return TypeNode.createDefaultValue(clazz);
    }
  }

  public static final class StringLiteralTypeNode extends ObjectSlotTypeNode {
    private final String literal;

    public StringLiteralTypeNode(SourceSection sourceSection, String literal) {
      super(sourceSection);
      this.literal = literal;
    }

    @Override
    protected VmType doGetType() {
      return new VmType.StringLiteralType(literal);
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (literal.equals(value)) return value;

      throw typeMismatch(value, literal);
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.stringLiteralTypeFactory.create(this);
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }

    @Override
    public Object createDefaultValue(
        VirtualFrame frame,
        VmLanguage language,
        SourceSection headerSection,
        String qualifiedName) {
      return literal;
    }
  }

  public static final class TypedTypeNode extends ObjectSlotTypeNode {
    public TypedTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(BaseModule.getTypedClass());
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (value instanceof VmTyped) return value;

      throw typeMismatch(value, BaseModule.getTypedClass());
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  public static final class DynamicTypeNode extends ObjectSlotTypeNode {
    public DynamicTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(BaseModule.getDynamicClass());
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (value instanceof VmDynamic) return value;

      throw typeMismatch(value, BaseModule.getDynamicClass());
    }

    @Override
    public Object createDefaultValue(
        VirtualFrame frame,
        VmLanguage language,
        SourceSection headerSection,
        String qualifiedName) {
      return VmDynamic.empty();
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  /**
   * A non-open and non-abstract class type. Since this node is not used for
   * String/Boolean/Int/Float and their supertypes, only `VmValue`s can possibly pass its type
   * check.
   */
  public static final class FinalClassTypeNode extends ObjectSlotTypeNode
      implements UserClassTypeNode {
    private final VmClass clazz;

    public FinalClassTypeNode(SourceSection sourceSection, VmClass clazz) {
      super(sourceSection);
      this.clazz = clazz;
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(clazz);
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (value instanceof VmValue vmValue && clazz == vmValue.getVmClass()) return value;
      throw typeMismatch(value, clazz);
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      // `List<X>` is represented by `ListTypeNode`,
      // but `List` is represented by `FinalClassTypeNode`
      return createUnknownTypeArgumentMirrors(clazz);
    }

    @Override
    public @Nullable Object createDefaultValue(
        VirtualFrame frame,
        VmLanguage language,
        SourceSection headerSection,
        String qualifiedName) {

      return TypeNode.createDefaultValue(clazz);
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  /**
   * An {@code open} or {@code abstract} class type. Since this node is not used for
   * String/Boolean/Int/Float and their supertypes, only {@link VmValue}s can possibly pass its type
   * check.
   */
  public abstract static class NonFinalClassTypeNode extends ObjectSlotTypeNode
      implements UserClassTypeNode {
    protected final VmClass clazz;

    public NonFinalClassTypeNode(SourceSection sourceSection, VmClass clazz) {
      super(sourceSection);
      this.clazz = clazz;
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(clazz);
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      // `Collection<X>` is represented by `CollectionTypeNode`,
      // but `Collection` is represented by `NonFinalClassTypeNode`
      return createUnknownTypeArgumentMirrors(clazz);
    }

    @ExplodeLoop
    @SuppressWarnings("unused")
    @Specialization(guards = "value.getVmClass() == cachedClass")
    protected Object eval(
        VmValue value,
        @Cached("value.getVmClass()") VmClass cachedClass,
        @Cached("clazz.isSuperclassOf(cachedClass)") boolean isSuperclass) {
      if (isSuperclass) return value;
      throw typeMismatch(value, clazz);
    }

    @Specialization
    protected Object eval(VmValue value) {
      if (clazz.isSuperclassOf(value.getVmClass())) return value;
      throw typeMismatch(value, clazz);
    }

    @Fallback
    protected Object eval(Object value) {
      throw typeMismatch(value, clazz);
    }

    @Override
    public @Nullable Object createDefaultValue(
        VirtualFrame frame,
        VmLanguage language,
        SourceSection headerSection,
        String qualifiedName) {
      return TypeNode.createDefaultValue(clazz);
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  public static class NullableTypeNode extends WriteFrameSlotTypeNode {
    @Child private TypeNode elementTypeNode;

    public NullableTypeNode(SourceSection sourceSection, TypeNode elementTypeNode) {
      super(sourceSection);
      this.elementTypeNode = elementTypeNode;
    }

    @Override
    protected VmType doGetType() {
      return new VmType.NullableType(elementTypeNode.getType());
    }

    public TypeNode getElementTypeNode() {
      return elementTypeNode;
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.nullableTypeFactory.create(this);
    }

    public VmTyped getElementTypeMirror() {
      return elementTypeNode.getMirror();
    }

    @Override
    public @Nullable Object createDefaultValue(
        VirtualFrame frame,
        VmLanguage language,
        SourceSection headerSection,
        String qualifiedName) {
      return VmNull.withDefault(
          elementTypeNode.createDefaultValue(frame, language, headerSection, qualifiedName));
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (value instanceof VmNull) {
        // do nothing
        return value;
      }
      return elementTypeNode.executeLazily(frame, value);
    }

    @Override
    public Object executeEagerly(VirtualFrame frame, Object value) {
      if (value instanceof VmNull) {
        // do nothing
        return value;
      }
      return elementTypeNode.executeEagerly(frame, value);
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this) && elementTypeNode.acceptTypeNode(visitTypeArguments, consumer);
    }
  }

  public static class UnionTypeNode extends WriteFrameSlotTypeNode {
    @Children final TypeNode[] elementTypeNodes;
    private final int defaultIndex;

    public UnionTypeNode(
        SourceSection sourceSection, int defaultIndex, TypeNode[] elementTypeNodes) {
      super(sourceSection);
      assert elementTypeNodes.length > 0;
      this.elementTypeNodes = elementTypeNodes;
      this.defaultIndex = defaultIndex;
    }

    @Override
    protected VmType doGetType() {
      return new VmType.UnionType(defaultIndex, toTypes(elementTypeNodes));
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.unionTypeFactory.create(this);
    }

    public VmList getElementTypeMirrors() {
      return getMirrors(elementTypeNodes);
    }

    public TypeNode[] getElementTypeNodes() {
      return elementTypeNodes;
    }

    @Override
    public boolean isNoopTypeCheck() {
      for (var element : elementTypeNodes) {
        if (!element.isNoopTypeCheck()) return false;
      }
      return true;
    }

    @Override
    public @Nullable Object createDefaultValue(
        VirtualFrame frame,
        VmLanguage language,
        SourceSection headerSection,
        String qualifiedName) {

      return defaultIndex == -1
          ? null
          : elementTypeNodes[defaultIndex].createDefaultValue(
              frame, language, headerSection, qualifiedName);
    }

    @Override
    @ExplodeLoop
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      if (!consumer.accept(this)) {
        return false;
      }
      var ret = true;
      // don't break early to ensure constant number of iterations
      //noinspection ForLoopReplaceableByForEach
      for (var i = 0; i < elementTypeNodes.length; i++) {
        if (!ret) {
          continue;
        }
        if (!elementTypeNodes[i].acceptTypeNode(visitTypeArguments, consumer)) {
          ret = false;
        }
      }
      LoopNode.reportLoopCount(this, elementTypeNodes.length);
      return ret;
    }

    /**
     * Tells if the union type should be eagerly checked or not (shallow-force members of
     * Listing/Mapping).
     *
     * <p>Union types should be eagerly checked if two of the alternatives are the same generic
     * type; e.g. {@code Listing<Person>|Listing<Animal>}
     */
    @TruffleBoundary
    private boolean shouldEagerCheck() {
      var seenParameterizedClasses = EconomicSets.<VmClass>create();
      var ret = new MutableBoolean(false);
      acceptTypeNode(
          false,
          (typeNode) -> {
            if (!typeNode.getType().isParametric()) {
              return true;
            }
            var typeClass = typeNode.getType().getVmClass();
            if (typeClass == null) {
              return true;
            }
            if (seenParameterizedClasses.contains(typeClass)) {
              ret.set(true);
              return false;
            } else {
              EconomicSets.add(seenParameterizedClasses, typeClass);
              return true;
            }
          });
      return ret.get();
    }

    @Fallback
    @ExplodeLoop
    protected Object executeLazily(VirtualFrame frame, Object value) {
      // escape analysis should remove this allocation in compiled code
      var typeMismatches = new VmTypeMismatchException[elementTypeNodes.length];

      // disallow power assertions from triggering in case one union member checks successfully
      var localContext = VmLanguage.get(this).localContext.get();
      var wasInTypeTest = localContext.isInTypeTest();
      localContext.setInTypeTest(true);

      // Do eager checks (shallow-force) if there are two listings or two mappings represented.
      // (we can't know that `new Listing { 0; "hi" }[0]` fails for `Listing<Int>|Listing<String>`
      // without checking both index 0 and index 1).
      var shouldEagerCheck = shouldEagerCheck();
      for (var i = 0; i < elementTypeNodes.length; i++) {
        var elementTypeNode = elementTypeNodes[i];
        try {
          var result =
              shouldEagerCheck
                  ? elementTypeNode.executeEagerly(frame, value)
                  : elementTypeNode.executeLazily(frame, value);
          localContext.setInTypeTest(wasInTypeTest);
          return result;
        } catch (VmTypeMismatchException e) {
          typeMismatches[i] = e;
        }
      }

      // all members failed to type check
      // if enabled, re-execute type checks to generate power assertions
      localContext.setInTypeTest(wasInTypeTest);
      if (VmContext.get(this).getPowerAssertionsEnabled()
          && (!wasInTypeTest || localContext.hasActiveTracker())) {
        for (var i = 0; i < elementTypeNodes.length; i++) {
          var elementTypeNode = elementTypeNodes[i];
          try {
            if (shouldEagerCheck) {
              elementTypeNode.executeEagerly(frame, value);
            } else {
              elementTypeNode.executeLazily(frame, value);
            }
          } catch (VmTypeMismatchException e) {
            typeMismatches[i] = e;
          }
        }
      }

      throw new VmTypeMismatchException.Union(sourceSection, value, this, typeMismatches);
    }

    @Override
    public Object executeEagerly(VirtualFrame frame, Object value) {
      // escape analysis should remove this allocation in compiled code
      var typeMismatches = new VmTypeMismatchException[elementTypeNodes.length];

      // disallow power assertions from triggering in case one union member checks successfully
      var localContext = VmLanguage.get(this).localContext.get();
      var wasInTypeTest = localContext.isInTypeTest();
      localContext.setInTypeTest(true);

      for (var i = 0; i < elementTypeNodes.length; i++) {
        // eager checks
        try {
          var result = elementTypeNodes[i].executeEagerly(frame, value);
          localContext.setInTypeTest(wasInTypeTest);
          return result;
        } catch (VmTypeMismatchException e) {
          typeMismatches[i] = e;
        }
      }

      // all members failed to type check
      // if enabled, re-execute type checks to generate power assertions
      localContext.setInTypeTest(wasInTypeTest);
      if (VmContext.get(this).getPowerAssertionsEnabled()
          && (!wasInTypeTest || localContext.hasActiveTracker())) {
        for (var i = 0; i < elementTypeNodes.length; i++) {
          try {
            elementTypeNodes[i].executeEagerly(frame, value);
          } catch (VmTypeMismatchException e) {
            typeMismatches[i] = e;
          }
        }
      }

      throw new VmTypeMismatchException.Union(sourceSection, value, this, typeMismatches);
    }
  }

  public static final class UnionOfStringLiteralsTypeNode extends ObjectSlotTypeNode {
    private final Set<String> stringLiterals;
    private final @Nullable String unionDefault;
    private final int defaultIndex;

    UnionOfStringLiteralsTypeNode(
        SourceSection sourceSection, int defaultIndex, Set<String> stringLiterals) {
      super(sourceSection);
      assert !stringLiterals.isEmpty();
      this.stringLiterals = stringLiterals;
      this.defaultIndex = defaultIndex;
      if (defaultIndex == -1) {
        unionDefault = null;
      } else {
        unionDefault = stringLiterals.toArray(new String[0])[defaultIndex];
      }
    }

    @Override
    @TruffleBoundary
    protected VmType doGetType() {
      return new VmType.UnionType(defaultIndex, stringLiterals.toArray(new String[0]));
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.unionOfStringLiteralsTypeFactory.create(this);
    }

    public VmList getElementTypeMirrors() {
      var builder = VmList.EMPTY.builder();
      for (var literal : stringLiterals) {
        builder.add(MirrorFactories.stringLiteralTypeFactory2.create(literal));
      }
      return builder.build();
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (contains(value)) return value;
      throw typeMismatch(value, stringLiterals);
    }

    @TruffleBoundary
    private boolean contains(Object value) {
      //noinspection SuspiciousMethodCalls
      return stringLiterals.contains(value);
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }

    @Override
    public @Nullable Object createDefaultValue(
        VirtualFrame frame,
        VmLanguage language,
        SourceSection headerSection,
        String qualifiedName) {
      return unionDefault;
    }
  }

  public static final class CollectionTypeNode extends ObjectSlotTypeNode {
    @Child private TypeNode elementTypeNode;

    public CollectionTypeNode(SourceSection sourceSection, TypeNode elementTypeNode) {
      super(sourceSection);
      this.elementTypeNode = elementTypeNode;
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(BaseModule.getCollectionClass(), elementTypeNode.getType());
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (value instanceof VmList vmList) {
        return evalList(frame, vmList);
      }
      if (value instanceof VmSet vmSet) {
        return evalSet(frame, vmSet);
      }
      throw typeMismatch(value, BaseModule.getCollectionClass());
    }

    @Override
    public Object executeEagerly(VirtualFrame frame, Object value) {
      if (value instanceof VmList vmList) {
        return evalListEagerly(frame, vmList);
      }
      if (value instanceof VmSet vmSet) {
        // sets are always checked eagerly
        return evalSet(frame, vmSet);
      }
      throw typeMismatch(value, BaseModule.getCollectionClass());
    }

    @Override
    public Object createDefaultValue(
        VirtualFrame frame,
        VmLanguage language,
        SourceSection headerSection,
        String qualifiedName) {
      return VmList.EMPTY;
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      return VmList.of(elementTypeNode.getMirror());
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      if (visitTypeArguments) {
        return consumer.accept(this) && elementTypeNode.acceptTypeNode(true, consumer);
      }
      return consumer.accept(this);
    }

    @SuppressWarnings("DuplicatedCode")
    @ExplodeLoop
    private Object evalList(VirtualFrame frame, VmList value) {
      var ret = value;
      var idx = 0;

      for (var elem : value) {
        var result = elementTypeNode.executeLazily(frame, elem);
        if (result != elem) {
          ret = ret.replace(idx, result);
        }
        idx++;
      }

      LoopNode.reportLoopCount(this, idx);
      return ret;
    }

    private Object evalListEagerly(VirtualFrame frame, VmList value) {
      for (var elem : value) {
        elementTypeNode.executeEagerly(frame, elem);
      }

      LoopNode.reportLoopCount(this, value.getLength());
      return value;
    }

    private Object evalSet(VirtualFrame frame, VmSet value) {
      for (var elem : value) {
        elementTypeNode.executeEagerly(frame, elem);
      }

      LoopNode.reportLoopCount(this, value.getLength());
      return value;
    }
  }

  public static final class ListTypeNode extends ObjectSlotTypeNode {
    @Child private TypeNode elementTypeNode;

    public ListTypeNode(SourceSection sourceSection, TypeNode elementTypeNode) {
      super(sourceSection);
      this.elementTypeNode = elementTypeNode;
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(BaseModule.getListClass(), elementTypeNode.getType());
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      if (visitTypeArguments) {
        return consumer.accept(this) && elementTypeNode.acceptTypeNode(true, consumer);
      }
      return consumer.accept(this);
    }

    @Override
    public Object createDefaultValue(
        VirtualFrame frame,
        VmLanguage language,
        SourceSection headerSection,
        String qualifiedName) {
      return VmList.EMPTY;
    }

    public TypeNode getElementTypeNode() {
      return elementTypeNode;
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      return VmList.of(elementTypeNode.getMirror());
    }

    @Override
    public Object executeEagerly(VirtualFrame frame, Object value) {
      if (!(value instanceof VmList vmList)) {
        throw typeMismatch(value, BaseModule.getListClass());
      }
      if (elementTypeNode.isNoopTypeCheck()) return vmList;

      for (var elem : vmList) {
        elementTypeNode.executeEagerly(frame, elem);
      }

      LoopNode.reportLoopCount(this, vmList.getLength());
      return value;
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    @ExplodeLoop
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (!(value instanceof VmList vmList)) {
        throw typeMismatch(value, BaseModule.getListClass());
      }
      if (elementTypeNode.isNoopTypeCheck()) return vmList;
      var ret = vmList;
      var idx = 0;

      for (var elem : vmList) {
        var result = elementTypeNode.executeLazily(frame, elem);
        if (result != elem) {
          ret = ret.replace(idx, result);
        }
        idx++;
      }

      LoopNode.reportLoopCount(this, vmList.getLength());
      return ret;
    }
  }

  public abstract static class SetTypeNode extends ObjectSlotTypeNode {
    @Child private TypeNode elementTypeNode;

    protected SetTypeNode(SourceSection sourceSection, TypeNode elementTypeNode) {
      super(sourceSection);
      this.elementTypeNode = elementTypeNode;
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(BaseModule.getSetClass(), elementTypeNode.getType());
    }

    @Override
    public final Object createDefaultValue(
        VirtualFrame frame,
        VmLanguage language,
        SourceSection headerSection,
        String qualifiedName) {
      return VmSet.EMPTY;
    }

    public TypeNode getElementTypeNode() {
      return elementTypeNode;
    }

    @Override
    public final VmList getTypeArgumentMirrors() {
      return VmList.of(elementTypeNode.getMirror());
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      if (visitTypeArguments) {
        return consumer.accept(this) && elementTypeNode.acceptTypeNode(true, consumer);
      }
      return consumer.accept(this);
    }

    @Specialization
    protected Object eval(VirtualFrame frame, VmSet value) {
      if (elementTypeNode.isNoopTypeCheck()) return value;
      for (var elem : value) {
        // no point doing a lazy check because set members have their hash code computed, which
        // necessarily deep-forces them.
        elementTypeNode.executeEagerly(frame, elem);
      }

      LoopNode.reportLoopCount(this, value.getLength());
      return value;
    }

    @Fallback
    protected Object fallback(Object value) {
      throw typeMismatch(value, BaseModule.getSetClass());
    }
  }

  public static final class MapTypeNode extends ObjectSlotTypeNode {
    @Child private TypeNode keyTypeNode;
    @Child private TypeNode valueTypeNode;

    public MapTypeNode(SourceSection sourceSection, TypeNode keyTypeNode, TypeNode valueTypeNode) {
      super(sourceSection);
      this.keyTypeNode = keyTypeNode;
      this.valueTypeNode = valueTypeNode;
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(
          BaseModule.getMapClass(), keyTypeNode.getType(), valueTypeNode.getType());
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (value instanceof VmMap vmMap) {
        return eval(frame, vmMap);
      }
      throw typeMismatch(value, BaseModule.getMapClass());
    }

    @Override
    public Object executeEagerly(VirtualFrame frame, Object value) {
      if (value instanceof VmMap vmMap) {
        return evalEager(frame, vmMap);
      }
      throw typeMismatch(value, BaseModule.getMapClass());
    }

    @Override
    public Object createDefaultValue(
        VirtualFrame frame,
        VmLanguage language,
        SourceSection headerSection,
        String qualifiedName) {

      return VmMap.EMPTY;
    }

    public TypeNode getValueTypeNode() {
      return valueTypeNode;
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      return VmList.of(keyTypeNode.getMirror(), valueTypeNode.getMirror());
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      if (visitTypeArguments) {
        return consumer.accept(this)
            && keyTypeNode.acceptTypeNode(true, consumer)
            && valueTypeNode.acceptTypeNode(true, consumer);
      }
      return consumer.accept(this);
    }

    private Object eval(VirtualFrame frame, VmMap value) {
      if (keyTypeNode.isNoopTypeCheck() && valueTypeNode.isNoopTypeCheck()) return value;
      var ret = value;

      for (var entry : value) {
        var key = VmUtils.getKey(entry);
        keyTypeNode.executeEagerly(frame, key);
        var result = valueTypeNode.executeLazily(frame, VmUtils.getValue(entry));
        if (result != VmUtils.getValue(entry)) {
          ret = ret.put(key, result);
        }
      }

      LoopNode.reportLoopCount(this, value.getLength());
      return ret;
    }

    private Object evalEager(VirtualFrame frame, VmMap value) {
      if (keyTypeNode.isNoopTypeCheck() && valueTypeNode.isNoopTypeCheck()) return value;
      for (var entry : value) {
        keyTypeNode.executeEagerly(frame, VmUtils.getKey(entry));
        valueTypeNode.executeEagerly(frame, VmUtils.getValue(entry));
      }

      LoopNode.reportLoopCount(this, value.getLength());
      return value;
    }
  }

  public static final class ListingTypeNode extends ListingOrMappingTypeNode {
    public ListingTypeNode(
        SourceSection sourceSection, VmLanguage language, TypeNode valueTypeNode) {
      super(sourceSection, language, null, valueTypeNode);
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(BaseModule.getListingClass(), valueTypeNode.getType());
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (!(value instanceof VmListing vmListing)) {
        throw typeMismatch(value, BaseModule.getListingClass());
      }
      if (vmListing.isValueTypeKnownSubtypeOf(valueTypeNode)) {
        return vmListing;
      }
      return new VmListing(
          vmListing.getEnclosingFrame(),
          vmListing,
          EconomicMaps.emptyMap(),
          vmListing.getLength(),
          getValueTypeCastNode(frame.getFrameDescriptor()),
          VmUtils.getReceiver(frame),
          VmUtils.getOwner(frame));
    }

    @Override
    public Object executeEagerly(VirtualFrame frame, Object value) {
      if (!(value instanceof VmListing vmListing)) {
        throw typeMismatch(value, BaseModule.getListingClass());
      }
      doEagerCheck(frame, vmListing);
      return value;
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      return VmList.of(valueTypeNode.getMirror());
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      if (visitTypeArguments) {
        return consumer.accept(this) && valueTypeNode.acceptTypeNode(true, consumer);
      }
      return consumer.accept(this);
    }
  }

  public static final class MappingTypeNode extends ListingOrMappingTypeNode {
    public MappingTypeNode(
        SourceSection sourceSection,
        VmLanguage language,
        TypeNode keyTypeNode,
        TypeNode valueTypeNode) {
      super(sourceSection, language, keyTypeNode, valueTypeNode);
    }

    @Override
    protected VmType doGetType() {
      assert keyTypeNode != null;
      return new VmType.ClassType(
          BaseModule.getMappingClass(), keyTypeNode.getType(), valueTypeNode.getType());
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (!(value instanceof VmMapping vmMapping)) {
        throw typeMismatch(value, BaseModule.getMappingClass());
      }
      // execute type checks on mapping keys
      doEagerCheck(frame, vmMapping, false, true);
      if (vmMapping.isValueTypeKnownSubtypeOf(valueTypeNode)) {
        return vmMapping;
      }
      return new VmMapping(
          vmMapping.getEnclosingFrame(),
          vmMapping,
          EconomicMaps.emptyMap(),
          getValueTypeCastNode(frame.getFrameDescriptor()),
          VmUtils.getReceiver(frame),
          VmUtils.getOwner(frame));
    }

    @Override
    public Object executeEagerly(VirtualFrame frame, Object value) {
      if (!(value instanceof VmMapping vmMapping)) {
        throw typeMismatch(value, BaseModule.getMappingClass());
      }
      doEagerCheck(frame, vmMapping, false, false);
      return value;
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      assert keyTypeNode != null;
      return VmList.of(keyTypeNode.getMirror(), valueTypeNode.getMirror());
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      if (visitTypeArguments) {
        assert keyTypeNode != null;
        return consumer.accept(this) && valueTypeNode.acceptTypeNode(true, consumer);
      }
      return consumer.accept(this);
    }
  }

  public abstract static class ListingOrMappingTypeNode extends ObjectSlotTypeNode {
    private final VmLanguage language;
    @Child protected @Nullable TypeNode keyTypeNode;
    @Child protected TypeNode valueTypeNode;
    @Child @Nullable protected ListingOrMappingTypeCastNode valueTypeCastNode;

    protected ListingOrMappingTypeNode(
        SourceSection sourceSection,
        VmLanguage language,
        @Nullable TypeNode keyTypeNode,
        TypeNode valueTypeNode) {
      super(sourceSection);
      this.language = language;
      this.keyTypeNode = keyTypeNode;
      this.valueTypeNode = valueTypeNode;
    }

    private boolean isListing() {
      return keyTypeNode == null;
    }

    public @Nullable TypeNode getKeyTypeNode() {
      return keyTypeNode;
    }

    public TypeNode getValueTypeNode() {
      return valueTypeNode;
    }

    protected ListingOrMappingTypeCastNode getValueTypeCastNode(FrameDescriptor descriptor) {
      if (valueTypeCastNode == null) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        valueTypeCastNode =
            new ListingOrMappingTypeCastNode(
                language, descriptor, valueTypeNode, getRootNode().getName());
      }
      return valueTypeCastNode;
    }

    @TruffleBoundary
    private Object newEmptyListingOrMapping() {
      if (isListing()) {
        return new VmListing(
            VmUtils.createEmptyMaterializedFrame(),
            BaseModule.getListingClass().getPrototype(),
            EconomicMaps.create(),
            0);
      }

      return new VmMapping(
          VmUtils.createEmptyMaterializedFrame(),
          BaseModule.getMappingClass().getPrototype(),
          EconomicMaps.create());
    }

    @TruffleBoundary
    private Object newEmptyListingOrMapping(ObjectMember defaultMember) {
      if (isListing()) {
        return new VmListing(
            VmUtils.createEmptyMaterializedFrame(),
            BaseModule.getListingClass().getPrototype(),
            EconomicMaps.of(Identifier.DEFAULT, defaultMember),
            0);
      }

      return new VmMapping(
          VmUtils.createEmptyMaterializedFrame(),
          BaseModule.getMappingClass().getPrototype(),
          EconomicMaps.of(Identifier.DEFAULT, defaultMember));
    }

    @TruffleBoundary
    private ObjectMember createDefaultMember(
        SourceSection headerSection, String qualifiedName, @Nullable Object defaultMemberValue) {
      var defaultMember =
          new ObjectMember(
              headerSection,
              headerSection,
              VmModifier.HIDDEN,
              Identifier.DEFAULT,
              VmUtils.concat(qualifiedName, ".default"));
      if (defaultMemberValue == null) {
        defaultMember.initMemberNode(
            new UntypedObjectMemberNode(
                language,
                new FrameDescriptor(),
                defaultMember,
                new DefaultPropertyBodyNode(headerSection, Identifier.DEFAULT, null)));
      } else {
        //noinspection ConstantConditions
        defaultMember.initConstantValue(
            new VmFunction(
                // Assumption: don't need to set the correct `thisValue`
                // because it is guaranteed to be never accessed.
                VmUtils.createEmptyMaterializedFrame(),
                null,
                1,
                new SimpleRootNode(
                    language,
                    new FrameDescriptor(),
                    headerSection,
                    VmUtils.concat(defaultMember.getQualifiedName(), ".<function>"),
                    new ConstantValueNode(defaultMemberValue)),
                null));
      }
      return defaultMember;
    }

    // either (if defaultMemberValue != null):
    // x: Listing<Foo> // = new Listing {
    //   default = name -> new Foo {}
    // }
    // or (if defaultMemberValue == null):
    // x: Listing<Int> // = new Listing {
    //   default = Undefined()
    // }
    @Override
    public final Object createDefaultValue(
        VirtualFrame frame,
        VmLanguage language,
        SourceSection headerSection,
        String qualifiedName) {

      if (valueTypeNode instanceof UnknownTypeNode) {
        return newEmptyListingOrMapping();
      }

      var defaultMemberValue =
          valueTypeNode.createDefaultValue(frame, language, headerSection, qualifiedName);

      var defaultMember = createDefaultMember(headerSection, qualifiedName, defaultMemberValue);

      return newEmptyListingOrMapping(defaultMember);
    }

    protected void doEagerCheck(VirtualFrame frame, VmObject object) {
      doEagerCheck(
          frame,
          object,
          keyTypeNode == null || keyTypeNode.isNoopTypeCheck(),
          valueTypeNode.isNoopTypeCheck());
    }

    protected void doEagerCheck(
        VirtualFrame frame,
        VmObject object,
        boolean skipKeyTypeChecks,
        boolean skipValueTypeChecks) {
      if (skipKeyTypeChecks && skipValueTypeChecks) return;

      var loopCount = 0;

      // similar to shallow forcing
      for (var owner = object; owner != null; owner = owner.getParent()) {
        var cursor = EconomicMaps.getEntries(owner.getMembers());
        while (cursor.advance()) {
          loopCount += 1;
          var member = cursor.getValue();
          if (member.isProp()) continue;

          var memberKey = cursor.getKey();

          if (!skipKeyTypeChecks) {
            assert keyTypeNode != null;
            try {
              keyTypeNode.executeEagerly(frame, memberKey);
            } catch (VmTypeMismatchException e) {
              CompilerDirectives.transferToInterpreter();
              e.putInsertedStackFrame(
                  getRootNode().getCallTarget(),
                  VmUtils.createStackFrame(member.getHeaderSection(), member.getQualifiedName()));
              throw e;
            }
          }

          if (!skipValueTypeChecks) {
            var memberValue = object.getCachedValue(memberKey);
            if (memberValue == null) {
              memberValue = member.getConstantValue();
              if (memberValue == null) {
                var callTarget = member.getCallTarget();
                memberValue = callTarget.call(object, owner, memberKey);
              }
              object.setCachedValue(memberKey, memberValue);
            }
            valueTypeNode.executeEagerly(frame, memberValue);
          }
        }
      }

      LoopNode.reportLoopCount(this, loopCount);
    }
  }

  // A type such as `(Int, String) -> Duration`.
  public abstract static class FunctionTypeNode extends ObjectSlotTypeNode {
    private final TypeNode[] parameterTypeNodes;
    private final TypeNode returnTypeNode;

    protected FunctionTypeNode(
        SourceSection sourceSection, TypeNode[] parameterTypeNodes, TypeNode returnTypeNode) {
      super(sourceSection);
      this.parameterTypeNodes = parameterTypeNodes;
      this.returnTypeNode = returnTypeNode;
    }

    @Override
    protected VmType doGetType() {
      var typeArguments = toTypes(parameterTypeNodes, parameterTypeNodes.length + 1);
      typeArguments[parameterTypeNodes.length] = returnTypeNode.getType();
      return new VmType.ClassType(
          BaseModule.getFunctionNClass(parameterTypeNodes.length), typeArguments);
    }

    @Override
    public final VmTyped getMirror() {
      return MirrorFactories.functionTypeFactory.create(this);
    }

    public final VmList getParameterTypeMirrors() {
      return getMirrors(parameterTypeNodes);
    }

    public final VmTyped getReturnTypeMirror() {
      return returnTypeNode.getMirror();
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }

    protected VmClass getVmClass() {
      return BaseModule.getFunctionNClass(parameterTypeNodes.length);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "value.getVmClass() == getVmClass()")
    protected Object eval(VmFunction value) {
      // do nothing
      return value;
    }

    @Fallback
    protected Object fallback(Object value) {
      throw typeMismatch(value, getVmClass());
    }
  }

  // A type such as `Function<Duration>` (but not `FunctionN<...>`).
  public abstract static class FunctionClassTypeNode extends ObjectSlotTypeNode {
    private final TypeNode typeArgumentNode;

    protected FunctionClassTypeNode(SourceSection sourceSection, TypeNode typeArgumentNode) {
      super(sourceSection);
      this.typeArgumentNode = typeArgumentNode;
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(BaseModule.getFunctionClass(), typeArgumentNode.getType());
    }

    public final VmList getTypeArgumentMirrors() {
      return VmList.of(typeArgumentNode.getMirror());
    }

    @Specialization
    protected Object eval(VmFunction value) {
      // do nothing
      return value;
    }

    @Fallback
    protected void fallback(Object value) {
      throw typeMismatch(value, BaseModule.getFunctionClass());
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  // A type such as `Function2<Int, String, Duration>`.
  public abstract static class FunctionNClassTypeNode extends ObjectSlotTypeNode {
    private final TypeNode[] typeArgumentNodes;

    protected FunctionNClassTypeNode(SourceSection sourceSection, TypeNode[] typeArgumentNodes) {
      super(sourceSection);
      this.typeArgumentNodes = typeArgumentNodes;
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(
          BaseModule.getFunctionNClass(typeArgumentNodes.length - 1), toTypes(typeArgumentNodes));
    }

    public final VmList getTypeArgumentMirrors() {
      return getMirrors(typeArgumentNodes);
    }

    protected VmClass getVmClass() {
      return BaseModule.getFunctionNClass(typeArgumentNodes.length - 1);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "value.getVmClass() == getVmClass()")
    protected Object eval(VmFunction value) {
      // do nothing
      return value;
    }

    @Fallback
    protected Object fallback(Object value) {
      throw typeMismatch(value, getVmClass());
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  public abstract static class ReferenceTypeNode extends ValidatingObjectSlotTypeNode {
    @Child private TypeNode domainTypeNode;
    @Child private TypeNode referentTypeNode;
    @Child private ExpressionNode getReceiverClassNode;
    @Child private ExpressionNode getModuleNode;

    public ReferenceTypeNode(
        SourceSection sourceSection, TypeNode domainTypeNode, TypeNode referentTypeNode) {
      super(sourceSection);
      this.domainTypeNode = domainTypeNode;
      this.referentTypeNode = referentTypeNode;
      this.getReceiverClassNode = new GetReceiverClassNode(sourceSection);
      this.getModuleNode = new GetModuleNode(sourceSection);
      validate();
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(
          RefModule.getReferenceClass(), domainTypeNode.getType(), referentTypeNode.getType());
    }

    @Override
    public final String getValidationErrorKey() {
      return "invalidReferenceTypeAnnotationWithConstraint";
    }

    @Override
    protected final @Nullable Node getViolatingNode() {
      // constraints may not be used in Reference type annotation referents
      // walk the type and throw if any part of the referent is constrained
      var violation = new MutableReference<Node>(null);
      referentTypeNode.acceptTypeNode(
          true,
          (typeNode) -> {
            if (typeNode instanceof ConstrainedTypeNode) {
              violation.set(typeNode);
              return false;
            }
            return true;
          });
      return violation.getOrNull();
    }

    @Override
    protected final boolean isIncludedInTrace(Node node) {
      return node instanceof ReferenceTypeNode || node instanceof ConstrainedTypeNode;
    }

    @Specialization
    protected Object eval(VirtualFrame frame, VmReference value) {
      if (domainTypeNode.isNoopTypeCheck() && referentTypeNode.isNoopTypeCheck()) {
        return value;
      }

      try {
        domainTypeNode.execute(frame, value.getDomain());
      } catch (VmTypeMismatchException e) {
        CompilerDirectives.transferToInterpreter();
        throw typeMismatch(value, getType());
      }

      var module = (VmTyped) getModuleNode.executeGeneric(frame);
      if (value.referentTypeIsSubtypeOf(
          referentTypeNode.getType(),
          (VmClass) getReceiverClassNode.executeGeneric(frame),
          module.getVmClass())) {
        return value;
      }

      throw typeMismatch(value, getType());
    }

    @Fallback
    protected Object fallback(Object value) {
      throw typeMismatch(value, RefModule.getReferenceClass());
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      if (visitTypeArguments)
        return consumer.accept(this)
            && consumer.accept(domainTypeNode)
            && consumer.accept(referentTypeNode);
      return consumer.accept(this);
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      return VmList.of(domainTypeNode.getMirror(), referentTypeNode.getMirror());
    }
  }

  public static final class PairTypeNode extends ObjectSlotTypeNode {
    @Child private TypeNode firstTypeNode;
    @Child private TypeNode secondTypeNode;

    public PairTypeNode(
        SourceSection sourceSection, TypeNode firstTypeNode, TypeNode secondTypeNode) {
      super(sourceSection);
      this.firstTypeNode = firstTypeNode;
      this.secondTypeNode = secondTypeNode;
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(
          BaseModule.getPairClass(), firstTypeNode.getType(), secondTypeNode.getType());
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (value instanceof VmPair vmPair) {
        var first = firstTypeNode.executeLazily(frame, vmPair.getFirst());
        var second = secondTypeNode.executeLazily(frame, vmPair.getSecond());
        if (first == vmPair.getFirst() && second == vmPair.getSecond()) {
          return vmPair;
        }
        return new VmPair(first, second);
      }
      throw typeMismatch(value, BaseModule.getPairClass());
    }

    @Override
    public Object executeEagerly(VirtualFrame frame, Object value) {
      if (value instanceof VmPair vmPair) {
        firstTypeNode.executeEagerly(frame, vmPair.getFirst());
        secondTypeNode.executeEagerly(frame, vmPair.getSecond());
        return value;
      }
      throw typeMismatch(value, BaseModule.getPairClass());
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      return VmList.of(firstTypeNode.getMirror(), secondTypeNode.getMirror());
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      if (visitTypeArguments) {
        return consumer.accept(this)
            && firstTypeNode.acceptTypeNode(true, consumer)
            && secondTypeNode.acceptTypeNode(true, consumer);
      }
      return consumer.accept(this);
    }
  }

  public static class VarArgsTypeNode extends ObjectSlotTypeNode {
    private final TypeNode elementTypeNode; // not a child: never executes

    public VarArgsTypeNode(SourceSection sourceSection, TypeNode elementTypeNode) {
      super(sourceSection);
      this.elementTypeNode = elementTypeNode;
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(BaseModule.getVarArgsClass(), elementTypeNode.getType());
    }

    @Override
    public @Nullable Object createDefaultValue(
        VirtualFrame frame,
        VmLanguage language,
        SourceSection headerSection,
        String qualifiedName) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("internalStdLibClass", "VarArgs")
          .withSourceSection(headerSection)
          .build();
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder().evalError("internalStdLibClass", "VarArgs").build();
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  public static final class TypeVariableNode extends WriteFrameSlotTypeNode {
    private final TypeParameter typeParameter;

    public TypeVariableNode(SourceSection sourceSection, TypeParameter typeParameter) {
      super(sourceSection);
      this.typeParameter = typeParameter;
    }

    @Override
    protected VmType doGetType() {
      return new VmType.TypeVariableType(typeParameter);
    }

    public int getTypeParameterIndex() {
      return typeParameter.getIndex();
    }

    @Override
    public boolean isNoopTypeCheck() {
      return true;
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.typeVariableFactory.create(this);
    }

    public VmTyped getTypeParameterMirror() {
      return MirrorFactories.typeParameterFactory.create(typeParameter);
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      // do nothing
      return value;
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  public static final class NonNullTypeAliasTypeNode extends WriteFrameSlotTypeNode {
    public NonNullTypeAliasTypeNode() {
      super(VmUtils.unavailableSourceSection());
    }

    @Override
    protected VmType doGetType() {
      return new VmType.AliasType(BaseModule.getNonNullTypeAlias());
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (value instanceof VmNull) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw constraintException(value, BaseModule.getNonNullTypeAlias().getConstraintSection());
      }
      return value;
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.typeAliasTypeFactory.create(this);
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  public static sealed class IntMaskSlotTypeNode extends IntSlotTypeNode
      permits UInt8TypeAliasTypeNode {
    protected final long mask;
    private final VmTypeAlias typeAlias;

    public IntMaskSlotTypeNode(VmTypeAlias typeAlias, long mask) {
      super(VmUtils.unavailableSourceSection());
      this.mask = mask;
      this.typeAlias = typeAlias;
    }

    @Override
    protected VmType doGetType() {
      return new VmType.AliasType(typeAlias, BaseModule.getIntClass());
    }

    @Override
    protected final Object executeLazily(VirtualFrame frame, Object value) {
      if (value instanceof Long l) {
        if ((l & mask) == l) return value;

        CompilerDirectives.transferToInterpreterAndInvalidate();
        var sourceSection = typeAlias.getConstraintSection();
        throw constraintException(value, sourceSection);
      }

      throw new VmTypeMismatchException.Simple(
          typeAlias.getBaseTypeSection(), value, BaseModule.getIntClass());
    }

    @Override
    public final VmTyped getMirror() {
      return MirrorFactories.typeAliasTypeFactory.create(this);
    }

    @Override
    protected final boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  public static final class UInt8TypeAliasTypeNode extends IntMaskSlotTypeNode {
    public UInt8TypeAliasTypeNode() {
      super(BaseModule.getUInt8TypeAlias(), 0x00000000000000FFL);
    }
  }

  public static final class Int8TypeAliasTypeNode extends IntSlotTypeNode {
    public Int8TypeAliasTypeNode() {
      super(VmUtils.unavailableSourceSection());
    }

    @Override
    protected VmType doGetType() {
      return new VmType.AliasType(BaseModule.getInt8TypeAlias(), BaseModule.getIntClass());
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (value instanceof Long l) {
        if (l == l.byteValue()) return value;

        CompilerDirectives.transferToInterpreterAndInvalidate();
        var sourceSection = BaseModule.getInt8TypeAlias().getConstraintSection();
        throw constraintException(value, sourceSection);
      }

      throw new VmTypeMismatchException.Simple(
          BaseModule.getInt8TypeAlias().getBaseTypeSection(), value, BaseModule.getIntClass());
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.typeAliasTypeFactory.create(this);
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  public static final class Int16TypeAliasTypeNode extends IntSlotTypeNode {
    public Int16TypeAliasTypeNode() {
      super(VmUtils.unavailableSourceSection());
    }

    @Override
    protected VmType doGetType() {
      return new VmType.AliasType(BaseModule.getInt16TypeAlias(), BaseModule.getIntClass());
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (value instanceof Long l) {
        if (l == l.shortValue()) return value;

        CompilerDirectives.transferToInterpreterAndInvalidate();
        var sourceSection = BaseModule.getInt16TypeAlias().getConstraintSection();
        throw constraintException(value, sourceSection);
      }

      throw new VmTypeMismatchException.Simple(
          BaseModule.getInt16TypeAlias().getBaseTypeSection(), value, BaseModule.getIntClass());
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.typeAliasTypeFactory.create(this);
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  public static final class Int32TypeAliasTypeNode extends IntSlotTypeNode {
    public Int32TypeAliasTypeNode() {
      super(VmUtils.unavailableSourceSection());
    }

    @Override
    protected VmType doGetType() {
      return new VmType.AliasType(BaseModule.getInt32TypeAlias(), BaseModule.getIntClass());
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (value instanceof Long l) {
        if (l == l.intValue()) return value;

        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw constraintException(value, BaseModule.getInt32TypeAlias().getConstraintSection());
      }

      CompilerDirectives.transferToInterpreterAndInvalidate();
      throw new VmTypeMismatchException.Simple(
          BaseModule.getInt32TypeAlias().getBaseTypeSection(), value, BaseModule.getIntClass());
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.typeAliasTypeFactory.create(this);
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  public static final class TypeAliasTypeNode extends TypeNode {
    private final VmTypeAlias typeAlias;
    private final TypeNode[] typeArgumentNodes;
    @Child private TypeNode aliasedTypeNode;

    public TypeAliasTypeNode(
        SourceSection sourceSection, VmTypeAlias typeAlias, TypeNode[] typeArgumentNodes) {
      super(sourceSection);

      if (!typeAlias.isInitialized()) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder().evalError("cyclicTypeAlias").build();
      }

      if (typeArgumentNodes.length > 0
          && typeArgumentNodes.length != typeAlias.getTypeParameterCount()) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder()
            .evalError(
                "wrongTypeArgumentCount",
                typeAlias.getTypeParameterCount(),
                typeArgumentNodes.length)
            .build();
      }

      this.typeAlias = typeAlias;
      this.typeArgumentNodes = typeArgumentNodes;
      aliasedTypeNode = typeAlias.instantiate(typeArgumentNodes);
      aliasedTypeNode.accept(
          node -> {
            if (node instanceof ValidatingObjectSlotTypeNode typeNode) {
              typeNode.validate(this);
            }
            return true;
          });
    }

    @Override
    protected VmType doGetType() {
      return new VmType.AliasType(typeAlias, toTypes(typeArgumentNodes), aliasedTypeNode.getType());
    }

    public VmTypeAlias getTypeAlias() {
      return typeAlias;
    }

    @Override
    public FrameSlotKind getFrameSlotKind() {
      return aliasedTypeNode.getFrameSlotKind();
    }

    @Override
    public TypeNode initWriteSlotNode(int slot) {
      aliasedTypeNode.initWriteSlotNode(slot);
      return this;
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.typeAliasTypeFactory.create(this);
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      return getMirrors(typeArgumentNodes);
    }

    protected Object executeLazily(VirtualFrame frame, Object value) {
      return aliasedTypeNode.executeLazily(frame, value);
    }

    /** See docstring on {@link TypeAliasTypeNode#executeLazily}. */
    @Override
    public Object executeEagerly(VirtualFrame frame, Object value) {
      return aliasedTypeNode.executeEagerly(frame, value);
    }

    @Override
    public Object executeAndSet(VirtualFrame frame, Object value) {
      return aliasedTypeNode.executeAndSet(frame, value);
    }

    @TruffleBoundary
    private VmFunction newMixin(VmLanguage language, String qualifiedName) {
      //noinspection ConstantConditions
      return new VmFunction(
          VmUtils.createEmptyMaterializedFrame(),
          // Assumption: don't need to set the correct `thisValue`
          // because it is guaranteed to be never accessed.
          null,
          1,
          new IdentityMixinNode(
              language,
              new FrameDescriptor(),
              getSourceSection(),
              qualifiedName,
              typeArgumentNodes.length == 1
                  ?
                  // shouldn't need to deepCopy() this node because it isn't used as @Child
                  // anywhere else
                  typeArgumentNodes[0]
                  : null),
          null);
    }

    @Override
    public @Nullable Object createDefaultValue(
        VirtualFrame frame,
        VmLanguage language,
        SourceSection headerSection,
        String qualifiedName) {
      if (typeAlias == BaseModule.getMixinTypeAlias()) {
        return newMixin(language, qualifiedName);
      }

      return aliasedTypeNode.createDefaultValue(frame, language, headerSection, qualifiedName);
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this) && aliasedTypeNode.acceptTypeNode(visitTypeArguments, consumer);
    }
  }

  public static final class ConstrainedTypeNode extends TypeNode {

    private final VmLanguage language;
    @Child private TypeNode childNode;
    @Children private final TypeConstraintNode[] constraintNodes;

    public ConstrainedTypeNode(
        SourceSection sourceSection,
        VmLanguage language,
        TypeNode childNode,
        TypeConstraintNode[] constraintNodes) {
      super(sourceSection);
      this.language = language;
      this.childNode = childNode;
      this.constraintNodes = constraintNodes;
    }

    @Override
    @TruffleBoundary
    protected VmType doGetType() {
      return new VmType.ConstrainedType(
          childNode.getType(),
          Arrays.stream(constraintNodes).map(TypeConstraintNode::export).toArray(String[]::new),
          System.identityHashCode(this));
    }

    @Override
    public FrameSlotKind getFrameSlotKind() {
      return childNode.getFrameSlotKind();
    }

    @Override
    public TypeNode initWriteSlotNode(int slot) {
      childNode.initWriteSlotNode(slot);
      return this;
    }

    @TruffleBoundary
    private int getCustomThisSlot(FrameDescriptor frameDescriptor) {
      // can't store the slot id as this node may be called from different root nodes
      // (see constraints14 snippet)
      return frameDescriptor.findOrAddAuxiliarySlot(VmUtils.CUSTOM_THIS_FRAME_SLOT_ID);
    }

    @ExplodeLoop
    protected Object executeLazily(VirtualFrame frame, Object value) {
      var ret = childNode.executeLazily(frame, value);

      var localContext = language.localContext.get();
      var prevShouldTypeCheck = localContext.shouldEagerTypecheck();
      localContext.shouldEagerTypecheck(true);
      frame.setAuxiliarySlot(getCustomThisSlot(frame.getFrameDescriptor()), value);
      try {
        for (var node : constraintNodes) {
          node.execute(frame);
        }
        return ret;
      } finally {
        localContext.shouldEagerTypecheck(prevShouldTypeCheck);
      }
    }

    @Override
    public Object executeAndSet(VirtualFrame frame, Object value) {
      var ret = execute(frame, value);
      childNode.executeAndSet(frame, ret);
      return ret;
    }

    @Override
    public @Nullable Object createDefaultValue(
        VirtualFrame frame,
        VmLanguage language,
        SourceSection headerSection,
        String qualifiedName) {

      return childNode.createDefaultValue(frame, language, headerSection, qualifiedName);
    }

    public SourceSection getBaseTypeSection() {
      return childNode.getSourceSection();
    }

    public SourceSection getFirstConstraintSection() {
      return constraintNodes[0].getSourceSection();
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      if (!consumer.accept(this)) {
        return false;
      }
      return childNode.acceptTypeNode(visitTypeArguments, consumer);
    }

    public VmTyped getMirror() {
      // pkl:reflect doesn't currently expose constraints
      return childNode.getMirror();
    }
  }

  public static final class AnyTypeNode extends WriteFrameSlotTypeNode {
    public AnyTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(BaseModule.getAnyClass());
    }

    @Override
    public boolean isNoopTypeCheck() {
      return true;
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      // do nothing
      return value;
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  public static final class StringTypeNode extends ObjectSlotTypeNode {
    public StringTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(BaseModule.getStringClass());
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (value instanceof String) return value;

      throw typeMismatch(value, BaseModule.getStringClass());
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  public static final class NumberTypeNode extends FrameSlotTypeNode {
    public NumberTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(BaseModule.getNumberClass());
    }

    @Override
    public FrameSlotKind getFrameSlotKind() {
      return FrameSlotKind.Illegal;
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (value instanceof Long || value instanceof Double) return value;

      throw typeMismatch(value, BaseModule.getNumberClass());
    }

    @Override
    public Object executeAndSet(VirtualFrame frame, Object value) {
      var kind = frame.getFrameDescriptor().getSlotKind(slot);
      if (value instanceof Long l) {
        if (kind == FrameSlotKind.Double || kind == FrameSlotKind.Object) {
          frame.getFrameDescriptor().setSlotKind(slot, FrameSlotKind.Object);
          frame.setObject(slot, l);
        } else {
          frame.getFrameDescriptor().setSlotKind(slot, FrameSlotKind.Long);
          frame.setLong(slot, l);
        }
        return value;
      } else if (value instanceof Double d) {
        if (kind == FrameSlotKind.Long || kind == FrameSlotKind.Object) {
          frame.getFrameDescriptor().setSlotKind(slot, FrameSlotKind.Object);
          frame.setObject(slot, d);
        } else {
          frame.getFrameDescriptor().setSlotKind(slot, FrameSlotKind.Double);
          frame.setDouble(slot, d);
        }
        return value;
      } else {
        throw typeMismatch(value, BaseModule.getNumberClass());
      }
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  public static final class IntTypeNode extends IntSlotTypeNode {
    public IntTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(BaseModule.getIntClass());
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (value instanceof Long) return value;

      throw typeMismatch(value, BaseModule.getIntClass());
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  public static final class FloatTypeNode extends FrameSlotTypeNode {
    public FloatTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(BaseModule.getFloatClass());
    }

    @Override
    public FrameSlotKind getFrameSlotKind() {
      return FrameSlotKind.Double;
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (value instanceof Double) return value;

      throw typeMismatch(value, BaseModule.getFloatClass());
    }

    @Override
    public Object executeAndSet(VirtualFrame frame, Object value) {
      executeLazily(frame, value);
      frame.setDouble(slot, (double) value);
      return value;
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  public static final class BooleanTypeNode extends FrameSlotTypeNode {
    public BooleanTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(BaseModule.getBooleanClass());
    }

    @Override
    public FrameSlotKind getFrameSlotKind() {
      return FrameSlotKind.Boolean;
    }

    @Override
    protected Object executeLazily(VirtualFrame frame, Object value) {
      if (value instanceof Boolean) return value;

      throw typeMismatch(value, BaseModule.getBooleanClass());
    }

    @Override
    public Object executeAndSet(VirtualFrame frame, Object value) {
      execute(frame, value);
      frame.setBoolean(slot, (boolean) value);
      return value;
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      return consumer.accept(this);
    }
  }

  public abstract static class ClassClassTypeNode extends ObjectSlotTypeNode {

    @Child private TypeNode typeNode;
    @CompilationFinal private boolean initialized = false;
    @CompilationFinal private @Nullable VmClass clazz = null;

    public ClassClassTypeNode(SourceSection sourceSection, TypeNode typeNode) {
      super(sourceSection);
      this.typeNode = typeNode;
    }

    @Override
    protected VmType doGetType() {
      return new VmType.ClassType(BaseModule.getClassClass(), typeNode.getType());
    }

    private void initVmClass() {
      if (initialized) return;

      CompilerDirectives.transferToInterpreterAndInvalidate();
      initialized = true;

      var type = typeNode.getType();
      while (type instanceof VmType.AliasType typeAliasType) {
        type = typeAliasType.getAliasedType();
      }

      if (type == VmType.UnknownType.INSTANCE || type instanceof VmType.TypeVariableType) {
        clazz = BaseModule.getAnyClass();
      } else if (!type.isParametric()) {
        clazz = type.getVmClass();
      }
    }

    @Specialization
    protected Object eval(VmClass value) {
      // safe to init clazz here (instead of on init and typealias instantiate)
      // because in the typealias case this node will never execute prior to instantiation
      initVmClass();

      // Fast path: all classes match Class<Any> / Class<unknown> / Class<type arg>.
      // In this case, skip the subclass check and behave like a bare `Class` type annotation.
      if (clazz == BaseModule.getAnyClass()) {
        return value;
      }

      // clazz will be null iff the type arg is a not a valid class type
      if (clazz == null) {
        CompilerDirectives.transferToInterpreter();
        throw new VmTypeMismatchException.ClassType(sourceSection, value, getType());
      }

      if (!value.isSubclassOf(clazz)) {
        CompilerDirectives.transferToInterpreter();
        throw new VmTypeMismatchException.ClassType(sourceSection, value, clazz);
      }

      return value;
    }

    @Fallback
    protected Object fallback(Object value) {
      throw typeMismatch(value, BaseModule.getClassClass());
    }

    @Override
    protected boolean acceptTypeNode(boolean visitTypeArguments, TypeNodeConsumer consumer) {
      if (visitTypeArguments) {
        return consumer.accept(this) && typeNode.acceptTypeNode(true, consumer);
      }
      return consumer.accept(this);
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      return VmList.of(typeNode.getMirror());
    }
  }

  public abstract static class ValidatingObjectSlotTypeNode extends ObjectSlotTypeNode {

    protected ValidatingObjectSlotTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    protected abstract String getValidationErrorKey();

    protected abstract @Nullable Node getViolatingNode();

    protected final void validate() {
      var violation = getViolatingNode();
      if (violation == null) return;
      throw exceptionBuilder()
          .evalError(getValidationErrorKey())
          .withLeadingStackFrames(buildLeadingFrames(violation, getSourceSection(), null))
          .build();
    }

    public final void validate(TypeAliasTypeNode outermostAliasNode) {
      var violation = getViolatingNode();
      if (violation == null) return;

      throw exceptionBuilder()
          .withLocation(outermostAliasNode)
          .evalError(getValidationErrorKey())
          .withLeadingStackFrames(
              buildLeadingFrames(
                  violation,
                  outermostAliasNode.getSourceSection(),
                  outermostAliasNode.getTypeAlias()))
          .build();
    }

    protected abstract boolean isIncludedInTrace(Node node);

    private List<StackFrame> buildLeadingFrames(
        Node violatingNode, SourceSection usageSection, @Nullable VmTypeAlias outermostAlias) {
      var frames = new ArrayList<StackFrame>();
      for (var node = violatingNode; node != null; node = node.getParent()) {
        if (!(node instanceof TypeAliasTypeNode || isIncludedInTrace(node))) continue;
        var section = node.getSourceSection();
        if (section == null || !section.isAvailable() || isWithin(usageSection, section)) {
          continue;
        }
        var owner = ownerAlias(node, outermostAlias);
        if (owner != null) {
          frames.add(VmUtils.createStackFrame(section, owner.getQualifiedName()));
        }
      }
      return frames;
    }

    /**
     * The type alias whose body contains {@code node}: the nearest enclosing alias, else the
     * outermost alias being instantiated (which is {@code null} when no alias is used).
     */
    @SuppressWarnings("DataFlowIssue")
    private static @Nullable VmTypeAlias ownerAlias(
        Node node, @Nullable VmTypeAlias outermostAlias) {
      var parent = NodeUtil.findParent(node, TypeAliasTypeNode.class);
      //noinspection ConstantValue
      return parent != null ? parent.getTypeAlias() : outermostAlias;
    }

    private static boolean isWithin(SourceSection outer, SourceSection inner) {
      return inner.getSource().equals(outer.getSource())
          && inner.getCharIndex() >= outer.getCharIndex()
          && inner.getCharEndIndex() <= outer.getCharEndIndex();
    }
  }

  public static VmType[] toTypes(TypeNode[] typeNodes) {
    return toTypes(typeNodes, typeNodes.length);
  }

  // this variant is used for maps that need to change length (e.g. FunctionTypeNode -> ClassType)
  static VmType[] toTypes(TypeNode[] typeNodes, int len) {
    var result = new VmType[len];
    for (var i = 0; i < typeNodes.length; i++) {
      result[i] = typeNodes[i].getType();
    }
    return result;
  }

  private static @Nullable Object createDefaultValue(VmClass clazz) {
    if (clazz.isInstantiable()) {
      if (clazz.isListingClass()) return VmListing.empty();
      if (clazz.isMappingClass()) return VmMapping.empty();
      if (clazz.isDynamicClass()) return VmDynamic.empty();
      return clazz.getPrototype();
    }

    if (clazz.isListClass()) return VmList.EMPTY;
    if (clazz.isSetClass()) return VmSet.EMPTY;
    if (clazz.isMapClass()) return VmMap.EMPTY;
    if (clazz.isCollectionClass()) return VmList.EMPTY;
    if (clazz.isNullClass()) return VmNull.withoutDefault();

    return null;
  }

  private static VmList createUnknownTypeArgumentMirrors(VmClass clazz) {
    var typeParameterCount = clazz.getTypeParameterCount();
    if (typeParameterCount == 0) return VmList.EMPTY;

    var builder = VmList.EMPTY.builder();
    for (var i = 0; i < typeParameterCount; i++) {
      builder.add(MirrorFactories.unknownTypeFactory.create(null));
    }
    return builder.build();
  }

  @FunctionalInterface
  protected interface TypeNodeConsumer {
    /** Returns true if the visitor should continue visiting type nodes. */
    boolean accept(TypeNode typeNode);
  }
}
