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
package org.pkl.core.ast.builder;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameDescriptor.Builder;
import com.oracle.truffle.api.nodes.Node;
import java.util.*;
import java.util.function.Function;
import org.pkl.core.TypeParameter;
import org.pkl.core.ast.ConstantNode;
import org.pkl.core.ast.ConstantValueNode;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.builder.MethodResolution.DirectMethod;
import org.pkl.core.ast.builder.MethodResolution.LexicalMethod;
import org.pkl.core.ast.builder.MethodResolution.VirtualMethod;
import org.pkl.core.ast.builder.PropertyResolution.ConstantProperty;
import org.pkl.core.ast.builder.PropertyResolution.LetOrLambdaProperty;
import org.pkl.core.ast.builder.PropertyResolution.LocalClassProperty;
import org.pkl.core.ast.builder.PropertyResolution.NormalClassProperty;
import org.pkl.core.ast.expression.generator.GeneratorMemberNode;
import org.pkl.core.ast.expression.primary.PartiallyResolvedVariable;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.ModuleInfo;
import org.pkl.core.runtime.VmDataSize;
import org.pkl.core.runtime.VmDuration;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.util.Nullable;
import org.pkl.parser.Lexer;
import org.pkl.parser.syntax.Class;
import org.pkl.parser.syntax.ClassMethod;
import org.pkl.parser.syntax.Modifier;
import org.pkl.parser.syntax.Modifier.ModifierValue;
import org.pkl.parser.syntax.ObjectBody;
import org.pkl.parser.syntax.Parameter;

public final class SymbolTable {
  private Scope currentScope;

  public SymbolTable(ModuleInfo moduleInfo) {
    currentScope = new ModuleScope(moduleInfo);
  }

  public SymbolTable(ModuleInfo moduleInfo, VmTyped base) {
    var baseScope = new BaseScope(base);
    currentScope = new ModuleScope(moduleInfo, baseScope);
  }

  public Scope getCurrentScope() {
    return currentScope;
  }

  public @Nullable TypeParameter findTypeParameter(String name) {
    TypeParameter result;
    for (var scope = currentScope; scope != null; scope = scope.getParent()) {
      result = scope.getTypeParameter(name);
      if (result != null) return result;
    }
    return null;
  }

  public ObjectMember enterClass(
      Identifier name,
      Class clazz,
      List<TypeParameter> typeParameters,
      Function<ClassScope, ObjectMember> nodeFactory) {
    return doEnter(
        new ClassScope(
            currentScope,
            name,
            toQualifiedName(name),
            clazz,
            FrameDescriptor.newBuilder(),
            typeParameters),
        nodeFactory);
  }

  public ObjectMember enterTypeAlias(
      Identifier name,
      List<TypeParameter> typeParameters,
      Function<TypeAliasScope, ObjectMember> nodeFactory) {
    return doEnter(
        new TypeAliasScope(
            currentScope,
            name,
            toQualifiedName(name),
            FrameDescriptor.newBuilder(),
            typeParameters),
        nodeFactory);
  }

  public <T> T enterMethod(
      Identifier name,
      ConstLevel constLevel,
      List<String> bindings,
      Builder frameDescriptorBuilder,
      List<TypeParameter> typeParameters,
      Function<MethodScope, T> nodeFactory) {
    return doEnter(
        new MethodScope(
            currentScope,
            name,
            toQualifiedName(name),
            constLevel,
            bindings,
            frameDescriptorBuilder,
            typeParameters),
        nodeFactory);
  }

  public <T> T enterForEager(Function<ForEagerScope, T> nodeFactory) {
    return doEnter(new ForEagerScope(currentScope, currentScope.qualifiedName), nodeFactory);
  }

  public <T> T enterForGenerator(
      List<String> params,
      int nestLevel,
      FrameDescriptor.Builder frameDescriptorBuilder,
      FrameDescriptor.Builder memberDescriptorBuilder,
      Function<ForGeneratorScope, T> nodeFactory) {
    return doEnter(
        new ForGeneratorScope(
            currentScope,
            currentScope.qualifiedName,
            params,
            nestLevel,
            frameDescriptorBuilder,
            memberDescriptorBuilder),
        nodeFactory);
  }

  public <T> T enterLambda(
      List<String> bindings,
      int slot,
      FrameDescriptor.Builder frameDescriptorBuilder,
      Function<LambdaScope, T> nodeFactory) {

    // flatten names of lambdas nested inside other lambdas for presentation purposes
    var parentScope = currentScope;
    while (parentScope instanceof LambdaScope) {
      parentScope = parentScope.getParent();
    }

    assert parentScope != null;
    var qualifiedName = parentScope.qualifiedName + "." + parentScope.getNextLambdaName();

    return doEnter(
        new LambdaScope(currentScope, bindings, slot, qualifiedName, frameDescriptorBuilder),
        nodeFactory);
  }

  public <T> T enterProperty(
      Identifier name, ConstLevel constLevel, Function<PropertyScope, T> nodeFactory) {
    return doEnter(
        new PropertyScope(
            currentScope, name, toQualifiedName(name), constLevel, FrameDescriptor.newBuilder()),
        nodeFactory);
  }

  public <T> T enterEntry(
      @Nullable ExpressionNode keyNode, // null for listing elements
      Function<EntryScope, T> nodeFactory) {

    var qualifiedName = currentScope.getQualifiedName() + currentScope.getNextEntryName(keyNode);
    var builder =
        currentScope instanceof ForGeneratorScope forScope
            ? forScope.memberDescriptorBuilder
            : FrameDescriptor.newBuilder();
    return doEnter(new EntryScope(currentScope, qualifiedName, builder), nodeFactory);
  }

  public <T> T enterCustomThisScope(Function<CustomThisScope, T> nodeFactory) {
    return doEnter(
        new CustomThisScope(currentScope, currentScope.frameDescriptorBuilder), nodeFactory);
  }

  public <T> T enterAnnotationScope(Function<AnnotationScope, T> nodeFactory) {
    return doEnter(
        new AnnotationScope(currentScope, currentScope.frameDescriptorBuilder), nodeFactory);
  }

  public <T> T enterObjectScope(ObjectBody body, Function<ObjectScope, T> nodeFactory) {
    return doEnter(
        new ObjectScope(currentScope, body, currentScope.frameDescriptorBuilder), nodeFactory);
  }

  private <T, S extends Scope> T doEnter(S scope, Function<S, T> nodeFactory) {
    var parentScope = currentScope;
    currentScope = scope;
    try {
      return nodeFactory.apply(scope);
    } finally {
      currentScope = parentScope;
    }
  }

  private String toQualifiedName(Identifier name) {
    var separator = currentScope instanceof ModuleScope ? "#" : ".";
    return currentScope.qualifiedName + separator + Lexer.maybeQuoteIdentifier(name.toString());
  }

  public abstract static class Scope {
    private final @Nullable Scope parent;
    private final @Nullable Identifier name;
    private final String qualifiedName;
    private int lambdaCount = 0;
    private int entryCount = 0;
    private final FrameDescriptor.Builder frameDescriptorBuilder;
    private final ConstLevel constLevel;

    private Scope(
        @Nullable Scope parent,
        @Nullable Identifier name,
        String qualifiedName,
        ConstLevel constLevel,
        FrameDescriptor.Builder frameDescriptorBuilder) {
      this.parent = parent;
      this.name = name;
      this.qualifiedName = qualifiedName;
      this.frameDescriptorBuilder = frameDescriptorBuilder;
      // const level can never decrease
      this.constLevel =
          parent != null && parent.constLevel.biggerOrEquals(constLevel)
              ? parent.constLevel
              : constLevel;
    }

    public final @Nullable Scope getParent() {
      return parent;
    }

    public final Identifier getName() {
      assert name != null;
      return name;
    }

    public final @Nullable Identifier getNameOrNull() {
      return name;
    }

    public final String getQualifiedName() {
      return qualifiedName;
    }

    public FrameDescriptor buildFrameDescriptor() {
      return frameDescriptorBuilder.build();
    }

    /**
     * Returns a new descriptor builder that contains the same slots as the current scope's frame
     * descriptor.
     */
    public FrameDescriptor.Builder newFrameDescriptorBuilder() {
      return newFrameDescriptorBuilder(buildFrameDescriptor());
    }

    /** Returns a new descriptor builder for a {@link GeneratorMemberNode} in the current scope. */
    public FrameDescriptor.Builder newForGeneratorMemberDescriptorBuilder() {
      return this instanceof ForGeneratorScope forScope
          ? newFrameDescriptorBuilder(forScope.buildMemberDescriptor())
          : FrameDescriptor.newBuilder();
    }

    private static FrameDescriptor.Builder newFrameDescriptorBuilder(FrameDescriptor descriptor) {
      var builder = FrameDescriptor.newBuilder();
      for (var i = 0; i < descriptor.getNumberOfSlots(); i++) {
        builder.addSlot(
            descriptor.getSlotKind(i), descriptor.getSlotName(i), descriptor.getSlotInfo(i));
      }
      return builder;
    }

    public @Nullable TypeParameter getTypeParameter(String name) {
      return null;
    }

    public final Scope getLexicalScope() {
      var scope = this;
      while (!scope.isLexicalScope()) {
        scope = scope.parent;
        assert scope != null;
      }
      return scope;
    }

    /**
     * Returns the lexical depth from the current scope to the top-most scope that is const. Depth
     * is 0-indexed, and -1 means that the scope is not a const scope.
     *
     * <p>A const scope is a lexical scope on the right-hand side of a const property.
     *
     * <pre>{@code
     * const foo = new {
     *   bar {
     *     baz // <-- depth == 1
     *   }
     * }
     * }</pre>
     */
    public int getConstDepth() {
      var depth = -1;
      var lexicalScope = getLexicalScope();
      while (lexicalScope.getConstLevel() == ConstLevel.ALL) {
        depth += 1;
        var parent = lexicalScope.getParent();
        if (parent == null) {
          return depth;
        }
        lexicalScope = parent.getLexicalScope();
      }
      return depth;
    }

    private String getNextLambdaName() {
      return "<function#" + (++skipLambdaScopes().lambdaCount) + ">";
    }

    protected String getNextEntryName(@Nullable ExpressionNode keyNode) {
      if (keyNode instanceof ConstantNode constantNode) {
        var value = constantNode.getValue();
        if (value instanceof String) {
          return "[\"" + value + "\"]";
        }

        if (value instanceof Long
            || value instanceof Double
            || value instanceof Boolean
            || value instanceof VmDuration
            || value instanceof VmDataSize) {
          return "[" + value + "]";
        }
      }

      return "[#" + (++entryCount) + "]";
    }

    public final Scope skipLambdaScopes() {
      var curr = this;
      while (curr.isLambdaScope()) {
        curr = curr.getParent();
        assert curr != null : "Lambda scope always has a parent";
      }
      return curr;
    }

    public final boolean isModuleScope() {
      return this instanceof ModuleScope;
    }

    public final boolean isClassScope() {
      return this instanceof ClassScope;
    }

    public final boolean isClassMemberScope() {
      var effectiveScope = skipLambdaScopes();
      var parent = effectiveScope.parent;
      if (parent == null) return false;

      return parent.isClassScope()
          || parent.isModuleScope() && !((ModuleScope) parent).moduleInfo.isAmend();
    }

    public final boolean isLambdaScope() {
      return this instanceof LambdaScope;
    }

    public final boolean isCustomThisScope() {
      return this instanceof CustomThisScope;
    }

    public final boolean isLexicalScope() {
      return this instanceof LexicalScope;
    }

    public final boolean isForGeneratorScope() {
      return this instanceof ForGeneratorScope;
    }

    public ConstLevel getConstLevel() {
      return constLevel;
    }

    @FunctionalInterface
    private interface ResolutionFunction<T> {
      @Nullable
      T apply(LexicalScope scope, int levelUp);
    }

    public @Nullable PartiallyResolvedVariable resolveProperty(
        Identifier name, Function<PropertyResolution, PartiallyResolvedVariable> fun) {
      return resolve((scope, levelUp) -> scope.doResolveProperty(name, levelUp, fun));
    }

    public @Nullable Node resolveMethod(Identifier name, Function<MethodResolution, Node> fun) {
      return resolve((scope, levelUp) -> scope.doResolveMethod(name, levelUp, fun));
    }

    private <T> @Nullable T resolve(ResolutionFunction<T> fun) {
      var levelUp = 0;
      var shouldSkip = false;
      for (var scope = this; scope != null; scope = scope.getParent()) {
        // for headers resolve variables one scope up
        if (scope instanceof ForEagerScope) {
          shouldSkip = true;
          continue;
        }
        if (scope instanceof LexicalScope lex) {
          if (shouldSkip && !(scope instanceof ForGeneratorScope)) {
            shouldSkip = false;
            continue;
          }
          var result = fun.apply(lex, levelUp);
          if (result != null) return result;
          if (scope instanceof MethodScope || scope instanceof ForGeneratorScope) {
            // fors and methods don't level up
            continue;
          }
          levelUp++;
        }
      }
      return null;
    }
  }

  protected interface LexicalScope {
    @Nullable
    PartiallyResolvedVariable doResolveProperty(
        Identifier name,
        int levelUp,
        Function<PropertyResolution, PartiallyResolvedVariable> callback);

    @Nullable
    Node doResolveMethod(Identifier name, int levelUp, Function<MethodResolution, Node> callback);
  }

  public static class ObjectScope extends Scope implements LexicalScope {
    private final Map<String, Integer> params;
    private final Map<String, Integer> props;
    private final Map<String, Integer> methods;

    private ObjectScope(Scope parent, ObjectBody body, Builder frameDescriptorBuilder) {
      super(
          parent,
          parent.getNameOrNull(),
          parent.getQualifiedName(),
          ConstLevel.NONE,
          frameDescriptorBuilder);
      params = collectParams(body);
      props = collectProps(body);
      methods = collectMethods(body);
    }

    @Override
    public @Nullable PartiallyResolvedVariable doResolveProperty(
        Identifier name,
        int levelUp,
        Function<PropertyResolution, PartiallyResolvedVariable> callback) {
      var strName = name.toString();
      var prop = props.get(strName);
      if (prop != null) {
        if (VmModifier.isLocal(prop)) {
          return callback.apply(new LocalClassProperty(name.toLocalProperty(), levelUp));
        } else {
          return callback.apply(new NormalClassProperty(name, levelUp));
        }
      }
      var paramIndex = params.get(strName);
      if (paramIndex != null) {
        // params are on a higher level than the properties
        return callback.apply(new LetOrLambdaProperty(name));
      }
      return null;
    }

    @Override
    public @Nullable Node doResolveMethod(
        Identifier name, int levelUp, Function<MethodResolution, Node> callback) {
      //      var method = methods.get(name.toString());
      //      if (method != null) {
      //
      //      }
      return null;
    }

    private static Map<String, Integer> collectParams(ObjectBody body) {
      var params = new HashMap<String, Integer>();
      for (var i = 0; i < body.getParameters().size(); i++) {
        var param = body.getParameters().get(i);
        if (param instanceof Parameter.TypedIdentifier ti) {
          params.put(ti.getIdentifier().getValue(), i);
        } else {
          params.put("_", i);
        }
      }
      return params;
    }

    private static Map<String, Integer> collectProps(ObjectBody body) {
      var props = new HashMap<String, Integer>();
      for (var member : body.getMembers()) {
        if (member instanceof org.pkl.parser.syntax.ObjectMember.ObjectProperty prop) {
          props.put(prop.getIdentifier().getValue(), modifiers(prop.getModifiers()));
        }
      }
      return props;
    }

    private static Map<String, Integer> collectMethods(ObjectBody body) {
      var methods = new HashMap<String, Integer>();
      for (var member : body.getMembers()) {
        if (member instanceof org.pkl.parser.syntax.ObjectMember.ObjectMethod method) {
          methods.put(method.getIdentifier().getValue(), modifiers(method.getModifiers()));
        }
      }
      return methods;
    }

    private static int modifiers(List<Modifier> modifiers) {
      int res = 0;
      for (var mod : modifiers) {
        res += AstBuilder.toModifier(mod);
      }
      return res;
    }
  }

  public abstract static class TypeParameterizableScope extends Scope {
    private final List<TypeParameter> typeParameters;

    public TypeParameterizableScope(
        Scope parent,
        Identifier name,
        String qualifiedName,
        ConstLevel constLevel,
        Builder frameDescriptorBuilder,
        List<TypeParameter> typeParameters) {
      super(parent, name, qualifiedName, constLevel, frameDescriptorBuilder);
      this.typeParameters = typeParameters;
    }

    @Override
    public @Nullable TypeParameter getTypeParameter(String name) {
      for (var param : typeParameters) {
        if (name.equals(param.getName())) return param;
      }
      return null;
    }
  }

  public static final class ModuleScope extends Scope implements LexicalScope {
    private final ModuleInfo moduleInfo;

    public ModuleScope(ModuleInfo moduleInfo) {
      super(null, null, moduleInfo.getModuleName(), ConstLevel.NONE, FrameDescriptor.newBuilder());
      this.moduleInfo = moduleInfo;
    }

    // modules other than base have pkl:base as their parent
    public ModuleScope(ModuleInfo moduleInfo, BaseScope base) {
      super(base, null, moduleInfo.getModuleName(), ConstLevel.NONE, FrameDescriptor.newBuilder());
      this.moduleInfo = moduleInfo;
    }

    @Override
    public @Nullable PartiallyResolvedVariable doResolveProperty(
        Identifier name,
        int levelUp,
        Function<PropertyResolution, PartiallyResolvedVariable> callback) {
      return null;
    }

    @Override
    public @Nullable Node doResolveMethod(
        Identifier name, int levelUp, Function<MethodResolution, Node> callback) {
      return null;
    }
  }

  // The scope of pkl:base, implicitly imported in every file
  public static final class BaseScope extends Scope implements LexicalScope {
    private final VmTyped base;

    public BaseScope(VmTyped base) {
      super(null, null, "base", ConstLevel.NONE, FrameDescriptor.newBuilder());
      this.base = base;
    }

    @Override
    public @Nullable PartiallyResolvedVariable doResolveProperty(
        Identifier name,
        int levelUp,
        Function<PropertyResolution, PartiallyResolvedVariable> callback) {
      var cachedValue = base.getCachedValue(name);
      if (cachedValue != null) {
        return callback.apply(new ConstantProperty(cachedValue));
      }

      var member = base.getMember(name);

      if (member != null) {
        var constantValue = member.getConstantValue();
        if (constantValue != null) {
          base.setCachedValue(name, constantValue);
          return callback.apply(new ConstantProperty(constantValue));
        }

        var computedValue = member.getCallTarget().call(base, base);
        base.setCachedValue(name, computedValue);
        return callback.apply(new ConstantProperty(computedValue));
      }
      return null;
    }

    @Override
    public @Nullable Node doResolveMethod(
        Identifier name, int levelUp, Function<MethodResolution, Node> callback) {
      var method = base.getVmClass().getDeclaredMethod(name);
      if (method != null) {
        assert !method.isLocal();
        return callback.apply(new DirectMethod(method, new ConstantValueNode(base), true));
      }
      return null;
    }
  }

  public static final class MethodScope extends TypeParameterizableScope implements LexicalScope {
    private final List<String> bindings;

    public MethodScope(
        Scope parent,
        Identifier name,
        String qualifiedName,
        ConstLevel constLevel,
        List<String> bindings,
        Builder frameDescriptorBuilder,
        List<TypeParameter> typeParameters) {
      super(parent, name, qualifiedName, constLevel, frameDescriptorBuilder, typeParameters);
      this.bindings = bindings;
    }

    @Override
    public @Nullable PartiallyResolvedVariable doResolveProperty(
        Identifier name,
        int levelUp,
        Function<PropertyResolution, PartiallyResolvedVariable> callback) {
      var index = bindings.indexOf(name.toString());
      if (index != -1) {
        return callback.apply(new LetOrLambdaProperty(name));
      }
      return null;
    }

    @Override
    public @Nullable Node doResolveMethod(
        Identifier name, int levelUp, Function<MethodResolution, Node> callback) {
      return null;
    }
  }

  public static final class LambdaScope extends Scope implements LexicalScope {
    private final List<String> bindings;
    private final int slot;

    public LambdaScope(
        Scope parent,
        List<String> bindings,
        int slot,
        String qualifiedName,
        FrameDescriptor.Builder frameDescriptorBuilder) {
      super(parent, null, qualifiedName, ConstLevel.NONE, frameDescriptorBuilder);
      this.bindings = bindings;
      this.slot = slot;
    }

    @Override
    public @Nullable PartiallyResolvedVariable doResolveProperty(
        Identifier name,
        int levelUp,
        Function<PropertyResolution, PartiallyResolvedVariable> callback) {
      var index = bindings.indexOf(name.toString());
      if (index != -1) {
        var _slot = slot != -1 ? slot : index;
        return callback.apply(new LetOrLambdaProperty(name));
      }
      return null;
    }

    @Override
    public @Nullable Node doResolveMethod(
        Identifier name, int levelUp, Function<MethodResolution, Node> callback) {
      return null;
    }
  }

  // A scope used only for variable resolution
  public static final class ForEagerScope extends Scope {
    private ForEagerScope(@Nullable Scope parent, String qualifiedName) {
      super(parent, null, qualifiedName, ConstLevel.NONE, FrameDescriptor.newBuilder());
    }
  }

  public static final class ForGeneratorScope extends Scope implements LexicalScope {
    private final FrameDescriptor.Builder memberDescriptorBuilder;
    final List<String> params;
    private final int nestLevel;

    public ForGeneratorScope(
        Scope parent,
        String qualifiedName,
        List<String> params,
        int nestLevel,
        FrameDescriptor.Builder frameDescriptorBuilder,
        FrameDescriptor.Builder memberDescriptorBuilder) {
      super(parent, null, qualifiedName, ConstLevel.NONE, frameDescriptorBuilder);
      this.memberDescriptorBuilder = memberDescriptorBuilder;
      this.params = params;
      this.nestLevel = nestLevel;
    }

    public FrameDescriptor buildMemberDescriptor() {
      return memberDescriptorBuilder.build();
    }

    @Override
    protected String getNextEntryName(@Nullable ExpressionNode keyNode) {
      var parent = getParent();
      assert parent != null;
      return parent.getNextEntryName(keyNode);
    }

    @Override
    public @Nullable PartiallyResolvedVariable doResolveProperty(
        Identifier name,
        int levelUp,
        Function<PropertyResolution, PartiallyResolvedVariable> callback) {
      var index = params.indexOf(name.toString());
      if (index >= 0) {
        return callback.apply(new LetOrLambdaProperty(name));
      }
      return null;
    }

    @Override
    public @Nullable Node doResolveMethod(
        Identifier name, int levelUp, Function<MethodResolution, Node> callback) {
      return null;
    }
  }

  public static final class PropertyScope extends Scope {
    public PropertyScope(
        Scope parent,
        Identifier name,
        String qualifiedName,
        ConstLevel constLevel,
        FrameDescriptor.Builder frameDescriptorBuilder) {
      super(parent, name, qualifiedName, constLevel, frameDescriptorBuilder);
    }
  }

  public static final class EntryScope extends Scope {
    public EntryScope(
        Scope parent, String qualifiedName, FrameDescriptor.Builder frameDescriptorBuilder) {
      super(parent, null, qualifiedName, ConstLevel.NONE, frameDescriptorBuilder);
    }
  }

  public static final class ClassScope extends TypeParameterizableScope implements LexicalScope {
    private final Map<Identifier, List<Modifier>> properties;
    private final Map<Identifier, ClassMethod> methods;
    private final boolean isClosed;

    public ClassScope(
        Scope parent,
        Identifier name,
        String qualifiedName,
        Class clazz,
        Builder frameDescriptorBuilder,
        List<TypeParameter> typeParameters) {
      super(parent, name, qualifiedName, ConstLevel.MODULE, frameDescriptorBuilder, typeParameters);
      properties = collectProperties(clazz);
      methods = collectMethods(clazz);
      isClosed = isClosed(clazz);
    }

    @Override
    public @Nullable PartiallyResolvedVariable doResolveProperty(
        Identifier name,
        int levelUp,
        Function<PropertyResolution, PartiallyResolvedVariable> callback) {

      var modifiers = properties.get(name);
      if (modifiers == null) return null;
      if (isLocal(modifiers)) {
        return callback.apply(new LocalClassProperty(name.toLocalProperty(), levelUp));
      } else {
        return callback.apply(new NormalClassProperty(name, levelUp));
      }
    }

    @Override
    public @Nullable Node doResolveMethod(
        Identifier name, int levelUp, Function<MethodResolution, Node> callback) {

      var method = methods.get(name);
      if (method == null) return null;
      if (isClosed || isLocal(method.getModifiers())) {
        return callback.apply(new LexicalMethod(method, levelUp));
      } else {
        return callback.apply(new VirtualMethod(method, levelUp));
      }
    }

    private static Map<Identifier, List<Modifier>> collectProperties(Class clazz) {
      var map = new HashMap<Identifier, List<Modifier>>();
      var body = clazz.getBody();
      if (body == null) return map;
      for (var prop : body.getProperties()) {
        map.put(Identifier.get(prop.getName().getValue()), prop.getModifiers());
      }
      return map;
    }

    private static Map<Identifier, ClassMethod> collectMethods(Class clazz) {
      var map = new HashMap<Identifier, ClassMethod>();
      var body = clazz.getBody();
      if (body == null) return map;
      for (var prop : body.getMethods()) {
        map.put(Identifier.get(prop.getName().getValue()), prop);
      }
      return map;
    }

    private static boolean isLocal(List<Modifier> modifiers) {
      for (var modifier : modifiers) {
        if (modifier.getValue() == ModifierValue.LOCAL) return true;
      }
      return false;
    }

    private static boolean isClosed(Class clazz) {
      for (var modifier : clazz.getModifiers()) {
        if (modifier.getValue() == ModifierValue.OPEN
            || modifier.getValue() == ModifierValue.ABSTRACT) {
          return false;
        }
      }
      return true;
    }
  }

  public static final class TypeAliasScope extends TypeParameterizableScope {
    public TypeAliasScope(
        Scope parent,
        Identifier name,
        String qualifiedName,
        FrameDescriptor.Builder frameDescriptorBuilder,
        List<TypeParameter> typeParameters) {
      super(parent, name, qualifiedName, ConstLevel.MODULE, frameDescriptorBuilder, typeParameters);
    }
  }

  /**
   * A scope where {@code this} has a special meaning (type constraint, object member predicate).
   *
   * <p>Technically, a scope where {@code this} isn't {@code frame.getArguments()[0]}, but the value
   * at an auxiliary slot identified by {@link CustomThisScope#FRAME_SLOT_ID}.
   */
  public static final class CustomThisScope extends Scope {
    public static final Object FRAME_SLOT_ID =
        new Object() {
          @Override
          public String toString() {
            return "customThisSlot";
          }
        };

    public CustomThisScope(Scope parent, FrameDescriptor.Builder frameDescriptorBuilder) {
      super(
          parent,
          parent.getNameOrNull(),
          parent.getQualifiedName(),
          ConstLevel.NONE,
          frameDescriptorBuilder);
    }
  }

  public static final class AnnotationScope extends Scope implements LexicalScope {
    public AnnotationScope(Scope parent, FrameDescriptor.Builder frameDescriptorBuilder) {
      super(
          parent,
          parent.getNameOrNull(),
          parent.getQualifiedName(),
          ConstLevel.MODULE,
          frameDescriptorBuilder);
    }

    @Override
    public @Nullable PartiallyResolvedVariable doResolveProperty(
        Identifier name,
        int levelUp,
        Function<PropertyResolution, PartiallyResolvedVariable> callback) {
      return null;
    }

    @Override
    public @Nullable Node doResolveMethod(
        Identifier name, int levelUp, Function<MethodResolution, Node> callback) {
      return null;
    }
  }
}
