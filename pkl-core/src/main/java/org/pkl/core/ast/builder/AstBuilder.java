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
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.PClassInfo;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.TypeParameter;
import org.pkl.core.TypeParameter.Variance;
import org.pkl.core.ast.*;
import org.pkl.core.ast.builder.SymbolTable.AnnotationScope;
import org.pkl.core.ast.builder.SymbolTable.ClassScope;
import org.pkl.core.ast.builder.SymbolTable.EntryScope;
import org.pkl.core.ast.builder.SymbolTable.Scope;
import org.pkl.core.ast.expression.binary.*;
import org.pkl.core.ast.expression.generator.*;
import org.pkl.core.ast.expression.literal.*;
import org.pkl.core.ast.expression.member.*;
import org.pkl.core.ast.expression.primary.*;
import org.pkl.core.ast.expression.ternary.IfElseNode;
import org.pkl.core.ast.expression.unary.*;
import org.pkl.core.ast.internal.GetBaseModuleClassNode;
import org.pkl.core.ast.internal.GetClassNodeGen;
import org.pkl.core.ast.internal.ToStringNodeGen;
import org.pkl.core.ast.lambda.ApplyVmFunction1NodeGen;
import org.pkl.core.ast.member.*;
import org.pkl.core.ast.type.*;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.module.ModuleKeys;
import org.pkl.core.module.ResolvedModuleKey;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.parser.antlr.PklLexer;
import org.pkl.core.parser.antlr.PklParser.*;
import org.pkl.core.runtime.*;
import org.pkl.core.runtime.VmException.ProgramValue;
import org.pkl.core.stdlib.LanguageAwareNode;
import org.pkl.core.stdlib.registry.ExternalMemberRegistry;
import org.pkl.core.stdlib.registry.MemberRegistryFactory;
import org.pkl.core.util.CollectionUtils;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.Pair;

public final class AstBuilder extends AbstractAstBuilder<Object> {
  private final VmLanguage language;
  private final ModuleInfo moduleInfo;

  private final ModuleKey moduleKey;
  private final ModuleResolver moduleResolver;
  private final boolean isBaseModule;
  private final boolean isStdLibModule;
  private final ExternalMemberRegistry externalMemberRegistry;
  private final SymbolTable symbolTable;
  private final boolean isMethodReturnTypeChecked;

  public AstBuilder(
      Source source, VmLanguage language, ModuleInfo moduleInfo, ModuleResolver moduleResolver) {
    super(source);
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

  public static AstBuilder create(
      Source source,
      VmLanguage language,
      ModuleContext ctx,
      ModuleKey moduleKey,
      ResolvedModuleKey resolvedModuleKey,
      ModuleResolver moduleResolver) {
    var moduleDecl = ctx.moduleDecl();
    var moduleHeader = moduleDecl != null ? moduleDecl.moduleHeader() : null;
    var sourceSection = createSourceSection(source, ctx);
    var headerSection =
        moduleHeader != null
            ? createSourceSection(source, moduleHeader)
            :
            // no explicit module declaration; designate start of file as header section
            source.createSection(0, 0);
    var docComment = moduleDecl != null ? createSourceSection(source, moduleDecl.t) : null;

    ModuleInfo moduleInfo;
    if (moduleDecl == null) {
      var moduleName = IoUtils.inferModuleName(moduleKey);
      moduleInfo =
          new ModuleInfo(
              sourceSection, headerSection, null, moduleName, moduleKey, resolvedModuleKey, false);
    } else {
      var declaredModuleName = moduleDecl.moduleHeader().qualifiedIdentifier();
      var moduleName =
          declaredModuleName != null
              ? declaredModuleName.getText()
              : IoUtils.inferModuleName(moduleKey);
      var clause = moduleDecl.moduleHeader().moduleExtendsOrAmendsClause();
      var isAmend = clause != null && clause.t.getType() == PklLexer.AMENDS;
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

    return new AstBuilder(source, language, moduleInfo, moduleResolver);
  }

  @Override
  public PklRootNode visitModule(ModuleContext ctx) {
    var moduleDecl = ctx.moduleDecl();
    var moduleHeader = moduleDecl != null ? moduleDecl.moduleHeader() : null;

    var annotationNodes =
        moduleDecl != null ? doVisitAnnotations(moduleDecl.annotation()) : new ExpressionNode[] {};

    int modifiers;
    if (moduleHeader == null) {
      modifiers = VmModifier.NONE;
    } else {
      var modifierCtxs = moduleHeader.modifier();
      modifiers =
          doVisitModifiers(
              modifierCtxs, VmModifier.VALID_MODULE_MODIFIERS, "invalidModuleModifier");
      // doing this in a second step gives better error messages
      if (moduleInfo.isAmend()) {
        modifiers =
            doVisitModifiers(
                modifierCtxs,
                VmModifier.VALID_AMENDING_MODULE_MODIFIERS,
                "invalidAmendingModuleModifier");
      }
    }

    var extendsOrAmendsClause =
        moduleHeader != null ? moduleHeader.moduleExtendsOrAmendsClause() : null;

    var supermoduleNode =
        extendsOrAmendsClause == null
            ? resolveBaseModuleClass(Identifier.MODULE, BaseModule::getModuleClass)
            : doVisitImport(
                PklLexer.IMPORT, extendsOrAmendsClause, extendsOrAmendsClause.stringConstant());

    var propertyNames =
        CollectionUtils.<String>newHashSet(
            ctx.is.size() + ctx.cs.size() + ctx.ts.size() + ctx.ps.size());

    if (!moduleInfo.isAmend()) {
      var supertypeNode =
          new UnresolvedTypeNode.Declared(supermoduleNode.getSourceSection(), supermoduleNode);
      var moduleProperties =
          doVisitModuleProperties(ctx.is, ctx.cs, ctx.ts, List.of(), propertyNames, moduleInfo);
      var unresolvedPropertyNodes = doVisitClassProperties(ctx.ps, propertyNames);

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
              doVisitMethodDefs(ctx.ms));

      return new ModuleNode(
          language, moduleInfo.getSourceSection(), moduleInfo.getModuleName(), classNode);
    }

    var moduleProperties =
        doVisitModuleProperties(ctx.is, ctx.cs, ctx.ts, ctx.ps, propertyNames, moduleInfo);

    for (var methodCtx : ctx.ms) {
      var localMethod = doVisitObjectMethod(methodCtx.methodHeader(), methodCtx.expr(), true);
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

  @Override
  public ObjectMember visitClazz(ClazzContext ctx) {
    var headerCtx = ctx.classHeader();

    var sourceSection = createSourceSection(ctx);
    var headerSection = createSourceSection(headerCtx);

    var bodyCtx = ctx.classBody();
    if (bodyCtx != null) {
      checkClosingDelimiter(bodyCtx.err, "}", bodyCtx.stop);
    }

    var typeParameters = visitTypeParameterList(headerCtx.typeParameterList());

    List<ClassPropertyContext> propertyCtxs = bodyCtx == null ? List.of() : bodyCtx.ps;
    List<ClassMethodContext> methodCtxs = bodyCtx == null ? List.of() : bodyCtx.ms;

    var modifiers =
        doVisitModifiers(
                headerCtx.modifier(), VmModifier.VALID_CLASS_MODIFIERS, "invalidClassModifier")
            | VmModifier.CLASS;

    var className =
        Identifier.property(headerCtx.Identifier().getText(), VmModifier.isLocal(modifiers));

    return symbolTable.enterClass(
        className,
        typeParameters,
        scope -> {
          var supertypeCtx = headerCtx.type();

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
          var propertyNames = CollectionUtils.<String>newHashSet(propertyCtxs.size());

          var classNode =
              new ClassNode(
                  sourceSection,
                  headerSection,
                  createSourceSection(ctx.t),
                  doVisitAnnotations(ctx.annotation()),
                  modifiers,
                  classInfo,
                  typeParameters,
                  null,
                  supertypeNode,
                  EconomicMaps.create(),
                  doVisitClassProperties(propertyCtxs, propertyNames),
                  doVisitMethodDefs(methodCtxs));

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

  @Override
  public ObjectMember visitTypeAlias(TypeAliasContext ctx) {
    var headerCtx = ctx.typeAliasHeader();
    var sourceSection = createSourceSection(ctx);
    var headerSection = createSourceSection(headerCtx);

    var modifiers =
        doVisitModifiers(
                headerCtx.modifier(),
                VmModifier.VALID_TYPE_ALIAS_MODIFIERS,
                "invalidTypeAliasModifier")
            | VmModifier.TYPE_ALIAS;

    var isLocal = VmModifier.isLocal(modifiers);
    var name = Identifier.property(headerCtx.Identifier().getText(), isLocal);

    var typeParameters = visitTypeParameterList(headerCtx.typeParameterList());

    return symbolTable.enterTypeAlias(
        name,
        typeParameters,
        scope -> {
          var scopeName = scope.getName();
          var typeAliasNode =
              new TypeAliasNode(
                  sourceSection,
                  headerSection,
                  createSourceSection(ctx.t),
                  doVisitAnnotations(ctx.annotation()),
                  modifiers,
                  scopeName.toString(),
                  scope.getQualifiedName(),
                  typeParameters,
                  (UnresolvedTypeNode) ctx.type().accept(this));

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
  public UnresolvedTypeNode[] visitTypeArgumentList(@Nullable TypeArgumentListContext ctx) {
    if (ctx == null) return new UnresolvedTypeNode[0];

    checkCommaSeparatedElements(ctx, ctx.ts, ctx.errs);
    checkClosingDelimiter(ctx.err, ">", ctx.stop);

    var result = new UnresolvedTypeNode[ctx.ts.size()];
    for (int i = 0; i < ctx.ts.size(); i++) {
      result[i] = (UnresolvedTypeNode) ctx.ts.get(i).accept(this);
    }
    return result;
  }

  @Override
  public List<TypeParameter> visitTypeParameterList(@Nullable TypeParameterListContext ctx) {
    if (ctx == null) return List.of();

    checkCommaSeparatedElements(ctx, ctx.ts, ctx.errs);
    checkClosingDelimiter(ctx.err, ">", ctx.stop);

    if (!(ctx.parent instanceof TypeAliasHeaderContext) && !isStdLibModule) {
      throw exceptionBuilder()
          .evalError("cannotDeclareTypeParameter")
          .withSourceSection(createSourceSection(ctx.ts.get(0)))
          .build();
    }

    var size = ctx.ts.size();
    var result = new ArrayList<TypeParameter>(size);
    for (var i = 0; i < size; i++) {
      var paramCtx = ctx.ts.get(i);
      Variance variance;
      if (paramCtx.t == null) {
        variance = TypeParameter.Variance.INVARIANT;
      } else if (paramCtx.t.getType() == PklLexer.IN) {
        variance = TypeParameter.Variance.CONTRAVARIANT;
      } else {
        assert paramCtx.t.getType() == PklLexer.OUT;
        variance = TypeParameter.Variance.COVARIANT;
      }
      var parameterName = paramCtx.Identifier().getText();
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
  public @Nullable UnresolvedTypeNode visitTypeAnnotation(@Nullable TypeAnnotationContext ctx) {
    return ctx == null ? null : (UnresolvedTypeNode) ctx.type().accept(this);
  }

  @Override
  public Object visitNewExpr(NewExprContext ctx) {
    var typeCtx = ctx.type();
    return typeCtx != null
        ? doVisitNewExprWithExplicitParent(ctx, typeCtx)
        : doVisitNewExprWithInferredParent(ctx);
  }

  // `new Listing<Person> {}` is sugar for: `new Listing<Person> {} as Listing<Person>`
  private Object doVisitNewExprWithExplicitParent(NewExprContext ctx, TypeContext typeCtx) {
    var parentType = visitType(typeCtx);
    var expr =
        doVisitObjectBody(
            ctx.objectBody(),
            new GetParentForTypeNode(
                createSourceSection(ctx),
                parentType,
                symbolTable.getCurrentScope().getQualifiedName()));
    if (typeCtx instanceof DeclaredTypeContext declaredTypeContext
        && declaredTypeContext.typeArgumentList() != null) {
      return new TypeCastNode(parentType.getSourceSection(), expr, parentType);
    }
    return expr;
  }

  private Object doVisitNewExprWithInferredParent(NewExprContext ctx) {
    ExpressionNode inferredParentNode;

    ParserRuleContext child = ctx;
    var parent = ctx.getParent();
    var scope = symbolTable.getCurrentScope();
    var levelsUp = 0;

    while (parent instanceof IfExprContext
        || parent instanceof TraceExprContext
        || parent instanceof LetExprContext letExpr && letExpr.r == child) {

      if (parent instanceof LetExprContext) {
        assert scope != null;
        scope = scope.getParent();
        levelsUp += 1;
      }
      child = parent;
      parent = parent.getParent();
    }

    assert scope != null;

    if (parent instanceof ClassPropertyContext || parent instanceof ObjectPropertyContext) {
      inferredParentNode =
          InferParentWithinPropertyNodeGen.create(
              createSourceSection(ctx.t),
              scope.getName(),
              levelsUp == 0 ? new GetOwnerNode() : new GetEnclosingOwnerNode(levelsUp));
    } else if (parent instanceof ObjectElementContext
        || parent instanceof ObjectEntryContext objectEntry && objectEntry.v == child) {
      inferredParentNode =
          ApplyVmFunction1NodeGen.create(
              ReadPropertyNodeGen.create(
                  createSourceSection(ctx.t),
                  Identifier.DEFAULT,
                  levelsUp == 0 ? new GetReceiverNode() : new GetEnclosingReceiverNode(levelsUp)),
              new GetMemberKeyNode());
    } else if (parent instanceof ClassMethodContext || parent instanceof ObjectMethodContext) {
      var isObjectMethod =
          parent instanceof ObjectMethodContext
              || parent.getParent() instanceof ModuleContext && moduleInfo.isAmend();
      Identifier scopeName = scope.getName();
      inferredParentNode =
          isObjectMethod
              ? new InferParentWithinObjectMethodNode(
                  createSourceSection(ctx.t),
                  language,
                  scopeName,
                  levelsUp == 0 ? new GetOwnerNode() : new GetEnclosingOwnerNode(levelsUp))
              : new InferParentWithinMethodNode(
                  createSourceSection(ctx.t),
                  language,
                  scopeName,
                  levelsUp == 0 ? new GetOwnerNode() : new GetEnclosingOwnerNode(levelsUp));
    } else if (parent instanceof LetExprContext letExpr && letExpr.l == child) {
      // TODO (unclear how to infer type now that let-expression is implemented as lambda
      // invocation)
      throw exceptionBuilder()
          .evalError("cannotInferParent")
          .withSourceSection(createSourceSection(ctx.t))
          .build();
    } else {
      throw exceptionBuilder()
          .evalError("cannotInferParent")
          .withSourceSection(createSourceSection(ctx.t))
          .build();
    }

    return doVisitObjectBody(ctx.objectBody(), inferredParentNode);
  }

  @Override
  public Object visitAmendExpr(AmendExprContext ctx) {
    var parentExpr = ctx.expr();

    if (!(parentExpr instanceof NewExprContext
        || parentExpr instanceof AmendExprContext
        || parentExpr instanceof ParenthesizedExprContext)) {
      throw exceptionBuilder()
          .evalError("unexpectedCurlyProbablyAmendsExpression", parentExpr.getText())
          .withSourceSection(createSourceSection(ctx.objectBody().start))
          .build();
    }

    return doVisitObjectBody(ctx.objectBody(), visitExpr(parentExpr));
  }

  @Override
  public UnresolvedPropertyNode visitClassProperty(ClassPropertyContext ctx) {
    var docComment = createSourceSection(ctx.t);
    var annotationNodes = doVisitAnnotations(ctx.annotation());
    var modifierCtxs = ctx.modifier();
    var identifier = ctx.Identifier();
    var typeAnnCtx = ctx.typeAnnotation();
    var sourceSection = createSourceSection(ctx);
    var identifierSymbol = identifier.getSymbol();
    var headerSection =
        createSourceSection(
            !modifierCtxs.isEmpty() ? modifierCtxs.get(0).start : identifierSymbol,
            typeAnnCtx != null ? typeAnnCtx.getStop() : identifierSymbol);

    var modifiers =
        doVisitModifiers(
            ctx.modifier(), VmModifier.VALID_PROPERTY_MODIFIERS, "invalidPropertyModifier");

    var isLocal = VmModifier.isLocal(modifiers);
    var propertyName = Identifier.property(identifier.getText(), isLocal);

    return symbolTable.enterProperty(
        propertyName,
        getConstLevel(modifiers),
        scope -> {
          var exprCtx = ctx.expr();
          var objBodyCtx = ctx.objectBody();
          ExpressionNode bodyNode;

          if (exprCtx != null) { // prop = expr
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
            bodyNode = visitExpr(exprCtx);
          } else if (objBodyCtx != null && !objBodyCtx.isEmpty()) { // prop { ... }
            if (typeAnnCtx != null) {
              throw exceptionBuilder()
                  .evalError("cannotAmendPropertyDefinition")
                  .withSourceSection(createSourceSection(ctx))
                  .build();
            }
            bodyNode =
                doVisitObjectBody(
                    objBodyCtx,
                    new ReadSuperPropertyNode(
                        unavailableSourceSection(),
                        scope.getName(),
                        scope.getConstLevel() == ConstLevel.ALL));
          } else { // no value given
            if (isLocal) {
              assert typeAnnCtx != null;
              throw missingLocalPropertyValue(typeAnnCtx);
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

          var typeAnnNode = visitTypeAnnotation(typeAnnCtx);

          return new UnresolvedPropertyNode(
              language,
              sourceSection,
              headerSection,
              createSourceSection(identifier),
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

  private VmException missingLocalPropertyValue(TypeAnnotationContext typeAnnCtx) {
    var stop = typeAnnCtx.stop.getStopIndex();
    return exceptionBuilder()
        .evalError("missingLocalPropertyValue")
        .withSourceSection(source.createSection(stop + 1, 0))
        .build();
  }

  private ObjectMember doVisitObjectProperty(ObjectPropertyContext ctx) {
    return doVisitObjectProperty(
        ctx,
        ctx.modifier(),
        ctx.Identifier(),
        ctx.typeAnnotation(),
        ctx.v,
        ctx.d,
        ctx.objectBody());
  }

  private ObjectMember doVisitObjectMethod(ObjectMethodContext ctx) {
    return doVisitObjectMethod(ctx.methodHeader(), ctx.expr(), false);
  }

  private ObjectMember doVisitObjectMethod(
      MethodHeaderContext headerCtx, ExprContext exprCtx, boolean isModuleMethod) {
    var modifiers =
        doVisitModifiers(
            headerCtx.modifier(),
            VmModifier.VALID_OBJECT_MEMBER_MODIFIERS,
            "invalidObjectMemberModifier");

    if (!VmModifier.isLocal(modifiers)) {
      throw exceptionBuilder()
          .evalError(isModuleMethod ? "moduleMethodMustBeLocal" : "objectMethodMustBeLocal")
          .withSourceSection(createSourceSection(headerCtx))
          .build();
    }

    var methodName = Identifier.method(headerCtx.Identifier().getText(), true);

    var paramListCtx = headerCtx.parameterList();
    var frameDescriptorBuilder = createFrameDescriptorBuilder(paramListCtx);

    return symbolTable.enterMethod(
        methodName,
        getConstLevel(modifiers),
        frameDescriptorBuilder,
        List.of(),
        scope -> {
          if (headerCtx.typeParameterList() != null) {
            throw exceptionBuilder()
                .evalError("cannotDeclareTypeParameter")
                .withSourceSection(createSourceSection(headerCtx.typeParameterList()))
                .build();
          }

          var member =
              new ObjectMember(
                  createSourceSection(headerCtx.getParent()),
                  createSourceSection(headerCtx),
                  modifiers,
                  scope.getName(),
                  scope.getQualifiedName());
          var body = visitExpr(exprCtx);
          var node =
              new ObjectMethodNode(
                  language,
                  scope.buildFrameDescriptor(),
                  member,
                  body,
                  paramListCtx.ts.size(),
                  doVisitParameterTypes(paramListCtx),
                  visitTypeAnnotation(headerCtx.typeAnnotation()));

          member.initMemberNode(node);
          return member;
        });
  }

  private ObjectMember doVisitObjectProperty(
      ParserRuleContext ctx,
      List<? extends ModifierContext> modifierCtxs,
      TerminalNode propertyName,
      @Nullable TypeAnnotationContext typeAnnCtx,
      @Nullable ExprContext exprCtx,
      @Nullable Token deleteToken,
      @Nullable List<? extends ObjectBodyContext> bodyCtx) {

    return doVisitObjectProperty(
        createSourceSection(ctx),
        createSourceSection(propertyName),
        doVisitModifiers(
            modifierCtxs, VmModifier.VALID_OBJECT_MEMBER_MODIFIERS, "invalidObjectMemberModifier"),
        propertyName.getText(),
        typeAnnCtx,
        exprCtx,
        deleteToken,
        bodyCtx);
  }

  private ObjectMember doVisitObjectProperty(
      SourceSection sourceSection,
      SourceSection headerSection,
      int propertyModifiers,
      String propertyName,
      @Nullable TypeAnnotationContext typeAnnCtx,
      @Nullable ExprContext exprCtx,
      @Nullable Token deleteToken,
      @Nullable List<? extends ObjectBodyContext> bodyCtx) {

    var modifiers = propertyModifiers | (deleteToken == null ? 0 : VmModifier.DELETE);
    var isLocal = VmModifier.isLocal(modifiers);
    var identifier = Identifier.property(propertyName, isLocal);

    return symbolTable.enterProperty(
        identifier,
        getConstLevel(modifiers),
        scope -> {
          if (isLocal) {
            if (exprCtx == null
                && typeAnnCtx != null) { // module property that has type annotation but no value
              throw missingLocalPropertyValue(typeAnnCtx);
            }
          } else {
            if (typeAnnCtx != null) {
              throw exceptionBuilder()
                  .evalError("nonLocalObjectPropertyCannotHaveTypeAnnotation")
                  .withSourceSection(createSourceSection(typeAnnCtx.type()))
                  .build();
            }
          }

          ExpressionNode bodyNode;
          if (bodyCtx != null && !bodyCtx.isEmpty()) { // foo { ... }
            if (isLocal) {
              throw exceptionBuilder()
                  .evalError("cannotAmendLocalPropertyDefinition")
                  .withSourceSection(createSourceSection(bodyCtx.get(0).start))
                  .build();
            }
            bodyNode =
                doVisitObjectBody(
                    bodyCtx,
                    new ReadSuperPropertyNode(
                        unavailableSourceSection(),
                        scope.getName(),
                        // Never need a const check for amends declarations. In `foo { ... }`:
                        // 1. if `foo` is const (i.e. `const foo { ... }`, `super.foo` is required
                        // to be const (the const-ness of a property cannot be changed)
                        // 2. if in a const scope (i.e. `const bar = new { foo { ... } }`),
                        // `super.foo` does not reference something outside the scope.
                        false));
          } else if (deleteToken != null) {
            var deleteSourceSection = createSourceSection(deleteToken);
            if (isLocal) {
              throw exceptionBuilder()
                  .evalError("cannotDeleteLocalProperty")
                  .withSourceSection(deleteSourceSection)
                  .build();
            }
            bodyNode = VmUtils.DELETE_MARKER;
          } else { // foo = ...
            assert exprCtx != null;
            bodyNode = visitExpr(exprCtx);
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
                  visitTypeAnnotation(typeAnnCtx))
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

  private GeneratorMemberNode[] doVisitGeneratorMemberNodes(
      List<? extends ObjectMemberContext> memberCtxs) {
    var result = new GeneratorMemberNode[memberCtxs.size()];
    for (var i = 0; i < result.length; i++) {
      result[i] = (GeneratorMemberNode) memberCtxs.get(i).accept(this);
    }
    return result;
  }

  private GeneratorObjectLiteralNode doVisitGeneratorObjectBody(
      ObjectBodyContext ctx, ExpressionNode parentNode) {
    var parametersDescriptor = createFrameDescriptorBuilder(ctx);
    var parameterTypes = doVisitParameterTypes(ctx);
    var memberNodes = doVisitGeneratorMemberNodes(ctx.objectMember());
    var currentScope = symbolTable.getCurrentScope();
    //noinspection ConstantConditions
    return GeneratorObjectLiteralNodeGen.create(
        createSourceSection(ctx.getParent()),
        language,
        currentScope.getQualifiedName(),
        currentScope.isCustomThisScope(),
        parametersDescriptor == null ? null : parametersDescriptor.build(),
        parameterTypes,
        memberNodes,
        parentNode);
  }

  @Override
  public GeneratorPropertyNode visitObjectProperty(ObjectPropertyContext ctx) {
    checkHasNoForGenerator(ctx, "forGeneratorCannotGenerateProperties");
    var member = doVisitObjectProperty(ctx);
    return GeneratorPropertyNodeGen.create(member);
  }

  @Override
  public GeneratorMemberNode visitObjectMethod(ObjectMethodContext ctx) {
    checkHasNoForGenerator(ctx, "forGeneratorCannotGenerateMethods");
    var member = doVisitObjectMethod(ctx);
    return GeneratorPropertyNodeGen.create(member);
  }

  private void checkHasNoForGenerator(ParserRuleContext ctx, String errorMessageKey) {
    if (symbolTable.getCurrentScope().getForGeneratorVariables().isEmpty()) {
      return;
    }
    var forExprCtx = ctx.getParent();
    while (forExprCtx.getClass() != ForGeneratorContext.class) {
      forExprCtx = forExprCtx.getParent();
    }
    throw exceptionBuilder()
        .evalError(errorMessageKey)
        .withSourceSection(createSourceSection(((ForGeneratorContext) forExprCtx).FOR()))
        .build();
  }

  @Override
  public GeneratorMemberNode visitMemberPredicate(MemberPredicateContext ctx) {
    var keyNodeAndMember = doVisitMemberPredicate(ctx);
    var keyNode = keyNodeAndMember.first;
    var member = keyNodeAndMember.second;
    insertWriteForGeneratorVarsToFrameSlotsNode(member.getMemberNode());

    return GeneratorPredicateMemberNodeGen.create(keyNode, member);
  }

  @Override
  public GeneratorMemberNode visitObjectEntry(ObjectEntryContext ctx) {
    var keyNodeAndMember = doVisitObjectEntry(ctx);
    var keyNode = keyNodeAndMember.first;
    var member = keyNodeAndMember.second;
    insertWriteForGeneratorVarsToFrameSlotsNode(member.getMemberNode());

    return GeneratorEntryNodeGen.create(keyNode, member);
  }

  @Override
  public GeneratorMemberNode visitObjectSpread(ObjectSpreadContext ctx) {
    return GeneratorSpreadNodeGen.create(
        createSourceSection(ctx), visitExpr(ctx.expr()), ctx.QSPREAD() != null);
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

  @Override
  public GeneratorElementNode visitObjectElement(ObjectElementContext ctx) {
    var member = doVisitObjectElement(ctx);
    insertWriteForGeneratorVarsToFrameSlotsNode(member.getMemberNode());
    return GeneratorElementNodeGen.create(member);
  }

  private GeneratorMemberNode[] doVisitForWhenBody(ObjectBodyContext ctx) {
    if (!ctx.ps.isEmpty()) {
      throw exceptionBuilder()
          .evalError("forWhenBodyCannotHaveParameters")
          .withSourceSection(createSourceSection(ctx.ps.get(0)))
          .build();
    }
    return doVisitGeneratorMemberNodes(ctx.objectMember());
  }

  @Override
  public GeneratorWhenNode visitWhenGenerator(WhenGeneratorContext ctx) {
    checkClosingDelimiter(ctx.err, ")", ctx.e.stop);

    var sourceSection = createSourceSection(ctx);
    var thenNodes = doVisitForWhenBody(ctx.b1);
    var elseNodes = ctx.b2 == null ? new GeneratorMemberNode[0] : doVisitForWhenBody(ctx.b2);

    return new GeneratorWhenNode(sourceSection, visitExpr(ctx.e), thenNodes, elseNodes);
  }

  private int pushForGeneratorVariableContext(ParameterContext ctx) {
    var currentScope = symbolTable.getCurrentScope();
    var slot = currentScope.pushForGeneratorVariableContext(ctx);
    if (slot == -1) {
      throw exceptionBuilder()
          .evalError("duplicateDefinition", ctx.typedIdentifier().Identifier().getText())
          .withSourceSection(createSourceSection(ctx))
          .build();
    }
    return slot;
  }

  private static boolean isIgnored(@Nullable ParameterContext param) {
    return param != null && param.UNDERSCORE() != null;
  }

  @Override
  public GeneratorForNode visitForGenerator(ForGeneratorContext ctx) {
    checkClosingDelimiter(ctx.err, ")", ctx.e.stop);
    var sourceSection = createSourceSection(ctx);
    int keyVariableSlot;
    int valueVariableSlot;
    UnresolvedTypeNode unresolvedKeyTypeNode;
    UnresolvedTypeNode unresolvedValueTypeNode;
    var currentScope = symbolTable.getCurrentScope();
    var ignoreT1 = isIgnored(ctx.t1);
    var ignoreT2 = ctx.t2 == null ? ignoreT1 : isIgnored(ctx.t2);

    if (ctx.t2 != null) {
      keyVariableSlot = ignoreT1 ? -1 : pushForGeneratorVariableContext(ctx.t1);
      valueVariableSlot = ignoreT2 ? -1 : pushForGeneratorVariableContext(ctx.t2);
      unresolvedKeyTypeNode =
          ignoreT1 ? null : visitTypeAnnotation(ctx.t1.typedIdentifier().typeAnnotation());
      unresolvedValueTypeNode =
          ignoreT2 ? null : visitTypeAnnotation(ctx.t2.typedIdentifier().typeAnnotation());
    } else {
      keyVariableSlot = -1;
      valueVariableSlot = ignoreT1 ? -1 : pushForGeneratorVariableContext(ctx.t1);
      unresolvedKeyTypeNode = null;
      unresolvedValueTypeNode =
          ignoreT1 ? null : visitTypeAnnotation(ctx.t1.typedIdentifier().typeAnnotation());
    }

    var iterableNode = visitExpr(ctx.e);
    var memberNodes = doVisitForWhenBody(ctx.objectBody());
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
        ctx.t2 != null && !ignoreT1,
        !ignoreT2);
  }

  private void checkSpaceSeparatedObjectMembers(ObjectBodyContext objectBodyContext) {
    assert objectBodyContext.objectMember() != null;
    if (objectBodyContext.objectMember().size() < 2) {
      return;
    }
    ObjectMemberContext prevMember = null;
    for (var member : objectBodyContext.objectMember()) {
      if (prevMember == null) {
        prevMember = member;
        continue;
      }
      var startIndex = member.getStart().getStartIndex();
      var prevStopIndex = prevMember.getStop().getStopIndex();
      if (startIndex - prevStopIndex == 1) {
        throw exceptionBuilder()
            .evalError("unseparatedObjectMembers")
            .withSourceSection(createSourceSection(member))
            .build();
      }
    }
  }

  private ExpressionNode doVisitObjectBody(
      List<? extends ObjectBodyContext> ctxs, ExpressionNode parentNode) {
    for (var ctx : ctxs) {
      parentNode = doVisitObjectBody(ctx, parentNode);
    }
    return parentNode;
  }

  private ExpressionNode doVisitObjectBody(ObjectBodyContext ctx, ExpressionNode parentNode) {
    checkClosingDelimiter(ctx.err, "}", ctx.stop);
    return symbolTable.enterObjectScope(
        (scope) -> {
          var objectMemberCtx = ctx.objectMember();
          if (objectMemberCtx.isEmpty()) {
            return EmptyObjectLiteralNodeGen.create(
                createSourceSection(ctx.getParent()), parentNode);
          }
          var sourceSection = createSourceSection(ctx.getParent());

          var parametersDescriptorBuilder = createFrameDescriptorBuilder(ctx);
          var parameterTypes = doVisitParameterTypes(ctx);

          var members = EconomicMaps.<Object, ObjectMember>create();
          var elements = new ArrayList<ObjectMember>();
          var keyNodes = new ArrayList<ExpressionNode>();
          var values = new ArrayList<ObjectMember>();
          var isConstantKeyNodes = true;

          checkSpaceSeparatedObjectMembers(ctx);
          for (var memberCtx : objectMemberCtx) {
            if (memberCtx instanceof ObjectPropertyContext propertyCtx) {
              addProperty(members, doVisitObjectProperty(propertyCtx));
              continue;
            }

            if (memberCtx instanceof ObjectEntryContext entryCtx) {
              var keyAndValue = doVisitObjectEntry(entryCtx);
              var key = keyAndValue.first;
              keyNodes.add(key);
              isConstantKeyNodes = isConstantKeyNodes && key instanceof ConstantNode;
              values.add(keyAndValue.second);
              continue;
            }

            if (memberCtx instanceof ObjectElementContext elementCtx) {
              var element = doVisitObjectElement(elementCtx);
              elements.add(element);
              continue;
            }

            if (memberCtx instanceof ObjectMethodContext methodCtx) {
              addProperty(members, doVisitObjectMethod(methodCtx));
              continue;
            }

            assert memberCtx instanceof ForGeneratorContext
                || memberCtx instanceof WhenGeneratorContext
                || memberCtx instanceof MemberPredicateContext
                || memberCtx instanceof ObjectSpreadContext;
            // bail out and create GeneratorObjectLiteralNode instead
            // (but can't we easily reuse members/elements/keyNodes/values?)
            return doVisitGeneratorObjectBody(ctx, parentNode);
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

  private ObjectMember doVisitObjectElement(ObjectElementContext ctx) {
    return symbolTable.enterEntry(
        null,
        scope -> {
          var elementNode = visitExpr(ctx.expr());

          var member =
              new ObjectMember(
                  createSourceSection(ctx),
                  elementNode.getSourceSection(),
                  VmModifier.ELEMENT,
                  null,
                  scope.getQualifiedName());

          if (elementNode instanceof ConstantNode constantNode) {
            member.initConstantValue(constantNode);
          } else {
            member.initMemberNode(
                new UntypedObjectMemberNode(
                    language, scope.buildFrameDescriptor(), member, elementNode));
          }

          return member;
        });
  }

  private Pair<ExpressionNode, ObjectMember> doVisitMemberPredicate(MemberPredicateContext ctx) {
    if (ctx.err1 == null && ctx.err2 == null) {
      throw missingDelimiter("]]", ctx.k.stop.getStopIndex() + 1);
    } else if (ctx.err1 != null
        && (ctx.err2 == null || ctx.err1.getStartIndex() != ctx.err2.getStartIndex() - 1)) {
      // There shouldn't be any whitespace between the first and second ']'.
      throw wrongDelimiter("]]", "]", ctx.err1.getStartIndex());
    }

    var keyNode = symbolTable.enterCustomThisScope(scope -> visitExpr(ctx.k));

    return symbolTable.enterEntry(
        keyNode,
        objectMemberInserter(createSourceSection(ctx), keyNode, ctx.v, ctx.d, ctx.objectBody()));
  }

  private Pair<ExpressionNode, ObjectMember> doVisitObjectEntry(ObjectEntryContext ctx) {
    checkClosingDelimiter(ctx.err1, "]", ctx.k.stop);
    if (ctx.err2 != null) {
      throw ctx.err1.getStartIndex() == ctx.err2.getStartIndex() - 1
          ? wrongDelimiter("]", "]]", ctx.err1.getStartIndex())
          : danglingDelimiter("]", ctx.err2.getStartIndex());
    }

    var keyNode = visitExpr(ctx.k);

    return symbolTable.enterEntry(
        keyNode,
        objectMemberInserter(createSourceSection(ctx), keyNode, ctx.v, ctx.d, ctx.objectBody()));
  }

  private Function<EntryScope, Pair<ExpressionNode, ObjectMember>> objectMemberInserter(
      SourceSection sourceSection,
      ExpressionNode keyNode,
      @Nullable ExprContext valueCtx,
      @Nullable Token deleteToken,
      List<? extends ObjectBodyContext> objectBodyCtxs) {
    return scope -> {
      var member =
          new ObjectMember(
              sourceSection,
              keyNode.getSourceSection(),
              VmModifier.ENTRY | (deleteToken == null ? 0 : VmModifier.DELETE),
              null,
              scope.getQualifiedName());

      if (valueCtx != null) { // ["key"] = value
        var valueNode = visitExpr(valueCtx);
        if (valueNode instanceof ConstantNode constantNode) {
          member.initConstantValue(constantNode);
        } else {
          member.initMemberNode(
              new UntypedObjectMemberNode(
                  language, scope.buildFrameDescriptor(), member, valueNode));
        }
      } else { // ["key"] { ... }
        var objectBody =
            doVisitObjectBody(
                objectBodyCtxs,
                new ReadSuperEntryNode(unavailableSourceSection(), new GetMemberKeyNode()));
        member.initMemberNode(
            new UntypedObjectMemberNode(
                language, scope.buildFrameDescriptor(), member, objectBody));
      }

      return Pair.of(keyNode, member);
    };
  }

  @Override
  public ExpressionNode visitAnnotation(AnnotationContext ctx) {
    var verifyNode = new CheckIsAnnotationClassNode(visitType(ctx.type()));

    var bodyCtx = ctx.objectBody();
    if (bodyCtx == null) {
      var currentScope = symbolTable.getCurrentScope();
      //noinspection ConstantConditions
      return PropertiesLiteralNodeGen.create(
          createSourceSection(ctx),
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

  private ExpressionNode[] doVisitAnnotations(List<? extends AnnotationContext> ctxs) {
    return ctxs.stream().map(this::visitAnnotation).toArray(ExpressionNode[]::new);
  }

  @Override
  public Integer visitModifier(ModifierContext ctx) {
    return switch (ctx.t.getType()) {
      case PklLexer.EXTERNAL -> VmModifier.EXTERNAL;
      case PklLexer.ABSTRACT -> VmModifier.ABSTRACT;
      case PklLexer.OPEN -> VmModifier.OPEN;
      case PklLexer.LOCAL -> VmModifier.LOCAL;
      case PklLexer.HIDDEN_ -> VmModifier.HIDDEN;
      case PklLexer.FIXED -> VmModifier.FIXED;
      case PklLexer.CONST -> VmModifier.CONST;
      default -> throw createUnexpectedTokenError(ctx.t);
    };
  }

  private int doVisitModifiers(
      List<? extends ModifierContext> contexts, int validModifiers, String errorMessage) {

    var result = VmModifier.NONE;
    for (var ctx : contexts) {
      int modifier = visitModifier(ctx);
      if ((modifier & validModifiers) == 0) {
        throw exceptionBuilder()
            .evalError(errorMessage, ctx.t.getText())
            .withSourceSection(createSourceSection(ctx))
            .build();
      }
      result += modifier;
    }

    // flag modifier combinations that are never valid right away

    if (VmModifier.isExternal(result) && !ModuleKeys.isStdLibModule(moduleKey)) {
      throw exceptionBuilder()
          .evalError("cannotDefineExternalMember")
          .withSourceSection(createSourceSection(contexts, PklLexer.EXTERNAL))
          .build();
    }

    if (VmModifier.isLocal(result) && VmModifier.isHidden(result)) {
      throw exceptionBuilder()
          .evalError("redundantHiddenModifier")
          .withSourceSection(createSourceSection(contexts, PklLexer.HIDDEN_))
          .build();
    }

    if (VmModifier.isLocal(result) && VmModifier.isFixed(result)) {
      throw exceptionBuilder()
          .evalError("redundantFixedModifier")
          .withSourceSection(createSourceSection(contexts, PklLexer.FIXED))
          .build();
    }

    if (VmModifier.isAbstract(result) && VmModifier.isOpen(result)) {
      throw exceptionBuilder()
          .evalError("redundantOpenModifier")
          .withSourceSection(createSourceSection(contexts, PklLexer.OPEN))
          .build();
    }

    return result;
  }

  @Override
  public UnresolvedMethodNode visitClassMethod(ClassMethodContext ctx) {
    var headerCtx = ctx.methodHeader();
    var headerSection = createSourceSection(headerCtx);

    var typeParameters = visitTypeParameterList(headerCtx.typeParameterList());

    var modifiers =
        doVisitModifiers(
            headerCtx.modifier(), VmModifier.VALID_METHOD_MODIFIERS, "invalidMethodModifier");

    var isLocal = VmModifier.isLocal(modifiers);
    var methodName = Identifier.method(headerCtx.Identifier().getText(), isLocal);

    var bodyContext = ctx.expr();
    var paramListCtx = headerCtx.parameterList();
    var descriptorBuilder = createFrameDescriptorBuilder(paramListCtx);
    var paramCount = paramListCtx.ts.size();

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
              createSourceSection(ctx),
              headerSection,
              scope.buildFrameDescriptor(),
              createSourceSection(ctx.t),
              doVisitAnnotations(ctx.annotation()),
              modifiers,
              methodName,
              scope.getQualifiedName(),
              paramCount,
              typeParameters,
              doVisitParameterTypes(paramListCtx),
              visitTypeAnnotation(headerCtx.typeAnnotation()),
              isMethodReturnTypeChecked,
              bodyNode);
        });
  }

  @Override
  public ExpressionNode visitFunctionLiteral(FunctionLiteralContext ctx) {
    var sourceSection = createSourceSection(ctx);
    var paramCtx = ctx.parameterList();
    var descriptorBuilder = createFrameDescriptorBuilder(paramCtx);
    var paramCount = paramCtx.ts.size();

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
          var expr = visitExpr(ctx.expr());
          var functionNode =
              new UnresolvedFunctionNode(
                  language,
                  scope.buildFrameDescriptor(),
                  new Lambda(sourceSection, scope.getQualifiedName()),
                  paramCount,
                  doVisitParameterTypes(paramCtx),
                  null,
                  expr);

          return new FunctionLiteralNode(sourceSection, functionNode, isCustomThisScope);
        });
  }

  @Override
  public ConstantValueNode visitNullLiteral(NullLiteralContext ctx) {
    return new ConstantValueNode(createSourceSection(ctx), VmNull.withoutDefault());
  }

  @Override
  public ExpressionNode visitTrueLiteral(TrueLiteralContext ctx) {
    return new TrueLiteralNode(createSourceSection(ctx));
  }

  @Override
  public Object visitFalseLiteral(FalseLiteralContext ctx) {
    return new FalseLiteralNode(createSourceSection(ctx));
  }

  @Override
  public IntLiteralNode visitIntLiteral(IntLiteralContext ctx) {
    var section = createSourceSection(ctx);
    var text = ctx.IntLiteral().getText();

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
      if (text.startsWith("_")) {
        invalidSeparatorPosition(source.createSection(ctx.getStart().getStartIndex() + 2, 1));
      }
    }

    // relies on grammar rule nesting depth, but a breakage won't go unnoticed by tests
    if (ctx.getParent() instanceof UnaryMinusExprContext) {
      // handle negation here to make parsing of base.MinInt work
      // also moves negation from runtime to parse time
      text = "-" + text;
    }

    text = text.replaceAll("_", "");
    try {
      var num = Long.parseLong(text, radix);
      return new IntLiteralNode(section, num);
    } catch (NumberFormatException e) {
      throw exceptionBuilder().evalError("intTooLarge", text).withSourceSection(section).build();
    }
  }

  @Override
  public FloatLiteralNode visitFloatLiteral(FloatLiteralContext ctx) {
    var section = createSourceSection(ctx);
    var text = ctx.FloatLiteral().getText();
    // relies on grammar rule nesting depth, but a breakage won't go unnoticed by tests
    if (ctx.getParent() instanceof UnaryMinusExprContext) {
      // handle negation here for consistency with visitIntegerLiteral
      // also moves negation from runtime to parse time
      text = "-" + text;
    }

    var dotIdx = text.indexOf('.');
    if (dotIdx != -1 && text.charAt(dotIdx + 1) == '_') {
      invalidSeparatorPosition(
          source.createSection(ctx.getStart().getStartIndex() + dotIdx + 1, 1));
    }
    var exponentIdx = text.indexOf('e');
    if (exponentIdx == -1) {
      exponentIdx = text.indexOf('E');
    }
    if (exponentIdx != -1 && text.charAt(exponentIdx + 1) == '_') {
      invalidSeparatorPosition(
          source.createSection(ctx.getStart().getStartIndex() + exponentIdx + 1, 1));
    }

    text = text.replaceAll("_", "");
    try {
      var num = Double.parseDouble(text);
      return new FloatLiteralNode(section, num);
    } catch (NumberFormatException e) {
      throw exceptionBuilder().evalError("floatTooLarge", text).withSourceSection(section).build();
    }
  }

  @Override
  public Object visitSingleLineStringLiteral(SingleLineStringLiteralContext ctx) {
    checkSingleLineStringDelimiters(ctx.t, ctx.t2);

    var singleParts = ctx.singleLineStringPart();
    if (singleParts.isEmpty()) {
      return new ConstantValueNode(createSourceSection(ctx), "");
    }

    if (singleParts.size() == 1) {
      var ts = singleParts.get(0).ts;
      if (!ts.isEmpty()) {
        return new ConstantValueNode(
            createSourceSection(ctx), doVisitSingleLineConstantStringPart(ts));
      }
    }

    return new InterpolatedStringLiteralNode(
        createSourceSection(ctx),
        singleParts.stream().map(this::visitSingleLineStringPart).toArray(ExpressionNode[]::new));
  }

  @Override
  public Object visitMultiLineStringLiteral(MultiLineStringLiteralContext ctx) {
    var multiPart = ctx.multiLineStringPart();

    if (multiPart.isEmpty()) {
      throw exceptionBuilder()
          .evalError("stringContentMustBeginOnNewLine")
          .withSourceSection(createSourceSection(ctx.t2))
          .build();
    }

    var firstPart = multiPart.get(0);
    if (firstPart.e != null || firstPart.ts.get(0).getType() != PklLexer.MLNewline) {
      throw exceptionBuilder()
          .evalError("stringContentMustBeginOnNewLine")
          .withSourceSection(
              firstPart.e != null
                  ? startOf(firstPart.MLInterpolation())
                  : startOf(firstPart.ts.get(0)))
          .build();
    }

    var lastPart = multiPart.get(multiPart.size() - 1);
    var commonIndent = getCommonIndent(lastPart, ctx.t2);

    if (multiPart.size() == 1) {
      return new ConstantValueNode(
          createSourceSection(ctx),
          doVisitMultiLineConstantStringPart(firstPart.ts, commonIndent, true, true));
    }

    final var multiPartExprs = new ExpressionNode[multiPart.size()];
    var lastIndex = multiPart.size() - 1;

    for (var i = 0; i <= lastIndex; i++) {
      multiPartExprs[i] =
          doVisitMultiLineStringPart(multiPart.get(i), commonIndent, i == 0, i == lastIndex);
    }

    return new InterpolatedStringLiteralNode(createSourceSection(ctx), multiPartExprs);
  }

  @Override
  public String visitStringConstant(StringConstantContext ctx) {
    checkSingleLineStringDelimiters(ctx.t, ctx.t2);
    return doVisitSingleLineConstantStringPart(ctx.ts);
  }

  @Override
  public ExpressionNode visitSingleLineStringPart(SingleLineStringPartContext ctx) {
    if (ctx.e != null) {
      return ToStringNodeGen.create(createSourceSection(ctx), visitExpr(ctx.e));
    }

    return new ConstantValueNode(
        createSourceSection(ctx), doVisitSingleLineConstantStringPart(ctx.ts));
  }

  @Override
  public ExpressionNode visitMultiLineStringPart(MultiLineStringPartContext ctx) {
    throw exceptionBuilder().unreachableCode().build();
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

  private ExpressionNode doVisitListLiteral(ExprContext ctx, ArgumentListContext argListCtx) {
    var elementNodes = createCollectionArgumentNodes(argListCtx);

    if (elementNodes.first.length == 0) {
      return new ConstantValueNode(VmList.EMPTY);
    }

    return elementNodes.second
        ? new ConstantValueNode(
            createSourceSection(ctx), VmList.createFromConstantNodes(elementNodes.first))
        : new ListLiteralNode(createSourceSection(ctx), elementNodes.first);
  }

  private ExpressionNode doVisitSetLiteral(ExprContext ctx, ArgumentListContext argListCtx) {
    var elementNodes = createCollectionArgumentNodes(argListCtx);

    if (elementNodes.first.length == 0) {
      return new ConstantValueNode(VmSet.EMPTY);
    }

    return elementNodes.second
        ? new ConstantValueNode(
            createSourceSection(ctx), VmSet.createFromConstantNodes(elementNodes.first))
        : new SetLiteralNode(createSourceSection(ctx), elementNodes.first);
  }

  private ExpressionNode doVisitMapLiteral(ExprContext ctx, ArgumentListContext argListCtx) {
    var keyAndValueNodes = createCollectionArgumentNodes(argListCtx);

    if (keyAndValueNodes.first.length == 0) {
      return new ConstantValueNode(VmMap.EMPTY);
    }

    if (keyAndValueNodes.first.length % 2 != 0) {
      throw exceptionBuilder()
          .evalError("missingMapValue")
          .withSourceSection(createSourceSection(ctx.stop))
          .build();
    }

    return keyAndValueNodes.second
        ? new ConstantValueNode(
            createSourceSection(ctx), VmMap.createFromConstantNodes(keyAndValueNodes.first))
        : new MapLiteralNode(createSourceSection(ctx), keyAndValueNodes.first);
  }

  private Pair<ExpressionNode[], Boolean> createCollectionArgumentNodes(ArgumentListContext ctx) {
    checkCommaSeparatedElements(ctx, ctx.es, ctx.errs);
    checkClosingDelimiter(ctx.err, ")", ctx.stop);

    var exprCtxs = ctx.expr();
    var elementNodes = new ExpressionNode[exprCtxs.size()];
    var isConstantNodes = true;

    for (var i = 0; i < elementNodes.length; i++) {
      var exprNode = visitExpr(exprCtxs.get(i));
      elementNodes[i] = exprNode;
      isConstantNodes = isConstantNodes && exprNode instanceof ConstantNode;
    }

    return Pair.of(elementNodes, isConstantNodes);
  }

  @Override
  public ExpressionNode visitExpr(ExprContext ctx) {
    return (ExpressionNode) ctx.accept(this);
  }

  @Override
  public Object visitComparisonExpr(ComparisonExprContext ctx) {
    return switch (ctx.t.getType()) {
      case PklLexer.LT ->
          LessThanNodeGen.create(createSourceSection(ctx), visitExpr(ctx.l), visitExpr(ctx.r));
      case PklLexer.GT ->
          GreaterThanNodeGen.create(createSourceSection(ctx), visitExpr(ctx.l), visitExpr(ctx.r));
      case PklLexer.LTE ->
          LessThanOrEqualNodeGen.create(
              createSourceSection(ctx), visitExpr(ctx.l), visitExpr(ctx.r));
      case PklLexer.GTE ->
          GreaterThanOrEqualNodeGen.create(
              createSourceSection(ctx), visitExpr(ctx.l), visitExpr(ctx.r));
      default -> throw createUnexpectedTokenError(ctx.t);
    };
  }

  @Override
  public Object visitEqualityExpr(EqualityExprContext ctx) {
    return switch (ctx.t.getType()) {
      case PklLexer.EQUAL ->
          EqualNodeGen.create(createSourceSection(ctx), visitExpr(ctx.l), visitExpr(ctx.r));
      case PklLexer.NOT_EQUAL ->
          NotEqualNodeGen.create(createSourceSection(ctx), visitExpr(ctx.l), visitExpr(ctx.r));
      default -> throw createUnexpectedTokenError(ctx.t);
    };
  }

  @Override
  public ObjectMember visitImportClause(ImportClauseContext ctx) {
    var importNode = doVisitImport(ctx.t.getType(), ctx, ctx.stringConstant());
    var moduleKey = moduleResolver.resolve(importNode.getImportUri());
    var importName =
        Identifier.property(
            ctx.Identifier() != null
                ? ctx.Identifier().getText()
                : IoUtils.inferModuleName(moduleKey),
            true);

    return symbolTable.enterProperty(
        importName,
        ConstLevel.NONE,
        scope -> {
          var modifiers = VmModifier.IMPORT | VmModifier.LOCAL | VmModifier.CONST;
          if (ctx.IMPORT_GLOB() != null) {
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

  private URI resolveImport(String importUri, StringConstantContext importUriCtx) {
    URI parsedUri;
    try {
      parsedUri = IoUtils.toUri(importUri);
    } catch (URISyntaxException e) {
      throw exceptionBuilder()
          .evalError("invalidModuleUri", importUri)
          .withHint(e.getReason())
          .withSourceSection(createSourceSection(importUriCtx))
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
              .withSourceSection(createSourceSection(importUriCtx));
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
          .withSourceSection(createSourceSection(importUriCtx))
          .build();
    } catch (IOException e) {
      throw exceptionBuilder()
          .evalError("ioErrorLoadingModule", importUri)
          .withCause(e)
          .withSourceSection(createSourceSection(importUriCtx))
          .build();
    } catch (SecurityManagerException | PackageLoadError e) {
      throw exceptionBuilder()
          .withSourceSection(createSourceSection(importUriCtx))
          .withCause(e)
          .build();
    } catch (VmException e) {
      throw exceptionBuilder()
          .evalError(e.getMessage(), e.getMessageArguments())
          .withCause(e.getCause())
          .withHint(e.getHint())
          .withSourceSection(createSourceSection(importUriCtx))
          .build();
    }

    if (!resolvedUri.isAbsolute()) {
      throw exceptionBuilder()
          .evalError("cannotHaveRelativeImport", moduleKey.getUri())
          .withSourceSection(createSourceSection(importUriCtx))
          .build();
    }
    return resolvedUri;
  }

  @Override
  public ExpressionNode visitQualifiedIdentifier(QualifiedIdentifierContext ctx) {
    var firstToken = ctx.ts.get(0);
    var result =
        createResolveVariableNode(createSourceSection(firstToken), toIdentifier(firstToken));

    for (var i = 1; i < ctx.ts.size(); i++) {
      var token = ctx.ts.get(i);
      result = ReadPropertyNodeGen.create(createSourceSection(token), toIdentifier(token), result);
    }

    return result;
  }

  @Override
  public Object visitNonNullExpr(NonNullExprContext ctx) {
    return new NonNullNode(createSourceSection(ctx), visitExpr(ctx.expr()));
  }

  @Override
  public ExpressionNode visitUnaryMinusExpr(UnaryMinusExprContext ctx) {
    var childExpr = visitExpr(ctx.expr());
    if (childExpr instanceof IntLiteralNode || childExpr instanceof FloatLiteralNode) {
      // negation already handled in child expr (see corresponding code)
      return childExpr;
    }

    return UnaryMinusNodeGen.create(createSourceSection(ctx), childExpr);
  }

  @Override
  public ExpressionNode visitAdditiveExpr(AdditiveExprContext ctx) {
    return switch (ctx.t.getType()) {
      case PklLexer.PLUS ->
          AdditionNodeGen.create(createSourceSection(ctx), visitExpr(ctx.l), visitExpr(ctx.r));
      case PklLexer.MINUS ->
          SubtractionNodeGen.create(createSourceSection(ctx), visitExpr(ctx.l), visitExpr(ctx.r));
      default -> throw createUnexpectedTokenError(ctx.t);
    };
  }

  @Override
  public ExpressionNode visitMultiplicativeExpr(MultiplicativeExprContext ctx) {
    return switch (ctx.t.getType()) {
      case PklLexer.STAR ->
          MultiplicationNodeGen.create(
              createSourceSection(ctx), visitExpr(ctx.l), visitExpr(ctx.r));
      case PklLexer.DIV ->
          DivisionNodeGen.create(createSourceSection(ctx), visitExpr(ctx.l), visitExpr(ctx.r));
      case PklLexer.INT_DIV ->
          TruncatingDivisionNodeGen.create(
              createSourceSection(ctx), visitExpr(ctx.l), visitExpr(ctx.r));
      case PklLexer.MOD ->
          RemainderNodeGen.create(createSourceSection(ctx), visitExpr(ctx.l), visitExpr(ctx.r));
      default -> throw createUnexpectedTokenError(ctx.t);
    };
  }

  @Override
  public Object visitExponentiationExpr(ExponentiationExprContext ctx) {
    return ExponentiationNodeGen.create(
        createSourceSection(ctx), visitExpr(ctx.l), visitExpr(ctx.r));
  }

  @Override
  public ExpressionNode visitLogicalAndExpr(LogicalAndExprContext ctx) {
    return LogicalAndNodeGen.create(createSourceSection(ctx), visitExpr(ctx.r), visitExpr(ctx.l));
  }

  @Override
  public ExpressionNode visitLogicalOrExpr(LogicalOrExprContext ctx) {
    return LogicalOrNodeGen.create(createSourceSection(ctx), visitExpr(ctx.r), visitExpr(ctx.l));
  }

  @Override
  public ExpressionNode visitLogicalNotExpr(LogicalNotExprContext ctx) {
    return LogicalNotNodeGen.create(createSourceSection(ctx), visitExpr(ctx.expr()));
  }

  @Override
  public ExpressionNode visitQualifiedAccessExpr(QualifiedAccessExprContext ctx) {
    if (ctx.argumentList() != null) {
      return doVisitMethodAccessExpr(ctx);
    }

    return doVisitPropertyInvocationExpr(ctx);
  }

  private ExpressionNode doVisitMethodAccessExpr(QualifiedAccessExprContext ctx) {
    var sourceSection = createSourceSection(ctx);
    var functionName = toIdentifier(ctx.Identifier());
    var argCtx = ctx.argumentList();
    var receiver = visitExpr(ctx.expr());
    var needsConst = needsConst(receiver);

    if (ctx.t.getType() == PklLexer.QDOT) {
      //noinspection ConstantConditions
      return new NullPropagatingOperationNode(
          sourceSection,
          InvokeMethodVirtualNodeGen.create(
              sourceSection,
              functionName,
              visitArgumentList(argCtx),
              MemberLookupMode.EXPLICIT_RECEIVER,
              needsConst,
              PropagateNullReceiverNodeGen.create(unavailableSourceSection(), receiver),
              GetClassNodeGen.create(null)));
    }

    assert ctx.t.getType() == PklLexer.DOT;
    //noinspection ConstantConditions
    return InvokeMethodVirtualNodeGen.create(
        sourceSection,
        functionName,
        visitArgumentList(argCtx),
        MemberLookupMode.EXPLICIT_RECEIVER,
        needsConst,
        receiver,
        GetClassNodeGen.create(null));
  }

  private ExpressionNode doVisitPropertyInvocationExpr(QualifiedAccessExprContext ctx) {
    var sourceSection = createSourceSection(ctx);
    var propertyName = toIdentifier(ctx.Identifier());
    var receiver = visitExpr(ctx.expr());

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
    if (ctx.t.getType() == PklLexer.QDOT) {
      return new NullPropagatingOperationNode(
          sourceSection,
          ReadPropertyNodeGen.create(
              sourceSection,
              propertyName,
              needsConst,
              PropagateNullReceiverNodeGen.create(unavailableSourceSection(), receiver)));
    }

    assert ctx.t.getType() == PklLexer.DOT;
    return ReadPropertyNodeGen.create(sourceSection, propertyName, needsConst, receiver);
  }

  @Override
  public ExpressionNode visitSuperAccessExpr(SuperAccessExprContext ctx) {
    var sourceSection = createSourceSection(ctx);
    var memberName = toIdentifier(ctx.Identifier());
    var argCtx = ctx.argumentList();
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
          sourceSection, memberName, visitArgumentList(argCtx), needsConst);
    }

    // superproperty call
    return new ReadSuperPropertyNode(createSourceSection(ctx), memberName, needsConst);
  }

  @Override
  public ExpressionNode visitSuperSubscriptExpr(SuperSubscriptExprContext ctx) {
    checkClosingDelimiter(ctx.err, "]", ctx.e.stop);

    return new ReadSuperEntryNode(createSourceSection(ctx), visitExpr(ctx.e));
  }

  @Override
  public Object visitPipeExpr(PipeExprContext ctx) {
    return PipeNodeGen.create(createSourceSection(ctx), visitExpr(ctx.l), visitExpr(ctx.r));
  }

  @Override
  public ExpressionNode visitNullCoalesceExpr(NullCoalesceExprContext ctx) {
    return NullCoalescingNodeGen.create(
        createSourceSection(ctx), visitExpr(ctx.r), visitExpr(ctx.l));
  }

  @Override
  public ExpressionNode visitUnqualifiedAccessExpr(UnqualifiedAccessExprContext ctx) {
    var identifier = toIdentifier(ctx.Identifier());
    var argListCtx = ctx.argumentList();

    if (argListCtx == null) {
      return createResolveVariableNode(createSourceSection(ctx), identifier);
    }

    // TODO: make sure that no user-defined List/Set/Map method is in scope
    // TODO: support qualified calls (e.g., `import "pkl:base"; x = base.List()/Set()/Map()`) for
    // correctness
    if (identifier == Identifier.LIST) {
      return doVisitListLiteral(ctx, argListCtx);
    }

    if (identifier == Identifier.SET) {
      return doVisitSetLiteral(ctx, argListCtx);
    }

    if (identifier == Identifier.MAP) {
      return doVisitMapLiteral(ctx, argListCtx);
    }

    var scope = symbolTable.getCurrentScope();

    return new ResolveMethodNode(
        createSourceSection(ctx),
        identifier,
        visitArgumentList(argListCtx),
        isBaseModule,
        scope.isCustomThisScope(),
        scope.getConstLevel(),
        scope.getConstDepth());
  }

  @Override
  public ExpressionNode[] visitArgumentList(ArgumentListContext ctx) {
    checkCommaSeparatedElements(ctx, ctx.es, ctx.errs);
    checkClosingDelimiter(ctx.err, ")", ctx.stop);

    return ctx.es.stream().map(this::visitExpr).toArray(ExpressionNode[]::new);
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

  private Identifier toIdentifier(TerminalNode node) {
    return Identifier.get(node.getText());
  }

  private Identifier toIdentifier(Token token) {
    return Identifier.get(token.getText());
  }

  private FrameDescriptor.Builder createFrameDescriptorBuilder(ParameterListContext ctx) {
    checkCommaSeparatedElements(ctx, ctx.ts, ctx.errs);
    checkClosingDelimiter(ctx.err, ")", ctx.stop);

    var builder = FrameDescriptor.newBuilder(ctx.ts.size());
    for (var param : ctx.ts) {
      var ident = isIgnored(param) ? null : toIdentifier(param.typedIdentifier().Identifier());
      builder.addSlot(FrameSlotKind.Illegal, ident, null);
    }
    return builder;
  }

  private @Nullable FrameDescriptor.Builder createFrameDescriptorBuilder(ObjectBodyContext ctx) {
    if (ctx.ps.isEmpty()) return null;

    checkCommaSeparatedElements(ctx, ctx.ps, ctx.errs);

    var builder = FrameDescriptor.newBuilder(ctx.ps.size());
    for (var param : ctx.ps) {
      var ident = isIgnored(param) ? null : toIdentifier(param.typedIdentifier().Identifier());
      builder.addSlot(FrameSlotKind.Illegal, ident, null);
    }
    return builder;
  }

  private UnresolvedTypeNode[] doVisitParameterTypes(ParameterListContext ctx) {
    return ctx.ts.stream()
        .map(
            it -> isIgnored(it) ? null : visitTypeAnnotation(it.typedIdentifier().typeAnnotation()))
        .toArray(UnresolvedTypeNode[]::new);
  }

  private UnresolvedTypeNode[] doVisitParameterTypes(ObjectBodyContext ctx) {
    return ctx.ps.stream()
        .map(
            it -> isIgnored(it) ? null : visitTypeAnnotation(it.typedIdentifier().typeAnnotation()))
        .toArray(UnresolvedTypeNode[]::new);
  }

  @Override
  public Object visitTypedIdentifier(TypedIdentifierContext ctx) {
    throw exceptionBuilder().unreachableCode().build(); // handled directly
  }

  @Override
  public Object visitThrowExpr(ThrowExprContext ctx) {
    var exprCtx = ctx.expr();
    checkClosingDelimiter(ctx.err, ")", exprCtx.stop);

    return ThrowNodeGen.create(createSourceSection(ctx), visitExpr(exprCtx));
  }

  @Override
  public Object visitTraceExpr(TraceExprContext ctx) {
    var exprCtx = ctx.expr();
    checkClosingDelimiter(ctx.err, ")", exprCtx.stop);

    return new TraceNode(createSourceSection(ctx), visitExpr(exprCtx));
  }

  @Override
  public Object visitImportExpr(ImportExprContext ctx) {
    var importUriCtx = ctx.stringConstant();
    checkClosingDelimiter(ctx.err, ")", importUriCtx.stop);
    return doVisitImport(ctx.t.getType(), ctx, importUriCtx);
  }

  @Override
  public Object visitIfExpr(IfExprContext ctx) {
    checkClosingDelimiter(ctx.err, ")", ctx.c.stop);

    return new IfElseNode(
        createSourceSection(ctx), visitExpr(ctx.c), visitExpr(ctx.l), visitExpr(ctx.r));
  }

  @Override
  public Object visitReadExpr(ReadExprContext ctx) {
    var exprCtx = ctx.expr();
    checkClosingDelimiter(ctx.err, ")", exprCtx.stop);

    var tokenType = ctx.t.getType();

    if (tokenType == PklLexer.READ) {
      return ReadNodeGen.create(createSourceSection(ctx), moduleKey, visitExpr(exprCtx));
    }
    if (tokenType == PklLexer.READ_OR_NULL) {
      return ReadOrNullNodeGen.create(createSourceSection(ctx), moduleKey, visitExpr(exprCtx));
    }
    assert tokenType == PklLexer.READ_GLOB;
    return ReadGlobNodeGen.create(createSourceSection(ctx), moduleKey, visitExpr(exprCtx));
  }

  @Override
  public Object visitLetExpr(LetExprContext ctx) {
    checkClosingDelimiter(ctx.err, ")", ctx.l.stop);

    var sourceSection = createSourceSection(ctx);
    var idCtx = ctx.parameter();
    var frameBuilder = FrameDescriptor.newBuilder();
    var isIgnored = isIgnored(idCtx);
    var typeNodes =
        isIgnored
            ? new UnresolvedTypeNode[0]
            : new UnresolvedTypeNode[] {
              visitTypeAnnotation(idCtx.typedIdentifier().typeAnnotation())
            };
    if (!isIgnored) {
      frameBuilder.addSlot(
          FrameSlotKind.Illegal, toIdentifier(idCtx.typedIdentifier().Identifier()), null);
    }

    var isCustomThisScope = symbolTable.getCurrentScope().isCustomThisScope();

    UnresolvedFunctionNode functionNode =
        symbolTable.enterLambda(
            frameBuilder,
            scope -> {
              var expr = visitExpr(ctx.r);
              return new UnresolvedFunctionNode(
                  language,
                  scope.buildFrameDescriptor(),
                  new Lambda(createSourceSection(ctx.r), scope.getQualifiedName()),
                  1,
                  typeNodes,
                  null,
                  expr);
            });

    return new LetExprNode(sourceSection, functionNode, visitExpr(ctx.l), isCustomThisScope);
  }

  @Override
  public ExpressionNode visitThisExpr(ThisExprContext ctx) {
    if (!(ctx.parent instanceof QualifiedAccessExprContext)) {
      var currentScope = symbolTable.getCurrentScope();
      var needsConst =
          currentScope.getConstLevel() == ConstLevel.ALL
              && currentScope.getConstDepth() == -1
              && !currentScope.isCustomThisScope();
      if (needsConst) {
        throw exceptionBuilder()
            .withSourceSection(createSourceSection(ctx))
            .evalError("thisIsNotConst")
            .build();
      }
    }
    return VmUtils.createThisNode(
        createSourceSection(ctx), symbolTable.getCurrentScope().isCustomThisScope());
  }

  // TODO: `outer.` should probably have semantics similar to `super.`,
  // rather than just performing a lookup in the immediately enclosing object
  // also, consider interpreting `x = ... x ...` as `x = ... outer.x ...`
  @Override
  public OuterNode visitOuterExpr(OuterExprContext ctx) {
    if (!(ctx.parent instanceof QualifiedAccessExprContext)) {
      var constLevel = symbolTable.getCurrentScope().getConstLevel();
      var outerScope = getParentLexicalScope();
      if (outerScope != null && constLevel.bigger(outerScope.getConstLevel())) {
        throw exceptionBuilder()
            .evalError("outerIsNotConst")
            .withSourceSection(createSourceSection(ctx))
            .build();
      }
    }
    return new OuterNode(createSourceSection(ctx));
  }

  @Override
  public Object visitModuleExpr(ModuleExprContext ctx) {
    // cannot use unqualified `module` in a const context
    if (symbolTable.getCurrentScope().getConstLevel().isConst()
        && !(ctx.parent instanceof QualifiedAccessExprContext)) {
      var scope = symbolTable.getCurrentScope();
      while (scope != null
          && !(scope instanceof AnnotationScope)
          && !(scope instanceof ClassScope)) {
        scope = scope.getParent();
      }
      if (scope == null) {
        throw exceptionBuilder()
            .evalError("moduleIsNotConst", symbolTable.getCurrentScope().getName().toString())
            .withSourceSection(createSourceSection(ctx))
            .build();
      }
      var messageKey =
          scope instanceof AnnotationScope ? "moduleIsNotConstAnnotation" : "moduleIsNotConstClass";
      throw exceptionBuilder()
          .evalError(messageKey)
          .withSourceSection(createSourceSection(ctx))
          .build();
    }
    return new GetModuleNode(createSourceSection(ctx));
  }

  @Override
  public ExpressionNode visitParenthesizedExpr(ParenthesizedExprContext ctx) {
    checkClosingDelimiter(ctx.err, ")", ctx.stop);

    return visitExpr(ctx.expr());
  }

  @Override
  public Object visitSubscriptExpr(SubscriptExprContext ctx) {
    checkClosingDelimiter(ctx.err, "]", ctx.stop);

    return SubscriptNodeGen.create(createSourceSection(ctx), visitExpr(ctx.l), visitExpr(ctx.r));
  }

  @Override
  public Object visitTypeTestExpr(TypeTestExprContext ctx) {
    if (ctx.t.getType() == PklLexer.IS) {
      return new TypeTestNode(createSourceSection(ctx), visitExpr(ctx.l), visitType(ctx.r));
    }

    assert ctx.t.getType() == PklLexer.AS;
    return new TypeCastNode(createSourceSection(ctx), visitExpr(ctx.l), visitType(ctx.r));
  }

  @Override
  public Object visitUnknownType(UnknownTypeContext ctx) {
    return new UnresolvedTypeNode.Unknown(createSourceSection(ctx));
  }

  @Override
  public Object visitNothingType(NothingTypeContext ctx) {
    return new UnresolvedTypeNode.Nothing(createSourceSection(ctx));
  }

  @Override
  public Object visitModuleType(ModuleTypeContext ctx) {
    return new UnresolvedTypeNode.Module(createSourceSection(ctx));
  }

  @Override
  public Object visitStringLiteralType(StringLiteralTypeContext ctx) {
    return new UnresolvedTypeNode.StringLiteral(
        createSourceSection(ctx), visitStringConstant(ctx.stringConstant()));
  }

  @Override
  public UnresolvedTypeNode visitType(TypeContext ctx) {
    return (UnresolvedTypeNode) ctx.accept(this);
  }

  @Override
  public UnresolvedTypeNode visitDeclaredType(DeclaredTypeContext ctx) {
    var idCtx = ctx.qualifiedIdentifier();
    var argCtx = ctx.typeArgumentList();

    if (argCtx == null) {
      if (idCtx.ts.size() == 1) {
        String text = idCtx.ts.get(0).getText();
        TypeParameter typeParameter = symbolTable.findTypeParameter(text);
        if (typeParameter != null) {
          return new UnresolvedTypeNode.TypeVariable(createSourceSection(ctx), typeParameter);
        }
      }

      return new UnresolvedTypeNode.Declared(createSourceSection(ctx), doVisitTypeName(idCtx));
    }

    checkCommaSeparatedElements(argCtx, argCtx.ts, argCtx.errs);
    checkClosingDelimiter(argCtx.err, ">", argCtx.stop);

    return new UnresolvedTypeNode.Parameterized(
        createSourceSection(ctx),
        doVisitTypeName(idCtx),
        argCtx.ts.stream().map(this::visitType).toArray(UnresolvedTypeNode[]::new));
  }

  @Override
  public UnresolvedTypeNode visitParenthesizedType(ParenthesizedTypeContext ctx) {
    checkClosingDelimiter(ctx.err, ")", ctx.stop);

    return visitType(ctx.type());
  }

  @Override
  public Object visitDefaultUnionType(DefaultUnionTypeContext ctx) {
    throw exceptionBuilder()
        .evalError("notAUnion")
        .withSourceSection(createSourceSection(ctx))
        .build();
  }

  @Override
  public UnresolvedTypeNode visitUnionType(UnionTypeContext ctx) {
    var elementTypeCtxs = new ArrayList<TypeContext>();

    var result = flattenUnionType(ctx, elementTypeCtxs);
    boolean isUnionOfStringLiterals = result.first;
    int defaultIndex = result.second;

    if (isUnionOfStringLiterals) {
      return new UnresolvedTypeNode.UnionOfStringLiterals(
          createSourceSection(ctx),
          defaultIndex,
          elementTypeCtxs.stream()
              .map(it -> visitStringConstant(((StringLiteralTypeContext) it).stringConstant()))
              .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    return new UnresolvedTypeNode.Union(
        createSourceSection(ctx),
        defaultIndex,
        elementTypeCtxs.stream().map(this::visitType).toArray(UnresolvedTypeNode[]::new));
  }

  private Pair<Boolean, Integer> flattenUnionType(
      UnionTypeContext ctx, List<TypeContext> collector) {
    boolean isUnionOfStringLiterals = true;
    int index = 0;
    int defaultIndex = -1;
    var list = new ArrayDeque<TypeContext>();
    list.addLast(ctx.l);
    list.addLast(ctx.r);

    while (!list.isEmpty()) {
      var current = list.removeFirst();
      if (current instanceof UnionTypeContext unionType) {
        list.addFirst(unionType.r);
        list.addFirst(unionType.l);
        continue;
      }
      if (current instanceof DefaultUnionTypeContext defaultUnionType) {
        if (defaultIndex == -1) {
          defaultIndex = index;
        } else {
          throw exceptionBuilder()
              .evalError("multipleUnionDefaults")
              .withSourceSection(createSourceSection(ctx))
              .build();
        }
        isUnionOfStringLiterals =
            isUnionOfStringLiterals && defaultUnionType.type() instanceof StringLiteralTypeContext;
        collector.add(defaultUnionType.type());
      } else {
        isUnionOfStringLiterals =
            isUnionOfStringLiterals && current instanceof StringLiteralTypeContext;
        collector.add(current);
      }
      index++;
    }
    return Pair.of(isUnionOfStringLiterals, defaultIndex);
  }

  @Override
  public UnresolvedTypeNode visitNullableType(NullableTypeContext ctx) {
    return new UnresolvedTypeNode.Nullable(
        createSourceSection(ctx), (UnresolvedTypeNode) ctx.type().accept(this));
  }

  @Override
  public UnresolvedTypeNode visitConstrainedType(ConstrainedTypeContext ctx) {
    checkCommaSeparatedElements(ctx, ctx.es, ctx.errs);
    checkClosingDelimiter(ctx.err, ")", ctx.stop);

    var childNode = (UnresolvedTypeNode) ctx.type().accept(this);

    return symbolTable.enterCustomThisScope(
        scope ->
            new UnresolvedTypeNode.Constrained(
                createSourceSection(ctx),
                childNode,
                ctx.es.stream()
                    .map(this::visitExpr)
                    .map(it -> TypeConstraintNodeGen.create(it.getSourceSection(), it))
                    .toArray(TypeConstraintNode[]::new)));
  }

  @Override
  public UnresolvedTypeNode visitFunctionType(FunctionTypeContext ctx) {
    checkCommaSeparatedElements(ctx, ctx.ps, ctx.errs);
    checkClosingDelimiter(
        ctx.err, ")", ctx.ps.isEmpty() ? ctx.t : ctx.ps.get(ctx.ps.size() - 1).stop);

    return new UnresolvedTypeNode.Function(
        createSourceSection(ctx),
        ctx.ps.stream().map(this::visitType).toArray(UnresolvedTypeNode[]::new),
        (UnresolvedTypeNode) ctx.r.accept(this));
  }

  private ExpressionNode resolveBaseModuleClass(Identifier className, Supplier<VmClass> clazz) {
    return isBaseModule
        ?
        // Can't access BaseModule.getXYZClass() while parsing base module
        new GetBaseModuleClassNode(className)
        : new ConstantValueNode(clazz.get());
  }

  private UnresolvedPropertyNode[] doVisitClassProperties(
      List<ClassPropertyContext> propertyContexts, Set<String> propertyNames) {
    var propertyNodes = new UnresolvedPropertyNode[propertyContexts.size()];

    for (var i = 0; i < propertyNodes.length; i++) {
      var propertyCtx = propertyContexts.get(i);
      var propertyNode = visitClassProperty(propertyCtx);
      checkDuplicateMember(propertyNode.getName(), propertyNode.getHeaderSection(), propertyNames);
      propertyNodes[i] = propertyNode;
    }

    return propertyNodes;
  }

  private UnresolvedMethodNode[] doVisitMethodDefs(List<ClassMethodContext> methodDefs) {
    var methodNodes = new UnresolvedMethodNode[methodDefs.size()];
    var methodNames = CollectionUtils.<String>newHashSet(methodDefs.size());

    for (var i = 0; i < methodNodes.length; i++) {
      var methodNode = visitClassMethod(methodDefs.get(i));
      checkDuplicateMember(methodNode.getName(), methodNode.getHeaderSection(), methodNames);
      methodNodes[i] = methodNode;
    }

    return methodNodes;
  }

  private EconomicMap<Object, ObjectMember> doVisitModuleProperties(
      List<ImportClauseContext> importCtxs,
      List<ClazzContext> classCtxs,
      List<TypeAliasContext> typeAliasCtxs,
      List<ClassPropertyContext> propertyCtxs,
      Set<String> propertyNames,
      ModuleInfo moduleInfo) {

    var totalSize = importCtxs.size() + classCtxs.size() + typeAliasCtxs.size();
    var result = EconomicMaps.<Object, ObjectMember>create(totalSize);

    for (var ctx : importCtxs) {
      var member = visitImportClause(ctx);
      checkDuplicateMember(member.getName(), member.getHeaderSection(), propertyNames);
      EconomicMaps.put(result, member.getName(), member);
    }

    for (var ctx : classCtxs) {
      ObjectMember member = visitClazz(ctx);

      if (moduleInfo.isAmend() && !member.isLocal()) {
        throw exceptionBuilder()
            .evalError("classMustBeLocal")
            .withSourceSection(member.getHeaderSection())
            .build();
      }

      checkDuplicateMember(member.getName(), member.getHeaderSection(), propertyNames);
      EconomicMaps.put(result, member.getName(), member);
    }

    for (TypeAliasContext ctx : typeAliasCtxs) {
      var member = visitTypeAlias(ctx);

      if (moduleInfo.isAmend() && !member.isLocal()) {
        throw exceptionBuilder()
            .evalError("typeAliasMustBeLocal")
            .withSourceSection(member.getHeaderSection())
            .build();
      }

      checkDuplicateMember(member.getName(), member.getHeaderSection(), propertyNames);
      EconomicMaps.put(result, member.getName(), member);
    }

    for (var ctx : propertyCtxs) {
      var member =
          doVisitObjectProperty(
              ctx,
              ctx.modifier(),
              ctx.Identifier(),
              ctx.typeAnnotation(),
              ctx.expr(),
              null,
              ctx.objectBody());

      if (moduleInfo.isAmend() && !member.isLocal() && ctx.typeAnnotation() != null) {
        throw exceptionBuilder()
            .evalError("nonLocalObjectPropertyCannotHaveTypeAnnotation")
            .withSourceSection(createSourceSection(ctx.typeAnnotation().type()))
            .build();
      }

      checkDuplicateMember(member.getName(), member.getHeaderSection(), propertyNames);
      EconomicMaps.put(result, member.getName(), member);
    }

    return result;
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

  // TODO: use Set<String> and checkDuplicateMember() to find duplicates between local and non-local
  // properties
  private void addProperty(EconomicMap<Object, ObjectMember> objectMembers, ObjectMember property) {
    if (EconomicMaps.put(objectMembers, property.getName(), property) != null) {
      throw exceptionBuilder()
          .evalError("duplicateDefinition", property.getName())
          .withSourceSection(property.getHeaderSection())
          .build();
    }
  }

  private void invalidSeparatorPosition(SourceSection source) {
    throw exceptionBuilder()
        .evalError("invalidSeparatorPosition")
        .withSourceSection(source)
        .build();
  }

  private AbstractImportNode doVisitImport(
      int lexerToken, ParserRuleContext ctx, StringConstantContext importUriCtx) {
    var isGlobImport = lexerToken == PklLexer.IMPORT_GLOB;
    var section = createSourceSection(ctx);
    var importUri = visitStringConstant(importUriCtx);
    if (isGlobImport && importUri.startsWith("...")) {
      throw exceptionBuilder().evalError("cannotGlobTripleDots").withSourceSection(section).build();
    }
    var resolvedUri = resolveImport(importUri, importUriCtx);
    if (isGlobImport) {
      return new ImportGlobNode(section, moduleInfo.getResolvedModuleKey(), resolvedUri, importUri);
    }
    return new ImportNode(language, section, moduleInfo.getResolvedModuleKey(), resolvedUri);
  }

  private SourceSection startOf(TerminalNode node) {
    return startOf(node.getSymbol());
  }

  private SourceSection startOf(Token token) {
    return source.createSection(token.getStartIndex(), 1);
  }

  private SourceSection shrinkLeft(SourceSection section, int length) {
    return source.createSection(section.getCharIndex() + length, section.getCharLength() - length);
  }

  private VmException createUnexpectedTokenError(Token token) {
    return exceptionBuilder().bug("Unexpected token `%s`.", token).build();
  }

  @Override
  protected VmExceptionBuilder exceptionBuilder() {
    return new VmExceptionBuilder()
        .withMemberName(symbolTable.getCurrentScope().getQualifiedName());
  }

  private static SourceSection unavailableSourceSection() {
    return VmUtils.unavailableSourceSection();
  }

  private String getCommonIndent(MultiLineStringPartContext lastPart, Token endQuoteToken) {
    if (lastPart.e != null) {
      throw exceptionBuilder()
          .evalError("closingStringDelimiterMustBeginOnNewLine")
          .withSourceSection(startOf(endQuoteToken))
          .build();
    }

    var tokens = lastPart.ts;
    assert !tokens.isEmpty();
    var lastToken = tokens.get(tokens.size() - 1);

    if (lastToken.getType() == PklLexer.MLNewline) {
      return "";
    }

    if (tokens.size() > 1) {
      var lastButOneToken = tokens.get(tokens.size() - 2);
      if (lastButOneToken.getType() == PklLexer.MLNewline && isIndentChars(lastToken)) {
        return lastToken.getText();
      }
    }

    throw exceptionBuilder()
        .evalError("closingStringDelimiterMustBeginOnNewLine")
        .withSourceSection(startOf(endQuoteToken))
        .build();
  }

  private static boolean isIndentChars(Token token) {
    var text = token.getText();

    for (var i = 0; i < text.length(); i++) {
      var ch = text.charAt(i);
      if (ch != ' ' && ch != '\t') return false;
    }

    return true;
  }

  private static String getLeadingIndent(Token token) {
    var text = token.getText();

    for (var i = 0; i < text.length(); i++) {
      var ch = text.charAt(i);
      if (ch != ' ' && ch != '\t') {
        return text.substring(0, i);
      }
    }

    return text;
  }

  private ExpressionNode doVisitMultiLineStringPart(
      MultiLineStringPartContext ctx,
      String commonIndent,
      boolean isStringStart,
      boolean isStringEnd) {

    if (ctx.e != null) {
      return ToStringNodeGen.create(createSourceSection(ctx), visitExpr(ctx.e));
    }

    return new ConstantValueNode(
        createSourceSection(ctx),
        doVisitMultiLineConstantStringPart(ctx.ts, commonIndent, isStringStart, isStringEnd));
  }

  private String doVisitMultiLineConstantStringPart(
      List<Token> tokens, String commonIndent, boolean isStringStart, boolean isStringEnd) {

    int startIndex = 0;
    if (isStringStart) {
      // skip leading newline token
      startIndex = 1;
    }

    var endIndex = tokens.size() - 1;
    if (isStringEnd) {
      if (tokens.get(endIndex).getType() == PklLexer.MLNewline) {
        // skip trailing newline token
        endIndex -= 1;
      } else {
        // skip trailing newline and whitespace (common indent) tokens
        endIndex -= 2;
      }
    }

    var builder = new StringBuilder();
    var isLineStart = isStringStart;

    for (var i = startIndex; i <= endIndex; i++) {
      Token token = tokens.get(i);

      switch (token.getType()) {
        case PklLexer.MLNewline -> {
          builder.append('\n');
          isLineStart = true;
        }
        case PklLexer.MLCharacters -> {
          var text = token.getText();
          if (isLineStart) {
            if (text.startsWith(commonIndent)) {
              builder.append(text, commonIndent.length(), text.length());
            } else {
              String actualIndent = getLeadingIndent(token);
              if (actualIndent.length() > commonIndent.length()) {
                actualIndent = actualIndent.substring(0, commonIndent.length());
              }
              throw exceptionBuilder()
                  .evalError("stringIndentationMustMatchLastLine")
                  .withSourceSection(shrinkLeft(createSourceSection(token), actualIndent.length()))
                  .build();
            }
          } else {
            builder.append(text);
          }
          isLineStart = false;
        }
        case PklLexer.MLCharacterEscape -> {
          if (isLineStart && !commonIndent.isEmpty()) {
            throw exceptionBuilder()
                .evalError("stringIndentationMustMatchLastLine")
                .withSourceSection(createSourceSection(token))
                .build();
          }
          builder.append(parseCharacterEscapeSequence(token));
          isLineStart = false;
        }
        case PklLexer.MLUnicodeEscape -> {
          if (isLineStart && !commonIndent.isEmpty()) {
            throw exceptionBuilder()
                .evalError("stringIndentationMustMatchLastLine")
                .withSourceSection(createSourceSection(token))
                .build();
          }
          builder.appendCodePoint(parseUnicodeEscapeSequence(token));
          isLineStart = false;
        }
        default -> throw exceptionBuilder().unreachableCode().build();
      }
    }

    return builder.toString();
  }

  private ResolveDeclaredTypeNode doVisitTypeName(QualifiedIdentifierContext ctx) {
    var tokens = ctx.ts;
    return switch (tokens.size()) {
      case 1 -> {
        var token = tokens.get(0);
        yield new ResolveSimpleDeclaredTypeNode(
            createSourceSection(token), Identifier.get(token.getText()), isBaseModule);
      }
      case 2 -> {
        var token1 = tokens.get(0);
        var token2 = tokens.get(1);
        yield new ResolveQualifiedDeclaredTypeNode(
            createSourceSection(ctx),
            createSourceSection(token1),
            createSourceSection(token2),
            Identifier.localProperty(token1.getText()),
            Identifier.get(token2.getText()));
      }
      default ->
          throw exceptionBuilder()
              .evalError("invalidTypeName", ctx.getText())
              .withSourceSection(createSourceSection(ctx))
              .build();
    };
  }

  private void checkCommaSeparatedElements(
      ParserRuleContext ctx, List<? extends ParserRuleContext> elements, List<Token> separators) {

    if (elements.isEmpty() || separators.size() == elements.size() - 1) return;

    // determine location of missing separator
    // O(n^2) but only runs once a syntax error has been detected
    ParseTree prevChild = null;
    for (ParseTree child : ctx.children) {
      @SuppressWarnings("SuspiciousMethodCalls")
      var index = elements.indexOf(child);
      if (index > 0) { // 0 rather than -1 because no separator is expected before first element
        assert prevChild != null;
        if (!(prevChild instanceof TerminalNode terminalNode)
            || !separators.contains(terminalNode.getSymbol())) {
          var prevToken =
              prevChild instanceof TerminalNode terminalNode
                  ? terminalNode.getSymbol()
                  : ((ParserRuleContext) prevChild).getStop();
          throw exceptionBuilder()
              .evalError("missingCommaSeparator")
              .withSourceSection(source.createSection(prevToken.getStopIndex() + 1, 1))
              .build();
        }
      }
      prevChild = child;
    }

    throw exceptionBuilder().unreachableCode().build();
  }

  private void checkClosingDelimiter(
      @Nullable Token delimiter, String delimiterSymbol, Token tokenBeforeDelimiter) {

    if (delimiter == null) {
      throw missingDelimiter(delimiterSymbol, tokenBeforeDelimiter.getStopIndex() + 1);
    }
  }

  private void checkSingleLineStringDelimiters(Token openingDelimiter, Token closingDelimiter) {
    var closingText = closingDelimiter.getText();
    var lastChar = closingText.charAt(closingText.length() - 1);
    if (lastChar == '"' || lastChar == '#') return;

    assert lastChar == '\n' || lastChar == '\r';
    var openingText = openingDelimiter.getText();
    throw missingDelimiter(
        "\"" + openingText.substring(0, openingText.length() - 1), closingDelimiter.getStopIndex());
  }

  private VmException missingDelimiter(String delimiter, int charIndex) {
    return exceptionBuilder()
        .evalError("missingDelimiter", delimiter)
        .withSourceSection(source.createSection(charIndex, 0))
        .build();
  }

  private VmException wrongDelimiter(String expected, String actual, int charIndex) {
    return exceptionBuilder()
        .evalError("wrongDelimiter", expected, actual)
        .withSourceSection(source.createSection(charIndex, 0))
        .build();
  }

  private VmException danglingDelimiter(String delimiter, int charIndex) {
    return exceptionBuilder()
        .evalError("danglingDelimiter", delimiter)
        .withSourceSection(source.createSection(charIndex, 0))
        .build();
  }

  private @Nullable Scope getParentLexicalScope() {
    var parent = symbolTable.getCurrentScope().getLexicalScope().getParent();
    if (parent != null) return parent.getLexicalScope();
    return null;
  }

  private ConstLevel getConstLevel(int modifiers) {
    if (VmModifier.isConst(modifiers)) return ConstLevel.ALL;
    return symbolTable.getCurrentScope().getConstLevel();
  }
}
