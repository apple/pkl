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
package org.pkl.core.ast.type;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.source.SourceSection;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.pkl.core.PType;
import org.pkl.core.PType.StringLiteral;
import org.pkl.core.PklBugException;
import org.pkl.core.TypeParameter;
import org.pkl.core.ast.*;
import org.pkl.core.ast.builder.SymbolTable.CustomThisScope;
import org.pkl.core.ast.expression.primary.GetModuleNode;
import org.pkl.core.ast.frame.WriteFrameSlotNode;
import org.pkl.core.ast.frame.WriteFrameSlotNodeGen;
import org.pkl.core.ast.member.DefaultPropertyBodyNode;
import org.pkl.core.ast.member.ListingOrMappingTypeCheckNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.member.UntypedObjectMemberNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.MutableLong;
import org.pkl.core.util.Nonnull;
import org.pkl.core.util.Nullable;

public abstract class TypeNode extends PklNode {

  protected TypeNode(SourceSection sourceSection) {
    super(sourceSection);
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
   * Checks if {@code value} conforms to this type, and possibly casts it in the case of {@link
   * MappingTypeNode} or {@link ListingTypeNode}.
   */
  public abstract Object execute(VirtualFrame frame, Object value);

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
    return execute(frame, value);
  }

  // method arguments are used when default value contains a root node
  public @Nullable Object createDefaultValue(
      VmLanguage language,
      // header section of the property or method that carries the type annotation
      SourceSection headerSection,
      // qualified name of the property or method that carries the type annotation
      String qualifiedName) {
    return null;
  }

  /**
   * Visit child type nodes; but not paramaterized types (does not visit {@code String} in {@code
   * Listing<String>}).
   */
  protected abstract void acceptTypeNode(Consumer<TypeNode> visitor);

  public static TypeNode forClass(SourceSection sourceSection, VmClass clazz) {
    return clazz.isClosed()
        ? new FinalClassTypeNode(sourceSection, clazz)
        : TypeNodeFactory.NonFinalClassTypeNodeGen.create(sourceSection, clazz);
  }

  public static PType export(@Nullable TypeNode node) {
    return node != null ? node.doExport() : PType.UNKNOWN;
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

  protected PType doExport() {
    var alias = getVmTypeAlias();
    // needs to come before `clazz != null` check
    if (alias != null) {
      return new PType.Alias(alias.export());
    }
    var clazz = getVmClass();
    if (clazz != null) {
      return new PType.Class(clazz.export());
    }
    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder()
        .bug("`%s` must override method `doExport()`.", getClass().getTypeName())
        .build();
  }

  public @Nullable VmClass getVmClass() {
    return null;
  }

  public @Nullable VmTypeAlias getVmTypeAlias() {
    return null;
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

    @CompilationFinal @Child protected WriteFrameSlotNode writeFrameSlotNode;

    protected FrameSlotTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    public TypeNode initWriteSlotNode(int slot) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      this.slot = slot;
      //noinspection DataFlowIssue
      writeFrameSlotNode = WriteFrameSlotNodeGen.create(sourceSection, slot, null);
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
    @CompilationFinal protected int slot;
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
      //noinspection ConstantConditions
      writeSlotNode = WriteFrameSlotNodeGen.create(VmUtils.unavailableSourceSection(), slot, null);
      this.slot = slot;
      return this;
    }

    @Override
    public final Object executeAndSet(VirtualFrame frame, Object value) {
      var result = execute(frame, value);
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
    public boolean isNoopTypeCheck() {
      return true;
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      // do nothing
      return value;
    }

    public VmTyped getMirror() {
      return MirrorFactories.unknownTypeFactory.create(null);
    }

    @Override
    protected PType doExport() {
      return PType.UNKNOWN;
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  /** The `nothing` type. */
  public static final class NothingTypeNode extends TypeNode {
    public NothingTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    public TypeNode initWriteSlotNode(int slot) {
      // do nothing
      return this;
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      CompilerDirectives.transferToInterpreter();
      throw new VmTypeMismatchException.Nothing(sourceSection, value);
    }

    @Override
    public Object executeAndSet(VirtualFrame frame, Object value) {
      execute(frame, value);
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
    protected PType doExport() {
      return PType.NOTHING;
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  /** The `module` type for a final module. */
  public static final class FinalModuleTypeNode extends ObjectSlotTypeNode {
    private final VmClass moduleClass;

    public FinalModuleTypeNode(SourceSection sourceSection, VmClass moduleClass) {
      super(sourceSection);
      this.moduleClass = moduleClass;
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      if (value instanceof VmTyped typed && typed.getVmClass() == moduleClass) return value;

      throw typeMismatch(value, moduleClass);
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.moduleTypeFactory.create(null);
    }

    @Override
    protected PType doExport() {
      return PType.MODULE;
    }

    @Override
    public VmClass getVmClass() {
      return moduleClass;
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  /** The `module` type for an open module. */
  public static final class NonFinalModuleTypeNode extends ObjectSlotTypeNode {
    private final VmClass moduleClass; // only used by getVmClass()
    @Child private ExpressionNode getModuleNode;

    public NonFinalModuleTypeNode(SourceSection sourceSection, VmClass moduleClass) {
      super(sourceSection);
      this.moduleClass = moduleClass;
      getModuleNode = new GetModuleNode(sourceSection);
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      var moduleClass = ((VmTyped) getModuleNode.executeGeneric(frame)).getVmClass();

      if (value instanceof VmTyped typed) {
        var valueClass = typed.getVmClass();
        if (moduleClass.isSuperclassOf(valueClass)) return value;
      }

      throw typeMismatch(value, moduleClass);
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.moduleTypeFactory.create(null);
    }

    @Override
    protected PType doExport() {
      return PType.MODULE;
    }

    @Override
    public VmClass getVmClass() {
      return moduleClass;
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static final class StringLiteralTypeNode extends ObjectSlotTypeNode {
    private final String literal;

    public StringLiteralTypeNode(SourceSection sourceSection, String literal) {
      super(sourceSection);
      this.literal = literal;
    }

    public String getLiteral() {
      return literal;
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      if (literal.equals(value)) return value;

      throw typeMismatch(value, literal);
    }

    @Override
    public Object createDefaultValue(
        VmLanguage language, SourceSection headerSection, String qualifiedName) {

      return literal;
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.stringLiteralTypeFactory.create(this);
    }

    @Override
    protected PType doExport() {
      return new PType.StringLiteral(literal);
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static final class TypedTypeNode extends ObjectSlotTypeNode {
    public TypedTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      if (value instanceof VmTyped) return value;

      throw typeMismatch(value, BaseModule.getTypedClass());
    }

    @Override
    public VmClass getVmClass() {
      return BaseModule.getTypedClass();
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static final class DynamicTypeNode extends ObjectSlotTypeNode {
    public DynamicTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      if (value instanceof VmDynamic) return value;

      throw typeMismatch(value, BaseModule.getDynamicClass());
    }

    @Override
    public VmClass getVmClass() {
      return BaseModule.getDynamicClass();
    }

    @Override
    public Object createDefaultValue(
        VmLanguage language, SourceSection headerSection, String qualifiedName) {

      return VmDynamic.empty();
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  /**
   * A non-open and non-abstract class type. Since this node is not used for
   * String/Boolean/Int/Float and their supertypes, only `VmValue`s can possibly pass its type
   * check.
   */
  public static final class FinalClassTypeNode extends ObjectSlotTypeNode {
    private final VmClass clazz;

    public FinalClassTypeNode(SourceSection sourceSection, VmClass clazz) {
      super(sourceSection);
      this.clazz = clazz;
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      if (value instanceof VmValue vmValue && clazz == vmValue.getVmClass()) return value;

      throw typeMismatch(value, clazz);
    }

    @Override
    public VmClass getVmClass() {
      return clazz;
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      // `List<X>` is represented by `ListTypeNode`,
      // but `List` is represented by `FinalClassTypeNode`
      return createUnknownTypeArgumentMirrors(clazz);
    }

    @Override
    public @Nullable Object createDefaultValue(
        VmLanguage language, SourceSection headerSection, String qualifiedName) {

      return TypeNode.createDefaultValue(clazz);
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  /**
   * An `open` or `abstract` class type. Since this node is not used for String/Boolean/Int/Float
   * and their supertypes, only `VmValue`s can possibly pass its type check.
   */
  public abstract static class NonFinalClassTypeNode extends ObjectSlotTypeNode {
    protected final VmClass clazz;

    public NonFinalClassTypeNode(SourceSection sourceSection, VmClass clazz) {
      super(sourceSection);
      this.clazz = clazz;
    }

    public final VmClass getVmClass() {
      return clazz;
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
        VmLanguage language, SourceSection headerSection, String qualifiedName) {

      return TypeNode.createDefaultValue(clazz);
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static class NullableTypeNode extends WriteFrameSlotTypeNode {
    @Child private TypeNode elementTypeNode;

    public NullableTypeNode(SourceSection sourceSection, TypeNode elementTypeNode) {

      super(sourceSection);
      this.elementTypeNode = elementTypeNode;
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
        VmLanguage language, SourceSection headerSection, String qualifiedName) {

      return VmNull.withDefault(
          elementTypeNode.createDefaultValue(language, headerSection, qualifiedName));
    }

    @Override
    protected final PType doExport() {
      return new PType.Nullable(elementTypeNode.doExport());
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      if (value instanceof VmNull) {
        // do nothing
        return value;
      }
      return elementTypeNode.execute(frame, value);
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
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
      elementTypeNode.acceptTypeNode(visitor);
    }
  }

  public static class UnionTypeNode extends WriteFrameSlotTypeNode {
    @Children final TypeNode[] elementTypeNodes;
    private final boolean skipElementTypeChecks;
    private final int defaultIndex;

    public UnionTypeNode(
        SourceSection sourceSection,
        int defaultIndex,
        TypeNode[] elementTypeNodes,
        boolean skipElementTypeChecks) {
      super(sourceSection);
      assert elementTypeNodes.length > 0;
      this.elementTypeNodes = elementTypeNodes;
      this.defaultIndex = defaultIndex;
      this.skipElementTypeChecks = skipElementTypeChecks;
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
      return skipElementTypeChecks;
    }

    @Override
    public @Nullable Object createDefaultValue(
        VmLanguage language, SourceSection headerSection, String qualifiedName) {

      return defaultIndex == -1
          ? null
          : elementTypeNodes[defaultIndex].createDefaultValue(
              language, headerSection, qualifiedName);
    }

    @Override
    protected PType doExport() {
      var elementTypes =
          Arrays.stream(elementTypeNodes).map(TypeNode::export).collect(Collectors.toList());
      return new PType.Union(elementTypes);
    }

    @Override
    @ExplodeLoop
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
      //noinspection ForLoopReplaceableByForEach
      for (var i = 0; i < elementTypeNodes.length; i++) {
        elementTypeNodes[i].acceptTypeNode(visitor);
      }
      LoopNode.reportLoopCount(this, elementTypeNodes.length);
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
      var seenListings = new MutableLong(0);
      var seenMappings = new MutableLong(0);
      var seenLists = new MutableLong(0);
      var seenMaps = new MutableLong(0);
      var seenSets = new MutableLong(0);
      var seenPairs = new MutableLong(0);
      var seenCollections = new MutableLong(0);
      this.acceptTypeNode(
          (typeNode) -> {
            if (typeNode instanceof ListingTypeNode) {
              seenListings.getAndIncrement();
            }
            if (typeNode instanceof MappingTypeNode) {
              seenMappings.getAndIncrement();
            }
            if (typeNode instanceof ListTypeNode) {
              seenLists.getAndIncrement();
            }
            if (typeNode instanceof MapTypeNode) {
              seenMaps.getAndIncrement();
            }
            if (typeNode instanceof SetTypeNode) {
              seenSets.getAndIncrement();
            }
            if (typeNode instanceof PairTypeNode) {
              seenPairs.getAndIncrement();
            }
            if (typeNode instanceof CollectionTypeNode) {
              seenCollections.getAndIncrement();
            }
          });
      return seenMappings.get() >= 2
          || seenListings.get() >= 2
          || seenLists.get() >= 2
          || seenMaps.get() >= 2
          || seenSets.get() >= 2
          || seenPairs.get() >= 2
          || seenCollections.get() >= 2;
    }

    @Fallback
    @ExplodeLoop
    public Object execute(VirtualFrame frame, Object value) {
      if (skipElementTypeChecks) return value;

      // escape analysis should remove this allocation in compiled code
      var typeMismatches = new VmTypeMismatchException[elementTypeNodes.length];

      // Do eager checks (shallow-force) if there are two listings or two mappings represented.
      // (we can't know that `new Listing { 0; "hi" }[0]` fails for `Listing<Int>|Listing<String>`
      // without checking both index 0 and index 1).
      var shouldEagerCheck = shouldEagerCheck();
      for (var i = 0; i < elementTypeNodes.length; i++) {
        var elementTypeNode = elementTypeNodes[i];
        try {
          if (shouldEagerCheck) {
            return elementTypeNode.executeEagerly(frame, value);
          } else {
            return elementTypeNode.execute(frame, value);
          }
        } catch (VmTypeMismatchException e) {
          typeMismatches[i] = e;
        }
      }
      throw new VmTypeMismatchException.Union(sourceSection, value, this, typeMismatches);
    }

    @Override
    public Object executeEagerly(VirtualFrame frame, Object value) {
      if (skipElementTypeChecks) return value;

      // escape analysis should remove this allocation in compiled code
      var typeMismatches = new VmTypeMismatchException[elementTypeNodes.length];

      for (var i = 0; i < elementTypeNodes.length; i++) {
        // eager checks
        try {
          return elementTypeNodes[i].executeEagerly(frame, value);
        } catch (VmTypeMismatchException e) {
          typeMismatches[i] = e;
        }
      }
      throw new VmTypeMismatchException.Union(sourceSection, value, this, typeMismatches);
    }
  }

  public static final class UnionOfStringLiteralsTypeNode extends ObjectSlotTypeNode {
    private final Set<String> stringLiterals;
    private final @Nullable String unionDefault;

    UnionOfStringLiteralsTypeNode(
        SourceSection sourceSection, int defaultIndex, Set<String> stringLiterals) {
      super(sourceSection);

      assert !stringLiterals.isEmpty();
      this.stringLiterals = stringLiterals;
      if (defaultIndex == -1) {
        unionDefault = null;
      } else {
        unionDefault = stringLiterals.toArray(new String[0])[defaultIndex];
      }
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
    public Object execute(VirtualFrame frame, Object value) {
      if (contains(value)) return value;

      throw typeMismatch(value, stringLiterals);
    }

    @TruffleBoundary
    private boolean contains(Object value) {
      //noinspection SuspiciousMethodCalls
      return stringLiterals.contains(value);
    }

    @Override
    protected PType doExport() {
      return new PType.Union(
          stringLiterals.stream().map(StringLiteral::new).collect(Collectors.toList()));
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }

    @Override
    @TruffleBoundary
    public @Nullable Object createDefaultValue(
        VmLanguage language, SourceSection headerSection, String qualifiedName) {

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
    public Object execute(VirtualFrame frame, Object value) {
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
        VmLanguage language, SourceSection headerSection, String qualifiedName) {

      return VmList.EMPTY;
    }

    @Override
    public VmClass getVmClass() {
      return BaseModule.getCollectionClass();
    }

    @Override
    protected PType doExport() {
      return new PType.Class(BaseModule.getCollectionClass().export(), elementTypeNode.doExport());
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      return VmList.of(elementTypeNode.getMirror());
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }

    private Object evalList(VirtualFrame frame, VmList value) {
      var ret = value;
      var idx = 0;

      for (var elem : value) {
        var result = elementTypeNode.execute(frame, elem);
        if (result != elem) {
          ret = ret.replace(idx, result);
        }
        idx++;
      }

      LoopNode.reportLoopCount(this, idx);
      return ret;
    }

    private Object evalListEagerly(VirtualFrame frame, VmList value) {
      var ret = value;
      var idx = 0;

      for (var elem : value) {
        var result = elementTypeNode.executeEagerly(frame, elem);
        if (result != elem) {
          ret = ret.replace(idx, result);
        }
        idx++;
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
    private final boolean skipElementTypeChecks;

    public ListTypeNode(SourceSection sourceSection, TypeNode elementTypeNode) {
      super(sourceSection);
      this.elementTypeNode = elementTypeNode;
      skipElementTypeChecks = elementTypeNode.isNoopTypeCheck();
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }

    @Override
    public Object createDefaultValue(
        VmLanguage language, SourceSection headerSection, String qualifiedName) {

      return VmList.EMPTY;
    }

    @Override
    public VmClass getVmClass() {
      return BaseModule.getListClass();
    }

    public TypeNode getElementTypeNode() {
      return elementTypeNode;
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      return VmList.of(elementTypeNode.getMirror());
    }

    @Override
    protected PType doExport() {
      return new PType.Class(BaseModule.getListClass().export(), elementTypeNode.doExport());
    }

    @Override
    public Object executeEagerly(VirtualFrame frame, Object value) {
      if (!(value instanceof VmList vmList)) {
        throw typeMismatch(value, BaseModule.getListClass());
      }
      if (skipElementTypeChecks) return vmList;

      for (var elem : vmList) {
        elementTypeNode.executeEagerly(frame, elem);
      }

      LoopNode.reportLoopCount(this, vmList.getLength());
      return value;
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      if (!(value instanceof VmList vmList)) {
        throw typeMismatch(value, BaseModule.getListClass());
      }
      if (skipElementTypeChecks) return vmList;
      var ret = vmList;
      var idx = 0;

      for (var elem : vmList) {
        var result = elementTypeNode.execute(frame, elem);
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
    private final boolean skipElementTypeChecks;

    protected SetTypeNode(SourceSection sourceSection, TypeNode elementTypeNode) {
      super(sourceSection);
      this.elementTypeNode = elementTypeNode;
      skipElementTypeChecks = elementTypeNode.isNoopTypeCheck();
    }

    @Override
    public final Object createDefaultValue(
        VmLanguage language, SourceSection headerSection, String qualifiedName) {

      return VmSet.EMPTY;
    }

    @Override
    public final VmClass getVmClass() {
      return BaseModule.getSetClass();
    }

    public TypeNode getElementTypeNode() {
      return elementTypeNode;
    }

    @Override
    public final VmList getTypeArgumentMirrors() {
      return VmList.of(elementTypeNode.getMirror());
    }

    @Override
    protected final PType doExport() {
      return new PType.Class(BaseModule.getSetClass().export(), elementTypeNode.doExport());
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }

    @Specialization
    protected Object eval(VirtualFrame frame, VmSet value) {
      if (skipElementTypeChecks) return value;
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
    private final boolean skipEntryTypeChecks;

    public MapTypeNode(SourceSection sourceSection, TypeNode keyTypeNode, TypeNode valueTypeNode) {

      super(sourceSection);
      this.keyTypeNode = keyTypeNode;
      this.valueTypeNode = valueTypeNode;
      skipEntryTypeChecks = keyTypeNode.isNoopTypeCheck() && valueTypeNode.isNoopTypeCheck();
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
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
        VmLanguage language, SourceSection headerSection, String qualifiedName) {

      return VmMap.EMPTY;
    }

    @Override
    public VmClass getVmClass() {
      return BaseModule.getMapClass();
    }

    public TypeNode getValueTypeNode() {
      return valueTypeNode;
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      return VmList.of(keyTypeNode.getMirror(), valueTypeNode.getMirror());
    }

    @Override
    protected PType doExport() {
      return new PType.Class(
          BaseModule.getMapClass().export(), keyTypeNode.doExport(), valueTypeNode.doExport());
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }

    private Object eval(VirtualFrame frame, VmMap value) {
      if (skipEntryTypeChecks) return value;
      var ret = value;

      for (var entry : value) {
        var key = VmUtils.getKey(entry);
        keyTypeNode.executeEagerly(frame, key);
        var result = valueTypeNode.execute(frame, VmUtils.getValue(entry));
        if (result != VmUtils.getValue(entry)) {
          ret = ret.put(key, result);
        }
      }

      LoopNode.reportLoopCount(this, value.getLength());
      return ret;
    }

    private Object evalEager(VirtualFrame frame, VmMap value) {
      if (skipEntryTypeChecks) return value;
      for (var entry : value) {
        keyTypeNode.executeEagerly(frame, VmUtils.getKey(entry));
        valueTypeNode.execute(frame, VmUtils.getValue(entry));
      }

      LoopNode.reportLoopCount(this, value.getLength());
      return value;
    }
  }

  public static final class ListingTypeNode extends ListingOrMappingTypeNode {
    public ListingTypeNode(SourceSection sourceSection, TypeNode valueTypeNode) {
      super(sourceSection, null, valueTypeNode);
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      if (!(value instanceof VmListing vmListing)) {
        throw typeMismatch(value, BaseModule.getListingClass());
      }
      return valueTypeNode.isNoopTypeCheck()
          ? vmListing
          : vmListing.createSurrogate(getListingOrMappingTypeCheckNode(), frame.materialize());
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
    public VmClass getVmClass() {
      return BaseModule.getListingClass();
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      return VmList.of(valueTypeNode.getMirror());
    }

    @Override
    protected PType doExport() {
      return new PType.Class(BaseModule.getListingClass().export(), valueTypeNode.doExport());
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static final class MappingTypeNode extends ListingOrMappingTypeNode {
    public MappingTypeNode(
        SourceSection sourceSection, TypeNode keyTypeNode, TypeNode valueTypeNode) {

      super(sourceSection, keyTypeNode, valueTypeNode);
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      if (!(value instanceof VmMapping vmMapping)) {
        throw typeMismatch(value, BaseModule.getMappingClass());
      }
      // execute type checks on mapping keys
      doEagerCheck(frame, vmMapping, false, true);
      return valueTypeNode.isNoopTypeCheck()
          ? vmMapping
          : vmMapping.createSurrogate(getListingOrMappingTypeCheckNode(), frame.materialize());
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
    public VmClass getVmClass() {
      return BaseModule.getMappingClass();
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      assert keyTypeNode != null;
      return VmList.of(keyTypeNode.getMirror(), valueTypeNode.getMirror());
    }

    @Override
    protected PType doExport() {
      assert keyTypeNode != null;
      return new PType.Class(
          BaseModule.getMappingClass().export(), keyTypeNode.doExport(), valueTypeNode.doExport());
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public abstract static class ListingOrMappingTypeNode extends ObjectSlotTypeNode {
    @Child protected @Nullable TypeNode keyTypeNode;
    @Child protected TypeNode valueTypeNode;
    @Child @Nullable protected ListingOrMappingTypeCheckNode listingOrMappingTypeCheckNode;

    private final boolean skipKeyTypeChecks;
    private final boolean skipValueTypeChecks;

    protected ListingOrMappingTypeNode(
        SourceSection sourceSection, @Nullable TypeNode keyTypeNode, TypeNode valueTypeNode) {

      super(sourceSection);
      this.keyTypeNode = keyTypeNode;
      this.valueTypeNode = valueTypeNode;

      skipKeyTypeChecks = keyTypeNode == null || keyTypeNode.isNoopTypeCheck();
      skipValueTypeChecks = valueTypeNode.isNoopTypeCheck();
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

    protected ListingOrMappingTypeCheckNode getListingOrMappingTypeCheckNode() {
      if (listingOrMappingTypeCheckNode == null) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        listingOrMappingTypeCheckNode =
            new ListingOrMappingTypeCheckNode(
                VmLanguage.get(this),
                getRootNode().getFrameDescriptor(),
                valueTypeNode,
                getRootNode().getName());
      }
      return listingOrMappingTypeCheckNode;
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
    @TruffleBoundary
    public final Object createDefaultValue(
        VmLanguage language, SourceSection headerSection, String qualifiedName) {

      if (valueTypeNode instanceof UnknownTypeNode) {
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

      var defaultMember =
          new ObjectMember(
              headerSection,
              headerSection,
              VmModifier.HIDDEN,
              Identifier.DEFAULT,
              qualifiedName + ".default");

      var defaultMemberValue =
          valueTypeNode.createDefaultValue(language, headerSection, qualifiedName);

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
                VmUtils.createEmptyMaterializedFrame(),
                // Assumption: don't need to set the correct `thisValue`
                // because it is guaranteed to be never accessed.
                null,
                1,
                new SimpleRootNode(
                    language,
                    new FrameDescriptor(),
                    headerSection,
                    defaultMember.getQualifiedName() + ".<function>",
                    new ConstantValueNode(defaultMemberValue)),
                null));
      }

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

    protected void doEagerCheck(VirtualFrame frame, VmObject object) {
      doEagerCheck(frame, object, skipKeyTypeChecks, skipValueTypeChecks);
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
              object.setCachedValue(memberKey, memberValue, member);
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
    public final VmClass getVmClass() {
      return getFunctionNClass();
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
    protected final PType doExport() {
      var parameterTypes =
          Arrays.stream(parameterTypeNodes).map(TypeNode::export).collect(Collectors.toList());
      return new PType.Function(parameterTypes, TypeNode.export(returnTypeNode));
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "value.getVmClass() == getFunctionNClass()")
    protected Object eval(VmFunction value) {
      /* do nothing */
      return value;
    }

    @Fallback
    protected Object fallback(Object value) {
      throw typeMismatch(value, getFunctionNClass());
    }

    // not a field to avoid a circular evaluation error
    protected VmClass getFunctionNClass() {
      return BaseModule.getFunctionNClass(parameterTypeNodes.length);
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
    public final VmClass getVmClass() {
      return BaseModule.getFunctionClass();
    }

    public final VmList getTypeArgumentMirrors() {
      return VmList.of(typeArgumentNode.getMirror());
    }

    @Override
    protected final PType doExport() {
      return new PType.Class(
          BaseModule.getFunctionClass().export(), TypeNode.export(typeArgumentNode));
    }

    @Specialization
    protected Object eval(VmFunction value) {
      /* do nothing */
      return value;
    }

    @Fallback
    protected void fallback(Object value) {
      throw typeMismatch(value, BaseModule.getFunctionClass());
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
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
    public final VmClass getVmClass() {
      return getFunctionNClass();
    }

    public final VmList getTypeArgumentMirrors() {
      return getMirrors(typeArgumentNodes);
    }

    @Override
    protected final PType doExport() {
      var typeArguments =
          Arrays.stream(typeArgumentNodes).map(TypeNode::export).collect(Collectors.toList());
      return new PType.Class(getFunctionNClass().export(), typeArguments);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "value.getVmClass() == getFunctionNClass()")
    protected Object eval(VmFunction value) {
      return value;
    }

    @Fallback
    protected Object fallback(Object value) {
      throw typeMismatch(value, getFunctionNClass());
    }

    // not a field to avoid a circular evaluation error
    protected VmClass getFunctionNClass() {
      return BaseModule.getFunctionNClass(typeArgumentNodes.length - 1);
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
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
    public Object execute(VirtualFrame frame, Object value) {
      if (value instanceof VmPair vmPair) {
        var first = firstTypeNode.execute(frame, vmPair.getFirst());
        var second = secondTypeNode.execute(frame, vmPair.getSecond());
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
    public VmClass getVmClass() {
      return BaseModule.getPairClass();
    }

    @Override
    public VmList getTypeArgumentMirrors() {
      return VmList.of(firstTypeNode.getMirror(), secondTypeNode.getMirror());
    }

    @Override
    protected PType doExport() {
      return new PType.Class(
          BaseModule.getPairClass().export(), firstTypeNode.doExport(), secondTypeNode.doExport());
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static class VarArgsTypeNode extends ObjectSlotTypeNode {
    @Child private TypeNode elementTypeNode;

    public VarArgsTypeNode(SourceSection sourceSection, TypeNode elementTypeNode) {

      super(sourceSection);
      this.elementTypeNode = elementTypeNode;
    }

    @Override
    public @Nullable Object createDefaultValue(
        VmLanguage language, SourceSection headerSection, String qualifiedName) {

      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("internalStdLibClass", "VarArgs")
          .withSourceSection(headerSection)
          .build();
    }

    @Override
    protected final PType doExport() {
      return new PType.Class(BaseModule.getVarArgsClass().export(), elementTypeNode.doExport());
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder().evalError("internalStdLibClass", "VarArgs").build();
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static final class TypeVariableNode extends WriteFrameSlotTypeNode {
    private final TypeParameter typeParameter;

    public TypeVariableNode(SourceSection sourceSection, TypeParameter typeParameter) {

      super(sourceSection);
      this.typeParameter = typeParameter;
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
    public Object execute(VirtualFrame frame, Object value) {
      // do nothing
      return value;
    }

    @Override
    protected PType doExport() {
      return new PType.TypeVariable(typeParameter);
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static final class NonNullTypeAliasTypeNode extends WriteFrameSlotTypeNode {
    public NonNullTypeAliasTypeNode() {
      super(VmUtils.unavailableSourceSection());
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      if (value instanceof VmNull) {
        throw new VmTypeMismatchException.Constraint(
            BaseModule.getNonNullTypeAlias().getConstraintSection(), value);
      }
      return value;
    }

    @Override
    public VmTypeAlias getVmTypeAlias() {
      return BaseModule.getNonNullTypeAlias();
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.typeAliasTypeFactory.create(this);
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static final class UIntTypeAliasTypeNode extends IntSlotTypeNode {
    private final VmTypeAlias typeAlias;
    private final long mask;

    public UIntTypeAliasTypeNode(VmTypeAlias typeAlias, long mask) {

      super(VmUtils.unavailableSourceSection());
      this.typeAlias = typeAlias;
      this.mask = mask;
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      if (value instanceof Long l) {
        if ((l & mask) == l) return value;

        throw new VmTypeMismatchException.Constraint(typeAlias.getConstraintSection(), value);
      }

      throw new VmTypeMismatchException.Simple(
          typeAlias.getBaseTypeSection(), value, BaseModule.getIntClass());
    }

    @Override
    public VmClass getVmClass() {
      return BaseModule.getIntClass();
    }

    @Override
    public VmTypeAlias getVmTypeAlias() {
      return typeAlias;
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.typeAliasTypeFactory.create(this);
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static final class Int8TypeAliasTypeNode extends IntSlotTypeNode {
    public Int8TypeAliasTypeNode() {
      super(VmUtils.unavailableSourceSection());
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      if (value instanceof Long l) {
        if (l == l.byteValue()) return value;

        throw new VmTypeMismatchException.Constraint(
            BaseModule.getInt8TypeAlias().getConstraintSection(), value);
      }

      throw new VmTypeMismatchException.Simple(
          BaseModule.getInt8TypeAlias().getBaseTypeSection(), value, BaseModule.getIntClass());
    }

    @Override
    public VmClass getVmClass() {
      return BaseModule.getIntClass();
    }

    @Override
    public VmTypeAlias getVmTypeAlias() {
      return BaseModule.getInt8TypeAlias();
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.typeAliasTypeFactory.create(this);
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static final class Int16TypeAliasTypeNode extends IntSlotTypeNode {
    public Int16TypeAliasTypeNode() {
      super(VmUtils.unavailableSourceSection());
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      if (value instanceof Long l) {
        if (l == l.shortValue()) return value;

        throw new VmTypeMismatchException.Constraint(
            BaseModule.getInt16TypeAlias().getConstraintSection(), value);
      }

      throw new VmTypeMismatchException.Simple(
          BaseModule.getInt16TypeAlias().getBaseTypeSection(), value, BaseModule.getIntClass());
    }

    @Override
    public VmClass getVmClass() {
      return BaseModule.getIntClass();
    }

    @Override
    public VmTypeAlias getVmTypeAlias() {
      return BaseModule.getInt16TypeAlias();
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.typeAliasTypeFactory.create(this);
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static final class Int32TypeAliasTypeNode extends IntSlotTypeNode {
    public Int32TypeAliasTypeNode() {
      super(VmUtils.unavailableSourceSection());
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      if (value instanceof Long l) {
        if (l == l.intValue()) return value;

        throw new VmTypeMismatchException.Constraint(
            BaseModule.getInt32TypeAlias().getConstraintSection(), value);
      }

      throw new VmTypeMismatchException.Simple(
          BaseModule.getInt32TypeAlias().getBaseTypeSection(), value, BaseModule.getIntClass());
    }

    @Override
    public VmClass getVmClass() {
      return BaseModule.getIntClass();
    }

    @Override
    public VmTypeAlias getVmTypeAlias() {
      return BaseModule.getInt32TypeAlias();
    }

    @Override
    public VmTyped getMirror() {
      return MirrorFactories.typeAliasTypeFactory.create(this);
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
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

    /**
     * A typealias body is effectively inlined into the type node, and not executed in its own
     * frame.
     *
     * <p>Before executing the typealias body, use the owner and receiver of the original frame
     * where the typealias was declared, so that we preserve its original scope.
     */
    public Object execute(VirtualFrame frame, Object value) {
      var prevOwner = VmUtils.getOwner(frame);
      var prevReceiver = VmUtils.getReceiver(frame);
      VmUtils.setOwner(frame, VmUtils.getOwner(typeAlias.getEnclosingFrame()));
      VmUtils.setReceiver(frame, VmUtils.getReceiver(typeAlias.getEnclosingFrame()));

      try {
        return aliasedTypeNode.execute(frame, value);
      } finally {
        VmUtils.setOwner(frame, prevOwner);
        VmUtils.setReceiver(frame, prevReceiver);
      }
    }

    /** See docstring on {@link TypeAliasTypeNode#execute}. */
    @Override
    public Object executeAndSet(VirtualFrame frame, Object value) {
      var prevOwner = VmUtils.getOwner(frame);
      var prevReceiver = VmUtils.getReceiver(frame);
      VmUtils.setOwner(frame, VmUtils.getOwner(typeAlias.getEnclosingFrame()));
      VmUtils.setReceiver(frame, VmUtils.getReceiver(typeAlias.getEnclosingFrame()));

      try {
        return aliasedTypeNode.executeAndSet(frame, value);
      } finally {
        VmUtils.setOwner(frame, prevOwner);
        VmUtils.setReceiver(frame, prevReceiver);
      }
    }

    @Override
    @TruffleBoundary
    public @Nullable Object createDefaultValue(
        VmLanguage language, SourceSection headerSection, String qualifiedName) {

      if (typeAlias == BaseModule.getMixinTypeAlias()) {
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

      return aliasedTypeNode.createDefaultValue(language, headerSection, qualifiedName);
    }

    @Override
    public @Nullable VmClass getVmClass() {
      return aliasedTypeNode.getVmClass();
    }

    @Override
    public @Nonnull VmTypeAlias getVmTypeAlias() {
      return typeAlias;
    }

    @Override
    protected PType doExport() {
      return new PType.Alias(
          typeAlias.export(),
          Arrays.stream(typeArgumentNodes).map(TypeNode::export).collect(Collectors.toList()),
          aliasedTypeNode.doExport());
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static final class ConstrainedTypeNode extends TypeNode {
    @Child private TypeNode childNode;
    @Children private final TypeConstraintNode[] constraintNodes;

    @CompilationFinal private int customThisSlot = -1;

    public ConstrainedTypeNode(
        SourceSection sourceSection, TypeNode childNode, TypeConstraintNode[] constraintNodes) {
      super(sourceSection);
      this.childNode = childNode;
      this.constraintNodes = constraintNodes;
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

    @ExplodeLoop
    public Object execute(VirtualFrame frame, Object value) {
      if (customThisSlot == -1) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        // deferred until execution time s.t. nodes of inlined type aliases get the right frame slot
        customThisSlot =
            frame.getFrameDescriptor().findOrAddAuxiliarySlot(CustomThisScope.FRAME_SLOT_ID);
      }

      var ret = childNode.execute(frame, value);

      frame.setAuxiliarySlot(customThisSlot, value);
      for (var node : constraintNodes) {
        node.execute(frame);
      }
      return ret;
    }

    @Override
    public Object executeAndSet(VirtualFrame frame, Object value) {
      var ret = execute(frame, value);
      childNode.executeAndSet(frame, ret);
      return ret;
    }

    @Override
    public @Nullable Object createDefaultValue(
        VmLanguage language, SourceSection headerSection, String qualifiedName) {

      return childNode.createDefaultValue(language, headerSection, qualifiedName);
    }

    public SourceSection getBaseTypeSection() {
      return childNode.getSourceSection();
    }

    public SourceSection getFirstConstraintSection() {
      return constraintNodes[0].getSourceSection();
    }

    @Override
    protected PType doExport() {
      return new PType.Constrained(
          childNode.doExport(),
          Arrays.stream(constraintNodes)
              .map(TypeConstraintNode::export)
              .collect(Collectors.toList()));
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
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
    public boolean isNoopTypeCheck() {
      return true;
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      // do nothing
      return value;
    }

    @Override
    public VmClass getVmClass() {
      return BaseModule.getAnyClass();
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static final class StringTypeNode extends ObjectSlotTypeNode {
    public StringTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      if (value instanceof String) return value;

      throw typeMismatch(value, BaseModule.getStringClass());
    }

    @Override
    public VmClass getVmClass() {
      return BaseModule.getStringClass();
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static final class NumberTypeNode extends FrameSlotTypeNode {
    public NumberTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    public FrameSlotKind getFrameSlotKind() {
      return FrameSlotKind.Illegal;
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
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
    public VmClass getVmClass() {
      return BaseModule.getNumberClass();
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static final class IntTypeNode extends IntSlotTypeNode {
    public IntTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      if (value instanceof Long) return value;

      throw typeMismatch(value, BaseModule.getIntClass());
    }

    @Override
    public VmClass getVmClass() {
      return BaseModule.getIntClass();
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static final class FloatTypeNode extends FrameSlotTypeNode {
    public FloatTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    public FrameSlotKind getFrameSlotKind() {
      return FrameSlotKind.Double;
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
      if (value instanceof Double) return value;

      throw typeMismatch(value, BaseModule.getFloatClass());
    }

    @Override
    public Object executeAndSet(VirtualFrame frame, Object value) {
      execute(frame, value);
      frame.setDouble(slot, (double) value);
      return value;
    }

    @Override
    public VmClass getVmClass() {
      return BaseModule.getFloatClass();
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  public static final class BooleanTypeNode extends FrameSlotTypeNode {
    public BooleanTypeNode(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    public FrameSlotKind getFrameSlotKind() {
      return FrameSlotKind.Boolean;
    }

    @Override
    public Object execute(VirtualFrame frame, Object value) {
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
    public VmClass getVmClass() {
      return BaseModule.getBooleanClass();
    }

    @Override
    protected void acceptTypeNode(Consumer<TypeNode> visitor) {
      visitor.accept(this);
    }
  }

  private static @Nullable Object createDefaultValue(VmClass clazz) {
    if (clazz.isInstantiable()) {
      if (clazz.isListingClass()) return VmListing.empty();
      if (clazz.isMappingClass()) return VmMapping.empty();
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
}
