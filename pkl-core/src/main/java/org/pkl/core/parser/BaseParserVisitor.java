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

import org.pkl.core.parser.syntax.Annotation;
import org.pkl.core.parser.syntax.ArgumentList;
import org.pkl.core.parser.syntax.Class;
import org.pkl.core.parser.syntax.ClassBody;
import org.pkl.core.parser.syntax.ClassMethod;
import org.pkl.core.parser.syntax.ClassProperty;
import org.pkl.core.parser.syntax.DocComment;
import org.pkl.core.parser.syntax.Expr.AmendsExpr;
import org.pkl.core.parser.syntax.Expr.BinaryOperatorExpr;
import org.pkl.core.parser.syntax.Expr.BoolLiteralExpr;
import org.pkl.core.parser.syntax.Expr.FloatLiteralExpr;
import org.pkl.core.parser.syntax.Expr.FunctionLiteralExpr;
import org.pkl.core.parser.syntax.Expr.IfExpr;
import org.pkl.core.parser.syntax.Expr.ImportExpr;
import org.pkl.core.parser.syntax.Expr.IntLiteralExpr;
import org.pkl.core.parser.syntax.Expr.LetExpr;
import org.pkl.core.parser.syntax.Expr.LogicalNotExpr;
import org.pkl.core.parser.syntax.Expr.ModuleExpr;
import org.pkl.core.parser.syntax.Expr.MultiLineStringLiteralExpr;
import org.pkl.core.parser.syntax.Expr.NewExpr;
import org.pkl.core.parser.syntax.Expr.NonNullExpr;
import org.pkl.core.parser.syntax.Expr.NullLiteralExpr;
import org.pkl.core.parser.syntax.Expr.OuterExpr;
import org.pkl.core.parser.syntax.Expr.ParenthesizedExpr;
import org.pkl.core.parser.syntax.Expr.QualifiedAccessExpr;
import org.pkl.core.parser.syntax.Expr.ReadExpr;
import org.pkl.core.parser.syntax.Expr.SingleLineStringLiteralExpr;
import org.pkl.core.parser.syntax.Expr.SubscriptExpr;
import org.pkl.core.parser.syntax.Expr.SuperAccessExpr;
import org.pkl.core.parser.syntax.Expr.SuperSubscriptExpr;
import org.pkl.core.parser.syntax.Expr.ThisExpr;
import org.pkl.core.parser.syntax.Expr.ThrowExpr;
import org.pkl.core.parser.syntax.Expr.TraceExpr;
import org.pkl.core.parser.syntax.Expr.TypeCastExpr;
import org.pkl.core.parser.syntax.Expr.TypeCheckExpr;
import org.pkl.core.parser.syntax.Expr.UnaryMinusExpr;
import org.pkl.core.parser.syntax.Expr.UnqualifiedAccessExpr;
import org.pkl.core.parser.syntax.ExtendsOrAmendsClause;
import org.pkl.core.parser.syntax.Identifier;
import org.pkl.core.parser.syntax.ImportClause;
import org.pkl.core.parser.syntax.Keyword;
import org.pkl.core.parser.syntax.Modifier;
import org.pkl.core.parser.syntax.ModuleDecl;
import org.pkl.core.parser.syntax.Node;
import org.pkl.core.parser.syntax.ObjectBody;
import org.pkl.core.parser.syntax.ObjectMember.ForGenerator;
import org.pkl.core.parser.syntax.ObjectMember.MemberPredicate;
import org.pkl.core.parser.syntax.ObjectMember.ObjectElement;
import org.pkl.core.parser.syntax.ObjectMember.ObjectEntry;
import org.pkl.core.parser.syntax.ObjectMember.ObjectMethod;
import org.pkl.core.parser.syntax.ObjectMember.ObjectProperty;
import org.pkl.core.parser.syntax.ObjectMember.ObjectSpread;
import org.pkl.core.parser.syntax.ObjectMember.WhenGenerator;
import org.pkl.core.parser.syntax.Parameter;
import org.pkl.core.parser.syntax.ParameterList;
import org.pkl.core.parser.syntax.QualifiedIdentifier;
import org.pkl.core.parser.syntax.ReplInput;
import org.pkl.core.parser.syntax.StringConstant;
import org.pkl.core.parser.syntax.StringPart;
import org.pkl.core.parser.syntax.Type.ConstrainedType;
import org.pkl.core.parser.syntax.Type.DeclaredType;
import org.pkl.core.parser.syntax.Type.FunctionType;
import org.pkl.core.parser.syntax.Type.ModuleType;
import org.pkl.core.parser.syntax.Type.NothingType;
import org.pkl.core.parser.syntax.Type.NullableType;
import org.pkl.core.parser.syntax.Type.ParenthesizedType;
import org.pkl.core.parser.syntax.Type.StringConstantType;
import org.pkl.core.parser.syntax.Type.UnionType;
import org.pkl.core.parser.syntax.Type.UnknownType;
import org.pkl.core.parser.syntax.TypeAlias;
import org.pkl.core.parser.syntax.TypeAnnotation;
import org.pkl.core.parser.syntax.TypeArgumentList;
import org.pkl.core.parser.syntax.TypeParameter;
import org.pkl.core.parser.syntax.TypeParameterList;

public abstract class BaseParserVisitor<T> implements ParserVisitor<T> {

  @Override
  public T visitUnknownType(UnknownType type) {
    return defaultValue();
  }

  @Override
  public T visitNothingType(NothingType type) {
    return defaultValue();
  }

  @Override
  public T visitModuleType(ModuleType type) {
    return defaultValue();
  }

  @Override
  public T visitStringConstantType(StringConstantType type) {
    return visitChildren(type);
  }

  @Override
  public T visitDeclaredType(DeclaredType type) {
    return visitChildren(type);
  }

  @Override
  public T visitParenthesizedType(ParenthesizedType type) {
    return visitChildren(type);
  }

  @Override
  public T visitNullableType(NullableType type) {
    return visitChildren(type);
  }

  @Override
  public T visitConstrainedType(ConstrainedType type) {
    return visitChildren(type);
  }

  @Override
  public T visitUnionType(UnionType type) {
    return visitChildren(type);
  }

  @Override
  public T visitFunctionType(FunctionType type) {
    return visitChildren(type);
  }

  @Override
  public T visitThisExpr(ThisExpr expr) {
    return defaultValue();
  }

  @Override
  public T visitOuterExpr(OuterExpr expr) {
    return defaultValue();
  }

  @Override
  public T visitModuleExpr(ModuleExpr expr) {
    return defaultValue();
  }

  @Override
  public T visitNullLiteralExpr(NullLiteralExpr expr) {
    return defaultValue();
  }

  @Override
  public T visitBoolLiteralExpr(BoolLiteralExpr expr) {
    return defaultValue();
  }

  @Override
  public T visitIntLiteralExpr(IntLiteralExpr expr) {
    return defaultValue();
  }

  @Override
  public T visitFloatLiteralExpr(FloatLiteralExpr expr) {
    return defaultValue();
  }

  @Override
  public T visitThrowExpr(ThrowExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitTraceExpr(TraceExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitImportExpr(ImportExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitReadExpr(ReadExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitUnqualifiedAccessExpr(UnqualifiedAccessExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitStringConstant(StringConstant expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitSingleLineStringLiteralExpr(SingleLineStringLiteralExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitMultiLineStringLiteralExpr(MultiLineStringLiteralExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitNewExpr(NewExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitAmendsExpr(AmendsExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitSuperAccessExpr(SuperAccessExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitSuperSubscriptExpr(SuperSubscriptExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitQualifiedAccessExpr(QualifiedAccessExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitSubscriptExpr(SubscriptExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitNonNullExpr(NonNullExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitUnaryMinusExpr(UnaryMinusExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitLogicalNotExpr(LogicalNotExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitBinaryOperatorExpr(BinaryOperatorExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitTypeCheckExpr(TypeCheckExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitTypeCastExpr(TypeCastExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitIfExpr(IfExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitLetExpr(LetExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitFunctionLiteralExpr(FunctionLiteralExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitParenthesizedExpr(ParenthesizedExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitObjectProperty(ObjectProperty member) {
    return visitChildren(member);
  }

  @Override
  public T visitObjectMethod(ObjectMethod member) {
    return visitChildren(member);
  }

  @Override
  public T visitMemberPredicate(MemberPredicate member) {
    return visitChildren(member);
  }

  @Override
  public T visitObjectElement(ObjectElement member) {
    return visitChildren(member);
  }

  @Override
  public T visitObjectEntry(ObjectEntry member) {
    return visitChildren(member);
  }

  @Override
  public T visitObjectSpread(ObjectSpread member) {
    return visitChildren(member);
  }

  @Override
  public T visitWhenGenerator(WhenGenerator member) {
    return visitChildren(member);
  }

  @Override
  public T visitForGenerator(ForGenerator member) {
    return visitChildren(member);
  }

  @Override
  public T visitModule(org.pkl.core.parser.syntax.Module module) {
    return visitChildren(module);
  }

  @Override
  public T visitModuleDecl(ModuleDecl decl) {
    return visitChildren(decl);
  }

  @Override
  public T visitExtendsOrAmendsClause(ExtendsOrAmendsClause decl) {
    return visitChildren(decl);
  }

  @Override
  public T visitImportClause(ImportClause imp) {
    return visitChildren(imp);
  }

  @Override
  public T visitClass(Class clazz) {
    return visitChildren(clazz);
  }

  @Override
  public T visitModifier(Modifier modifier) {
    return defaultValue();
  }

  @Override
  public T visitClassProperty(ClassProperty prop) {
    return visitChildren(prop);
  }

  @Override
  public T visitClassMethod(ClassMethod method) {
    return visitChildren(method);
  }

  @Override
  public T visitTypeAlias(TypeAlias typeAlias) {
    return visitChildren(typeAlias);
  }

  @Override
  public T visitAnnotation(Annotation annotation) {
    return visitChildren(annotation);
  }

  @Override
  public T visitParameter(Parameter param) {
    return visitChildren(param);
  }

  @Override
  public T visitParameterList(ParameterList paramList) {
    return visitChildren(paramList);
  }

  @Override
  public T visitTypeParameterList(TypeParameterList typeParameterList) {
    return visitChildren(typeParameterList);
  }

  @Override
  public T visitTypeAnnotation(TypeAnnotation typeAnnotation) {
    return visitChildren(typeAnnotation);
  }

  @Override
  public T visitArgumentList(ArgumentList argumentList) {
    return visitChildren(argumentList);
  }

  @Override
  public T visitStringPart(StringPart part) {
    return visitChildren(part);
  }

  @Override
  public T visitClassBody(ClassBody classBody) {
    return visitChildren(classBody);
  }

  @Override
  public T visitDocComment(DocComment docComment) {
    return defaultValue();
  }

  @Override
  public T visitIdentifier(Identifier identifier) {
    return defaultValue();
  }

  @Override
  public T visitQualifiedIdentifier(QualifiedIdentifier qualifiedIdentifier) {
    return visitChildren(qualifiedIdentifier);
  }

  @Override
  public T visitObjectBody(ObjectBody objectBody) {
    return visitChildren(objectBody);
  }

  @Override
  public T visitTypeParameter(TypeParameter typeParameter) {
    return visitChildren(typeParameter);
  }

  @Override
  public T visitReplInput(ReplInput replInput) {
    return visitChildren(replInput);
  }

  @Override
  public T visitKeyword(Keyword keyword) {
    return defaultValue();
  }

  @Override
  public T visitTypeArgumentList(TypeArgumentList typeArgumentList) {
    return visitChildren(typeArgumentList);
  }

  private T visitChildren(Node node) {
    T result = defaultValue();
    var children = node.children();
    if (children == null) return result;
    for (var child : children) {
      if (child != null) {
        result = aggregateResult(result, child.accept(this));
      }
    }
    return result;
  }

  protected abstract T defaultValue();

  protected T aggregateResult(T result, T nextResult) {
    return nextResult;
  }
}
