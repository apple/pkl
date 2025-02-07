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

import org.pkl.core.parser.cst.Annotation;
import org.pkl.core.parser.cst.ArgumentList;
import org.pkl.core.parser.cst.Class;
import org.pkl.core.parser.cst.ClassBody;
import org.pkl.core.parser.cst.ClassMethod;
import org.pkl.core.parser.cst.ClassProperty;
import org.pkl.core.parser.cst.DocComment;
import org.pkl.core.parser.cst.Expr;
import org.pkl.core.parser.cst.Expr.AmendsExpr;
import org.pkl.core.parser.cst.Expr.BinaryOperatorExpr;
import org.pkl.core.parser.cst.Expr.BoolLiteralExpr;
import org.pkl.core.parser.cst.Expr.FloatLiteralExpr;
import org.pkl.core.parser.cst.Expr.FunctionLiteralExpr;
import org.pkl.core.parser.cst.Expr.IfExpr;
import org.pkl.core.parser.cst.Expr.ImportExpr;
import org.pkl.core.parser.cst.Expr.IntLiteralExpr;
import org.pkl.core.parser.cst.Expr.LetExpr;
import org.pkl.core.parser.cst.Expr.LogicalNotExpr;
import org.pkl.core.parser.cst.Expr.ModuleExpr;
import org.pkl.core.parser.cst.Expr.MultiLineStringLiteralExpr;
import org.pkl.core.parser.cst.Expr.NewExpr;
import org.pkl.core.parser.cst.Expr.NonNullExpr;
import org.pkl.core.parser.cst.Expr.NullLiteralExpr;
import org.pkl.core.parser.cst.Expr.OuterExpr;
import org.pkl.core.parser.cst.Expr.ParenthesizedExpr;
import org.pkl.core.parser.cst.Expr.QualifiedAccessExpr;
import org.pkl.core.parser.cst.Expr.ReadExpr;
import org.pkl.core.parser.cst.Expr.SingleLineStringLiteralExpr;
import org.pkl.core.parser.cst.Expr.SubscriptExpr;
import org.pkl.core.parser.cst.Expr.SuperAccessExpr;
import org.pkl.core.parser.cst.Expr.SuperSubscriptExpr;
import org.pkl.core.parser.cst.Expr.ThisExpr;
import org.pkl.core.parser.cst.Expr.ThrowExpr;
import org.pkl.core.parser.cst.Expr.TraceExpr;
import org.pkl.core.parser.cst.Expr.TypeCastExpr;
import org.pkl.core.parser.cst.Expr.TypeCheckExpr;
import org.pkl.core.parser.cst.Expr.UnaryMinusExpr;
import org.pkl.core.parser.cst.Expr.UnqualifiedAccessExpr;
import org.pkl.core.parser.cst.ExtendsOrAmendsClause;
import org.pkl.core.parser.cst.Identifier;
import org.pkl.core.parser.cst.ImportClause;
import org.pkl.core.parser.cst.Modifier;
import org.pkl.core.parser.cst.ModuleDecl;
import org.pkl.core.parser.cst.Node;
import org.pkl.core.parser.cst.ObjectBody;
import org.pkl.core.parser.cst.ObjectMember;
import org.pkl.core.parser.cst.ObjectMember.ForGenerator;
import org.pkl.core.parser.cst.ObjectMember.MemberPredicate;
import org.pkl.core.parser.cst.ObjectMember.ObjectElement;
import org.pkl.core.parser.cst.ObjectMember.ObjectEntry;
import org.pkl.core.parser.cst.ObjectMember.ObjectMethod;
import org.pkl.core.parser.cst.ObjectMember.ObjectProperty;
import org.pkl.core.parser.cst.ObjectMember.ObjectSpread;
import org.pkl.core.parser.cst.ObjectMember.WhenGenerator;
import org.pkl.core.parser.cst.Parameter;
import org.pkl.core.parser.cst.ParameterList;
import org.pkl.core.parser.cst.QualifiedIdentifier;
import org.pkl.core.parser.cst.ReplInput;
import org.pkl.core.parser.cst.StringConstant;
import org.pkl.core.parser.cst.StringConstantPart;
import org.pkl.core.parser.cst.StringPart;
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
  public T visitDefaultUnionType(DefaultUnionType type) {
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
  public T visitType(Type type) {
    return type.accept(this);
  }

  @Override
  public @Nullable T visitThisExpr(ThisExpr expr) {
    return null;
  }

  @Override
  public @Nullable T visitOuterExpr(OuterExpr expr) {
    return null;
  }

  @Override
  public T visitModuleExpr(ModuleExpr expr) {
    return null;
  }

  @Override
  public T visitNullLiteralExpr(NullLiteralExpr expr) {
    return null;
  }

  @Override
  public T visitBoolLiteralExpr(BoolLiteralExpr expr) {
    return null;
  }

  @Override
  public T visitIntLiteralExpr(IntLiteralExpr expr) {
    return null;
  }

  @Override
  public T visitFloatLiteralExpr(FloatLiteralExpr expr) {
    return null;
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
  public T visitExpr(Expr expr) {
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
  public T visitObjectMember(ObjectMember member) {
    return visitChildren(member);
  }

  @Override
  public T visitModule(org.pkl.core.parser.cst.Module module) {
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
    return null;
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
  public T visitStringConstantPart(StringConstantPart part) {
    return null;
  }

  @Override
  public T visitClassBody(ClassBody classBody) {
    return visitChildren(classBody);
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

  private @Nullable T visitChildren(Node node) {
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

  protected @Nullable T defaultValue() {
    return null;
  }

  protected @Nullable T aggregateResult(@Nullable T result, @Nullable T nextResult) {
    return nextResult;
  }
}
