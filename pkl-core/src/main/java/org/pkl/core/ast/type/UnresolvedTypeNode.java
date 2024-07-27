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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import java.util.Set;
import org.pkl.core.TypeParameter;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.PklNode;
import org.pkl.core.ast.expression.primary.GetModuleNode;
import org.pkl.core.ast.type.TypeNode.*;
import org.pkl.core.ast.type.TypeNodeFactory.*;
import org.pkl.core.runtime.*;

public abstract class UnresolvedTypeNode extends PklNode {
  protected UnresolvedTypeNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  public abstract TypeNode execute(VirtualFrame frame);

  public static final class Constrained extends UnresolvedTypeNode {
    @Child UnresolvedTypeNode childNode;
    TypeConstraintNode[] constraintCheckNodes;

    public Constrained(
        SourceSection sourceSection,
        UnresolvedTypeNode childNode,
        TypeConstraintNode[] constraintCheckNodes) {
      super(sourceSection);
      this.childNode = childNode;
      this.constraintCheckNodes = constraintCheckNodes;
    }

    @Override
    public TypeNode execute(VirtualFrame frame) {
      CompilerDirectives.transferToInterpreter();

      return new ConstrainedTypeNode(sourceSection, childNode.execute(frame), constraintCheckNodes);
    }
  }

  public static final class Unknown extends UnresolvedTypeNode {
    public Unknown(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    public TypeNode execute(VirtualFrame frame) {
      CompilerDirectives.transferToInterpreter();

      return new UnknownTypeNode(sourceSection);
    }
  }

  public static final class Nothing extends UnresolvedTypeNode {
    public Nothing(SourceSection sourceSection) {
      super(sourceSection);
    }

    @Override
    public TypeNode execute(VirtualFrame frame) {
      CompilerDirectives.transferToInterpreter();

      return new NothingTypeNode(sourceSection);
    }
  }

  /** The `module` type. */
  public static final class Module extends UnresolvedTypeNode {
    @Child private ExpressionNode getModuleNode;

    public Module(SourceSection sourceSection) {
      super(sourceSection);
      getModuleNode = new GetModuleNode(sourceSection);
    }

    @Override
    public TypeNode execute(VirtualFrame frame) {
      CompilerDirectives.transferToInterpreter();

      var module = (VmTyped) getModuleNode.executeGeneric(frame);
      var moduleClass = module.getVmClass();
      return moduleClass.isClosed()
          ? new FinalModuleTypeNode(sourceSection, moduleClass)
          : new NonFinalModuleTypeNode(sourceSection, moduleClass);
    }
  }

  public static final class StringLiteral extends UnresolvedTypeNode {
    private final String literal;

    public StringLiteral(SourceSection sourceSection, String literal) {
      super(sourceSection);
      this.literal = literal;
    }

    @Override
    public TypeNode execute(VirtualFrame frame) {
      CompilerDirectives.transferToInterpreter();

      return new StringLiteralTypeNode(sourceSection, literal);
    }
  }

  public static final class Declared extends UnresolvedTypeNode {
    @Child private ExpressionNode resolveTypeNode;

    public Declared(SourceSection sourceSection, ExpressionNode resolveTypeNode) {
      super(sourceSection);
      this.resolveTypeNode = resolveTypeNode;
    }

    @Override
    public TypeNode execute(VirtualFrame frame) {
      CompilerDirectives.transferToInterpreter();

      var type = resolveTypeNode.executeGeneric(frame);

      if (type instanceof VmClass clazz) {
        // Note: FinalClassTypeNode and NonFinalClassTypeNode assume that
        // String/Boolean/Int/Float and their supertypes are handled separately.

        if (clazz.getModuleName().equals("pkl.base")) {
          switch (clazz.getSimpleName()) {
            case "String":
              return new StringTypeNode(sourceSection);
            case "Boolean":
              return new BooleanTypeNode(sourceSection);
            case "Int":
              return new IntTypeNode(sourceSection);
            case "Float":
              return new FloatTypeNode(sourceSection);
            case "Number":
              return new NumberTypeNode(sourceSection);
            case "Any":
              return new AnyTypeNode(sourceSection);
            case "Typed":
              return new TypedTypeNode(sourceSection);
            case "Dynamic":
              return new DynamicTypeNode(sourceSection);
          }
        }

        return TypeNode.forClass(sourceSection, clazz);
      }

      if (type instanceof VmTypeAlias alias) {
        if (alias.getModuleName().equals("pkl.base")) {
          switch (alias.getSimpleName()) {
            case "NonNull":
              return new NonNullTypeAliasTypeNode();
            case "Int8":
              return new Int8TypeAliasTypeNode();
            case "UInt8":
              return new UIntTypeAliasTypeNode(alias, 0x00000000000000FFL);
            case "Int16":
              return new Int16TypeAliasTypeNode();
            case "UInt16":
              return new UIntTypeAliasTypeNode(alias, 0x000000000000FFFFL);
            case "Int32":
              return new Int32TypeAliasTypeNode();
            case "UInt32":
              return new UIntTypeAliasTypeNode(alias, 0x00000000FFFFFFFFL);
            case "UInt":
              return new UIntTypeAliasTypeNode(alias, 0x7FFFFFFFFFFFFFFFL);
          }
        }

        return new TypeAliasTypeNode(sourceSection, alias, new TypeNode[0]);
      }

      var module = (VmTyped) type;
      assert module.isModuleObject();
      var clazz = module.getVmClass();
      if (!module.isPrototype()) {
        throw exceptionBuilder().evalError("notAModuleType", clazz.getModuleName()).build();
      }
      return TypeNode.forClass(sourceSection, module.getVmClass());
    }
  }

  public static final class Parameterized extends UnresolvedTypeNode {
    @Child private ExpressionNode resolveTypeNode;
    @Children private final UnresolvedTypeNode[] typeArgumentNodes;

    public Parameterized(
        SourceSection sourceSection,
        ExpressionNode resolveTypeNode,
        UnresolvedTypeNode[] typeArgumentNodes) {
      super(sourceSection);
      this.resolveTypeNode = resolveTypeNode;
      this.typeArgumentNodes = typeArgumentNodes;
    }

    @Override
    public TypeNode execute(VirtualFrame frame) {
      CompilerDirectives.transferToInterpreter();

      var baseType = resolveTypeNode.executeGeneric(frame);

      if (baseType instanceof VmClass clazz) {
        checkNumberOfTypeArguments(clazz);

        if (clazz.isCollectionClass()) {
          return new CollectionTypeNode(sourceSection, typeArgumentNodes[0].execute(frame));
        }

        if (clazz.isListClass()) {
          return new ListTypeNode(sourceSection, typeArgumentNodes[0].execute(frame));
        }

        if (clazz.isSetClass()) {
          return SetTypeNodeGen.create(sourceSection, typeArgumentNodes[0].execute(frame));
        }

        if (clazz.isMapClass()) {
          return new MapTypeNode(
              sourceSection,
              typeArgumentNodes[0].execute(frame),
              typeArgumentNodes[1].execute(frame));
        }

        if (clazz.isListingClass()) {
          return new ListingTypeNode(sourceSection, typeArgumentNodes[0].execute(frame));
        }

        if (clazz.isMappingClass()) {
          return new MappingTypeNode(
              sourceSection,
              typeArgumentNodes[0].execute(frame),
              typeArgumentNodes[1].execute(frame));
        }

        if (clazz.isPairClass()) {
          return new PairTypeNode(
              sourceSection,
              typeArgumentNodes[0].execute(frame),
              typeArgumentNodes[1].execute(frame));
        }

        if (clazz.isFunctionClass()) {
          return FunctionClassTypeNodeGen.create(
              sourceSection, typeArgumentNodes[0].execute(frame));
        }

        if (clazz.isFunctionNClass()) {
          var argLength = typeArgumentNodes.length;
          var resolvedTypeArgumentNodes = new TypeNode[argLength];
          for (var i = 0; i < argLength; i++) {
            resolvedTypeArgumentNodes[i] = typeArgumentNodes[i].execute(frame);
          }
          return FunctionNClassTypeNodeGen.create(sourceSection, resolvedTypeArgumentNodes);
        }

        // erase `x: Class<Foo>` to `x: Class` for now (cf. function types)
        if (clazz.isClassClass()) {
          return new FinalClassTypeNode(sourceSection, clazz);
        }

        if (clazz.isVarArgsClass()) {
          return new VarArgsTypeNode(sourceSection, typeArgumentNodes[0].execute(frame));
        }

        throw exceptionBuilder()
            .evalError("notAParameterizableClass", clazz.getDisplayName())
            .withSourceSection(typeArgumentNodes[0].sourceSection)
            .build();
      }

      if (baseType instanceof VmTypeAlias typeAlias) {
        var argLength = typeArgumentNodes.length;
        var resolvedTypeArgumentNodes = new TypeNode[argLength];
        for (var i = 0; i < argLength; i++) {
          resolvedTypeArgumentNodes[i] = typeArgumentNodes[i].execute(frame);
        }
        return new TypeAliasTypeNode(sourceSection, typeAlias, resolvedTypeArgumentNodes);
      }

      var module = (VmTyped) baseType;
      throw exceptionBuilder()
          .evalError("notAParameterizableClass", module.getModuleInfo().getModuleName())
          .withSourceSection(typeArgumentNodes[0].sourceSection)
          .build();
    }

    private void checkNumberOfTypeArguments(VmClass clazz) {
      var expectedCount = clazz.getTypeParameterCount();
      var actualCount = typeArgumentNodes.length;
      if (expectedCount != actualCount) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder()
            .evalError("wrongTypeArgumentCount", expectedCount, actualCount)
            .build();
      }
    }
  }

  public static final class Nullable extends UnresolvedTypeNode {
    @Child private UnresolvedTypeNode elementTypeNode;

    public Nullable(SourceSection sourceSection, UnresolvedTypeNode elementTypeNode) {
      super(sourceSection);
      this.elementTypeNode = elementTypeNode;
    }

    @Override
    public TypeNode execute(VirtualFrame frame) {
      CompilerDirectives.transferToInterpreter();

      return new NullableTypeNode(sourceSection, elementTypeNode.execute(frame));
    }
  }

  public static final class Union extends UnresolvedTypeNode {
    @Children private final UnresolvedTypeNode[] unresolvedElementTypeNodes;
    private final int defaultIndex;

    public Union(
        SourceSection sourceSection, int defaultIndex, UnresolvedTypeNode[] elementTypeNodes) {
      super(sourceSection);
      this.unresolvedElementTypeNodes = elementTypeNodes;
      this.defaultIndex = defaultIndex;
    }

    @Override
    @ExplodeLoop
    public TypeNode execute(VirtualFrame frame) {
      CompilerDirectives.transferToInterpreter();

      var elementTypeNodes = new TypeNode[unresolvedElementTypeNodes.length];
      var skipElementTypeChecks = true;

      for (var i = 0; i < elementTypeNodes.length; i++) {
        var elementTypeNode = unresolvedElementTypeNodes[i].execute(frame);
        elementTypeNodes[i] = elementTypeNode;
        skipElementTypeChecks &= elementTypeNode.isNoopTypeCheck();
      }

      return new UnionTypeNode(
          sourceSection, defaultIndex, elementTypeNodes, skipElementTypeChecks);
    }
  }

  public static final class UnionOfStringLiterals extends UnresolvedTypeNode {
    private final Set<String> stringLiterals;
    private final int defaultIndex;

    public UnionOfStringLiterals(
        SourceSection sourceSection, int defaultIndex, Set<String> stringLiterals) {
      super(sourceSection);
      this.stringLiterals = stringLiterals;
      this.defaultIndex = defaultIndex;
    }

    @Override
    public TypeNode execute(VirtualFrame frame) {
      CompilerDirectives.transferToInterpreter();

      return new UnionOfStringLiteralsTypeNode(sourceSection, defaultIndex, stringLiterals);
    }
  }

  public static final class Function extends UnresolvedTypeNode {
    @Children private final UnresolvedTypeNode[] parameterTypeNodes;
    @Child private UnresolvedTypeNode returnTypeNode;

    public Function(
        SourceSection sourceSection,
        UnresolvedTypeNode[] parameterTypeNodes,
        UnresolvedTypeNode returnTypeNode) {
      super(sourceSection);
      this.parameterTypeNodes = parameterTypeNodes;
      this.returnTypeNode = returnTypeNode;
    }

    @Override
    @ExplodeLoop
    public TypeNode execute(VirtualFrame frame) {
      CompilerDirectives.transferToInterpreter();

      var parameterTypeNodes = new TypeNode[this.parameterTypeNodes.length];
      for (var i = 0; i < parameterTypeNodes.length; i++) {
        parameterTypeNodes[i] = this.parameterTypeNodes[i].execute(frame);
      }

      return FunctionTypeNodeGen.create(
          sourceSection, parameterTypeNodes, returnTypeNode.execute(frame));
    }
  }

  public static final class TypeVariable extends UnresolvedTypeNode {
    private final TypeParameter typeParameter;

    public TypeVariable(SourceSection sourceSection, TypeParameter typeParameter) {
      super(sourceSection);
      this.typeParameter = typeParameter;
    }

    @Override
    public TypeNode execute(VirtualFrame frame) {
      CompilerDirectives.transferToInterpreter();

      return new TypeVariableNode(sourceSection, typeParameter);
    }
  }
}
