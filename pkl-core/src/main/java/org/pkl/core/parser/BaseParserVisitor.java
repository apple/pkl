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

import org.pkl.core.parser.ast.Annotation;
import org.pkl.core.parser.ast.ArgumentList;
import org.pkl.core.parser.ast.Class;
import org.pkl.core.parser.ast.ClassBody;
import org.pkl.core.parser.ast.ClassMethod;
import org.pkl.core.parser.ast.ClassProperty;
import org.pkl.core.parser.ast.DocComment;
import org.pkl.core.parser.ast.Expr.AmendsExpr;
import org.pkl.core.parser.ast.Expr.BinaryOperatorExpr;
import org.pkl.core.parser.ast.Expr.BoolLiteralExpr;
import org.pkl.core.parser.ast.Expr.FloatLiteralExpr;
import org.pkl.core.parser.ast.Expr.FunctionLiteralExpr;
import org.pkl.core.parser.ast.Expr.IfExpr;
import org.pkl.core.parser.ast.Expr.ImportExpr;
import org.pkl.core.parser.ast.Expr.IntLiteralExpr;
import org.pkl.core.parser.ast.Expr.LetExpr;
import org.pkl.core.parser.ast.Expr.LogicalNotExpr;
import org.pkl.core.parser.ast.Expr.ModuleExpr;
import org.pkl.core.parser.ast.Expr.MultiLineStringLiteralExpr;
import org.pkl.core.parser.ast.Expr.NewExpr;
import org.pkl.core.parser.ast.Expr.NonNullExpr;
import org.pkl.core.parser.ast.Expr.NullLiteralExpr;
import org.pkl.core.parser.ast.Expr.OuterExpr;
import org.pkl.core.parser.ast.Expr.ParenthesizedExpr;
import org.pkl.core.parser.ast.Expr.QualifiedAccessExpr;
import org.pkl.core.parser.ast.Expr.ReadExpr;
import org.pkl.core.parser.ast.Expr.SingleLineStringLiteralExpr;
import org.pkl.core.parser.ast.Expr.SubscriptExpr;
import org.pkl.core.parser.ast.Expr.SuperAccessExpr;
import org.pkl.core.parser.ast.Expr.SuperSubscriptExpr;
import org.pkl.core.parser.ast.Expr.ThisExpr;
import org.pkl.core.parser.ast.Expr.ThrowExpr;
import org.pkl.core.parser.ast.Expr.TraceExpr;
import org.pkl.core.parser.ast.Expr.TypeCastExpr;
import org.pkl.core.parser.ast.Expr.TypeCheckExpr;
import org.pkl.core.parser.ast.Expr.UnaryMinusExpr;
import org.pkl.core.parser.ast.Expr.UnqualifiedAccessExpr;
import org.pkl.core.parser.ast.ExtendsOrAmendsClause;
import org.pkl.core.parser.ast.Identifier;
import org.pkl.core.parser.ast.ImportClause;
import org.pkl.core.parser.ast.Modifier;
import org.pkl.core.parser.ast.ModuleDecl;
import org.pkl.core.parser.ast.Node;
import org.pkl.core.parser.ast.ObjectBody;
import org.pkl.core.parser.ast.ObjectMember.ForGenerator;
import org.pkl.core.parser.ast.ObjectMember.MemberPredicate;
import org.pkl.core.parser.ast.ObjectMember.ObjectElement;
import org.pkl.core.parser.ast.ObjectMember.ObjectEntry;
import org.pkl.core.parser.ast.ObjectMember.ObjectMethod;
import org.pkl.core.parser.ast.ObjectMember.ObjectProperty;
import org.pkl.core.parser.ast.ObjectMember.ObjectSpread;
import org.pkl.core.parser.ast.ObjectMember.WhenGenerator;
import org.pkl.core.parser.ast.Parameter;
import org.pkl.core.parser.ast.ParameterList;
import org.pkl.core.parser.ast.QualifiedIdentifier;
import org.pkl.core.parser.ast.ReplInput;
import org.pkl.core.parser.ast.StringConstant;
import org.pkl.core.parser.ast.StringConstantPart;
import org.pkl.core.parser.ast.StringPart;
import org.pkl.core.parser.ast.Type.ConstrainedType;
import org.pkl.core.parser.ast.Type.DeclaredType;
import org.pkl.core.parser.ast.Type.FunctionType;
import org.pkl.core.parser.ast.Type.ModuleType;
import org.pkl.core.parser.ast.Type.NothingType;
import org.pkl.core.parser.ast.Type.NullableType;
import org.pkl.core.parser.ast.Type.ParenthesizedType;
import org.pkl.core.parser.ast.Type.StringConstantType;
import org.pkl.core.parser.ast.Type.UnionType;
import org.pkl.core.parser.ast.Type.UnknownType;
import org.pkl.core.parser.ast.TypeAlias;
import org.pkl.core.parser.ast.TypeAnnotation;
import org.pkl.core.parser.ast.TypeParameter;
import org.pkl.core.parser.ast.TypeParameterList;
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
  public @Nullable T visitStringConstantType(StringConstantType type) {
    return visitChildren(type);
  }

  @Override
  public @Nullable T visitDeclaredType(DeclaredType type) {
    return visitChildren(type);
  }

  @Override
  public @Nullable T visitParenthesizedType(ParenthesizedType type) {
    return visitChildren(type);
  }

  @Override
  public @Nullable T visitNullableType(NullableType type) {
    return visitChildren(type);
  }

  @Override
  public @Nullable T visitConstrainedType(ConstrainedType type) {
    return visitChildren(type);
  }

  @Override
  public @Nullable T visitUnionType(UnionType type) {
    return visitChildren(type);
  }

  @Override
  public @Nullable T visitFunctionType(FunctionType type) {
    return visitChildren(type);
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
  public @Nullable T visitModuleExpr(ModuleExpr expr) {
    return null;
  }

  @Override
  public @Nullable T visitNullLiteralExpr(NullLiteralExpr expr) {
    return null;
  }

  @Override
  public @Nullable T visitBoolLiteralExpr(BoolLiteralExpr expr) {
    return null;
  }

  @Override
  public @Nullable T visitIntLiteralExpr(IntLiteralExpr expr) {
    return null;
  }

  @Override
  public @Nullable T visitFloatLiteralExpr(FloatLiteralExpr expr) {
    return null;
  }

  @Override
  public @Nullable T visitThrowExpr(ThrowExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitTraceExpr(TraceExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitImportExpr(ImportExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitReadExpr(ReadExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitUnqualifiedAccessExpr(UnqualifiedAccessExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitStringConstant(StringConstant expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitSingleLineStringLiteralExpr(SingleLineStringLiteralExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitMultiLineStringLiteralExpr(MultiLineStringLiteralExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitNewExpr(NewExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitAmendsExpr(AmendsExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitSuperAccessExpr(SuperAccessExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitSuperSubscriptExpr(SuperSubscriptExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitQualifiedAccessExpr(QualifiedAccessExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitSubscriptExpr(SubscriptExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitNonNullExpr(NonNullExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitUnaryMinusExpr(UnaryMinusExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitLogicalNotExpr(LogicalNotExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitBinaryOperatorExpr(BinaryOperatorExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitTypeCheckExpr(TypeCheckExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitTypeCastExpr(TypeCastExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitIfExpr(IfExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitLetExpr(LetExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitFunctionLiteralExpr(FunctionLiteralExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitParenthesizedExpr(ParenthesizedExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public @Nullable T visitObjectProperty(ObjectProperty member) {
    return visitChildren(member);
  }

  @Override
  public @Nullable T visitObjectMethod(ObjectMethod member) {
    return visitChildren(member);
  }

  @Override
  public @Nullable T visitMemberPredicate(MemberPredicate member) {
    return visitChildren(member);
  }

  @Override
  public @Nullable T visitObjectElement(ObjectElement member) {
    return visitChildren(member);
  }

  @Override
  public @Nullable T visitObjectEntry(ObjectEntry member) {
    return visitChildren(member);
  }

  @Override
  public @Nullable T visitObjectSpread(ObjectSpread member) {
    return visitChildren(member);
  }

  @Override
  public @Nullable T visitWhenGenerator(WhenGenerator member) {
    return visitChildren(member);
  }

  @Override
  public @Nullable T visitForGenerator(ForGenerator member) {
    return visitChildren(member);
  }

  @Override
  public @Nullable T visitModule(org.pkl.core.parser.ast.Module module) {
    return visitChildren(module);
  }

  @Override
  public @Nullable T visitModuleDecl(ModuleDecl decl) {
    return visitChildren(decl);
  }

  @Override
  public @Nullable T visitExtendsOrAmendsClause(ExtendsOrAmendsClause decl) {
    return visitChildren(decl);
  }

  @Override
  public @Nullable T visitImportClause(ImportClause imp) {
    return visitChildren(imp);
  }

  @Override
  public @Nullable T visitClass(Class clazz) {
    return visitChildren(clazz);
  }

  @Override
  public @Nullable T visitModifier(Modifier modifier) {
    return null;
  }

  @Override
  public @Nullable T visitClassProperty(ClassProperty prop) {
    return visitChildren(prop);
  }

  @Override
  public @Nullable T visitClassMethod(ClassMethod method) {
    return visitChildren(method);
  }

  @Override
  public @Nullable T visitTypeAlias(TypeAlias typeAlias) {
    return visitChildren(typeAlias);
  }

  @Override
  public @Nullable T visitAnnotation(Annotation annotation) {
    return visitChildren(annotation);
  }

  @Override
  public @Nullable T visitParameter(Parameter param) {
    return visitChildren(param);
  }

  @Override
  public @Nullable T visitParameterList(ParameterList paramList) {
    return visitChildren(paramList);
  }

  @Override
  public @Nullable T visitTypeParameterList(TypeParameterList typeParameterList) {
    return visitChildren(typeParameterList);
  }

  @Override
  public @Nullable T visitTypeAnnotation(TypeAnnotation typeAnnotation) {
    return visitChildren(typeAnnotation);
  }

  @Override
  public @Nullable T visitArgumentList(ArgumentList argumentList) {
    return visitChildren(argumentList);
  }

  @Override
  public @Nullable T visitStringPart(StringPart part) {
    return visitChildren(part);
  }

  @Override
  public @Nullable T visitStringConstantPart(StringConstantPart part) {
    return null;
  }

  @Override
  public @Nullable T visitClassBody(ClassBody classBody) {
    return visitChildren(classBody);
  }

  @Override
  public @Nullable T visitDocComment(DocComment docComment) {
    return null;
  }

  @Override
  public @Nullable T visitIdentifier(Identifier identifier) {
    return null;
  }

  @Override
  public @Nullable T visitQualifiedIdentifier(QualifiedIdentifier qualifiedIdentifier) {
    return visitChildren(qualifiedIdentifier);
  }

  @Override
  public @Nullable T visitObjectBody(ObjectBody objectBody) {
    return visitChildren(objectBody);
  }

  @Override
  public @Nullable T visitTypeParameter(TypeParameter typeParameter) {
    return visitChildren(typeParameter);
  }

  @Override
  public @Nullable T visitReplInput(ReplInput replInput) {
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
