/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.parser;

import java.util.List;
import org.pkl.core.parser.cst.Annotation;
import org.pkl.core.parser.cst.ArgumentList;
import org.pkl.core.parser.cst.Class;
import org.pkl.core.parser.cst.ClassBody;
import org.pkl.core.parser.cst.ClassMethod;
import org.pkl.core.parser.cst.ClassProperty;
import org.pkl.core.parser.cst.DocComment;
import org.pkl.core.parser.cst.Expr;
import org.pkl.core.parser.cst.Expr.Amends;
import org.pkl.core.parser.cst.Expr.BinaryOp;
import org.pkl.core.parser.cst.Expr.BoolLiteral;
import org.pkl.core.parser.cst.Expr.FloatLiteral;
import org.pkl.core.parser.cst.Expr.FunctionLiteral;
import org.pkl.core.parser.cst.Expr.If;
import org.pkl.core.parser.cst.Expr.ImportExpr;
import org.pkl.core.parser.cst.Expr.IntLiteral;
import org.pkl.core.parser.cst.Expr.Let;
import org.pkl.core.parser.cst.Expr.LogicalNot;
import org.pkl.core.parser.cst.Expr.Module;
import org.pkl.core.parser.cst.Expr.MultiLineStringLiteral;
import org.pkl.core.parser.cst.Expr.New;
import org.pkl.core.parser.cst.Expr.NonNull;
import org.pkl.core.parser.cst.Expr.NullLiteral;
import org.pkl.core.parser.cst.Expr.Outer;
import org.pkl.core.parser.cst.Expr.Parenthesized;
import org.pkl.core.parser.cst.Expr.QualifiedAccess;
import org.pkl.core.parser.cst.Expr.Read;
import org.pkl.core.parser.cst.Expr.SingleLineStringLiteral;
import org.pkl.core.parser.cst.Expr.Subscript;
import org.pkl.core.parser.cst.Expr.SuperAccess;
import org.pkl.core.parser.cst.Expr.SuperSubscript;
import org.pkl.core.parser.cst.Expr.This;
import org.pkl.core.parser.cst.Expr.Throw;
import org.pkl.core.parser.cst.Expr.Trace;
import org.pkl.core.parser.cst.Expr.TypeCast;
import org.pkl.core.parser.cst.Expr.TypeCheck;
import org.pkl.core.parser.cst.Expr.UnaryMinus;
import org.pkl.core.parser.cst.Expr.UnqualifiedAccess;
import org.pkl.core.parser.cst.ExtendsOrAmendsDecl;
import org.pkl.core.parser.cst.Identifier;
import org.pkl.core.parser.cst.Import;
import org.pkl.core.parser.cst.Modifier;
import org.pkl.core.parser.cst.ModuleDecl;
import org.pkl.core.parser.cst.Node;
import org.pkl.core.parser.cst.ObjectBody;
import org.pkl.core.parser.cst.ObjectMemberNode;
import org.pkl.core.parser.cst.ObjectMemberNode.ForGenerator;
import org.pkl.core.parser.cst.ObjectMemberNode.MemberPredicate;
import org.pkl.core.parser.cst.ObjectMemberNode.ObjectElement;
import org.pkl.core.parser.cst.ObjectMemberNode.ObjectEntry;
import org.pkl.core.parser.cst.ObjectMemberNode.ObjectMethod;
import org.pkl.core.parser.cst.ObjectMemberNode.ObjectProperty;
import org.pkl.core.parser.cst.ObjectMemberNode.ObjectSpread;
import org.pkl.core.parser.cst.ObjectMemberNode.WhenGenerator;
import org.pkl.core.parser.cst.Parameter;
import org.pkl.core.parser.cst.Parameter.TypedIdentifier;
import org.pkl.core.parser.cst.ParameterList;
import org.pkl.core.parser.cst.QualifiedIdentifier;
import org.pkl.core.parser.cst.ReplInput;
import org.pkl.core.parser.cst.StringConstant;
import org.pkl.core.parser.cst.StringConstantPart;
import org.pkl.core.parser.cst.StringPart;
import org.pkl.core.parser.cst.StringPart.StringConstantParts;
import org.pkl.core.parser.cst.StringPart.StringInterpolation;
import org.pkl.core.parser.cst.Type;
import org.pkl.core.parser.cst.Type.ConstrainedType;
import org.pkl.core.parser.cst.Type.DeclaredType;
import org.pkl.core.parser.cst.Type.DefaultUnionType;
import org.pkl.core.parser.cst.Type.FunctionType;
import org.pkl.core.parser.cst.Type.ModuleType;
import org.pkl.core.parser.cst.Type.NothingType;
import org.pkl.core.parser.cst.Type.NullableType;
import org.pkl.core.parser.cst.Type.ParenthesizedType;
import org.pkl.core.parser.cst.Type.StringConstantType;
import org.pkl.core.parser.cst.Type.UnionType;
import org.pkl.core.parser.cst.Type.UnknownType;
import org.pkl.core.parser.cst.TypeAlias;
import org.pkl.core.parser.cst.TypeAnnotation;
import org.pkl.core.parser.cst.TypeParameter;
import org.pkl.core.parser.cst.TypeParameterList;
import org.pkl.core.util.Nullable;

@SuppressWarnings("DuplicatedCode")
public abstract class BaseParserVisitor<T> implements ParserVisitor<T> {

  @Override
  public @Nullable T visitUnknownType(UnknownType type) {
    return null;
  }

  @Override
  public @Nullable T visitNothingType(NothingType type) {
    return null;
  }

  @Override
  public @Nullable T visitModuleType(ModuleType type) {
    return null;
  }

  @Override
  public T visitStringConstantType(StringConstantType type) {
    return visitStringConstant(type.getStr());
  }

  @Override
  public T visitDeclaredType(DeclaredType type) {
    return aggregateResult(visitQualifiedIdentifier(type.getName()), visitNodes(type.getArgs()));
  }

  @Override
  public T visitParenthesizedType(ParenthesizedType type) {
    return visitType(type.getType());
  }

  @Override
  public T visitNullableType(NullableType type) {
    return visitType(type.getType());
  }

  @Override
  public T visitConstrainedType(ConstrainedType type) {
    var res = visitType(type.getType());
    return aggregateResult(res, visitNodes(type.getExprs()));
  }

  @Override
  public T visitDefaultUnionType(DefaultUnionType type) {
    return visitType(type.getType());
  }

  @Override
  public T visitUnionType(UnionType type) {
    return aggregateResult(visitType(type.getLeft()), visitType(type.getRight()));
  }

  @Override
  public T visitFunctionType(FunctionType type) {
    return aggregateResult(visitNodes(type.getArgs()), visitType(type.getRet()));
  }

  @Override
  public T visitType(Type type) {
    return type.accept(this);
  }

  @Override
  public @Nullable T visitThisExpr(This expr) {
    return null;
  }

  @Override
  public @Nullable T visitOuterExpr(Outer expr) {
    return null;
  }

  @Override
  public T visitModuleExpr(Module expr) {
    return null;
  }

  @Override
  public T visitNullLiteralExpr(NullLiteral expr) {
    return null;
  }

  @Override
  public T visitBoolLiteralExpr(BoolLiteral expr) {
    return null;
  }

  @Override
  public T visitIntLiteralExpr(IntLiteral expr) {
    return null;
  }

  @Override
  public T visitFloatLiteralExpr(FloatLiteral expr) {
    return null;
  }

  @Override
  public T visitThrowExpr(Throw expr) {
    return visitExpr(expr.getExpr());
  }

  @Override
  public T visitTraceExpr(Trace expr) {
    return visitExpr(expr.getExpr());
  }

  @Override
  public T visitImportExpr(ImportExpr expr) {
    return visitStringConstant(expr.getImportStr());
  }

  @Override
  public T visitReadExpr(Read expr) {
    return visitExpr(expr.getExpr());
  }

  @Override
  public T visitUnqualifiedAccessExpr(UnqualifiedAccess expr) {
    var argList = expr.getArgumentList();
    if (argList != null) {
      return aggregateResult(visitIdentifier(expr.getIdentifier()), visitArgumentList(argList));
    }
    return visitIdentifier(expr.getIdentifier());
  }

  @Override
  public T visitStringConstant(StringConstant expr) {
    return visitNodes(expr.getStrParts().getParts());
  }

  @Override
  public T visitSingleLineStringLiteral(SingleLineStringLiteral expr) {
    return visitNodes(expr.getParts());
  }

  @Override
  public T visitMultiLineStringLiteral(MultiLineStringLiteral expr) {
    return visitNodes(expr.getParts());
  }

  @Override
  public T visitNewExpr(New expr) {
    var type = expr.getType();
    if (type != null) {
      return aggregateResult(visitType(type), visitObjectBody(expr.getBody()));
    }
    return visitObjectBody(expr.getBody());
  }

  @Override
  public T visitAmendsExpr(Amends expr) {
    return aggregateResult(visitExpr(expr.getExpr()), visitObjectBody(expr.getBody()));
  }

  @Override
  public T visitSuperAccessExpr(SuperAccess expr) {
    var args = expr.getArgumentList();
    if (args != null) {
      return aggregateResult(visitIdentifier(expr.getIdentifier()), visitArgumentList(args));
    }
    return visitIdentifier(expr.getIdentifier());
  }

  @Override
  public T visitSuperSubscriptExpr(SuperSubscript expr) {
    return visitExpr(expr.getArg());
  }

  @Override
  public T visitQualifiedAccessExpr(QualifiedAccess expr) {
    var res = aggregateResult(visitExpr(expr.getExpr()), visitIdentifier(expr.getIdentifier()));
    if (expr.getArgumentList() != null) {
      return aggregateResult(res, visitArgumentList(expr.getArgumentList()));
    }
    return res;
  }

  @Override
  public T visitSubscriptExpr(Subscript expr) {
    return aggregateResult(visitExpr(expr.getExpr()), visitExpr(expr.getArg()));
  }

  @Override
  public T visitNonNullExpr(NonNull expr) {
    return visitExpr(expr.getExpr());
  }

  @Override
  public T visitUnaryMinusExpr(UnaryMinus expr) {
    return visitExpr(expr.getExpr());
  }

  @Override
  public T visitLogicalNotExpr(LogicalNot expr) {
    return visitExpr(expr.getExpr());
  }

  @Override
  public T visitBinaryOpExpr(BinaryOp expr) {
    return aggregateResult(visitExpr(expr.getLeft()), visitExpr(expr.getRight()));
  }

  @Override
  public T visitTypeCheckExpr(TypeCheck expr) {
    return aggregateResult(visitExpr(expr.getExpr()), visitType(expr.getType()));
  }

  @Override
  public T visitTypeCastExpr(TypeCast expr) {
    return aggregateResult(visitExpr(expr.getExpr()), visitType(expr.getType()));
  }

  @Override
  public T visitIfExpr(If expr) {
    return aggregateResult(
        aggregateResult(visitExpr(expr.getCond()), visitExpr(expr.getThen())),
        visitExpr(expr.getEls()));
  }

  @Override
  public T visitLetExpr(Let expr) {
    return aggregateResult(
        aggregateResult(visitParameter(expr.getParameter()), visitExpr(expr.getBindingExpr())),
        visitExpr(expr.getExpr()));
  }

  @Override
  public T visitFunctionLiteralExpr(FunctionLiteral expr) {
    return aggregateResult(visitParameterList(expr.getParameterList()), visitExpr(expr.getExpr()));
  }

  @Override
  public T visitParenthesizedExpr(Parenthesized expr) {
    return visitExpr(expr.getExpr());
  }

  @Override
  public T visitExpr(Expr expr) {
    return expr.accept(this);
  }

  @Override
  public T visitObjectProperty(ObjectProperty member) {
    var res = visitNodes(member.getModifiers());
    res = aggregateResult(res, visitIdentifier(member.getIdentifier()));
    if (member.getTypeAnnotation() != null) {
      res = aggregateResult(res, visitTypeAnnotation(member.getTypeAnnotation()));
    }
    if (member.getExpr() != null) {
      res = aggregateResult(res, visitExpr(member.getExpr()));
    }
    if (member.getBodyList() != null) {
      res = aggregateResult(res, visitNodes(member.getBodyList()));
    }
    return res;
  }

  @Override
  public T visitObjectMethod(ObjectMethod member) {
    var res = visitNodes(member.getModifiers());
    res = aggregateResult(res, visitIdentifier(member.getIdentifier()));
    if (member.getTypeParameterList() != null) {
      res = aggregateResult(res, visitTypeParameterList(member.getTypeParameterList()));
    }
    res = aggregateResult(res, visitParameterList(member.getParamList()));
    if (member.getTypeAnnotation() != null) {
      res = aggregateResult(res, visitTypeAnnotation(member.getTypeAnnotation()));
    }
    return aggregateResult(res, visitExpr(member.getExpr()));
  }

  @Override
  public T visitMemberPredicate(MemberPredicate member) {
    var res = visitExpr(member.getPred());
    if (member.getExpr() != null) {
      res = aggregateResult(res, visitExpr(member.getExpr()));
    }
    if (member.getBodyList() != null) {
      res = aggregateResult(res, visitNodes(member.getBodyList()));
    }
    return res;
  }

  @Override
  public T visitObjectElement(ObjectElement member) {
    return visitExpr(member.getExpr());
  }

  @Override
  public T visitObjectEntry(ObjectEntry member) {
    var res = visitExpr(member.getKey());
    if (member.getValue() != null) {
      res = aggregateResult(res, visitExpr(member.getValue()));
    }
    if (member.getBodyList() != null) {
      res = aggregateResult(res, visitNodes(member.getBodyList()));
    }
    return res;
  }

  @Override
  public T visitObjectSpread(ObjectSpread member) {
    return visitExpr(member.getExpr());
  }

  @Override
  public T visitWhenGenerator(WhenGenerator member) {
    var res = aggregateResult(visitExpr(member.getCond()), visitObjectBody(member.getBody()));
    if (member.getElseClause() != null) {
      return aggregateResult(res, visitObjectBody(member.getElseClause()));
    }
    return res;
  }

  @Override
  public T visitForGenerator(ForGenerator member) {
    var res = visitParameter(member.getP1());
    if (member.getP2() != null) {
      res = aggregateResult(res, visitParameter(member.getP2()));
    }
    res = aggregateResult(res, visitExpr(member.getExpr()));
    return aggregateResult(res, visitObjectBody(member.getBody()));
  }

  @Override
  public T visitObjectMember(ObjectMemberNode member) {
    return member.accept(this);
  }

  @Override
  public T visitModule(org.pkl.core.parser.cst.Module module) {
    T res = null;
    if (module.getDecl() != null) {
      res = visitModuleDecl(module.getDecl());
    }
    res = aggregateResult(res, visitNodes(module.getImports()));
    res = aggregateResult(res, visitNodes(module.getClasses()));
    res = aggregateResult(res, visitNodes(module.getTypeAliases()));
    res = aggregateResult(res, visitNodes(module.getProperties()));
    return aggregateResult(res, visitNodes(module.getMethods()));
  }

  @Override
  public T visitModuleDecl(ModuleDecl decl) {
    T res = null;
    if (decl.getDocComment() != null) {
      res = visitDocComment(decl.getDocComment());
    }
    res = aggregateResult(res, visitNodes(decl.getAnnotations()));
    res = aggregateResult(res, visitNodes(decl.getModifiers()));
    if (decl.getName() != null) {
      res = aggregateResult(res, visitQualifiedIdentifier(decl.getName()));
    }
    if (decl.getExtendsOrAmendsDecl() != null) {
      res = aggregateResult(res, visitExtendsOrAmendsDecl(decl.getExtendsOrAmendsDecl()));
    }
    return res;
  }

  @Override
  public T visitExtendsOrAmendsDecl(ExtendsOrAmendsDecl decl) {
    return visitStringConstant(decl.getUrl());
  }

  @Override
  public T visitImport(Import imp) {
    var res = visitStringConstant(imp.getImportStr());
    if (imp.getAlias() != null) {
      return aggregateResult(res, visitIdentifier(imp.getAlias()));
    }
    return res;
  }

  @Override
  public T visitClass(Class clazz) {
    T res = null;
    if (clazz.getDocComment() != null) {
      res = visitDocComment(clazz.getDocComment());
    }
    res = aggregateResult(res, visitNodes(clazz.getAnnotations()));
    res = aggregateResult(res, visitNodes(clazz.getModifiers()));
    res = aggregateResult(res, visitIdentifier(clazz.getName()));
    if (clazz.getTypeParameterList() != null) {
      res = visitTypeParameterList(clazz.getTypeParameterList());
    }
    if (clazz.getSuperClass() != null) {
      res = visitType(clazz.getSuperClass());
    }
    if (clazz.getBody() != null) {
      res = visitClassBody(clazz.getBody());
    }
    return res;
  }

  @Override
  public T visitModifier(Modifier modifier) {
    return null;
  }

  @Override
  public T visitClassProperty(ClassProperty prop) {
    T res = null;
    if (prop.getDocComment() != null) {
      res = visitDocComment(prop.getDocComment());
    }
    res = aggregateResult(res, visitNodes(prop.getAnnotations()));
    res = aggregateResult(res, visitNodes(prop.getModifiers()));
    res = aggregateResult(res, visitIdentifier(prop.getName()));
    if (prop.getTypeAnnotation() != null) {
      res = aggregateResult(res, visitTypeAnnotation(prop.getTypeAnnotation()));
    }
    if (prop.getExpr() != null) {
      res = visitExpr(prop.getExpr());
    }
    if (prop.getBodyList() != null) {
      res = visitNodes(prop.getBodyList());
    }
    return res;
  }

  @Override
  public T visitClassMethod(ClassMethod method) {
    T res = null;
    if (method.getDocComment() != null) {
      res = visitDocComment(method.getDocComment());
    }
    res = aggregateResult(res, visitNodes(method.getAnnotations()));
    res = aggregateResult(res, visitNodes(method.getModifiers()));
    res = aggregateResult(res, visitIdentifier(method.getName()));
    if (method.getTypeParameterList() != null) {
      res = aggregateResult(res, visitTypeParameterList(method.getTypeParameterList()));
    }
    res = aggregateResult(res, visitParameterList(method.getParameterList()));
    if (method.getTypeAnnotation() != null) {
      res = aggregateResult(res, visitTypeAnnotation(method.getTypeAnnotation()));
    }
    if (method.getExpr() != null) {
      res = aggregateResult(res, visitExpr(method.getExpr()));
    }
    return res;
  }

  @Override
  public T visitTypeAlias(TypeAlias typeAlias) {
    T res = null;
    if (typeAlias.getDocComment() != null) {
      res = visitDocComment(typeAlias.getDocComment());
    }
    res = aggregateResult(res, visitNodes(typeAlias.getAnnotations()));
    res = aggregateResult(res, visitNodes(typeAlias.getModifiers()));
    res = aggregateResult(res, visitIdentifier(typeAlias.getName()));
    if (typeAlias.getTypeParameterList() != null) {
      res = aggregateResult(res, visitTypeParameterList(typeAlias.getTypeParameterList()));
    }
    return aggregateResult(res, visitType(typeAlias.getType()));
  }

  @Override
  public T visitAnnotation(Annotation annotation) {
    if (annotation.getBody() != null) {
      return aggregateResult(
          visitType(annotation.getType()), visitObjectBody(annotation.getBody()));
    }
    return visitType(annotation.getType());
  }

  @Override
  public T visitParameter(Parameter param) {
    if (param instanceof TypedIdentifier typedIdentifier) {
      if (typedIdentifier.getTypeAnnotation() != null) {
        return aggregateResult(
            visitIdentifier(typedIdentifier.getIdentifier()),
            visitTypeAnnotation(typedIdentifier.getTypeAnnotation()));
      }
      return visitIdentifier(typedIdentifier.getIdentifier());
    }
    return null;
  }

  @Override
  public T visitParameterList(ParameterList paramList) {
    return visitNodes(paramList.getParameters());
  }

  @Override
  public T visitTypeParameterList(TypeParameterList typeParameterList) {
    return visitNodes(typeParameterList.getParameters());
  }

  @Override
  public T visitTypeAnnotation(TypeAnnotation typeAnnotation) {
    return visitType(typeAnnotation.getType());
  }

  @Override
  public T visitArgumentList(ArgumentList argumentList) {
    return visitNodes(argumentList.getArguments());
  }

  @Override
  public T visitStringPart(StringPart part) {
    if (part instanceof StringInterpolation stringInterpolation) {
      return visitExpr(stringInterpolation.getExpr());
    } else if (part instanceof StringConstantParts stringConstantParts) {
      return visitNodes(stringConstantParts.getParts());
    }
    return null;
  }

  @Override
  public T visitStringConstantPart(StringConstantPart part) {
    return null;
  }

  @Override
  public T visitClassBody(ClassBody classBody) {
    return aggregateResult(
        visitNodes(classBody.getProperties()), visitNodes(classBody.getMethods()));
  }

  @Override
  public T visitDocComment(DocComment docComment) {
    return null;
  }

  @Override
  public T visitIdentifier(Identifier identifier) {
    return null;
  }

  @Override
  public T visitQualifiedIdentifier(QualifiedIdentifier qualifiedIdentifier) {
    return visitNodes(qualifiedIdentifier.getIdentifiers());
  }

  @Override
  public T visitObjectBody(ObjectBody objectBody) {
    return aggregateResult(
        visitNodes(objectBody.getParameters()), visitNodes(objectBody.getMembers()));
  }

  @Override
  public T visitTypeParameter(TypeParameter typeParameter) {
    return visitIdentifier(typeParameter.getIdentifier());
  }

  @Override
  public T visitReplInput(ReplInput replInput) {
    return visitNodes(replInput.getNodes());
  }

  private @Nullable T visitNodes(List<? extends Node> nodes) {
    T result = defaultValue();
    for (var child : nodes) {
      result = aggregateResult(result, child.accept(this));
    }
    return result;
  }

  protected @Nullable T defaultValue() {
    return null;
  }

  protected @Nullable T aggregateResult(@Nullable T result, @Nullable T nextResult) {
    return nextResult;
  }
}
