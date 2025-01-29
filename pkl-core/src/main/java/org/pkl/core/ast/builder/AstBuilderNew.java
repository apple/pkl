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
package org.pkl.core.ast.builder;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.PClassInfo;
import org.pkl.core.PklBugException;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.TypeParameter;
import org.pkl.core.TypeParameter.Variance;
import org.pkl.core.ast.ConstantNode;
import org.pkl.core.ast.ConstantValueNode;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.MemberLookupMode;
import org.pkl.core.ast.MemberNode;
import org.pkl.core.ast.PklRootNode;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.builder.SymbolTable.AnnotationScope;
import org.pkl.core.ast.builder.SymbolTable.ClassScope;
import org.pkl.core.ast.builder.SymbolTable.EntryScope;
import org.pkl.core.ast.expression.binary.AdditionNodeGen;
import org.pkl.core.ast.expression.binary.DivisionNodeGen;
import org.pkl.core.ast.expression.binary.EqualNodeGen;
import org.pkl.core.ast.expression.binary.ExponentiationNodeGen;
import org.pkl.core.ast.expression.binary.GreaterThanNodeGen;
import org.pkl.core.ast.expression.binary.GreaterThanOrEqualNodeGen;
import org.pkl.core.ast.expression.binary.LessThanNodeGen;
import org.pkl.core.ast.expression.binary.LessThanOrEqualNodeGen;
import org.pkl.core.ast.expression.binary.LetExprNode;
import org.pkl.core.ast.expression.binary.LogicalAndNodeGen;
import org.pkl.core.ast.expression.binary.LogicalOrNodeGen;
import org.pkl.core.ast.expression.binary.MultiplicationNodeGen;
import org.pkl.core.ast.expression.binary.NotEqualNodeGen;
import org.pkl.core.ast.expression.binary.NullCoalescingNodeGen;
import org.pkl.core.ast.expression.binary.PipeNodeGen;
import org.pkl.core.ast.expression.binary.RemainderNodeGen;
import org.pkl.core.ast.expression.binary.SubscriptNodeGen;
import org.pkl.core.ast.expression.binary.SubtractionNodeGen;
import org.pkl.core.ast.expression.binary.TruncatingDivisionNodeGen;
import org.pkl.core.ast.expression.generator.GeneratorElementNodeGen;
import org.pkl.core.ast.expression.generator.GeneratorEntryNodeGen;
import org.pkl.core.ast.expression.generator.GeneratorForNodeGen;
import org.pkl.core.ast.expression.generator.GeneratorMemberNode;
import org.pkl.core.ast.expression.generator.GeneratorObjectLiteralNode;
import org.pkl.core.ast.expression.generator.GeneratorObjectLiteralNodeGen;
import org.pkl.core.ast.expression.generator.GeneratorPredicateMemberNodeGen;
import org.pkl.core.ast.expression.generator.GeneratorPropertyNode;
import org.pkl.core.ast.expression.generator.GeneratorPropertyNodeGen;
import org.pkl.core.ast.expression.generator.GeneratorSpreadNodeGen;
import org.pkl.core.ast.expression.generator.GeneratorWhenNode;
import org.pkl.core.ast.expression.generator.WriteForVariablesNode;
import org.pkl.core.ast.expression.literal.AmendModuleNodeGen;
import org.pkl.core.ast.expression.literal.CheckIsAnnotationClassNode;
import org.pkl.core.ast.expression.literal.ConstantEntriesLiteralNodeGen;
import org.pkl.core.ast.expression.literal.ElementsEntriesLiteralNodeGen;
import org.pkl.core.ast.expression.literal.ElementsLiteralNodeGen;
import org.pkl.core.ast.expression.literal.EmptyObjectLiteralNodeGen;
import org.pkl.core.ast.expression.literal.EntriesLiteralNodeGen;
import org.pkl.core.ast.expression.literal.FalseLiteralNode;
import org.pkl.core.ast.expression.literal.FloatLiteralNode;
import org.pkl.core.ast.expression.literal.FunctionLiteralNode;
import org.pkl.core.ast.expression.literal.IntLiteralNode;
import org.pkl.core.ast.expression.literal.InterpolatedStringLiteralNode;
import org.pkl.core.ast.expression.literal.ListLiteralNode;
import org.pkl.core.ast.expression.literal.MapLiteralNode;
import org.pkl.core.ast.expression.literal.PropertiesLiteralNodeGen;
import org.pkl.core.ast.expression.literal.SetLiteralNode;
import org.pkl.core.ast.expression.literal.TrueLiteralNode;
import org.pkl.core.ast.expression.member.InferParentWithinMethodNode;
import org.pkl.core.ast.expression.member.InferParentWithinObjectMethodNode;
import org.pkl.core.ast.expression.member.InferParentWithinPropertyNodeGen;
import org.pkl.core.ast.expression.member.InvokeMethodVirtualNodeGen;
import org.pkl.core.ast.expression.member.InvokeSuperMethodNodeGen;
import org.pkl.core.ast.expression.member.ReadPropertyNodeGen;
import org.pkl.core.ast.expression.member.ReadSuperEntryNode;
import org.pkl.core.ast.expression.member.ReadSuperPropertyNode;
import org.pkl.core.ast.expression.member.ResolveMethodNode;
import org.pkl.core.ast.expression.primary.GetEnclosingOwnerNode;
import org.pkl.core.ast.expression.primary.GetEnclosingReceiverNode;
import org.pkl.core.ast.expression.primary.GetMemberKeyNode;
import org.pkl.core.ast.expression.primary.GetModuleNode;
import org.pkl.core.ast.expression.primary.GetOwnerNode;
import org.pkl.core.ast.expression.primary.GetReceiverNode;
import org.pkl.core.ast.expression.primary.OuterNode;
import org.pkl.core.ast.expression.primary.ResolveVariableNode;
import org.pkl.core.ast.expression.primary.ThisNode;
import org.pkl.core.ast.expression.ternary.IfElseNode;
import org.pkl.core.ast.expression.unary.AbstractImportNode;
import org.pkl.core.ast.expression.unary.ImportGlobNode;
import org.pkl.core.ast.expression.unary.ImportNode;
import org.pkl.core.ast.expression.unary.LogicalNotNodeGen;
import org.pkl.core.ast.expression.unary.NonNullNode;
import org.pkl.core.ast.expression.unary.NullPropagatingOperationNode;
import org.pkl.core.ast.expression.unary.PropagateNullReceiverNodeGen;
import org.pkl.core.ast.expression.unary.ReadGlobNode;
import org.pkl.core.ast.expression.unary.ReadGlobNodeGen;
import org.pkl.core.ast.expression.unary.ReadNode;
import org.pkl.core.ast.expression.unary.ReadNodeGen;
import org.pkl.core.ast.expression.unary.ReadOrNullNode;
import org.pkl.core.ast.expression.unary.ReadOrNullNodeGen;
import org.pkl.core.ast.expression.unary.ThrowNodeGen;
import org.pkl.core.ast.expression.unary.TraceNode;
import org.pkl.core.ast.expression.unary.UnaryMinusNodeGen;
import org.pkl.core.ast.internal.GetBaseModuleClassNode;
import org.pkl.core.ast.internal.GetClassNodeGen;
import org.pkl.core.ast.internal.ToStringNodeGen;
import org.pkl.core.ast.lambda.ApplyVmFunction1NodeGen;
import org.pkl.core.ast.member.ClassNode;
import org.pkl.core.ast.member.ElementOrEntryNodeGen;
import org.pkl.core.ast.member.Lambda;
import org.pkl.core.ast.member.ModuleNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.member.ObjectMethodNode;
import org.pkl.core.ast.member.TypeAliasNode;
import org.pkl.core.ast.member.UnresolvedFunctionNode;
import org.pkl.core.ast.member.UnresolvedMethodNode;
import org.pkl.core.ast.member.UnresolvedPropertyNode;
import org.pkl.core.ast.member.UntypedObjectMemberNode;
import org.pkl.core.ast.type.GetParentForTypeNode;
import org.pkl.core.ast.type.ResolveDeclaredTypeNode;
import org.pkl.core.ast.type.ResolveQualifiedDeclaredTypeNode;
import org.pkl.core.ast.type.ResolveSimpleDeclaredTypeNode;
import org.pkl.core.ast.type.TypeCastNode;
import org.pkl.core.ast.type.TypeConstraintNode;
import org.pkl.core.ast.type.TypeConstraintNodeGen;
import org.pkl.core.ast.type.TypeTestNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.ast.type.UnresolvedTypeNode.Constrained;
import org.pkl.core.externalreader.ExternalReaderProcessException;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.module.ModuleKeys;
import org.pkl.core.module.ResolvedModuleKey;
import org.pkl.core.newparser.ParserVisitor;
import org.pkl.core.newparser.Span;
import org.pkl.core.newparser.cst.Annotation;
import org.pkl.core.newparser.cst.ArgumentList;
import org.pkl.core.newparser.cst.ClassMethod;
import org.pkl.core.newparser.cst.ClassPropertyEntry;
import org.pkl.core.newparser.cst.Clazz;
import org.pkl.core.newparser.cst.Expr;
import org.pkl.core.newparser.cst.Expr.Amends;
import org.pkl.core.newparser.cst.Expr.BinaryOp;
import org.pkl.core.newparser.cst.Expr.BoolLiteral;
import org.pkl.core.newparser.cst.Expr.FloatLiteral;
import org.pkl.core.newparser.cst.Expr.FunctionLiteral;
import org.pkl.core.newparser.cst.Expr.If;
import org.pkl.core.newparser.cst.Expr.ImportExpr;
import org.pkl.core.newparser.cst.Expr.IntLiteral;
import org.pkl.core.newparser.cst.Expr.InterpolatedMultiString;
import org.pkl.core.newparser.cst.Expr.InterpolatedString;
import org.pkl.core.newparser.cst.Expr.Let;
import org.pkl.core.newparser.cst.Expr.LogicalNot;
import org.pkl.core.newparser.cst.Expr.New;
import org.pkl.core.newparser.cst.Expr.NonNull;
import org.pkl.core.newparser.cst.Expr.NullLiteral;
import org.pkl.core.newparser.cst.Expr.Outer;
import org.pkl.core.newparser.cst.Expr.Parenthesized;
import org.pkl.core.newparser.cst.Expr.QualifiedAccess;
import org.pkl.core.newparser.cst.Expr.Read;
import org.pkl.core.newparser.cst.Expr.ReadGlob;
import org.pkl.core.newparser.cst.Expr.ReadNull;
import org.pkl.core.newparser.cst.Expr.StringConstant;
import org.pkl.core.newparser.cst.Expr.Subscript;
import org.pkl.core.newparser.cst.Expr.SuperAccess;
import org.pkl.core.newparser.cst.Expr.SuperSubscript;
import org.pkl.core.newparser.cst.Expr.This;
import org.pkl.core.newparser.cst.Expr.Throw;
import org.pkl.core.newparser.cst.Expr.Trace;
import org.pkl.core.newparser.cst.Expr.TypeCast;
import org.pkl.core.newparser.cst.Expr.TypeCheck;
import org.pkl.core.newparser.cst.Expr.UnaryMinus;
import org.pkl.core.newparser.cst.Expr.UnqualifiedAccess;
import org.pkl.core.newparser.cst.ExtendsOrAmendsDecl;
import org.pkl.core.newparser.cst.Ident;
import org.pkl.core.newparser.cst.Import;
import org.pkl.core.newparser.cst.Modifier;
import org.pkl.core.newparser.cst.Modifier.ModifierValue;
import org.pkl.core.newparser.cst.Module;
import org.pkl.core.newparser.cst.ModuleDecl;
import org.pkl.core.newparser.cst.Node;
import org.pkl.core.newparser.cst.ObjectBody;
import org.pkl.core.newparser.cst.ObjectMemberNode;
import org.pkl.core.newparser.cst.ObjectMemberNode.ForGenerator;
import org.pkl.core.newparser.cst.ObjectMemberNode.MemberPredicate;
import org.pkl.core.newparser.cst.ObjectMemberNode.MemberPredicateBody;
import org.pkl.core.newparser.cst.ObjectMemberNode.ObjectBodyProperty;
import org.pkl.core.newparser.cst.ObjectMemberNode.ObjectElement;
import org.pkl.core.newparser.cst.ObjectMemberNode.ObjectEntry;
import org.pkl.core.newparser.cst.ObjectMemberNode.ObjectEntryBody;
import org.pkl.core.newparser.cst.ObjectMemberNode.ObjectMethod;
import org.pkl.core.newparser.cst.ObjectMemberNode.ObjectProperty;
import org.pkl.core.newparser.cst.ObjectMemberNode.ObjectSpread;
import org.pkl.core.newparser.cst.ObjectMemberNode.WhenGenerator;
import org.pkl.core.newparser.cst.Parameter;
import org.pkl.core.newparser.cst.Parameter.TypedIdent;
import org.pkl.core.newparser.cst.ParameterList;
import org.pkl.core.newparser.cst.QualifiedIdent;
import org.pkl.core.newparser.cst.Type;
import org.pkl.core.newparser.cst.Type.ConstrainedType;
import org.pkl.core.newparser.cst.Type.DeclaredType;
import org.pkl.core.newparser.cst.Type.DefaultUnionType;
import org.pkl.core.newparser.cst.Type.FunctionType;
import org.pkl.core.newparser.cst.Type.ModuleType;
import org.pkl.core.newparser.cst.Type.NothingType;
import org.pkl.core.newparser.cst.Type.NullableType;
import org.pkl.core.newparser.cst.Type.ParenthesizedType;
import org.pkl.core.newparser.cst.Type.StringConstantType;
import org.pkl.core.newparser.cst.Type.UnionType;
import org.pkl.core.newparser.cst.Type.UnknownType;
import org.pkl.core.newparser.cst.TypeAlias;
import org.pkl.core.newparser.cst.TypeAnnotation;
import org.pkl.core.newparser.cst.TypeParameterList;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.parser.antlr.PklParser.ModuleContext;
import org.pkl.core.parser.antlr.PklParser.QualifiedAccessExprContext;
import org.pkl.core.runtime.BaseModule;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.ModuleInfo;
import org.pkl.core.runtime.ModuleResolver;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmDataSize;
import org.pkl.core.runtime.VmDuration;
import org.pkl.core.runtime.VmException;
import org.pkl.core.runtime.VmException.ProgramValue;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmMap;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmSet;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.LanguageAwareNode;
import org.pkl.core.stdlib.registry.ExternalMemberRegistry;
import org.pkl.core.stdlib.registry.MemberRegistryFactory;
import org.pkl.core.util.CollectionUtils;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.Pair;

public class AstBuilderNew implements ParserVisitor<Object> {
  protected final Source source;
  private final VmLanguage language;
  private final ModuleInfo moduleInfo;

  private final ModuleKey moduleKey;
  private final ModuleResolver moduleResolver;
  private final boolean isBaseModule;
  private final boolean isStdLibModule;
  private final ExternalMemberRegistry externalMemberRegistry;
  private final SymbolTable symbolTable;
  private final boolean isMethodReturnTypeChecked;

  public AstBuilderNew(
      Source source, VmLanguage language, ModuleInfo moduleInfo, ModuleResolver moduleResolver) {
    this.source = source;
    this.language = language;
    this.moduleInfo = moduleInfo;

    moduleKey = moduleInfo.getModuleKey();
    this.moduleResolver = moduleResolver;
    isBaseModule = ModuleKeys.isBaseModule(moduleKey);
    isStdLibModule = ModuleKeys.isStdLibModule(moduleKey);
    externalMemberRegistry = MemberRegistryFactory.get(moduleKey);
    symbolTable = new SymbolTable(moduleInfo);
    isMethodReturnTypeChecked = !isStdLibModule || IoUtils.isTestMode();
  }

  public static AstBuilderNew create(
      Source source,
      VmLanguage language,
      Module ctx,
      ModuleKey moduleKey,
      ResolvedModuleKey resolvedModuleKey,
      ModuleResolver moduleResolver) {
    var moduleDecl = ctx.getDecl();
    var sourceSection = createSourceSection(source, ctx);
    var headerSection =
        moduleDecl != null
            ? createSourceSection(source, moduleDecl.headerSpan())
            :
            // no explicit module declaration; designate start of file as header section
            source.createSection(0, 0);
    var docComment =
        moduleDecl != null ? createSourceSection(source, moduleDecl.getDocComment()) : null;

    ModuleInfo moduleInfo;
    if (moduleDecl == null) {
      var moduleName = IoUtils.inferModuleName(moduleKey);
      moduleInfo =
          new ModuleInfo(
              sourceSection, headerSection, null, moduleName, moduleKey, resolvedModuleKey, false);
    } else {
      var declaredModuleName = moduleDecl.getName();
      var moduleName =
          declaredModuleName != null
              ? declaredModuleName.text()
              : IoUtils.inferModuleName(moduleKey);
      var clause = moduleDecl.getExtendsOrAmendsDecl();
      var isAmend = clause != null && clause.getType() == ExtendsOrAmendsDecl.Type.AMENDS;
      moduleInfo =
          new ModuleInfo(
              sourceSection,
              headerSection,
              docComment,
              moduleName,
              moduleKey,
              resolvedModuleKey,
              isAmend);
    }

    return new AstBuilderNew(source, language, moduleInfo, moduleResolver);
  }

  @Override
  public UnresolvedTypeNode visitUnknownType(UnknownType type) {
    return new UnresolvedTypeNode.Unknown(createSourceSection(type));
  }

  @Override
  public UnresolvedTypeNode visitNothingType(NothingType type) {
    return new UnresolvedTypeNode.Nothing(createSourceSection(type));
  }

  @Override
  public UnresolvedTypeNode visitModuleType(ModuleType type) {
    return new UnresolvedTypeNode.Module(createSourceSection(type));
  }

  @Override
  public UnresolvedTypeNode visitStringConstantType(StringConstantType type) {
    return new UnresolvedTypeNode.StringLiteral(createSourceSection(type), type.getStr());
  }

  @Override
  public UnresolvedTypeNode visitDeclaredType(DeclaredType type) {
    var ident = type.getName();
    var args = type.getArgs();

    if (args.isEmpty()) {
      if (ident.getIdents().size() == 1) {
        var text = ident.getIdents().get(0).getValue();
        var typeParameter = symbolTable.findTypeParameter(text);
        if (typeParameter != null) {
          return new UnresolvedTypeNode.TypeVariable(createSourceSection(type), typeParameter);
        }
      }

      return new UnresolvedTypeNode.Declared(createSourceSection(type), doVisitTypeName(ident));
    }

    var argTypes = new UnresolvedTypeNode[args.size()];
    for (var i = 0; i < args.size(); i++) {
      argTypes[i] = visitType(args.get(i));
    }

    return new UnresolvedTypeNode.Parameterized(
        createSourceSection(type), language, doVisitTypeName(ident), argTypes);
  }

  @Override
  public UnresolvedTypeNode visitParenthesizedType(ParenthesizedType type) {
    return visitType(type.getType());
  }

  @Override
  public UnresolvedTypeNode visitNullableType(NullableType type) {
    return new UnresolvedTypeNode.Nullable(createSourceSection(type), visitType(type.getType()));
  }

  @Override
  public UnresolvedTypeNode visitConstrainedType(ConstrainedType type) {
    var childNode = visitType(type.getType());

    var exprs = type.getExpr();
    var constraints = new TypeConstraintNode[exprs.size()];
    for (int i = 0; i < constraints.length; i++) {
      var expr = visitExpr(exprs.get(i));
      constraints[i] = TypeConstraintNodeGen.create(expr.getSourceSection(), expr);
    }

    return symbolTable.enterCustomThisScope(
        scope -> new Constrained(createSourceSection(type), childNode, constraints));
  }

  @Override
  public UnresolvedTypeNode visitDefaultUnionType(DefaultUnionType type) {
    throw exceptionBuilder()
        .evalError("notAUnion")
        .withSourceSection(createSourceSection(type))
        .build();
  }

  @Override
  public UnresolvedTypeNode visitUnionType(UnionType type) {
    var elementTypes = new ArrayList<Type>();

    var result = flattenUnionType(type, elementTypes);
    boolean isUnionOfStringLiterals = result.isUnionOfStringLiterals;
    int defaultIndex = result.defaultIndex;

    if (isUnionOfStringLiterals) {
      return new UnresolvedTypeNode.UnionOfStringLiterals(
          createSourceSection(type),
          defaultIndex,
          elementTypes.stream()
              .map(it -> ((StringConstantType) it).getStr())
              .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    var elements = new UnresolvedTypeNode[elementTypes.size()];
    for (int i = 0; i < elementTypes.size(); i++) {
      elements[i] = visitType(elementTypes.get(i));
    }

    return new UnresolvedTypeNode.Union(createSourceSection(type), defaultIndex, elements);
  }

  private FlatUnionResult flattenUnionType(UnionType type, List<Type> collector) {
    boolean isUnionOfStringLiterals = true;
    int index = 0;
    int defaultIndex = -1;
    var list = new ArrayDeque<Type>();
    list.addLast(type.getLeft());
    list.addLast(type.getRight());

    while (!list.isEmpty()) {
      var current = list.removeFirst();
      if (current instanceof UnionType unionType) {
        list.addFirst(unionType.getRight());
        list.addFirst(unionType.getLeft());
        continue;
      }
      if (current instanceof DefaultUnionType defaultUnionType) {
        if (defaultIndex == -1) {
          defaultIndex = index;
        } else {
          throw exceptionBuilder()
              .evalError("multipleUnionDefaults")
              .withSourceSection(createSourceSection(type))
              .build();
        }
        isUnionOfStringLiterals =
            isUnionOfStringLiterals && defaultUnionType.getType() instanceof StringConstantType;
        collector.add(defaultUnionType.getType());
      } else {
        isUnionOfStringLiterals = isUnionOfStringLiterals && current instanceof StringConstantType;
        collector.add(current);
      }
      index++;
    }
    return new FlatUnionResult(isUnionOfStringLiterals, defaultIndex);
  }

  private record FlatUnionResult(boolean isUnionOfStringLiterals, int defaultIndex) {}

  @Override
  public UnresolvedTypeNode visitFunctionType(FunctionType type) {
    var pars = new UnresolvedTypeNode[type.getArgs().size()];
    for (int i = 0; i < pars.length; i++) {
      pars[i] = visitType(type.getArgs().get(i));
    }

    return new UnresolvedTypeNode.Function(
        createSourceSection(type), pars, visitType(type.getRet()));
  }

  @Override
  public ExpressionNode visitThisExpr(This expr) {
    if (!(expr.parent() instanceof QualifiedAccessExprContext)) {
      var currentScope = symbolTable.getCurrentScope();
      var needsConst =
          currentScope.getConstLevel() == ConstLevel.ALL
              && currentScope.getConstDepth() == -1
              && !currentScope.isCustomThisScope();
      if (needsConst) {
        throw exceptionBuilder()
            .withSourceSection(createSourceSection(expr))
            .evalError("thisIsNotConst")
            .build();
      }
    }
    return VmUtils.createThisNode(
        createSourceSection(expr), symbolTable.getCurrentScope().isCustomThisScope());
  }

  // TODO: `outer.` should probably have semantics similar to `super.`,
  // rather than just performing a lookup in the immediately enclosing object
  // also, consider interpreting `x = ... x ...` as `x = ... outer.x ...`
  @Override
  public OuterNode visitOuterExpr(Outer expr) {
    if (!(expr.parent() instanceof QualifiedAccess)) {
      var constLevel = symbolTable.getCurrentScope().getConstLevel();
      var outerScope = getParentLexicalScope();
      if (outerScope != null && constLevel.bigger(outerScope.getConstLevel())) {
        throw exceptionBuilder()
            .evalError("outerIsNotConst")
            .withSourceSection(createSourceSection(expr))
            .build();
      }
    }
    return new OuterNode(createSourceSection(expr));
  }

  @Override
  public GetModuleNode visitModuleExpr(Expr.Module expr) {
    // cannot use unqualified `module` in a const context
    if (symbolTable.getCurrentScope().getConstLevel().isConst()
        && !(expr.parent() instanceof QualifiedAccess)) {
      var scope = symbolTable.getCurrentScope();
      while (scope != null
          && !(scope instanceof AnnotationScope)
          && !(scope instanceof ClassScope)) {
        scope = scope.getParent();
      }
      if (scope == null) {
        throw exceptionBuilder()
            .evalError("moduleIsNotConst", symbolTable.getCurrentScope().getName().toString())
            .withSourceSection(createSourceSection(expr))
            .build();
      }
      var messageKey =
          scope instanceof AnnotationScope ? "moduleIsNotConstAnnotation" : "moduleIsNotConstClass";
      throw exceptionBuilder()
          .evalError(messageKey)
          .withSourceSection(createSourceSection(expr))
          .build();
    }
    return new GetModuleNode(createSourceSection(expr));
  }

  @Override
  public ConstantValueNode visitNullLiteralExpr(NullLiteral expr) {
    return new ConstantValueNode(createSourceSection(expr), VmNull.withoutDefault());
  }

  @Override
  public ExpressionNode visitBoolLiteralExpr(BoolLiteral expr) {
    if (expr.isB()) {
      return new TrueLiteralNode(createSourceSection(expr));
    } else {
      return new FalseLiteralNode(createSourceSection(expr));
    }
  }

  @Override
  public IntLiteralNode visitIntLiteralExpr(IntLiteral expr) {
    var section = createSourceSection(expr);
    var text = expr.getNumber();

    var radix = 10;
    if (text.startsWith("0x") || text.startsWith("0b") || text.startsWith("0o")) {
      var type = text.charAt(1);
      if (type == 'x') {
        radix = 16;
      } else if (type == 'b') {
        radix = 2;
      } else {
        radix = 8;
      }

      text = text.substring(2);
    }

    // relies on grammar rule nesting depth, but a breakage won't go unnoticed by tests
    if (expr.parent() instanceof UnaryMinus) {
      // handle negation here to make parsing of base.MinInt work
      // also moves negation from runtime to parse time
      text = "-" + text;
    }

    try {
      var num = Long.parseLong(text, radix);
      return new IntLiteralNode(section, num);
    } catch (NumberFormatException e) {
      throw exceptionBuilder().evalError("intTooLarge", text).withSourceSection(section).build();
    }
  }

  @Override
  public FloatLiteralNode visitFloatLiteralExpr(FloatLiteral expr) {
    var section = createSourceSection(expr);
    var text = expr.getNumber();
    // relies on grammar rule nesting depth, but a breakage won't go unnoticed by tests
    if (expr.parent() instanceof UnaryMinus) {
      // handle negation here for consistency with visitIntegerLiteral
      // also moves negation from runtime to parse time
      text = "-" + text;
    }

    try {
      var num = Double.parseDouble(text);
      return new FloatLiteralNode(section, num);
    } catch (NumberFormatException e) {
      throw exceptionBuilder().evalError("floatTooLarge", text).withSourceSection(section).build();
    }
  }

  @Override
  public ExpressionNode visitThrowExpr(Throw expr) {
    return ThrowNodeGen.create(createSourceSection(expr), visitExpr(expr.getExpr()));
  }

  @Override
  public TraceNode visitTraceExpr(Trace expr) {
    return new TraceNode(createSourceSection(expr), visitExpr(expr.getExpr()));
  }

  @Override
  public AbstractImportNode visitImportExpr(ImportExpr expr) {
    var importUriCtx = expr.getImportStr();
    return doVisitImport(expr.isGlob(), expr, importUriCtx);
  }

  private AbstractImportNode doVisitImport(
      boolean isGlobImport, Node node, StringConstant importUriNode) {
    var section = createSourceSection(node);
    var importUri = importUriNode.getStr();
    if (isGlobImport && importUri.startsWith("...")) {
      throw exceptionBuilder().evalError("cannotGlobTripleDots").withSourceSection(section).build();
    }
    var resolvedUri = resolveImport(importUri, importUriNode);
    if (isGlobImport) {
      return new ImportGlobNode(section, moduleInfo.getResolvedModuleKey(), resolvedUri, importUri);
    }
    return new ImportNode(language, section, moduleInfo.getResolvedModuleKey(), resolvedUri);
  }

  @Override
  public ReadNode visitReadExpr(Read expr) {
    return ReadNodeGen.create(createSourceSection(expr), moduleKey, visitExpr(expr.getExpr()));
  }

  @Override
  public ReadOrNullNode visitReadNullExpr(ReadNull expr) {
    return ReadOrNullNodeGen.create(
        createSourceSection(expr), moduleKey, visitExpr(expr.getExpr()));
  }

  @Override
  public ReadGlobNode visitReadGlobExpr(ReadGlob expr) {
    return ReadGlobNodeGen.create(createSourceSection(expr), moduleKey, visitExpr(expr.getExpr()));
  }

  @Override
  public ExpressionNode visitUnqualifiedAccessExpr(UnqualifiedAccess expr) {
    var identifier = toIdentifier(expr.getIdent().getValue());
    var argList = expr.getArgumentList();

    if (argList == null) {
      return createResolveVariableNode(createSourceSection(expr), identifier);
    }

    // TODO: make sure that no user-defined List/Set/Map method is in scope
    // TODO: support qualified calls (e.g., `import "pkl:base"; x = base.List()/Set()/Map()`) for
    // correctness
    if (identifier == Identifier.LIST) {
      return doVisitListLiteral(expr, argList);
    }

    if (identifier == Identifier.SET) {
      return doVisitSetLiteral(expr, argList);
    }

    if (identifier == Identifier.MAP) {
      return doVisitMapLiteral(expr, argList);
    }

    var scope = symbolTable.getCurrentScope();

    return new ResolveMethodNode(
        createSourceSection(expr),
        identifier,
        visitArgumentList(argList),
        isBaseModule,
        scope.isCustomThisScope(),
        scope.getConstLevel(),
        scope.getConstDepth(),
        scope.isVisitingIterable());
  }

  @Override
  public ExpressionNode visitStringConstantExpr(StringConstant expr) {
    return new ConstantValueNode(createSourceSection(expr), expr.getStr());
  }

  @Override
  public ExpressionNode visitInterpolatedStringExpr(InterpolatedString expr) {
    var exprs = expr.getExprs();
    if (exprs.isEmpty()) {
      return new ConstantValueNode(createSourceSection(expr), "");
    }
    if (exprs.size() == 1 && exprs.get(0) instanceof StringConstant str) {
      return new ConstantValueNode(createSourceSection(expr), str.getStr());
    }

    var nodes = new ExpressionNode[exprs.size()];
    for (int i = 0; i < nodes.length; i++) {
      nodes[i] = visitExpr(exprs.get(i));
    }
    return new InterpolatedStringLiteralNode(createSourceSection(expr), nodes);
  }

  @Override
  public ExpressionNode visitInterpolatedMultiStringExpr(InterpolatedMultiString expr) {
    var exprs = expr.getExprs();
    if (exprs.isEmpty()) {
      throw exceptionBuilder()
          .evalError("stringContentMustBeginOnNewLine")
          .withSourceSection(createSourceSection(expr))
          .build();
    }
    var firstPart = exprs.get(0);
    if (firstPart instanceof StringConstant str && !str.getStr().startsWith("\n")
        || !(firstPart instanceof StringConstant)) {
      throw exceptionBuilder()
          .evalError("stringContentMustBeginOnNewLine")
          .withSourceSection(startOf(firstPart))
          .build();
    }

    var lastPart = exprs.get(exprs.size() - 1);
    var commonIndent = getCommonIndent(lastPart, expr.getEndDelimiterSpan());

    if (exprs.size() == 1) {
      return new ConstantValueNode(
          createSourceSection(expr),
          doVisitMultiLineStringConstant((StringConstant) firstPart, commonIndent, true, true));
    }

    var nodes = new ExpressionNode[exprs.size()];
    var lastIndex = nodes.length - 1;

    for (int i = 0; i <= lastIndex; i++) {
      nodes[i] = doVisitMultiLineStringPart(exprs.get(i), commonIndent, i == 0, i == lastIndex);
    }
    return new InterpolatedStringLiteralNode(createSourceSection(expr), nodes);
  }

  private ExpressionNode doVisitMultiLineStringPart(
      Expr expr, String commonIndent, boolean isStringStart, boolean isStringEnd) {
    if (expr instanceof StringConstant str) {
      return new ConstantValueNode(
          createSourceSection(expr),
          doVisitMultiLineStringConstant(str, commonIndent, isStringStart, isStringEnd));
    }
    return ToStringNodeGen.create(createSourceSection(expr), visitExpr(expr));
  }

  private String doVisitMultiLineStringConstant(
      StringConstant str, String commonIndent, boolean isStringStart, boolean isStringEnd) {
    var builder = new StringBuilder();
    var start = isStringStart ? 1 : 0;
    var lines = str.getStr().substring(start).lines().collect(Collectors.toList());
    if (isStringEnd) {
      lines.remove(lines.size() - 1);
    }
    for (String line : lines) {
      if (line.startsWith(commonIndent)) {
        builder.append(line, commonIndent.length(), line.length());
      } else {
        String actualIndent = getLeadingIndent(line);
        if (actualIndent.length() > commonIndent.length()) {
          actualIndent = actualIndent.substring(0, commonIndent.length());
        }
        throw exceptionBuilder()
            .evalError("stringIndentationMustMatchLastLine")
            .withSourceSection(shrinkLeft(createSourceSection(str), actualIndent.length()))
            .build();
      }
    }
    return builder.toString();
  }

  @Override
  public ExpressionNode visitNewExpr(New expr) {
    var type = expr.getType();
    return type != null
        ? doVisitNewExprWithExplicitParent(expr, type)
        : doVisitNewExprWithInferredParent(expr);
  }

  // `new Listing<Person> {}` is sugar for: `new Listing<Person> {} as Listing<Person>`
  private ExpressionNode doVisitNewExprWithExplicitParent(New newExpr, Type type) {
    var parentType = visitType(type);
    var expr =
        doVisitObjectBody(
            newExpr.getBody(),
            new GetParentForTypeNode(
                createSourceSection(newExpr),
                parentType,
                symbolTable.getCurrentScope().getQualifiedName()));
    if (type instanceof DeclaredType declaredType && !declaredType.getArgs().isEmpty()) {
      return new TypeCastNode(parentType.getSourceSection(), expr, parentType);
    }
    return expr;
  }

  private ExpressionNode doVisitNewExprWithInferredParent(New expr) {
    ExpressionNode inferredParentNode;

    Node child = expr;
    var parent = expr.parent();
    var scope = symbolTable.getCurrentScope();
    var levelsUp = 0;

    while (parent instanceof If
        || parent instanceof Trace
        || parent instanceof Let letExpr && letExpr.getExpr() == child) {

      if (parent instanceof Let) {
        assert scope != null;
        scope = scope.getParent();
        levelsUp += 1;
      }
      child = parent;
      parent = parent.parent();
    }

    assert scope != null;

    if (parent instanceof ClassPropertyEntry
        || parent instanceof ObjectProperty
        || parent instanceof ObjectBodyProperty) {
      inferredParentNode =
          InferParentWithinPropertyNodeGen.create(
              createSourceSection(expr.newSpan()),
              scope.getName(),
              levelsUp == 0 ? new GetOwnerNode() : new GetEnclosingOwnerNode(levelsUp));
    } else if (parent instanceof ObjectElement
        || parent instanceof ObjectEntry objectEntry && objectEntry.getValue() == child) {
      inferredParentNode =
          ApplyVmFunction1NodeGen.create(
              ReadPropertyNodeGen.create(
                  createSourceSection(expr.newSpan()),
                  Identifier.DEFAULT,
                  levelsUp == 0 ? new GetReceiverNode() : new GetEnclosingReceiverNode(levelsUp)),
              new GetMemberKeyNode());
    } else if (parent instanceof ClassMethod || parent instanceof ObjectMethod) {
      var isObjectMethod =
          parent instanceof ObjectMethod
              || parent.parent() instanceof ModuleContext && moduleInfo.isAmend();
      Identifier scopeName = scope.getName();
      inferredParentNode =
          isObjectMethod
              ? new InferParentWithinObjectMethodNode(
                  createSourceSection(expr.newSpan()),
                  language,
                  scopeName,
                  levelsUp == 0 ? new GetOwnerNode() : new GetEnclosingOwnerNode(levelsUp))
              : new InferParentWithinMethodNode(
                  createSourceSection(expr.newSpan()),
                  language,
                  scopeName,
                  levelsUp == 0 ? new GetOwnerNode() : new GetEnclosingOwnerNode(levelsUp));
    } else if (parent instanceof Let letExpr && letExpr.getBindingExpr() == child) {
      // TODO (unclear how to infer type now that let-expression is implemented as lambda
      // invocation)
      throw exceptionBuilder()
          .evalError("cannotInferParent")
          .withSourceSection(createSourceSection(expr.newSpan()))
          .build();
    } else {
      throw exceptionBuilder()
          .evalError("cannotInferParent")
          .withSourceSection(createSourceSection(expr.newSpan()))
          .build();
    }

    return doVisitObjectBody(expr.getBody(), inferredParentNode);
  }

  @Override
  public ExpressionNode visitAmendsExpr(Amends expr) {
    // parentExpr is always New, Amends or Parenthesized. The parser makes sure of it
    return doVisitObjectBody(expr.getBody(), visitExpr(expr.getExpr()));
  }

  @Override
  public ExpressionNode visitSuperAccessExpr(SuperAccess expr) {
    var sourceSection = createSourceSection(expr);
    var memberName = toIdentifier(expr.getIdent().getValue());
    var argCtx = expr.getArgumentList();
    var currentScope = symbolTable.getCurrentScope();
    var needsConst =
        currentScope.getConstLevel() == ConstLevel.ALL && currentScope.getConstDepth() == -1;

    if (argCtx != null) { // supermethod call
      if (!symbolTable.getCurrentScope().isClassMemberScope()) {
        throw exceptionBuilder()
            .evalError("cannotInvokeSupermethodFromHere")
            .withSourceSection(sourceSection)
            .build();
      }

      return InvokeSuperMethodNodeGen.create(
          sourceSection,
          memberName,
          symbolTable.getCurrentScope().isVisitingIterable(),
          visitArgumentList(argCtx),
          needsConst);
    }

    // superproperty call
    return new ReadSuperPropertyNode(createSourceSection(expr), memberName, needsConst);
  }

  @Override
  public ExpressionNode visitSuperSubscriptExpr(SuperSubscript expr) {
    return new ReadSuperEntryNode(createSourceSection(expr), visitExpr(expr.getArg()));
  }

  @Override
  public ExpressionNode visitQualifiedAccessExpr(QualifiedAccess expr) {
    if (expr.getArgumentList() != null) {
      return doVisitMethodAccessExpr(expr);
    }

    return doVisitPropertyInvocationExpr(expr);
  }

  @Override
  public ExpressionNode visitSubscriptExpr(Subscript expr) {
    return SubscriptNodeGen.create(
        createSourceSection(expr), visitExpr(expr.getExpr()), visitExpr(expr.getArg()));
  }

  @Override
  public ExpressionNode visitNonNullExpr(NonNull expr) {
    return new NonNullNode(createSourceSection(expr), visitExpr(expr.getExpr()));
  }

  @Override
  public ExpressionNode visitUnaryMinusExpr(UnaryMinus expr) {
    var childNode = expr.getExpr();
    var childExpr = visitExpr(childNode);
    if (childNode instanceof IntLiteral || childNode instanceof FloatLiteral) {
      // negation already handled (see visitIntLiteral/visitFloatLiteral)
      return childExpr;
    }
    return UnaryMinusNodeGen.create(createSourceSection(expr), childExpr);
  }

  @Override
  public ExpressionNode visitLogicalNotExpr(LogicalNot expr) {
    return LogicalNotNodeGen.create(createSourceSection(expr), visitExpr(expr.getExpr()));
  }

  @Override
  public ExpressionNode visitBinaryOpExpr(BinaryOp expr) {
    return switch (expr.getOp()) {
      case POW ->
          ExponentiationNodeGen.create(
              createSourceSection(expr), visitExpr(expr.getLeft()), visitExpr(expr.getRight()));
      case MULT ->
          MultiplicationNodeGen.create(
              createSourceSection(expr), visitExpr(expr.getLeft()), visitExpr(expr.getRight()));
      case DIV ->
          DivisionNodeGen.create(
              createSourceSection(expr), visitExpr(expr.getLeft()), visitExpr(expr.getRight()));
      case INT_DIV ->
          TruncatingDivisionNodeGen.create(
              createSourceSection(expr), visitExpr(expr.getLeft()), visitExpr(expr.getRight()));
      case MOD ->
          RemainderNodeGen.create(
              createSourceSection(expr), visitExpr(expr.getLeft()), visitExpr(expr.getRight()));
      case PLUS ->
          AdditionNodeGen.create(
              createSourceSection(expr), visitExpr(expr.getLeft()), visitExpr(expr.getRight()));
      case MINUS ->
          SubtractionNodeGen.create(
              createSourceSection(expr), visitExpr(expr.getLeft()), visitExpr(expr.getRight()));
      case LT ->
          LessThanNodeGen.create(
              createSourceSection(expr), visitExpr(expr.getLeft()), visitExpr(expr.getRight()));
      case GT ->
          GreaterThanNodeGen.create(
              createSourceSection(expr), visitExpr(expr.getLeft()), visitExpr(expr.getRight()));
      case LTE ->
          LessThanOrEqualNodeGen.create(
              createSourceSection(expr), visitExpr(expr.getLeft()), visitExpr(expr.getRight()));
      case GTE ->
          GreaterThanOrEqualNodeGen.create(
              createSourceSection(expr), visitExpr(expr.getLeft()), visitExpr(expr.getRight()));
      case EQ_EQ ->
          EqualNodeGen.create(
              createSourceSection(expr), visitExpr(expr.getLeft()), visitExpr(expr.getRight()));
      case NOT_EQ ->
          NotEqualNodeGen.create(
              createSourceSection(expr), visitExpr(expr.getLeft()), visitExpr(expr.getRight()));
      case AND ->
          LogicalAndNodeGen.create(
              createSourceSection(expr), visitExpr(expr.getRight()), visitExpr(expr.getLeft()));
      case OR ->
          LogicalOrNodeGen.create(
              createSourceSection(expr), visitExpr(expr.getRight()), visitExpr(expr.getLeft()));
      case PIPE ->
          PipeNodeGen.create(
              createSourceSection(expr), visitExpr(expr.getLeft()), visitExpr(expr.getRight()));
      case NULL_COALESCE ->
          NullCoalescingNodeGen.create(
              createSourceSection(expr), visitExpr(expr.getRight()), visitExpr(expr.getLeft()));
      default -> throw PklBugException.unreachableCode();
    };
  }

  @Override
  public ExpressionNode visitTypeCheckExpr(TypeCheck expr) {
    return new TypeTestNode(
        createSourceSection(expr), visitExpr(expr.getExpr()), visitType(expr.getType()));
  }

  @Override
  public ExpressionNode visitTypeCastExpr(TypeCast expr) {
    return new TypeCastNode(
        createSourceSection(expr), visitExpr(expr.getExpr()), visitType(expr.getType()));
  }

  @Override
  public ExpressionNode visitIfExpr(If expr) {
    return new IfElseNode(
        createSourceSection(expr),
        visitExpr(expr.getCond()),
        visitExpr(expr.getThen()),
        visitExpr(expr.getEls()));
  }

  @Override
  public ExpressionNode visitLetExpr(Let letExpr) {
    var sourceSection = createSourceSection(letExpr);
    var parameter = letExpr.getPar();
    var frameBuilder = FrameDescriptor.newBuilder();
    UnresolvedTypeNode[] typeNodes;
    if (parameter instanceof Parameter.TypedIdent par) {
      typeNodes = new UnresolvedTypeNode[] {visitTypeAnnotation(par.getTypeAnnotation())};
      frameBuilder.addSlot(FrameSlotKind.Illegal, toIdentifier(par.getIdent().getValue()), null);
    } else {
      typeNodes = new UnresolvedTypeNode[0];
    }

    var isCustomThisScope = symbolTable.getCurrentScope().isCustomThisScope();

    UnresolvedFunctionNode functionNode =
        symbolTable.enterLambda(
            frameBuilder,
            scope -> {
              var expr = visitExpr(letExpr.getExpr());
              return new UnresolvedFunctionNode(
                  language,
                  scope.buildFrameDescriptor(),
                  new Lambda(createSourceSection(letExpr.getExpr()), scope.getQualifiedName()),
                  1,
                  typeNodes,
                  null,
                  expr);
            });

    return new LetExprNode(
        sourceSection, functionNode, visitExpr(letExpr.getBindingExpr()), isCustomThisScope);
  }

  @Override
  public ExpressionNode visitFunctionLiteralExpr(FunctionLiteral expr) {
    var sourceSection = createSourceSection(expr);
    var params = expr.getParameterList();
    var descriptorBuilder = createFrameDescriptorBuilder(params);
    var paramCount = params.getParameters().size();

    if (paramCount > 5) {
      throw exceptionBuilder()
          .evalError("tooManyFunctionParameters")
          .withSourceSection(sourceSection)
          .build();
    }

    var isCustomThisScope = symbolTable.getCurrentScope().isCustomThisScope();

    return symbolTable.enterLambda(
        descriptorBuilder,
        scope -> {
          var exprNode = visitExpr(expr.getExpr());
          var functionNode =
              new UnresolvedFunctionNode(
                  language,
                  scope.buildFrameDescriptor(),
                  new Lambda(sourceSection, scope.getQualifiedName()),
                  paramCount,
                  doVisitParameterTypes(params),
                  null,
                  exprNode);

          return new FunctionLiteralNode(sourceSection, functionNode, isCustomThisScope);
        });
  }

  @Override
  public ExpressionNode visitParenthesizedExpr(Parenthesized expr) {
    return visitExpr(expr.getExpr());
  }

  private ExpressionNode doVisitListLiteral(Expr expr, ArgumentList argList) {
    var elementNodes = createCollectionArgumentNodes(argList);

    if (elementNodes.first.length == 0) {
      return new ConstantValueNode(VmList.EMPTY);
    }

    return elementNodes.second
        ? new ConstantValueNode(
            createSourceSection(expr), VmList.createFromConstantNodes(elementNodes.first))
        : new ListLiteralNode(createSourceSection(expr), elementNodes.first);
  }

  private ExpressionNode doVisitSetLiteral(Expr expr, ArgumentList argList) {
    var elementNodes = createCollectionArgumentNodes(argList);

    if (elementNodes.first.length == 0) {
      return new ConstantValueNode(VmSet.EMPTY);
    }

    return elementNodes.second
        ? new ConstantValueNode(
            createSourceSection(expr), VmSet.createFromConstantNodes(elementNodes.first))
        : new SetLiteralNode(createSourceSection(expr), elementNodes.first);
  }

  private ExpressionNode doVisitMapLiteral(Expr expr, ArgumentList argList) {
    var keyAndValueNodes = createCollectionArgumentNodes(argList);

    if (keyAndValueNodes.first.length == 0) {
      return new ConstantValueNode(VmMap.EMPTY);
    }

    if (keyAndValueNodes.first.length % 2 != 0) {
      throw exceptionBuilder()
          .evalError("missingMapValue")
          .withSourceSection(createSourceSection(expr))
          .build();
    }

    return keyAndValueNodes.second
        ? new ConstantValueNode(
            createSourceSection(expr), VmMap.createFromConstantNodes(keyAndValueNodes.first))
        : new MapLiteralNode(createSourceSection(expr), keyAndValueNodes.first);
  }

  private Pair<ExpressionNode[], Boolean> createCollectionArgumentNodes(ArgumentList exprs) {
    var args = exprs.getArgs();
    var elementNodes = new ExpressionNode[args.size()];
    var isConstantNodes = true;

    for (var i = 0; i < elementNodes.length; i++) {
      var exprNode = visitExpr(args.get(i));
      elementNodes[i] = exprNode;
      isConstantNodes = isConstantNodes && exprNode instanceof ConstantNode;
    }

    return Pair.of(elementNodes, isConstantNodes);
  }

  @Override
  public GeneratorPropertyNode visitObjectProperty(ObjectProperty member) {
    checkHasNoForGenerator(member, "forGeneratorCannotGenerateProperties");
    var memberNode = doVisitObjectProperty(member);
    return GeneratorPropertyNodeGen.create(memberNode);
  }

  @Override
  public Object visitObjectBodyProperty(ObjectBodyProperty member) {
    checkHasNoForGenerator(member, "forGeneratorCannotGenerateProperties");
    var memberNode = doVisitObjectProperty(member);
    return GeneratorPropertyNodeGen.create(memberNode);
  }

  @Override
  public Object visitObjectMethod(ObjectMethod memberNode) {
    checkHasNoForGenerator(memberNode, "forGeneratorCannotGenerateMethods");
    var member = doVisitObjectMethod(memberNode);
    return GeneratorPropertyNodeGen.create(member);
  }

  @Override
  public Object visitMemberPredicate(MemberPredicate member) {
    var keyNodeAndMember = doVisitMemberPredicate(member);
    var keyNode = keyNodeAndMember.first;
    var memberNode = keyNodeAndMember.second;
    insertWriteForGeneratorVarsToFrameSlotsNode(memberNode.getMemberNode());

    return GeneratorPredicateMemberNodeGen.create(keyNode, memberNode);
  }

  @Override
  public Object visitMemberPredicateBody(MemberPredicateBody member) {
    var keyNodeAndMember = doVisitMemberPredicate(member);
    var keyNode = keyNodeAndMember.first;
    var memberNode = keyNodeAndMember.second;
    insertWriteForGeneratorVarsToFrameSlotsNode(memberNode.getMemberNode());

    return GeneratorPredicateMemberNodeGen.create(keyNode, memberNode);
  }

  @Override
  public Object visitObjectElement(ObjectElement member) {
    var memberNode = doVisitObjectElement(member);
    insertWriteForGeneratorVarsToFrameSlotsNode(memberNode.getMemberNode());
    return GeneratorElementNodeGen.create(memberNode);
  }

  @Override
  public Object visitObjectEntry(ObjectEntry member) {
    var keyNodeAndMember = doVisitObjectEntry(member);
    var keyNode = keyNodeAndMember.first;
    var memberNode = keyNodeAndMember.second;
    insertWriteForGeneratorVarsToFrameSlotsNode(memberNode.getMemberNode());

    return GeneratorEntryNodeGen.create(keyNode, memberNode);
  }

  @Override
  public Object visitObjectEntryBody(ObjectEntryBody member) {
    var keyNodeAndMember = doVisitObjectEntry(member);
    var keyNode = keyNodeAndMember.first;
    var memberNode = keyNodeAndMember.second;
    insertWriteForGeneratorVarsToFrameSlotsNode(memberNode.getMemberNode());

    return GeneratorEntryNodeGen.create(keyNode, memberNode);
  }

  @Override
  public Object visitObjectSpread(ObjectSpread member) {
    var scope = symbolTable.getCurrentScope();
    var visitingIterable = scope.isVisitingIterable();
    scope.setVisitingIterable(true);
    var expr = visitExpr(member.getExpr());
    scope.setVisitingIterable(visitingIterable);
    return GeneratorSpreadNodeGen.create(createSourceSection(member), expr, member.isNullable());
  }

  @Override
  public Object visitWhenGenerator(WhenGenerator member) {
    var sourceSection = createSourceSection(member);
    var thenNodes = doVisitForWhenBody(member.getBody());
    var elseNodes =
        member.getElseClause() == null
            ? new GeneratorMemberNode[0]
            : doVisitForWhenBody(member.getElseClause());

    return new GeneratorWhenNode(sourceSection, visitExpr(member.getCond()), thenNodes, elseNodes);
  }

  private GeneratorMemberNode[] doVisitForWhenBody(ObjectBody body) {
    if (!body.getPars().isEmpty()) {
      throw exceptionBuilder()
          .evalError("forWhenBodyCannotHaveParameters")
          .withSourceSection(createSourceSection(body.getPars().get(0)))
          .build();
    }
    return doVisitGeneratorMemberNodes(body.getMembers());
  }

  private int pushForGeneratorVariableContext(TypedIdent param) {
    var currentScope = symbolTable.getCurrentScope();
    var slot = currentScope.pushForGeneratorVariableContext(param);
    if (slot == -1) {
      throw exceptionBuilder()
          .evalError("duplicateDefinition", param.getIdent().getValue())
          .withSourceSection(createSourceSection(param))
          .build();
    }
    return slot;
  }

  @Override
  public Object visitForGenerator(ForGenerator member) {
    var sourceSection = createSourceSection(member);
    int keyVariableSlot;
    int valueVariableSlot;
    UnresolvedTypeNode unresolvedKeyTypeNode;
    UnresolvedTypeNode unresolvedValueTypeNode;
    var currentScope = symbolTable.getCurrentScope();
    var p1 = member.getP1();
    var p2 = member.getP2();
    var ignoreT1 = p1 instanceof Parameter.Underscore;
    var ignoreT2 = p2 == null ? ignoreT1 : p2 instanceof Parameter.Underscore;

    if (p2 != null) {
      if (p1 instanceof TypedIdent p1ident) {
        keyVariableSlot = pushForGeneratorVariableContext(p1ident);
        unresolvedKeyTypeNode = visitTypeAnnotation(p1ident.getTypeAnnotation());
      } else {
        keyVariableSlot = -1;
        unresolvedKeyTypeNode = null;
      }
      if (p2 instanceof TypedIdent p2ident) {
        valueVariableSlot = pushForGeneratorVariableContext(p2ident);
        unresolvedValueTypeNode = visitTypeAnnotation(p2ident.getTypeAnnotation());
      } else {
        valueVariableSlot = -1;
        unresolvedValueTypeNode = null;
      }
    } else {
      keyVariableSlot = -1;
      unresolvedKeyTypeNode = null;
      if (p1 instanceof TypedIdent p1ident) {
        valueVariableSlot = pushForGeneratorVariableContext(p1ident);
        unresolvedValueTypeNode = visitTypeAnnotation(p1ident.getTypeAnnotation());
      } else {
        valueVariableSlot = -1;
        unresolvedValueTypeNode = null;
      }
    }

    var scope = symbolTable.getCurrentScope();
    var visitingIterable = scope.isVisitingIterable();
    scope.setVisitingIterable(true);
    var iterableNode = visitExpr(member.getExpr());
    scope.setVisitingIterable(visitingIterable);
    var memberNodes = doVisitForWhenBody(member.getBody());
    if (keyVariableSlot != -1) {
      currentScope.popForGeneratorVariable();
    }
    if (valueVariableSlot != -1) {
      currentScope.popForGeneratorVariable();
    }
    //noinspection ConstantConditions
    return GeneratorForNodeGen.create(
        sourceSection,
        keyVariableSlot,
        valueVariableSlot,
        iterableNode,
        unresolvedKeyTypeNode,
        unresolvedValueTypeNode,
        memberNodes,
        p2 != null && !ignoreT1,
        !ignoreT2);
  }

  @Override
  public PklRootNode visitModule(Module mod) {
    var moduleDecl = mod.getDecl();

    var annotationNodes =
        moduleDecl != null
            ? doVisitAnnotations(moduleDecl.getAnnotations())
            : new ExpressionNode[] {};

    int modifiers;
    if (moduleDecl == null) {
      modifiers = VmModifier.NONE;
    } else {
      var modifierNodes = moduleDecl.getModifiers();
      modifiers =
          doVisitModifiers(
              modifierNodes, VmModifier.VALID_MODULE_MODIFIERS, "invalidModuleModifier");
      // doing this in a second step gives better error messages
      if (moduleInfo.isAmend()) {
        modifiers =
            doVisitModifiers(
                modifierNodes,
                VmModifier.VALID_AMENDING_MODULE_MODIFIERS,
                "invalidAmendingModuleModifier");
      }
    }

    var extendsOrAmendsClause = moduleDecl != null ? moduleDecl.getExtendsOrAmendsDecl() : null;

    var supermoduleNode =
        extendsOrAmendsClause == null
            ? resolveBaseModuleClass(Identifier.MODULE, BaseModule::getModuleClass)
            : doVisitImport(false, extendsOrAmendsClause, extendsOrAmendsClause.getUrl());

    var propertyNames =
        CollectionUtils.<String>newHashSet(
            mod.getImports().size()
                + mod.getClasses().size()
                + mod.getTypeAliases().size()
                + mod.getProperties().size());

    if (!moduleInfo.isAmend()) {
      var supertypeNode =
          new UnresolvedTypeNode.Declared(supermoduleNode.getSourceSection(), supermoduleNode);
      var moduleProperties =
          doVisitModuleProperties(
              mod.getImports(),
              mod.getClasses(),
              mod.getTypeAliases(),
              List.of(),
              propertyNames,
              moduleInfo);
      var unresolvedPropertyNodes = doVisitClassProperties(mod.getProperties(), propertyNames);

      var classNode =
          new ClassNode(
              moduleInfo.getSourceSection(),
              moduleInfo.getHeaderSection(),
              moduleInfo.getDocComment(),
              annotationNodes,
              modifiers,
              PClassInfo.forModuleClass(
                  moduleInfo.getModuleName(), moduleInfo.getModuleKey().getUri()),
              List.of(),
              moduleInfo,
              supertypeNode,
              moduleProperties,
              unresolvedPropertyNodes,
              doVisitMethodDefs(mod.getMethods()));

      return new ModuleNode(
          language, moduleInfo.getSourceSection(), moduleInfo.getModuleName(), classNode);
    }

    var moduleProperties =
        doVisitModuleProperties(
            mod.getImports(),
            mod.getClasses(),
            mod.getTypeAliases(),
            mod.getProperties(),
            propertyNames,
            moduleInfo);

    for (var methodCtx : mod.getMethods()) {
      var localMethod =
          doVisitObjectMethod(
              methodCtx,
              methodCtx.getModifiers(),
              methodCtx.getHeaderSpan(),
              methodCtx.getName(),
              methodCtx.getParameterList(),
              methodCtx.getTypeParameterList(),
              methodCtx.getExpr(),
              methodCtx.getTypeAnnotation(),
              true);
      EconomicMaps.put(moduleProperties, localMethod.getName(), localMethod);
    }

    var moduleNode =
        AmendModuleNodeGen.create(
            moduleInfo.getSourceSection(),
            language,
            annotationNodes,
            moduleProperties,
            moduleInfo,
            supermoduleNode);

    return new ModuleNode(
        language, moduleInfo.getSourceSection(), moduleInfo.getModuleName(), moduleNode);
  }

  private EconomicMap<Object, ObjectMember> doVisitModuleProperties(
      List<Import> imports,
      List<Clazz> classes,
      List<TypeAlias> typeAliases,
      List<ClassPropertyEntry> properties,
      Set<String> propertyNames,
      ModuleInfo moduleInfo) {

    var totalSize = imports.size() + classes.size() + typeAliases.size() + properties.size();
    var result = EconomicMaps.<Object, ObjectMember>create(totalSize);

    for (var _import : imports) {
      var member = visitImport(_import);
      checkDuplicateMember(member.getName(), member.getHeaderSection(), propertyNames);
      EconomicMaps.put(result, member.getName(), member);
    }

    for (var clazz : classes) {
      ObjectMember member = visitClazz(clazz);

      if (moduleInfo.isAmend() && !member.isLocal()) {
        throw exceptionBuilder()
            .evalError("classMustBeLocal")
            .withSourceSection(member.getHeaderSection())
            .build();
      }

      checkDuplicateMember(member.getName(), member.getHeaderSection(), propertyNames);
      EconomicMaps.put(result, member.getName(), member);
    }

    for (var typeAlias : typeAliases) {
      var member = visitTypeAlias(typeAlias);

      if (moduleInfo.isAmend() && !member.isLocal()) {
        throw exceptionBuilder()
            .evalError("typeAliasMustBeLocal")
            .withSourceSection(member.getHeaderSection())
            .build();
      }

      checkDuplicateMember(member.getName(), member.getHeaderSection(), propertyNames);
      EconomicMaps.put(result, member.getName(), member);
    }

    for (var ctx : properties) {
      var member =
          doVisitObjectProperty(
              ctx, ctx.modifiers(), ctx.name(), ctx.typeAnnotation(), ctx.expr(), ctx.bodyList());

      if (moduleInfo.isAmend() && !member.isLocal() && ctx.typeAnnotation() != null) {
        //noinspection DataFlowIssue
        throw exceptionBuilder()
            .evalError("nonLocalObjectPropertyCannotHaveTypeAnnotation")
            .withSourceSection(createSourceSection(ctx.typeAnnotation().getType()))
            .build();
      }

      checkDuplicateMember(member.getName(), member.getHeaderSection(), propertyNames);
      EconomicMaps.put(result, member.getName(), member);
    }

    return result;
  }

  @Override
  public Object visitModuleDecl(ModuleDecl decl) {
    // visitModule should handle this
    throw PklBugException.unreachableCode();
  }

  @Override
  public Object visitExtendsOrAmendsDecl(ExtendsOrAmendsDecl decl) {
    // visitModule should handle this
    throw PklBugException.unreachableCode();
  }

  @Override
  public ObjectMember visitImport(Import imp) {
    var importNode = doVisitImport(imp.isGlob(), imp, imp.getUrl());
    var moduleKey = moduleResolver.resolve(importNode.getImportUri());
    var importName =
        Identifier.property(
            imp.getAlias() != null ? imp.getAlias().getValue() : IoUtils.inferModuleName(moduleKey),
            true);

    return symbolTable.enterProperty(
        importName,
        ConstLevel.NONE,
        scope -> {
          var modifiers = VmModifier.IMPORT | VmModifier.LOCAL | VmModifier.CONST;
          if (imp.isGlob()) {
            modifiers = modifiers | VmModifier.GLOB;
          }
          var result =
              new ObjectMember(
                  importNode.getSourceSection(),
                  importNode.getSourceSection(),
                  modifiers,
                  scope.getName(),
                  scope.getQualifiedName());

          result.initMemberNode(
              new UntypedObjectMemberNode(
                  language, scope.buildFrameDescriptor(), result, importNode));

          return result;
        });
  }

  @Override
  public ObjectMember visitClazz(Clazz clazz) {
    var sourceSection = createSourceSection(clazz);
    var headerSection = createSourceSection(clazz.getHeaderSpan());

    var bodyNode = clazz.getBody();

    var typeParameters = visitTypeParameterList(clazz.getTypeParameterList());

    List<ClassPropertyEntry> properties = bodyNode != null ? bodyNode.getProperties() : List.of();
    List<ClassMethod> methods = bodyNode != null ? bodyNode.getMethods() : List.of();

    var modifiers =
        doVisitModifiers(
                clazz.getModifiers(), VmModifier.VALID_CLASS_MODIFIERS, "invalidClassModifier")
            | VmModifier.CLASS;

    var className = Identifier.property(clazz.getName().getValue(), VmModifier.isLocal(modifiers));

    return symbolTable.enterClass(
        className,
        typeParameters,
        scope -> {
          var supertypeCtx = clazz.getSuperClass();

          // needs to be inside `enterClass` so that class' type parameters are in scope
          var supertypeNode =
              supertypeCtx != null
                  ? visitType(supertypeCtx)
                  : isBaseModule && className == Identifier.ANY
                      ? null
                      : new UnresolvedTypeNode.Declared(
                          VmUtils.unavailableSourceSection(),
                          resolveBaseModuleClass(Identifier.TYPED, BaseModule::getTypedClass));

          if (!(supertypeNode == null
              || supertypeNode instanceof UnresolvedTypeNode.Declared
              || supertypeNode instanceof UnresolvedTypeNode.Parameterized
              || supertypeNode instanceof UnresolvedTypeNode.Module)) {
            throw exceptionBuilder()
                .evalError("invalidSupertype", supertypeNode.getSourceSection().getCharacters())
                .withSourceSection(supertypeNode.getSourceSection())
                .build();
          }

          var classInfo =
              PClassInfo.get(
                  moduleInfo.getModuleName(),
                  className.toString(),
                  moduleInfo.getModuleKey().getUri());
          var propertyNames = CollectionUtils.<String>newHashSet(properties.size());

          var classNode =
              new ClassNode(
                  sourceSection,
                  headerSection,
                  createSourceSection(clazz.getDocComment()),
                  doVisitAnnotations(clazz.getAnnotations()),
                  modifiers,
                  classInfo,
                  typeParameters,
                  null,
                  supertypeNode,
                  EconomicMaps.create(),
                  doVisitClassProperties(properties, propertyNames),
                  doVisitMethodDefs(methods));

          var isLocal = VmModifier.isLocal(modifiers);

          var result =
              new ObjectMember(
                  sourceSection,
                  headerSection,
                  isLocal ? VmModifier.LOCAL_CLASS_OBJECT_MEMBER : VmModifier.CLASS_OBJECT_MEMBER,
                  scope.getName(),
                  scope.getQualifiedName());

          result.initMemberNode(
              new UntypedObjectMemberNode(
                  language, scope.buildFrameDescriptor(), result, classNode));

          return result;
        });
  }

  private ExpressionNode resolveBaseModuleClass(Identifier className, Supplier<VmClass> clazz) {
    return isBaseModule
        ?
        // Can't access BaseModule.getXYZClass() while parsing base module
        new GetBaseModuleClassNode(className)
        : new ConstantValueNode(clazz.get());
  }

  @Override
  public Integer visitModifier(Modifier modifier) {
    return switch (modifier.getValue()) {
      case EXTERNAL -> VmModifier.EXTERNAL;
      case ABSTRACT -> VmModifier.ABSTRACT;
      case OPEN -> VmModifier.OPEN;
      case LOCAL -> VmModifier.LOCAL;
      case HIDDEN -> VmModifier.HIDDEN;
      case FIXED -> VmModifier.FIXED;
      case CONST -> VmModifier.CONST;
    };
  }

  private UnresolvedPropertyNode[] doVisitClassProperties(
      List<ClassPropertyEntry> propertyContexts, Set<String> propertyNames) {
    var propertyNodes = new UnresolvedPropertyNode[propertyContexts.size()];

    for (var i = 0; i < propertyNodes.length; i++) {
      var propertyNode = visitClassPropertyEntry(propertyContexts.get(i));
      checkDuplicateMember(propertyNode.getName(), propertyNode.getHeaderSection(), propertyNames);
      propertyNodes[i] = propertyNode;
    }

    return propertyNodes;
  }

  private UnresolvedMethodNode[] doVisitMethodDefs(List<ClassMethod> methodDefs) {
    var methodNodes = new UnresolvedMethodNode[methodDefs.size()];
    var methodNames = CollectionUtils.<String>newHashSet(methodDefs.size());

    for (var i = 0; i < methodNodes.length; i++) {
      var methodNode = visitClassMethod(methodDefs.get(i));
      checkDuplicateMember(methodNode.getName(), methodNode.getHeaderSection(), methodNames);
      methodNodes[i] = methodNode;
    }

    return methodNodes;
  }

  @Override
  public UnresolvedPropertyNode visitClassPropertyEntry(ClassPropertyEntry entry) {
    var docCom = entry.docComment();
    var annotations = entry.annotations();
    var modifierList = entry.modifiers();
    var name = entry.name();
    var typeAnnotation = entry.typeAnnotation();
    var expr = entry.expr();
    var objectBodies = entry.bodyList();
    var docComment = createSourceSection(docCom);
    var annotationNodes = doVisitAnnotations(annotations);
    var sourceSection = createSourceSection(entry);
    var headerStart = !modifierList.isEmpty() ? modifierList.get(0).span() : name.span();
    var headerEnd = typeAnnotation != null ? typeAnnotation.span() : name.span();
    var headerSection = createSourceSection(headerStart.endWith(headerEnd));

    var modifiers =
        doVisitModifiers(
            modifierList, VmModifier.VALID_PROPERTY_MODIFIERS, "invalidPropertyModifier");

    var isLocal = VmModifier.isLocal(modifiers);
    var propertyName = Identifier.property(name.getValue(), isLocal);

    return symbolTable.enterProperty(
        propertyName,
        getConstLevel(modifiers),
        scope -> {
          ExpressionNode bodyNode;

          if (expr != null) { // prop = expr
            if (VmModifier.isExternal(modifiers)) {
              throw exceptionBuilder()
                  .evalError("externalMemberCannotHaveBody")
                  .withSourceSection(headerSection)
                  .build();
            }
            if (VmModifier.isAbstract(modifiers)) {
              throw exceptionBuilder()
                  .evalError("abstractMemberCannotHaveBody")
                  .withSourceSection(headerSection)
                  .build();
            }
            bodyNode = visitExpr(expr);
          } else if (objectBodies != null && !objectBodies.isEmpty()) { // prop { ... }
            if (typeAnnotation != null) {
              throw exceptionBuilder()
                  .evalError("cannotAmendPropertyDefinition")
                  .withSourceSection(createSourceSection(entry))
                  .build();
            }
            bodyNode =
                doVisitObjectBody(
                    objectBodies,
                    new ReadSuperPropertyNode(
                        unavailableSourceSection(),
                        scope.getName(),
                        scope.getConstLevel() == ConstLevel.ALL));
          } else { // no value given
            if (isLocal) {
              assert typeAnnotation != null;
              throw missingLocalPropertyValue(typeAnnotation);
            }
            if (VmModifier.isExternal(modifiers)) {
              bodyNode =
                  externalMemberRegistry.getPropertyBody(scope.getQualifiedName(), headerSection);
              if (bodyNode instanceof LanguageAwareNode languageAwareNode) {
                languageAwareNode.initLanguage(language);
              }
            } else if (VmModifier.isAbstract(modifiers)) {
              bodyNode =
                  new CannotInvokeAbstractPropertyNode(headerSection, scope.getQualifiedName());
            } else {
              bodyNode = null; // will be given a default by UnresolvedPropertyNode
            }
          }

          var typeAnnNode = visitTypeAnnotation(typeAnnotation);

          return new UnresolvedPropertyNode(
              language,
              sourceSection,
              headerSection,
              createSourceSection(name),
              scope.buildFrameDescriptor(),
              docComment,
              annotationNodes,
              modifiers,
              scope.getName(),
              scope.getQualifiedName(),
              typeAnnNode,
              bodyNode);
        });
  }

  @Override
  public UnresolvedMethodNode visitClassMethod(ClassMethod entry) {
    var headerSection = createSourceSection(entry.getHeaderSpan());

    var typeParameters = visitTypeParameterList(entry.getTypeParameterList());

    var modifiers =
        doVisitModifiers(
            entry.getModifiers(), VmModifier.VALID_METHOD_MODIFIERS, "invalidMethodModifier");

    var isLocal = VmModifier.isLocal(modifiers);
    var methodName = Identifier.method(entry.getName().getValue(), isLocal);

    var bodyContext = entry.getExpr();
    var paramListCtx = entry.getParameterList();
    var descriptorBuilder = createFrameDescriptorBuilder(paramListCtx);
    var paramCount = paramListCtx.getParameters().size();

    return symbolTable.enterMethod(
        methodName,
        getConstLevel(modifiers),
        descriptorBuilder,
        typeParameters,
        scope -> {
          ExpressionNode bodyNode;
          if (bodyContext != null) {
            if (VmModifier.isExternal(modifiers)) {
              throw exceptionBuilder()
                  .evalError("externalMemberCannotHaveBody")
                  .withSourceSection(headerSection)
                  .build();
            }
            if (VmModifier.isAbstract(modifiers)) {
              throw exceptionBuilder()
                  .evalError("abstractMemberCannotHaveBody")
                  .withSourceSection(headerSection)
                  .build();
            }
            bodyNode = visitExpr(bodyContext);
          } else {
            if (VmModifier.isExternal(modifiers)) {
              bodyNode =
                  externalMemberRegistry.getFunctionBody(
                      scope.getQualifiedName(), headerSection, paramCount);
              if (bodyNode instanceof LanguageAwareNode languageAwareNode) {
                languageAwareNode.initLanguage(language);
              }
            } else if (VmModifier.isAbstract(modifiers)) {
              bodyNode =
                  new CannotInvokeAbstractFunctionNode(headerSection, scope.getQualifiedName());
            } else {
              throw exceptionBuilder()
                  .evalError("missingMethodBody", methodName)
                  .withSourceSection(headerSection)
                  .build();
            }
          }

          return new UnresolvedMethodNode(
              language,
              createSourceSection(entry),
              headerSection,
              scope.buildFrameDescriptor(),
              createSourceSection(entry.getDocComment()),
              doVisitAnnotations(entry.getAnnotations()),
              modifiers,
              methodName,
              scope.getQualifiedName(),
              paramCount,
              typeParameters,
              doVisitParameterTypes(paramListCtx),
              visitTypeAnnotation(entry.getTypeAnnotation()),
              isMethodReturnTypeChecked,
              bodyNode);
        });
  }

  @Override
  public ObjectMember visitTypeAlias(TypeAlias typeAlias) {
    var sourceSection = createSourceSection(typeAlias);
    var headerSection = createSourceSection(typeAlias.getHeaderSpan());

    var modifiers =
        doVisitModifiers(
                typeAlias.getModifiers(),
                VmModifier.VALID_TYPE_ALIAS_MODIFIERS,
                "invalidTypeAliasModifier")
            | VmModifier.TYPE_ALIAS;

    var isLocal = VmModifier.isLocal(modifiers);
    var name = Identifier.property(typeAlias.getName().getValue(), isLocal);

    var typeParameters = visitTypeParameterList(typeAlias.getTypeParameterList());

    return symbolTable.enterTypeAlias(
        name,
        typeParameters,
        scope -> {
          var scopeName = scope.getName();
          var typeAliasNode =
              new TypeAliasNode(
                  sourceSection,
                  headerSection,
                  createSourceSection(typeAlias.getDocComment()),
                  doVisitAnnotations(typeAlias.getAnnotations()),
                  modifiers,
                  scopeName.toString(),
                  scope.getQualifiedName(),
                  typeParameters,
                  visitType(typeAlias.getType()));

          var result =
              new ObjectMember(
                  sourceSection,
                  headerSection,
                  isLocal
                      ? VmModifier.LOCAL_TYPEALIAS_OBJECT_MEMBER
                      : VmModifier.TYPEALIAS_OBJECT_MEMBER,
                  scopeName,
                  scope.getQualifiedName());

          result.initMemberNode(
              new UntypedObjectMemberNode(
                  language, scope.buildFrameDescriptor(), result, typeAliasNode));

          return result;
        });
  }

  @Override
  public ExpressionNode visitAnnotation(Annotation annotation) {
    var verifyNode = new CheckIsAnnotationClassNode(visitType(annotation.getNameAsType()));

    var bodyCtx = annotation.getBody();
    if (bodyCtx == null) {
      var currentScope = symbolTable.getCurrentScope();
      //noinspection ConstantConditions
      return PropertiesLiteralNodeGen.create(
          createSourceSection(annotation),
          language,
          currentScope.getQualifiedName(),
          currentScope.isCustomThisScope(),
          null,
          new UnresolvedTypeNode[0],
          EconomicMaps.create(),
          verifyNode);
    }

    return symbolTable.enterAnnotationScope((scope) -> doVisitObjectBody(bodyCtx, verifyNode));
  }

  private ExpressionNode[] doVisitAnnotations(List<? extends Annotation> annotations) {
    var nodes = new ExpressionNode[annotations.size()];
    for (var i = 0; i < nodes.length; i++) {
      nodes[i] = visitAnnotation(annotations.get(i));
    }
    return nodes;
  }

  @Override
  public UnresolvedTypeNode visitType(Type type) {
    if (type instanceof Type.UnknownType t) return visitUnknownType(t);
    else if (type instanceof Type.NothingType t) return visitNothingType(t);
    else if (type instanceof Type.ModuleType t) return visitModuleType(t);
    else if (type instanceof Type.StringConstantType t) return visitStringConstantType(t);
    else if (type instanceof Type.DeclaredType t) return visitDeclaredType(t);
    else if (type instanceof Type.ParenthesizedType t) return visitParenthesizedType(t);
    else if (type instanceof Type.NullableType t) return visitNullableType(t);
    else if (type instanceof Type.ConstrainedType t) return visitConstrainedType(t);
    else if (type instanceof Type.DefaultUnionType t) return visitDefaultUnionType(t);
    else if (type instanceof Type.UnionType t) return visitUnionType(t);
    else if (type instanceof Type.FunctionType t) return visitFunctionType(t);
    else throw PklBugException.unreachableCode();
  }

  @Override
  public ExpressionNode visitExpr(Expr expr) {
    if (expr instanceof Expr.This e) return visitThisExpr(e);
    if (expr instanceof Expr.Outer e) return visitOuterExpr(e);
    if (expr instanceof Expr.Module e) return visitModuleExpr(e);
    if (expr instanceof Expr.NullLiteral e) return visitNullLiteralExpr(e);
    if (expr instanceof Expr.BoolLiteral e) return visitBoolLiteralExpr(e);
    if (expr instanceof Expr.IntLiteral e) return visitIntLiteralExpr(e);
    if (expr instanceof Expr.FloatLiteral e) return visitFloatLiteralExpr(e);
    if (expr instanceof Expr.StringConstant e) return visitStringConstantExpr(e);
    if (expr instanceof Expr.InterpolatedString e) return visitInterpolatedStringExpr(e);
    if (expr instanceof Expr.InterpolatedMultiString e) return visitInterpolatedMultiStringExpr(e);
    if (expr instanceof Expr.Throw e) return visitThrowExpr(e);
    if (expr instanceof Expr.Trace e) return visitTraceExpr(e);
    if (expr instanceof Expr.ImportExpr e) return visitImportExpr(e);
    if (expr instanceof Expr.Read e) return visitReadExpr(e);
    if (expr instanceof Expr.ReadGlob e) return visitReadGlobExpr(e);
    if (expr instanceof Expr.ReadNull e) return visitReadNullExpr(e);
    if (expr instanceof Expr.UnqualifiedAccess e) return visitUnqualifiedAccessExpr(e);
    if (expr instanceof Expr.QualifiedAccess e) return visitQualifiedAccessExpr(e);
    if (expr instanceof Expr.SuperAccess e) return visitSuperAccessExpr(e);
    if (expr instanceof Expr.SuperSubscript e) return visitSuperSubscriptExpr(e);
    if (expr instanceof Expr.NonNull e) return visitNonNullExpr(e);
    if (expr instanceof Expr.UnaryMinus e) return visitUnaryMinusExpr(e);
    if (expr instanceof Expr.LogicalNot e) return visitLogicalNotExpr(e);
    if (expr instanceof Expr.If e) return visitIfExpr(e);
    if (expr instanceof Expr.Let e) return visitLetExpr(e);
    if (expr instanceof Expr.FunctionLiteral e) return visitFunctionLiteralExpr(e);
    if (expr instanceof Expr.Parenthesized e) return visitParenthesizedExpr(e);
    if (expr instanceof Expr.New e) return visitNewExpr(e);
    if (expr instanceof Expr.Amends e) return visitAmendsExpr(e);
    if (expr instanceof Expr.BinaryOp e) return visitBinaryOpExpr(e);
    if (expr instanceof Expr.Subscript e) return visitSubscriptExpr(e);
    if (expr instanceof Expr.TypeCast e) return visitTypeCastExpr(e);
    if (expr instanceof Expr.TypeCheck e) return visitTypeCheckExpr(e);
    // OperatorExpr and TypeExpr should never be reached here
    throw PklBugException.unreachableCode();
  }

  @Override
  public Object visitParameter(Parameter param) {
    // should never be called
    throw PklBugException.unreachableCode();
  }

  @Override
  public Object visitParameterList(ParameterList paramList) {
    // should never be called
    throw PklBugException.unreachableCode();
  }

  @Override
  public List<TypeParameter> visitTypeParameterList(@Nullable TypeParameterList ctx) {
    if (ctx == null) return List.of();

    if (!(ctx.parent() instanceof TypeAlias) && !isStdLibModule) {
      throw exceptionBuilder()
          .evalError("cannotDeclareTypeParameter")
          .withSourceSection(createSourceSection(ctx.getParams().get(0)))
          .build();
    }

    var params = ctx.getParams();
    var size = params.size();
    var result = new ArrayList<TypeParameter>(size);
    for (var i = 0; i < size; i++) {
      var paramCtx = params.get(i);
      Variance variance;
      var nodeVariance = paramCtx.getVariance();
      if (nodeVariance == null) {
        variance = TypeParameter.Variance.INVARIANT;
      } else {
        variance =
            switch (nodeVariance) {
              case IN -> TypeParameter.Variance.CONTRAVARIANT;
              case OUT -> TypeParameter.Variance.COVARIANT;
            };
      }
      var parameterName = paramCtx.getIdent().getValue();
      if (result.stream().anyMatch(it -> it.getName().equals(parameterName))) {
        throw exceptionBuilder()
            .evalError("duplicateTypeParameter", parameterName)
            .withSourceSection(createSourceSection(paramCtx))
            .build();
      }
      result.add(new TypeParameter(variance, parameterName, i));
    }
    return result;
  }

  @Override
  public @Nullable UnresolvedTypeNode visitTypeAnnotation(@Nullable TypeAnnotation typeAnnotation) {
    return typeAnnotation == null ? null : visitType(typeAnnotation.getType());
  }

  @Override
  public ExpressionNode[] visitArgumentList(ArgumentList argumentList) {
    var args = argumentList.getArgs();
    var res = new ExpressionNode[args.size()];
    for (int i = 0; i < res.length; i++) {
      res[i] = visitExpr(args.get(i));
    }
    return res;
  }

  private ResolveDeclaredTypeNode doVisitTypeName(QualifiedIdent ctx) {
    var idents = ctx.getIdents();
    return switch (idents.size()) {
      case 1 -> {
        var ident = idents.get(0);
        yield new ResolveSimpleDeclaredTypeNode(
            createSourceSection(ident), Identifier.get(ident.getValue()), isBaseModule);
      }
      case 2 -> {
        var ident1 = idents.get(0);
        var ident2 = idents.get(1);
        yield new ResolveQualifiedDeclaredTypeNode(
            createSourceSection(ctx),
            createSourceSection(ident1),
            createSourceSection(ident2),
            Identifier.localProperty(ident1.getValue()),
            Identifier.get(ident2.getValue()));
      }
      default ->
          throw exceptionBuilder()
              .evalError("invalidTypeName", ctx.text())
              .withSourceSection(createSourceSection(ctx))
              .build();
    };
  }

  private ExpressionNode doVisitObjectBody(
      List<? extends ObjectBody> bodies, ExpressionNode parentNode) {
    for (var ctx : bodies) {
      parentNode = doVisitObjectBody(ctx, parentNode);
    }
    return parentNode;
  }

  private ExpressionNode doVisitObjectBody(ObjectBody body, ExpressionNode parentNode) {
    return symbolTable.enterObjectScope(
        (scope) -> {
          var objectMembers = body.getMembers();
          if (objectMembers.isEmpty()) {
            return EmptyObjectLiteralNodeGen.create(createSourceSection(body.parent()), parentNode);
          }
          var sourceSection = createSourceSection(body.parent());

          var parametersDescriptorBuilder = createFrameDescriptorBuilder(body);
          var parameterTypes = doVisitParameterTypes(body);

          var members = EconomicMaps.<Object, ObjectMember>create();
          var elements = new ArrayList<ObjectMember>();
          var keyNodes = new ArrayList<ExpressionNode>();
          var values = new ArrayList<ObjectMember>();
          var isConstantKeyNodes = true;

          for (var memberCtx : objectMembers) {
            if (memberCtx instanceof ObjectProperty property) {
              addProperty(members, doVisitObjectProperty(property));
              continue;
            }
            if (memberCtx instanceof ObjectBodyProperty property) {
              addProperty(members, doVisitObjectProperty(property));
              continue;
            }

            if (memberCtx instanceof ObjectEntry entry) {
              var keyAndValue = doVisitObjectEntry(entry);
              var key = keyAndValue.first;
              keyNodes.add(key);
              isConstantKeyNodes = isConstantKeyNodes && key instanceof ConstantNode;
              values.add(keyAndValue.second);
              continue;
            }
            if (memberCtx instanceof ObjectEntryBody entry) {
              var keyAndValue = doVisitObjectEntry(entry);
              var key = keyAndValue.first;
              keyNodes.add(key);
              isConstantKeyNodes = isConstantKeyNodes && key instanceof ConstantNode;
              values.add(keyAndValue.second);
              continue;
            }

            if (memberCtx instanceof ObjectElement elementCtx) {
              var element = doVisitObjectElement(elementCtx);
              elements.add(element);
              continue;
            }

            if (memberCtx instanceof ObjectMethod methodCtx) {
              addProperty(members, doVisitObjectMethod(methodCtx));
              continue;
            }

            assert memberCtx instanceof ForGenerator
                || memberCtx instanceof WhenGenerator
                || memberCtx instanceof MemberPredicate
                || memberCtx instanceof MemberPredicateBody
                || memberCtx instanceof ObjectSpread;
            // bail out and create GeneratorObjectLiteralNode instead
            // (but can't we easily reuse members/elements/keyNodes/values?)
            return doVisitGeneratorObjectBody(body, parentNode);
          }

          var currentScope = symbolTable.getCurrentScope();
          var parametersDescriptor =
              parametersDescriptorBuilder == null ? null : parametersDescriptorBuilder.build();
          if (!elements.isEmpty()) {
            if (isConstantKeyNodes) { // true if zero key nodes
              addConstantEntries(members, keyNodes, values);
              //noinspection ConstantConditions
              return ElementsLiteralNodeGen.create(
                  sourceSection,
                  language,
                  currentScope.getQualifiedName(),
                  currentScope.isCustomThisScope(),
                  parametersDescriptor,
                  parameterTypes,
                  members,
                  elements.toArray(new ObjectMember[0]),
                  parentNode);
            }
            //noinspection ConstantConditions
            return ElementsEntriesLiteralNodeGen.create(
                sourceSection,
                language,
                currentScope.getQualifiedName(),
                currentScope.isCustomThisScope(),
                parametersDescriptor,
                parameterTypes,
                members,
                elements.toArray(new ObjectMember[0]),
                keyNodes.toArray(new ExpressionNode[0]),
                values.toArray(new ObjectMember[0]),
                parentNode);
          }

          if (!keyNodes.isEmpty()) {
            if (isConstantKeyNodes) {
              addConstantEntries(members, keyNodes, values);
              //noinspection ConstantConditions
              return ConstantEntriesLiteralNodeGen.create(
                  sourceSection,
                  language,
                  currentScope.getQualifiedName(),
                  currentScope.isCustomThisScope(),
                  parametersDescriptor,
                  parameterTypes,
                  members,
                  parentNode);
            }
            //noinspection ConstantConditions
            return EntriesLiteralNodeGen.create(
                sourceSection,
                language,
                currentScope.getQualifiedName(),
                currentScope.isCustomThisScope(),
                parametersDescriptor,
                parameterTypes,
                members,
                keyNodes.toArray(new ExpressionNode[0]),
                values.toArray(new ObjectMember[0]),
                parentNode);
          }
          //noinspection ConstantConditions
          return PropertiesLiteralNodeGen.create(
              sourceSection,
              language,
              currentScope.getQualifiedName(),
              currentScope.isCustomThisScope(),
              parametersDescriptor,
              parameterTypes,
              members,
              parentNode);
        });
  }

  private ObjectMember doVisitObjectProperty(ObjectProperty prop) {
    return doVisitObjectProperty(
        prop, prop.getModifiers(), prop.getIdent(), prop.getTypeAnnotation(), prop.getExpr(), null);
  }

  private ObjectMember doVisitObjectProperty(ObjectBodyProperty prop) {
    return doVisitObjectProperty(
        prop, prop.getModifiers(), prop.getIdent(), null, null, prop.getBodyList());
  }

  private ObjectMember doVisitObjectProperty(
      Node node,
      List<? extends Modifier> modifierNodes,
      Ident propertyName,
      @Nullable TypeAnnotation typeAnn,
      @Nullable Expr expr,
      @Nullable List<? extends ObjectBody> bodies) {
    var modifiers =
        doVisitModifiers(
            modifierNodes, VmModifier.VALID_OBJECT_MEMBER_MODIFIERS, "invalidObjectMemberModifier");
    if (VmModifier.isConst(modifiers) && !VmModifier.isLocal(modifiers)) {
      @SuppressWarnings("OptionalGetWithoutIsPresent")
      var constModifierCtx =
          modifierNodes.stream()
              .filter((it) -> it.getValue() == ModifierValue.CONST)
              .findFirst()
              .get();
      throw exceptionBuilder()
          .evalError("invalidConstObjectMemberModifier")
          .withSourceSection(createSourceSection(constModifierCtx))
          .build();
    }
    return doVisitObjectProperty(
        createSourceSection(node),
        createSourceSection(propertyName),
        modifiers,
        propertyName.getValue(),
        typeAnn,
        expr,
        bodies);
  }

  private ObjectMember doVisitObjectProperty(
      SourceSection sourceSection,
      SourceSection headerSection,
      int modifiers,
      String propertyName,
      @Nullable TypeAnnotation typeAnn,
      @Nullable Expr expr,
      @Nullable List<? extends ObjectBody> body) {

    var isLocal = VmModifier.isLocal(modifiers);
    var identifier = Identifier.property(propertyName, isLocal);

    return symbolTable.enterProperty(
        identifier,
        getConstLevel(modifiers),
        scope -> {
          if (isLocal) {
            if (expr == null
                && typeAnn != null) { // module property that has type annotation but no value
              throw missingLocalPropertyValue(typeAnn);
            }
          } else {
            if (typeAnn != null) {
              throw exceptionBuilder()
                  .evalError("nonLocalObjectPropertyCannotHaveTypeAnnotation")
                  .withSourceSection(createSourceSection(typeAnn))
                  .build();
            }
          }

          ExpressionNode bodyNode;
          if (body != null && !body.isEmpty()) { // foo { ... }
            if (isLocal) {
              throw exceptionBuilder()
                  .evalError("cannotAmendLocalPropertyDefinition")
                  .withSourceSection(createSourceSection(body.get(0)))
                  .build();
            }
            bodyNode =
                doVisitObjectBody(
                    body,
                    new ReadSuperPropertyNode(
                        unavailableSourceSection(),
                        scope.getName(),
                        // Never need a const check for amends declarations. In `foo { ... }`:
                        // 1. if `foo` is const (i.e. `const foo { ... }`, `super.foo` is required
                        // to be const (the const-ness of a property cannot be changed)
                        // 2. if in a const scope (i.e. `const bar = new { foo { ... } }`),
                        // `super.foo` does not reference something outside the scope.
                        false));
          } else { // foo = ...
            assert expr != null;
            bodyNode = visitExpr(expr);
          }

          return isLocal
              ? VmUtils.createLocalObjectProperty(
                  language,
                  sourceSection,
                  headerSection,
                  scope.getName(),
                  scope.getQualifiedName(),
                  scope.buildFrameDescriptor(),
                  modifiers,
                  bodyNode,
                  visitTypeAnnotation(typeAnn))
              : VmUtils.createObjectProperty(
                  language,
                  sourceSection,
                  headerSection,
                  scope.getName(),
                  scope.getQualifiedName(),
                  scope.buildFrameDescriptor(),
                  modifiers,
                  bodyNode,
                  null);
        });
  }

  private Pair<ExpressionNode, ObjectMember> doVisitObjectEntry(ObjectEntry entry) {
    var keyNode = visitExpr(entry.getKey());

    return symbolTable.enterEntry(
        keyNode,
        objectMemberInserter(createSourceSection(entry), keyNode, entry.getValue(), List.of()));
  }

  private Pair<ExpressionNode, ObjectMember> doVisitObjectEntry(ObjectEntryBody entry) {
    var keyNode = visitExpr(entry.getKey());

    return symbolTable.enterEntry(
        keyNode,
        objectMemberInserter(createSourceSection(entry), keyNode, null, entry.getBodyList()));
  }

  private ObjectMember doVisitObjectElement(ObjectElement element) {
    return symbolTable.enterEntry(
        null,
        scope -> {
          var elementNode = visitExpr(element.getExpr());

          var modifier =
              scope.isVisitingIterable()
                  ? VmModifier.ELEMENT | VmModifier.IS_IN_ITERABLE
                  : VmModifier.ELEMENT;
          var member =
              new ObjectMember(
                  createSourceSection(element),
                  elementNode.getSourceSection(),
                  modifier,
                  null,
                  scope.getQualifiedName());

          if (elementNode instanceof ConstantNode constantNode) {
            member.initConstantValue(constantNode);
          } else {
            member.initMemberNode(
                ElementOrEntryNodeGen.create(
                    language, scope.buildFrameDescriptor(), member, elementNode));
          }

          return member;
        });
  }

  private ObjectMember doVisitObjectMethod(ObjectMethod method) {
    return doVisitObjectMethod(method, false);
  }

  private ObjectMember doVisitObjectMethod(ObjectMethod method, boolean isModuleMethod) {
    return doVisitObjectMethod(
        method,
        method.getModifiers(),
        method.headerSpan(),
        method.getIdent(),
        method.getParamList(),
        method.getTypeParameterList(),
        method.getExpr(),
        method.getTypeAnnotation(),
        isModuleMethod);
  }

  private ObjectMember doVisitObjectMethod(
      Node method,
      List<Modifier> modifierNodes,
      Span headerSpan,
      Ident ident,
      ParameterList paramList,
      @Nullable TypeParameterList typeParamList,
      Expr expr,
      @Nullable TypeAnnotation typeAnnotation,
      boolean isModuleMethod) {
    var modifiers =
        doVisitModifiers(
            modifierNodes, VmModifier.VALID_OBJECT_MEMBER_MODIFIERS, "invalidObjectMemberModifier");

    if (!VmModifier.isLocal(modifiers)) {
      throw exceptionBuilder()
          .evalError(isModuleMethod ? "moduleMethodMustBeLocal" : "objectMethodMustBeLocal")
          .withSourceSection(createSourceSection(headerSpan))
          .build();
    }

    var methodName = Identifier.method(ident.getValue(), true);

    var frameDescriptorBuilder = createFrameDescriptorBuilder(paramList);

    return symbolTable.enterMethod(
        methodName,
        getConstLevel(modifiers),
        frameDescriptorBuilder,
        List.of(),
        scope -> {
          if (typeParamList != null) {
            throw exceptionBuilder()
                .evalError("cannotDeclareTypeParameter")
                .withSourceSection(createSourceSection(typeParamList))
                .build();
          }

          var member =
              new ObjectMember(
                  createSourceSection(method),
                  createSourceSection(headerSpan),
                  modifiers,
                  scope.getName(),
                  scope.getQualifiedName());
          var body = visitExpr(expr);
          var node =
              new ObjectMethodNode(
                  language,
                  scope.buildFrameDescriptor(),
                  member,
                  body,
                  paramList.getParameters().size(),
                  doVisitParameterTypes(paramList),
                  visitTypeAnnotation(typeAnnotation));

          member.initMemberNode(node);
          return member;
        });
  }

  private GeneratorObjectLiteralNode doVisitGeneratorObjectBody(
      ObjectBody body, ExpressionNode parentNode) {
    var parametersDescriptor = createFrameDescriptorBuilder(body);
    var parameterTypes = doVisitParameterTypes(body);
    var memberNodes = doVisitGeneratorMemberNodes(body.getMembers());
    var currentScope = symbolTable.getCurrentScope();
    //noinspection ConstantConditions
    return GeneratorObjectLiteralNodeGen.create(
        createSourceSection(body.parent()),
        language,
        currentScope.getQualifiedName(),
        currentScope.isCustomThisScope(),
        parametersDescriptor == null ? null : parametersDescriptor.build(),
        parameterTypes,
        memberNodes,
        parentNode);
  }

  private GeneratorMemberNode[] doVisitGeneratorMemberNodes(
      List<? extends ObjectMemberNode> members) {
    var result = new GeneratorMemberNode[members.size()];
    for (var i = 0; i < result.length; i++) {
      result[i] = (GeneratorMemberNode) visitObjectMember(members.get(i));
    }
    return result;
  }

  private Pair<ExpressionNode, ObjectMember> doVisitMemberPredicate(MemberPredicate member) {
    var keyNode = symbolTable.enterCustomThisScope(scope -> visitExpr(member.getPred()));

    return symbolTable.enterEntry(
        keyNode,
        objectMemberInserter(createSourceSection(member), keyNode, member.getExpr(), List.of()));
  }

  private Pair<ExpressionNode, ObjectMember> doVisitMemberPredicate(MemberPredicateBody member) {
    var keyNode = symbolTable.enterCustomThisScope(scope -> visitExpr(member.getKey()));

    return symbolTable.enterEntry(
        keyNode,
        objectMemberInserter(createSourceSection(member), keyNode, null, member.getBodyList()));
  }

  private ExpressionNode doVisitPropertyInvocationExpr(QualifiedAccess expr) {
    var sourceSection = createSourceSection(expr);
    var propertyName = toIdentifier(expr.getIdent().getValue());
    var receiver = visitExpr(expr.getExpr());

    if (receiver instanceof IntLiteralNode intLiteralNode) {
      var durationUnit = VmDuration.toUnit(propertyName);
      if (durationUnit != null) {
        //noinspection ConstantConditions
        return new ConstantValueNode(
            sourceSection, new VmDuration(intLiteralNode.executeInt(null), durationUnit));
      }
      var dataSizeUnit = VmDataSize.toUnit(propertyName);
      if (dataSizeUnit != null) {
        //noinspection ConstantConditions
        return new ConstantValueNode(
            sourceSection,
            new VmDataSize(((IntLiteralNode) receiver).executeInt(null), dataSizeUnit));
      }
    }

    if (receiver instanceof FloatLiteralNode floatLiteralNode) {
      var durationUnit = VmDuration.toUnit(propertyName);
      if (durationUnit != null) {
        //noinspection ConstantConditions
        return new ConstantValueNode(
            sourceSection, new VmDuration(floatLiteralNode.executeFloat(null), durationUnit));
      }
      var dataSizeUnit = VmDataSize.toUnit(propertyName);
      if (dataSizeUnit != null) {
        //noinspection ConstantConditions
        return new ConstantValueNode(
            sourceSection,
            new VmDataSize(((FloatLiteralNode) receiver).executeFloat(null), dataSizeUnit));
      }
    }

    var needsConst = needsConst(receiver);
    if (expr.isNullable()) {
      return new NullPropagatingOperationNode(
          sourceSection,
          ReadPropertyNodeGen.create(
              sourceSection,
              propertyName,
              needsConst,
              PropagateNullReceiverNodeGen.create(unavailableSourceSection(), receiver)));
    }

    return ReadPropertyNodeGen.create(sourceSection, propertyName, needsConst, receiver);
  }

  private ExpressionNode doVisitMethodAccessExpr(QualifiedAccess expr) {
    var sourceSection = createSourceSection(expr);
    var functionName = toIdentifier(expr.getIdent().getValue());
    var argCtx = expr.getArgumentList();
    var receiver = visitExpr(expr.getExpr());
    var needsConst = needsConst(receiver);

    if (expr.isNullable()) {
      //noinspection ConstantConditions
      return new NullPropagatingOperationNode(
          sourceSection,
          InvokeMethodVirtualNodeGen.create(
              sourceSection,
              functionName,
              visitArgumentList(argCtx),
              MemberLookupMode.EXPLICIT_RECEIVER,
              needsConst,
              symbolTable.getCurrentScope().isVisitingIterable(),
              PropagateNullReceiverNodeGen.create(unavailableSourceSection(), receiver),
              GetClassNodeGen.create(null)));
    }

    //noinspection ConstantConditions
    return InvokeMethodVirtualNodeGen.create(
        sourceSection,
        functionName,
        visitArgumentList(argCtx),
        MemberLookupMode.EXPLICIT_RECEIVER,
        needsConst,
        symbolTable.getCurrentScope().isVisitingIterable(),
        receiver,
        GetClassNodeGen.create(null));
  }

  private void addConstantEntries(
      EconomicMap<Object, ObjectMember> members,
      List<ExpressionNode> keyNodes,
      List<ObjectMember> values) {

    for (var i = 0; i < keyNodes.size(); i++) {
      var key = ((ConstantNode) keyNodes.get(i)).getValue();
      var value = values.get(i);
      var previousValue = EconomicMaps.put(members, key, value);
      if (previousValue != null) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder()
            .evalError("duplicateDefinition", new ProgramValue("", key))
            .withSourceSection(value.getHeaderSection())
            .build();
      }
    }
  }

  private int doVisitModifiers(
      List<? extends Modifier> modifiers, int validModifiers, String errorMessage) {

    var result = VmModifier.NONE;
    for (var ctx : modifiers) {
      int modifier = visitModifier(ctx);
      if ((modifier & validModifiers) == 0) {
        throw exceptionBuilder()
            .evalError(errorMessage, ctx.getValue().name().toLowerCase())
            .withSourceSection(createSourceSection(ctx))
            .build();
      }
      result += modifier;
    }

    if (symbolTable.getCurrentScope().isVisitingIterable()) {
      result += VmModifier.IS_IN_ITERABLE;
    }

    // flag modifier combinations that are never valid right away

    if (VmModifier.isExternal(result) && !ModuleKeys.isStdLibModule(moduleKey)) {
      throw exceptionBuilder()
          .evalError("cannotDefineExternalMember")
          .withSourceSection(createSourceSection(modifiers, ModifierValue.EXTERNAL))
          .build();
    }

    if (VmModifier.isLocal(result) && VmModifier.isHidden(result)) {
      throw exceptionBuilder()
          .evalError("redundantHiddenModifier")
          .withSourceSection(createSourceSection(modifiers, ModifierValue.HIDDEN))
          .build();
    }

    if (VmModifier.isLocal(result) && VmModifier.isFixed(result)) {
      throw exceptionBuilder()
          .evalError("redundantFixedModifier")
          .withSourceSection(createSourceSection(modifiers, ModifierValue.FIXED))
          .build();
    }

    if (VmModifier.isAbstract(result) && VmModifier.isOpen(result)) {
      throw exceptionBuilder()
          .evalError("redundantOpenModifier")
          .withSourceSection(createSourceSection(modifiers, ModifierValue.OPEN))
          .build();
    }

    return result;
  }

  private UnresolvedTypeNode[] doVisitParameterTypes(ObjectBody body) {
    return doVisitParameterTypes(body.getPars());
  }

  private UnresolvedTypeNode[] doVisitParameterTypes(ParameterList paramList) {
    return doVisitParameterTypes(paramList.getParameters());
  }

  private UnresolvedTypeNode[] doVisitParameterTypes(List<Parameter> params) {
    var typeNodes = new UnresolvedTypeNode[params.size()];
    for (int i = 0; i < typeNodes.length; i++) {
      if (params.get(i) instanceof Parameter.TypedIdent tident) {
        typeNodes[i] = visitTypeAnnotation(tident.getTypeAnnotation());
      } else {
        typeNodes[i] = null;
      }
    }
    return typeNodes;
  }

  // TODO: use Set<String> and checkDuplicateMember() to find duplicates between local and non-local
  //       properties
  private void addProperty(EconomicMap<Object, ObjectMember> objectMembers, ObjectMember property) {
    if (EconomicMaps.put(objectMembers, property.getName(), property) != null) {
      throw exceptionBuilder()
          .evalError("duplicateDefinition", property.getName())
          .withSourceSection(property.getHeaderSection())
          .build();
    }
  }

  private Function<EntryScope, Pair<ExpressionNode, ObjectMember>> objectMemberInserter(
      SourceSection sourceSection,
      ExpressionNode keyNode,
      @Nullable Expr valueExpr,
      List<? extends ObjectBody> objectBodies) {
    return scope -> {
      var modifier =
          scope.isVisitingIterable()
              ? VmModifier.ENTRY | VmModifier.IS_IN_ITERABLE
              : VmModifier.ENTRY;
      var member =
          new ObjectMember(
              sourceSection, keyNode.getSourceSection(), modifier, null, scope.getQualifiedName());

      if (valueExpr != null) { // ["key"] = value
        var valueNode = visitExpr(valueExpr);
        if (valueNode instanceof ConstantNode constantNode) {
          member.initConstantValue(constantNode);
        } else {
          member.initMemberNode(
              ElementOrEntryNodeGen.create(
                  language, scope.buildFrameDescriptor(), member, valueNode));
        }
      } else { // ["key"] { ... }
        var objectBody =
            doVisitObjectBody(
                objectBodies,
                new ReadSuperEntryNode(unavailableSourceSection(), new GetMemberKeyNode()));
        member.initMemberNode(
            ElementOrEntryNodeGen.create(
                language, scope.buildFrameDescriptor(), member, objectBody));
      }

      return Pair.of(keyNode, member);
    };
  }

  private void insertWriteForGeneratorVarsToFrameSlotsNode(@Nullable MemberNode memberNode) {
    if (memberNode == null) return; // member has constant value

    var descriptor = memberNode.getFrameDescriptor();
    var forGeneratorVars = symbolTable.getCurrentScope().getForGeneratorVariables();
    if (forGeneratorVars.isEmpty()) {
      return; // node is not within a for generator
    }
    var slots = new int[forGeneratorVars.size()];
    var i = 0;
    for (var variable : forGeneratorVars) {
      slots[i] = descriptor.findOrAddAuxiliarySlot(variable);
      i++;
    }
    memberNode.replaceBody((bodyNode) -> new WriteForVariablesNode(slots, bodyNode));
  }

  private boolean needsConst(ExpressionNode receiver) {
    var scope = symbolTable.getCurrentScope();
    var constLevel = scope.getConstLevel();
    var needsConst = false;
    if (receiver instanceof OuterNode) {
      var outerScope = getParentLexicalScope();
      if (outerScope != null) {
        needsConst =
            switch (constLevel) {
              case MODULE -> outerScope.isModuleScope();
              case ALL -> outerScope.getConstLevel() != ConstLevel.ALL;
              case NONE -> false;
            };
      }
    } else if (receiver instanceof GetModuleNode) {
      needsConst = constLevel != ConstLevel.NONE;
    } else if (receiver instanceof ThisNode) {
      var constDepth = scope.getConstDepth();
      needsConst = constLevel == ConstLevel.ALL && constDepth == -1;
    }
    return needsConst;
  }

  private FrameDescriptor.Builder createFrameDescriptorBuilder(ParameterList params) {
    var builder = FrameDescriptor.newBuilder(params.getParameters().size());
    for (var param : params.getParameters()) {
      Identifier ident = null;
      if (param instanceof Parameter.TypedIdent tident) {
        ident = toIdentifier(tident.getIdent().getValue());
      }
      builder.addSlot(FrameSlotKind.Illegal, ident, null);
    }
    return builder;
  }

  private @Nullable FrameDescriptor.Builder createFrameDescriptorBuilder(ObjectBody body) {
    if (body.getPars().isEmpty()) return null;

    var builder = FrameDescriptor.newBuilder(body.getPars().size());
    for (var param : body.getPars()) {
      Identifier ident = null;
      if (param instanceof Parameter.TypedIdent tident) {
        ident = toIdentifier(tident.getIdent().getValue());
      }
      builder.addSlot(FrameSlotKind.Illegal, ident, null);
    }
    return builder;
  }

  private void checkHasNoForGenerator(Node ctx, String errorMessageKey) {
    if (symbolTable.getCurrentScope().getForGeneratorVariables().isEmpty()) {
      return;
    }
    var forExprCtx = ctx.parent();
    while (forExprCtx.getClass() != ObjectMemberNode.ForGenerator.class) {
      forExprCtx = forExprCtx.parent();
    }
    throw exceptionBuilder()
        .evalError(errorMessageKey)
        .withSourceSection(
            createSourceSection(((ObjectMemberNode.ForGenerator) forExprCtx).forSpan()))
        .build();
  }

  private void checkDuplicateMember(
      Identifier memberName,
      SourceSection headerSection,
      // use Set<String> rather than Set<Identifier>
      // to detect conflicts between local and non-local identifiers
      Set<String> visited) {

    if (!visited.add(memberName.toString())) {
      throw exceptionBuilder()
          .evalError("duplicateDefinition", memberName)
          .withSourceSection(headerSection)
          .build();
    }
  }

  protected VmExceptionBuilder exceptionBuilder() {
    return new VmExceptionBuilder()
        .withMemberName(symbolTable.getCurrentScope().getQualifiedName());
  }

  private @Nullable SourceSection createSourceSection(@Nullable Node node) {
    return node == null
        ? null
        : source.createSection(node.span().charIndex(), node.span().length());
  }

  private SourceSection createSourceSection(Span span) {
    return source.createSection(span.charIndex(), span.length());
  }

  protected final SourceSection createSourceSection(
      List<? extends Modifier> modifiers, ModifierValue symbol) {

    var modifierCtx =
        modifiers.stream().filter(mod -> mod.getValue() == symbol).findFirst().orElseThrow();

    return createSourceSection(modifierCtx);
  }

  protected static @Nullable SourceSection createSourceSection(Source source, @Nullable Node node) {
    if (node == null) return null;
    return createSourceSection(source, node.span());
  }

  protected static SourceSection createSourceSection(Source source, Span span) {
    return source.createSection(span.charIndex(), span.length());
  }

  private SourceSection startOf(Node node) {
    return startOf(node.span());
  }

  private SourceSection startOf(Span span) {
    return source.createSection(span.charIndex(), 1);
  }

  private SourceSection shrinkLeft(SourceSection section, int length) {
    return source.createSection(section.getCharIndex() + length, section.getCharLength() - length);
  }

  private @Nullable SymbolTable.Scope getParentLexicalScope() {
    var parent = symbolTable.getCurrentScope().getLexicalScope().getParent();
    if (parent != null) return parent.getLexicalScope();
    return null;
  }

  private Identifier toIdentifier(String text) {
    return Identifier.get(text);
  }

  private ExpressionNode createResolveVariableNode(SourceSection section, Identifier propertyName) {
    var scope = symbolTable.getCurrentScope();
    return new ResolveVariableNode(
        section,
        propertyName,
        isBaseModule,
        scope.isCustomThisScope(),
        scope.getConstLevel(),
        scope.getConstDepth());
  }

  private String getCommonIndent(Node lastPart, Span endQuoteSpan) {
    if (!(lastPart instanceof StringConstant str) || str.getStr().isEmpty()) {
      throw exceptionBuilder()
          .evalError("closingStringDelimiterMustBeginOnNewLine")
          .withSourceSection(startOf(endQuoteSpan))
          .build();
    }
    var lastStr = str.getStr();
    var foundNewline = false;
    var indent = new StringBuilder();
    for (var i = lastStr.length() - 1; i >= 0; i--) {
      var ch = lastStr.charAt(i);
      if (ch == ' ' || ch == '\t') {
        indent.append(ch);
      } else {
        foundNewline = ch == '\n';
        break;
      }
    }

    if (foundNewline) {
      return indent.toString();
    }
    throw exceptionBuilder()
        .evalError("closingStringDelimiterMustBeginOnNewLine")
        .withSourceSection(startOf(endQuoteSpan))
        .build();
  }

  private URI resolveImport(String importUri, StringConstant ctx) {
    URI parsedUri;
    try {
      parsedUri = IoUtils.toUri(importUri);
    } catch (URISyntaxException e) {
      throw exceptionBuilder()
          .evalError("invalidModuleUri", importUri)
          .withHint(e.getReason())
          .withSourceSection(createSourceSection(ctx))
          .build();
    }
    URI resolvedUri;
    var context = VmContext.get(null);
    try {
      resolvedUri = IoUtils.resolve(context.getSecurityManager(), moduleKey, parsedUri);
    } catch (FileNotFoundException e) {

      var exceptionBuilder =
          exceptionBuilder()
              .evalError("cannotFindModule", importUri)
              .withSourceSection(createSourceSection(ctx));
      var path = parsedUri.getPath();
      if (path != null && path.contains("\\")) {
        exceptionBuilder.withHint(
            "To resolve modules in nested directories, use `/` as the directory separator.");
      }
      throw exceptionBuilder.build();
    } catch (URISyntaxException e) {
      throw exceptionBuilder()
          .evalError("invalidModuleUri", importUri)
          .withHint(e.getReason())
          .withSourceSection(createSourceSection(ctx))
          .build();
    } catch (IOException e) {
      throw exceptionBuilder()
          .evalError("ioErrorLoadingModule", importUri)
          .withCause(e)
          .withSourceSection(createSourceSection(ctx))
          .build();
    } catch (SecurityManagerException | PackageLoadError e) {
      throw exceptionBuilder().withSourceSection(createSourceSection(ctx)).withCause(e).build();
    } catch (VmException e) {
      throw exceptionBuilder()
          .evalError(e.getMessage(), e.getMessageArguments())
          .withCause(e.getCause())
          .withHint(e.getHint())
          .withSourceSection(createSourceSection(ctx))
          .build();
    } catch (ExternalReaderProcessException e) {
      throw exceptionBuilder()
          .evalError("externalReaderFailure")
          .withCause(e.getCause())
          .withSourceSection(createSourceSection(ctx))
          .build();
    }

    if (!resolvedUri.isAbsolute()) {
      throw exceptionBuilder()
          .evalError("cannotHaveRelativeImport", moduleKey.getUri())
          .withSourceSection(createSourceSection(ctx))
          .build();
    }
    return resolvedUri;
  }

  private ConstLevel getConstLevel(int modifiers) {
    if (VmModifier.isConst(modifiers)) return ConstLevel.ALL;
    return symbolTable.getCurrentScope().getConstLevel();
  }

  private VmException missingLocalPropertyValue(TypeAnnotation typeAnn) {
    var stop = typeAnn.span().stopIndex();
    return exceptionBuilder()
        .evalError("missingLocalPropertyValue")
        .withSourceSection(source.createSection(stop + 1, 0))
        .build();
  }

  private static SourceSection unavailableSourceSection() {
    return VmUtils.unavailableSourceSection();
  }

  private static String getLeadingIndent(String text) {
    for (var i = 0; i < text.length(); i++) {
      var ch = text.charAt(i);
      if (ch != ' ' && ch != '\t') {
        return text.substring(0, i);
      }
    }

    return text;
  }
}
