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
package org.pkl.core.ast.builder;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameDescriptor.Builder;
import com.oracle.truffle.api.frame.FrameSlotKind;
import java.util.*;
import java.util.function.Function;
import org.pkl.core.TypeParameter;
import org.pkl.core.ast.ConstantNode;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.parser.Lexer;
import org.pkl.core.parser.antlr.PklParser.ParameterContext;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.ModuleInfo;
import org.pkl.core.runtime.VmDataSize;
import org.pkl.core.runtime.VmDuration;
import org.pkl.core.util.Nullable;

public final class SymbolTable {
  private Scope currentScope;

  public static Object FOR_GENERATOR_VARIABLE = new Object();

  public SymbolTable(ModuleInfo moduleInfo) {
    currentScope = new ModuleScope(moduleInfo);
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
      List<TypeParameter> typeParameters,
      Function<ClassScope, ObjectMember> nodeFactory) {
    return doEnter(
        new ClassScope(
            currentScope,
            name,
            toQualifiedName(name),
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
      Builder frameDescriptorBuilder,
      List<TypeParameter> typeParameters,
      Function<MethodScope, T> nodeFactory) {
    return doEnter(
        new MethodScope(
            currentScope,
            name,
            toQualifiedName(name),
            constLevel,
            frameDescriptorBuilder,
            typeParameters),
        nodeFactory);
  }

  public <T> T enterLambda(
      FrameDescriptor.Builder frameDescriptorBuilder, Function<LambdaScope, T> nodeFactory) {

    // flatten names of lambdas nested inside other lambdas for presentation purposes
    var parentScope = currentScope;
    while (parentScope instanceof LambdaScope) {
      parentScope = parentScope.getParent();
    }

    assert parentScope != null;
    var qualifiedName = parentScope.qualifiedName + "." + parentScope.getNextLambdaName();

    return doEnter(
        new LambdaScope(currentScope, qualifiedName, frameDescriptorBuilder), nodeFactory);
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

    return doEnter(
        new EntryScope(currentScope, qualifiedName, FrameDescriptor.newBuilder()), nodeFactory);
  }

  public <T> T enterCustomThisScope(Function<CustomThisScope, T> nodeFactory) {
    return doEnter(
        new CustomThisScope(currentScope, currentScope.frameDescriptorBuilder), nodeFactory);
  }

  public <T> T enterAnnotationScope(Function<AnnotationScope, T> nodeFactory) {
    return doEnter(
        new AnnotationScope(currentScope, currentScope.frameDescriptorBuilder), nodeFactory);
  }

  public <T> T enterObjectScope(Function<ObjectScope, T> nodeFactory) {
    return doEnter(new ObjectScope(currentScope, currentScope.frameDescriptorBuilder), nodeFactory);
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
    private final Deque<Identifier> forGeneratorVariables = new ArrayDeque<>();
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

    /**
     * Adds the for generator variable to the frame descriptor.
     *
     * <p>Returns {@code -1} if a for-generator variable already exists with this name.
     */
    public int pushForGeneratorVariableContext(ParameterContext ctx) {
      var variable = Identifier.localProperty(ctx.typedIdentifier().Identifier().getText());
      if (forGeneratorVariables.contains(variable)) {
        return -1;
      }
      var slot =
          frameDescriptorBuilder.addSlot(FrameSlotKind.Illegal, variable, FOR_GENERATOR_VARIABLE);
      forGeneratorVariables.addLast(variable);
      return slot;
    }

    public void popForGeneratorVariable() {
      forGeneratorVariables.removeLast();
    }

    public Deque<Identifier> getForGeneratorVariables() {
      return forGeneratorVariables;
    }

    private String getNextLambdaName() {
      return "<function#" + (++skipLambdaScopes().lambdaCount) + ">";
    }

    private String getNextEntryName(@Nullable ExpressionNode keyNode) {
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

    public ConstLevel getConstLevel() {
      return constLevel;
    }
  }

  private interface LexicalScope {}

  public static class ObjectScope extends Scope implements LexicalScope {
    private ObjectScope(Scope parent, Builder frameDescriptorBuilder) {
      super(
          parent,
          parent.getNameOrNull(),
          parent.getQualifiedName(),
          ConstLevel.NONE,
          frameDescriptorBuilder);
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
  }

  public static final class MethodScope extends TypeParameterizableScope {
    public MethodScope(
        Scope parent,
        Identifier name,
        String qualifiedName,
        ConstLevel constLevel,
        Builder frameDescriptorBuilder,
        List<TypeParameter> typeParameters) {
      super(parent, name, qualifiedName, constLevel, frameDescriptorBuilder, typeParameters);
    }
  }

  public static final class LambdaScope extends Scope implements LexicalScope {
    public LambdaScope(
        Scope parent, String qualifiedName, FrameDescriptor.Builder frameDescriptorBuilder) {
      super(parent, null, qualifiedName, ConstLevel.NONE, frameDescriptorBuilder);
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
    public ClassScope(
        Scope parent,
        Identifier name,
        String qualifiedName,
        Builder frameDescriptorBuilder,
        List<TypeParameter> typeParameters) {
      super(parent, name, qualifiedName, ConstLevel.MODULE, frameDescriptorBuilder, typeParameters);
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
  }
}
