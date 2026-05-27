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

import static org.pkl.core.util.ArrayUtils.EMPTY_INT_ARRAY;

import com.oracle.truffle.api.frame.FrameDescriptor;
import java.util.*;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.pkl.core.TypeParameter;
import org.pkl.core.ast.ConstantNode;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.builder.MethodResolution.ImplicitBaseMethod;
import org.pkl.core.ast.builder.MethodResolution.ImplicitThisMethod;
import org.pkl.core.ast.builder.MethodResolution.LexicalMethod;
import org.pkl.core.ast.builder.VariableResolution.ForGeneratorVariableOrLetBinding;
import org.pkl.core.ast.builder.VariableResolution.ImplicitBaseProperty;
import org.pkl.core.ast.builder.VariableResolution.LexicalProperty;
import org.pkl.core.ast.builder.VariableResolution.Parameter;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.BaseModuleMembers;
import org.pkl.core.runtime.FrameDescriptorBuilder;
import org.pkl.core.runtime.FrameSlotVariable;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.ModuleInfo;
import org.pkl.core.runtime.VmDataSize;
import org.pkl.core.runtime.VmDuration;
import org.pkl.core.util.ArrayUtils;
import org.pkl.core.util.LateInit;
import org.pkl.parser.Lexer;

public final class SymbolTable {

  private Scope currentScope;

  public SymbolTable(ModuleInfo moduleInfo, boolean isBaseModule) {
    currentScope = new ModuleScope(moduleInfo, isBaseModule);
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
      int modifiers,
      List<TypeParameter> typeParameters,
      Function<ClassScope, ObjectMember> nodeFactory) {
    return doEnter(
        new ClassScope(
            currentScope,
            name,
            toQualifiedName(name),
            modifiers,
            new FrameDescriptorBuilder(),
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
            new FrameDescriptorBuilder(),
            typeParameters),
        nodeFactory);
  }

  public <T> T enterMethod(
      Identifier name,
      ConstLevel constLevel,
      FrameSlotVariable[] bindings,
      FrameDescriptorBuilder frameDescriptorBuilder,
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

  public <T> T enterEagerGenerator(Function<EagerGeneratorScope, T> nodeFactory) {
    return doEnter(new EagerGeneratorScope(currentScope, currentScope.qualifiedName), nodeFactory);
  }

  public <T> T enterForGenerator(
      @Nullable FrameSlotVariable keyBinding,
      @Nullable FrameSlotVariable valueBinding,
      FrameDescriptorBuilder frameDescriptorBuilder,
      Function<ForGeneratorScope, T> nodeFactory) {
    return doEnter(
        new ForGeneratorScope(
            currentScope,
            currentScope.qualifiedName,
            keyBinding,
            valueBinding,
            frameDescriptorBuilder),
        nodeFactory);
  }

  public <T> T enterLambda(
      FrameSlotVariable[] bindings,
      FrameDescriptorBuilder frameDescriptorBuilder,
      Function<LambdaScope, T> nodeFactory) {

    // flatten names of lambdas nested inside other lambdas for presentation purposes
    var parentScope = currentScope;
    while (parentScope instanceof LambdaScope) {
      parentScope = parentScope.getParent();
    }

    assert parentScope != null;
    var qualifiedName = parentScope.qualifiedName + "." + parentScope.getNextLambdaName();

    return doEnter(
        new LambdaScope(currentScope, bindings, qualifiedName, frameDescriptorBuilder),
        nodeFactory);
  }

  public <T> T enterLetExpression(
      @Nullable FrameSlotVariable binding, Function<LetExpressionScope, T> nodeFactory) {

    // flatten names of let exprs inside other let exprs for presentation purposes
    var parentScope = currentScope;
    while (parentScope instanceof LetExpressionScope) {
      parentScope = parentScope.getParent();
    }

    assert parentScope != null;
    var qualifiedName = parentScope.qualifiedName + "." + "<let expr>";
    return doEnter(new LetExpressionScope(currentScope, binding, qualifiedName), nodeFactory);
  }

  public <T> T enterProperty(
      Identifier name, ConstLevel constLevel, Function<PropertyScope, T> nodeFactory) {
    return doEnter(
        new PropertyScope(
            currentScope, name, toQualifiedName(name), constLevel, new FrameDescriptorBuilder()),
        nodeFactory);
  }

  public <T> T enterEntryOrElement(
      @Nullable ExpressionNode keyNode, // null for listing elements
      Function<EntryOrElementScope, T> nodeFactory) {

    var qualifiedName = currentScope.getQualifiedName() + currentScope.getNextEntryName(keyNode);
    var builder =
        currentScope instanceof ForGeneratorScope forScope
            ? forScope.frameDescriptorBuilder
            : new FrameDescriptorBuilder();
    return doEnter(new EntryOrElementScope(currentScope, qualifiedName, builder), nodeFactory);
  }

  public <T> T enterCustomThisScope(Function<CustomThisScope, T> nodeFactory) {
    return doEnter(
        new CustomThisScope(currentScope, currentScope.frameDescriptorBuilder), nodeFactory);
  }

  public <T> T enterAnnotationScope(
      @Nullable Identifier annotatedTargetName, Function<AnnotationScope, T> nodeFactory) {
    var qualifiedName =
        annotatedTargetName == null
            ? currentScope.getQualifiedName()
            : toQualifiedName(annotatedTargetName);
    return doEnter(
        new AnnotationScope(currentScope, qualifiedName, currentScope.frameDescriptorBuilder),
        nodeFactory);
  }

  public <T> T enterObjectScope(
      FrameSlotVariable[] bindings, Function<ObjectScope, T> nodeFactory) {
    return doEnter(
        new ObjectScope(currentScope, bindings, currentScope.frameDescriptorBuilder), nodeFactory);
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

  public record Member(Identifier name, int modifiers) {}

  public abstract static class Scope {
    private final @Nullable Scope parent;
    private final @Nullable Identifier name;
    private final String qualifiedName;
    private int lambdaCount = 0;
    private int entryCount = 0;
    protected final FrameDescriptorBuilder frameDescriptorBuilder;
    private final ConstLevel constLevel;
    protected boolean isBaseModule;
    // all for-generator slots in this scope (excludes args and let bindings).
    protected final int[] forGeneratorSlots;
    // all paramter slots in this scope; includes let bindings, function params, method params,
    // but excludes object body params (they are written one level higher)
    protected final int[] parameterSlots;
    // The properties defined on this (lexical) scope
    protected final Map<String, Member> properties = new HashMap<>();
    // The methods defined on this (lexical) scope
    protected final Map<String, Member> methods = new HashMap<>();

    static int[] getSlots(FrameSlotVariable[] bindings) {
      if (bindings.length == 0) {
        return EMPTY_INT_ARRAY;
      }
      var ret = new int[bindings.length];
      for (var i = 0; i < ret.length; i++) {
        ret[i] = bindings[i].slot();
      }
      return ret;
    }

    private Scope(
        @Nullable Scope parent,
        @Nullable Identifier name,
        String qualifiedName,
        ConstLevel constLevel,
        FrameDescriptorBuilder frameDescriptorBuilder,
        int[] forGeneratorSlots,
        int[] parameterSlots) {
      this.parent = parent;
      this.name = name;
      this.qualifiedName = qualifiedName;
      this.frameDescriptorBuilder = frameDescriptorBuilder;
      if (parent != null) {
        this.isBaseModule = parent.isBaseModule;
      }
      // const level can never decrease
      this.constLevel =
          parent != null && parent.constLevel.biggerOrEquals(constLevel)
              ? parent.constLevel
              : constLevel;
      this.forGeneratorSlots = forGeneratorSlots;
      this.parameterSlots = parameterSlots;
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

    public int[] getForGeneratorSlots() {
      return forGeneratorSlots;
    }

    /**
     * Returns the parameter slots in this scope.
     *
     * <p>Includes let bindings, object body params, method params, lambda params
     */
    public int[] getParameterSlots() {
      return parameterSlots;
    }

    public FrameDescriptor buildFrameDescriptor() {
      return frameDescriptorBuilder.build();
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
        // LambdaScope inherits constLevel but doesn't create a const scope barrier
        if (!(lexicalScope instanceof LambdaScope)) {
          depth += 1;
        }
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

    public final Scope skipLambdaAndLetScopes() {
      var curr = this;
      while (curr.isLambdaScope() || curr.isLetScope()) {
        curr = curr.getParent();
        assert curr != null : "Lambda scope always has a parent";
      }
      return curr;
    }

    public final boolean isLetScope() {
      return this instanceof LetExpressionScope;
    }

    public final boolean isModuleScope() {
      return this instanceof ModuleScope;
    }

    public final boolean isClassScope() {
      return this instanceof ClassScope;
    }

    public final boolean isClassMemberScope() {
      var effectiveScope = skipLambdaAndLetScopes();
      var parent = effectiveScope.parent;
      if (parent == null) return false;

      return parent.isClassScope()
          || parent.isModuleScope() && !((ModuleScope) parent).moduleInfo.isAmend();
    }

    public final boolean isLambdaScope() {
      return this instanceof LambdaScope;
    }

    public final boolean isCustomThisScope() {
      if (this instanceof LetExpressionScope) {
        var myParent = parent;
        while (myParent instanceof LetExpressionScope) {
          myParent = myParent.getParent();
        }
        return myParent instanceof CustomThisScope;
      }
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

    public void addProperty(Identifier name, int modifiers) {
      var prevProperty = this.properties.put(name.toString(), new Member(name, modifiers));
      if (prevProperty != null
          && !VmModifier.hasSameModifier(prevProperty.modifiers, modifiers, VmModifier.LOCAL)) {
        // this can happen in when generators:
        //
        // ```
        // when (cond) {
        //   local prop = 1
        // } else {
        //   prop = 2
        // }
        // ```
        //
        // this can't happen with methods; object methods can only be `local`.
        this.properties.put(
            name.toString(), new Member(name, modifiers | VmModifier.AMBIGUOUS_LOCALITY));
      }
    }

    public void addMethod(Identifier name, int modifiers) {
      this.methods.put(name.toString(), new Member(name, modifiers));
    }

    public final VariableResolution resolveVariable(String name) {
      var resolved = resolveLexical((scope, levelUp) -> scope.doResolveProperty(name, levelUp));
      if (resolved != null) {
        return resolved;
      }
      if (!isBaseModule) {
        if (BaseModuleMembers.hasProperty(name)) {
          return new ImplicitBaseProperty();
        }
      }
      return new VariableResolution.ImplicitThisProperty();
    }

    public final MethodResolution resolveMethod(String name) {
      var resolved = resolveLexical((scope, levelUp) -> scope.doResolveMethod(name, levelUp));
      if (resolved != null) {
        return resolved;
      }
      if (!isBaseModule) {
        if (BaseModuleMembers.hasMethod(name)) {
          return new ImplicitBaseMethod();
        }
      }
      return new ImplicitThisMethod();
    }

    @FunctionalInterface
    private interface ResolutionFunction<T> {
      @Nullable T apply(LexicalScope scope, int levelUp);
    }

    private @Nullable <R> R resolveLexical(ResolutionFunction<R> fun) {
      var levelsUp = 0;
      var shouldSkip = false;
      for (var scope = this; scope != null; scope = scope.getParent()) {
        // for headers resolve variables one scope up
        if (scope instanceof EagerGeneratorScope) {
          shouldSkip = true;
          continue;
        }
        // annotations on class members don't level up
        if (scope instanceof AnnotationScope && scope.getParent() instanceof ClassScope) {
          levelsUp--;
        }
        if (scope instanceof LexicalScope lex) {
          if (shouldSkip && !(scope instanceof ForGeneratorScope)) {
            if (scope instanceof ObjectScope objectScope && objectScope.hasParams()) {
              levelsUp++;
            }
            shouldSkip = false;
            continue;
          }
          var result = fun.apply(lex, levelsUp);
          if (result != null) return result;
          if (scope instanceof MethodScope
              || scope instanceof ForGeneratorScope
              || scope instanceof LetExpressionScope) {
            // fors, methods, and let exprs don't level up
            continue;
          }
          levelsUp++;
          if (scope instanceof ObjectScope objectScope && objectScope.hasParams()) {
            levelsUp++;
          }
        }
      }
      return null;
    }
  }

  public interface LexicalScope {
    @Nullable VariableResolution doResolveProperty(String name, int levelsUp);

    @Nullable MethodResolution doResolveMethod(String name, int levelsUp);
  }

  public static class ObjectScope extends Scope implements LexicalScope {
    private final FrameSlotVariable[] bindings;

    /**
     * NOTE: object body params desguar to wrapping this object with a lambda call.
     *
     * <p>So, the object itself does not contribute to parameter slots in the object's frame
     * descriptor.
     *
     * <p>This code:
     *
     * <pre>{@code
     * foo { param ->
     *   res = param
     * }
     * }</pre>
     *
     * Is sugar for:
     *
     * <pre>{@code
     * foo = (param) -> (super.foo.apply(param)) {
     *   res = param
     * }
     * }</pre>
     */
    private ObjectScope(
        Scope parent, FrameSlotVariable[] bindings, FrameDescriptorBuilder frameDescriptorBuilder) {
      super(
          parent,
          parent.getNameOrNull(),
          parent.getQualifiedName(),
          ConstLevel.NONE,
          frameDescriptorBuilder,
          parent.forGeneratorSlots,
          EMPTY_INT_ARRAY);
      this.bindings = bindings;
    }

    public boolean hasParams() {
      return bindings.length > 0;
    }

    @Override
    public @Nullable VariableResolution doResolveProperty(String name, int levelsUp) {
      // Underscore is a discard identifier and should not be resolvable
      if (name.equals("_")) {
        return null;
      }
      var prop = properties.get(name);
      if (prop != null) {
        return new VariableResolution.LexicalProperty(false, prop.modifiers, levelsUp);
      }
      for (var binding : bindings) {
        if (binding.name().equals(name)) {
          // params are on a higher level than the properties
          return new VariableResolution.Parameter(binding.slot(), levelsUp + 1);
        }
      }
      return null;
    }

    @Override
    public @Nullable MethodResolution doResolveMethod(String name, int levelsUp) {
      var method = methods.get(name);
      if (method != null) {
        return new LexicalMethod(true, false, false, method.modifiers, levelsUp);
      }
      return null;
    }
  }

  public abstract static class TypeParameterizableScope extends Scope {
    private final List<TypeParameter> typeParameters;

    public TypeParameterizableScope(
        Scope parent,
        Identifier name,
        String qualifiedName,
        ConstLevel constLevel,
        FrameDescriptorBuilder frameDescriptorBuilder,
        List<TypeParameter> typeParameters,
        int[] forGeneratorSlots,
        int[] parameterSlots) {
      super(
          parent,
          name,
          qualifiedName,
          constLevel,
          frameDescriptorBuilder,
          forGeneratorSlots,
          parameterSlots);
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
    @LateInit private boolean isClosed;
    private final boolean isAmend;

    public ModuleScope(ModuleInfo moduleInfo, boolean isBaseModule) {
      super(
          null,
          null,
          moduleInfo.getModuleName(),
          ConstLevel.NONE,
          new FrameDescriptorBuilder(),
          EMPTY_INT_ARRAY,
          EMPTY_INT_ARRAY);
      this.isBaseModule = isBaseModule;
      this.moduleInfo = moduleInfo;
      this.isAmend = moduleInfo.isAmend();
    }

    public void setModifiers(int modifiers) {
      this.isClosed = VmModifier.isClosed(modifiers);
    }

    @Override
    public @Nullable VariableResolution doResolveProperty(String name, int levelsUp) {
      var member = properties.get(name);
      if (member != null) {
        return new LexicalProperty(true, member.modifiers, levelsUp);
      }
      return null;
    }

    @Override
    public @Nullable MethodResolution doResolveMethod(String name, int levelsUp) {
      var method = methods.get(name);
      if (method == null) return null;
      var isObjectMethod = isAmend && VmModifier.isLocal(method.modifiers);
      return new LexicalMethod(isObjectMethod, isClosed, true, method.modifiers, levelsUp);
    }
  }

  public static final class MethodScope extends TypeParameterizableScope implements LexicalScope {
    private final FrameSlotVariable[] bindings;

    MethodScope(
        Scope parent,
        Identifier name,
        String qualifiedName,
        ConstLevel constLevel,
        FrameSlotVariable[] bindings,
        FrameDescriptorBuilder frameDescriptorBuilder,
        List<TypeParameter> typeParameters) {
      super(
          parent,
          name,
          qualifiedName,
          constLevel,
          frameDescriptorBuilder,
          typeParameters,
          EMPTY_INT_ARRAY,
          getSlots(bindings));
      this.bindings = bindings;
    }

    @Override
    public @Nullable VariableResolution doResolveProperty(String name, int levelsUp) {
      return resolveParameter(name, bindings, levelsUp);
    }

    @Override
    public @Nullable MethodResolution doResolveMethod(String name, int levelsUp) {
      return null;
    }
  }

  public static final class LambdaScope extends Scope implements LexicalScope {
    private final FrameSlotVariable[] bindings;

    public LambdaScope(
        Scope parent,
        FrameSlotVariable[] bindings,
        String qualifiedName,
        FrameDescriptorBuilder frameDescriptorBuilder) {
      super(
          parent,
          null,
          qualifiedName,
          parent.getConstLevel(),
          frameDescriptorBuilder,
          EMPTY_INT_ARRAY,
          getSlots(bindings));
      this.bindings = bindings;
    }

    @Override
    public @Nullable VariableResolution doResolveProperty(String name, int levelsUp) {
      return resolveParameter(name, bindings, levelsUp);
    }

    @Override
    public @Nullable MethodResolution doResolveMethod(String name, int levelsUp) {
      return null;
    }
  }

  public static final class LetExpressionScope extends Scope implements LexicalScope {
    public static final Object LET_BINDING_SLOT = new Object();
    private final @Nullable FrameSlotVariable binding;

    private static @Nullable Identifier getParentName(Scope parent) {
      while (parent != null && parent.name == null) {
        parent = parent.getParent();
      }
      return parent == null ? null : parent.name;
    }

    private static int[] getMyParameterSlots(Scope parent, @Nullable FrameSlotVariable binding) {
      var parentSlots = parent.parameterSlots;
      if (binding == null) {
        return parentSlots;
      }
      return ArrayUtils.plus(parentSlots, binding.slot());
    }

    public LetExpressionScope(
        Scope parent, @Nullable FrameSlotVariable binding, String qualifiedName) {
      super(
          parent,
          getParentName(parent),
          qualifiedName,
          parent.getConstLevel(),
          parent.frameDescriptorBuilder,
          parent.forGeneratorSlots,
          getMyParameterSlots(parent, binding));
      this.binding = binding;
    }

    @Override
    public @Nullable VariableResolution doResolveProperty(String name, int levelsUp) {
      if (name.equals("_") || binding == null) {
        return null;
      }
      if (name.equals(binding.name())) {
        return new ForGeneratorVariableOrLetBinding(binding.slot(), levelsUp);
      }
      return null;
    }

    @Override
    public @Nullable MethodResolution doResolveMethod(String name, int levelsUp) {
      return null;
    }
  }

  // A generator scope that is resolved eagerly and one level above
  public static final class EagerGeneratorScope extends Scope {
    private static FrameDescriptorBuilder getFrameDescriptorBuilder(Scope parent) {
      var grandParent = parent.parent;
      assert grandParent != null;
      return grandParent.frameDescriptorBuilder;
    }

    private static int[] getForGeneratorSlots(Scope parent) {
      var grandParent = parent.parent;
      assert grandParent != null;
      return grandParent.forGeneratorSlots;
    }

    private static int[] getParameterSlots(Scope parent) {
      var grandParent = parent.parent;
      assert grandParent != null;
      return grandParent.parameterSlots;
    }

    private EagerGeneratorScope(Scope parent, String qualifiedName) {
      super(
          parent,
          null,
          qualifiedName,
          ConstLevel.NONE,
          getFrameDescriptorBuilder(parent),
          getForGeneratorSlots(parent),
          getParameterSlots(parent));
    }
  }

  public static final class ForGeneratorScope extends Scope implements LexicalScope {
    private final @Nullable FrameSlotVariable keyBinding;
    private final @Nullable FrameSlotVariable valueBinding;

    private static int[] getMyForGeneratorSlots(
        Scope parentScope,
        @Nullable FrameSlotVariable keyBinding,
        @Nullable FrameSlotVariable valueBinding) {
      var slots = parentScope.forGeneratorSlots;
      if (keyBinding != null && valueBinding != null) {
        return ArrayUtils.plus(slots, keyBinding.slot(), valueBinding.slot());
      }
      if (keyBinding != null) {
        return ArrayUtils.plus(slots, keyBinding.slot());
      }
      if (valueBinding != null) {
        return ArrayUtils.plus(slots, valueBinding.slot());
      }
      return slots;
    }

    // for-generators execute in the frame above their enclosing object.
    // so, the parameters of the scope outside that object is visible.
    //
    // e.g. this for-generator reads param `it` as levels up == 0
    // ```
    // (it) -> new Listing {
    //   for (elem in it) {
    //     doSomething(elem)
    //   }
    // }
    // ```
    private static int[] getMyParameterSlots(Scope parent) {
      if (parent instanceof ObjectScope) {
        var grandParent = parent.parent;
        assert grandParent != null;
        return grandParent.parameterSlots;
      }
      return parent.parameterSlots;
    }

    public ForGeneratorScope(
        Scope parent,
        String qualifiedName,
        @Nullable FrameSlotVariable keyBinding,
        @Nullable FrameSlotVariable valueBinding,
        FrameDescriptorBuilder frameDescriptorBuilder) {
      super(
          parent,
          null,
          qualifiedName,
          ConstLevel.NONE,
          frameDescriptorBuilder,
          getMyForGeneratorSlots(parent, keyBinding, valueBinding),
          getMyParameterSlots(parent));
      this.keyBinding = keyBinding;
      this.valueBinding = valueBinding;
    }

    @Override
    protected String getNextEntryName(@Nullable ExpressionNode keyNode) {
      var parent = getParent();
      assert parent != null;
      return parent.getNextEntryName(keyNode);
    }

    @Override
    public @Nullable VariableResolution doResolveProperty(String name, int levelsUp) {
      if (keyBinding != null && keyBinding.name().equals(name)) {
        return new ForGeneratorVariableOrLetBinding(keyBinding.slot(), levelsUp);
      }
      if (valueBinding != null && valueBinding.name().equals(name)) {
        return new ForGeneratorVariableOrLetBinding(valueBinding.slot(), levelsUp);
      }
      return null;
    }

    @Override
    public @Nullable MethodResolution doResolveMethod(String name, int levelsUp) {
      return null;
    }
  }

  public static final class PropertyScope extends Scope {
    public PropertyScope(
        Scope parent,
        Identifier name,
        String qualifiedName,
        ConstLevel constLevel,
        FrameDescriptorBuilder frameDescriptorBuilder) {
      super(
          parent,
          name,
          qualifiedName,
          constLevel,
          frameDescriptorBuilder,
          // object members inherit the for-generator slots of the parent for-generator, if it
          // exists.
          parent instanceof ForGeneratorScope ? parent.forGeneratorSlots : EMPTY_INT_ARRAY,
          parent.parameterSlots);
    }
  }

  public static final class EntryOrElementScope extends Scope {
    public EntryOrElementScope(
        Scope parent, String qualifiedName, FrameDescriptorBuilder frameDescriptorBuilder) {
      super(
          parent,
          null,
          qualifiedName,
          ConstLevel.NONE,
          frameDescriptorBuilder,
          parent instanceof ForGeneratorScope ? parent.forGeneratorSlots : EMPTY_INT_ARRAY,
          parent.parameterSlots);
    }
  }

  public static final class ClassScope extends TypeParameterizableScope implements LexicalScope {
    private final boolean isClosed;

    public ClassScope(
        Scope parent,
        Identifier name,
        String qualifiedName,
        int modifiers,
        FrameDescriptorBuilder frameDescriptorBuilder,
        List<TypeParameter> typeParameters) {
      super(
          parent,
          name,
          qualifiedName,
          ConstLevel.MODULE,
          frameDescriptorBuilder,
          typeParameters,
          EMPTY_INT_ARRAY,
          EMPTY_INT_ARRAY);
      isClosed = VmModifier.isClosed(modifiers);
    }

    @Override
    public @Nullable VariableResolution doResolveProperty(String name, int levelsUp) {

      var member = properties.get(name);
      if (member == null) return null;
      return new LexicalProperty(false, member.modifiers, levelsUp);
    }

    @Override
    public @Nullable MethodResolution doResolveMethod(String name, int levelsUp) {

      var member = methods.get(name);
      if (member == null) return null;
      return new LexicalMethod(false, isClosed, false, member.modifiers, levelsUp);
    }
  }

  public static final class TypeAliasScope extends TypeParameterizableScope {
    public TypeAliasScope(
        Scope parent,
        Identifier name,
        String qualifiedName,
        FrameDescriptorBuilder frameDescriptorBuilder,
        List<TypeParameter> typeParameters) {
      super(
          parent,
          name,
          qualifiedName,
          ConstLevel.MODULE,
          frameDescriptorBuilder,
          typeParameters,
          parent.forGeneratorSlots,
          parent.parameterSlots);
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

    public CustomThisScope(Scope parent, FrameDescriptorBuilder frameDescriptorBuilder) {
      super(
          parent,
          parent.getNameOrNull(),
          parent.getQualifiedName(),
          ConstLevel.NONE,
          frameDescriptorBuilder,
          parent.forGeneratorSlots,
          parent.parameterSlots);
    }
  }

  public static final class AnnotationScope extends Scope {
    public AnnotationScope(
        Scope parent, String qualifiedName, FrameDescriptorBuilder frameDescriptorBuilder) {
      super(
          parent,
          parent.getNameOrNull(),
          qualifiedName,
          ConstLevel.MODULE,
          frameDescriptorBuilder,
          EMPTY_INT_ARRAY,
          EMPTY_INT_ARRAY);
    }
  }

  private static @Nullable VariableResolution resolveParameter(
      String name, FrameSlotVariable[] bindings, int levelsUp) {
    for (var binding : bindings) {
      if (name.equals(binding.name())) {
        return new Parameter(binding.slot(), levelsUp);
      }
    }
    return null;
  }
}
