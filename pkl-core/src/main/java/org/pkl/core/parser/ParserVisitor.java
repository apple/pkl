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
package org.pkl.core.parser;

import org.pkl.core.parser.ast.Annotation;
import org.pkl.core.parser.ast.ArgumentList;
import org.pkl.core.parser.ast.Class;
import org.pkl.core.parser.ast.ClassBody;
import org.pkl.core.parser.ast.ClassMethod;
import org.pkl.core.parser.ast.ClassProperty;
import org.pkl.core.parser.ast.DocComment;
import org.pkl.core.parser.ast.Expr;
import org.pkl.core.parser.ast.Expr.AmendsExpr;
import org.pkl.core.parser.ast.Expr.BinaryOperatorExpr;
import org.pkl.core.parser.ast.Expr.BoolLiteralExpr;
import org.pkl.core.parser.ast.Expr.FloatLiteralExpr;
import org.pkl.core.parser.ast.Expr.FunctionLiteralExpr;
import org.pkl.core.parser.ast.Expr.IfExpr;
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
import org.pkl.core.parser.ast.Module;
import org.pkl.core.parser.ast.ModuleDecl;
import org.pkl.core.parser.ast.ObjectBody;
import org.pkl.core.parser.ast.ObjectMember;
import org.pkl.core.parser.ast.Parameter;
import org.pkl.core.parser.ast.ParameterList;
import org.pkl.core.parser.ast.QualifiedIdentifier;
import org.pkl.core.parser.ast.ReplInput;
import org.pkl.core.parser.ast.StringConstant;
import org.pkl.core.parser.ast.StringConstantPart;
import org.pkl.core.parser.ast.StringPart;
import org.pkl.core.parser.ast.Type;
import org.pkl.core.parser.ast.TypeAlias;
import org.pkl.core.parser.ast.TypeAnnotation;
import org.pkl.core.parser.ast.TypeParameter;
import org.pkl.core.parser.ast.TypeParameterList;
import org.pkl.core.util.Nullable;

public interface ParserVisitor<Result> {

  @Nullable
  Result visitUnknownType(Type.UnknownType type);

  @Nullable
  Result visitNothingType(Type.NothingType type);

  @Nullable
  Result visitModuleType(Type.ModuleType type);

  @Nullable
  Result visitStringConstantType(Type.StringConstantType type);

  @Nullable
  Result visitDeclaredType(Type.DeclaredType type);

  @Nullable
  Result visitParenthesizedType(Type.ParenthesizedType type);

  @Nullable
  Result visitNullableType(Type.NullableType type);

  @Nullable
  Result visitConstrainedType(Type.ConstrainedType type);

  @Nullable
  Result visitDefaultUnionType(Type.DefaultUnionType type);

  @Nullable
  Result visitUnionType(Type.UnionType type);

  @Nullable
  Result visitFunctionType(Type.FunctionType type);

  @Nullable
  Result visitType(Type type);

  @Nullable
  Result visitThisExpr(ThisExpr expr);

  @Nullable
  Result visitOuterExpr(OuterExpr expr);

  @Nullable
  Result visitModuleExpr(ModuleExpr expr);

  @Nullable
  Result visitNullLiteralExpr(NullLiteralExpr expr);

  @Nullable
  Result visitBoolLiteralExpr(BoolLiteralExpr expr);

  @Nullable
  Result visitIntLiteralExpr(IntLiteralExpr expr);

  @Nullable
  Result visitFloatLiteralExpr(FloatLiteralExpr expr);

  @Nullable
  Result visitThrowExpr(ThrowExpr expr);

  @Nullable
  Result visitTraceExpr(TraceExpr expr);

  @Nullable
  Result visitImportExpr(Expr.ImportExpr expr);

  @Nullable
  Result visitReadExpr(ReadExpr expr);

  @Nullable
  Result visitUnqualifiedAccessExpr(UnqualifiedAccessExpr expr);

  @Nullable
  Result visitStringConstant(StringConstant expr);

  @Nullable
  Result visitSingleLineStringLiteralExpr(SingleLineStringLiteralExpr expr);

  @Nullable
  Result visitMultiLineStringLiteralExpr(MultiLineStringLiteralExpr expr);

  @Nullable
  Result visitNewExpr(NewExpr expr);

  @Nullable
  Result visitAmendsExpr(AmendsExpr expr);

  @Nullable
  Result visitSuperAccessExpr(SuperAccessExpr expr);

  @Nullable
  Result visitSuperSubscriptExpr(SuperSubscriptExpr expr);

  @Nullable
  Result visitQualifiedAccessExpr(QualifiedAccessExpr expr);

  @Nullable
  Result visitSubscriptExpr(SubscriptExpr expr);

  @Nullable
  Result visitNonNullExpr(NonNullExpr expr);

  @Nullable
  Result visitUnaryMinusExpr(UnaryMinusExpr expr);

  @Nullable
  Result visitLogicalNotExpr(LogicalNotExpr expr);

  @Nullable
  Result visitBinaryOperatorExpr(BinaryOperatorExpr expr);

  @Nullable
  Result visitTypeCheckExpr(TypeCheckExpr expr);

  @Nullable
  Result visitTypeCastExpr(TypeCastExpr expr);

  @Nullable
  Result visitIfExpr(IfExpr expr);

  @Nullable
  Result visitLetExpr(LetExpr expr);

  @Nullable
  Result visitFunctionLiteralExpr(FunctionLiteralExpr expr);

  @Nullable
  Result visitParenthesizedExpr(ParenthesizedExpr expr);

  @Nullable
  Result visitExpr(Expr expr);

  @Nullable
  Result visitObjectProperty(ObjectMember.ObjectProperty member);

  @Nullable
  Result visitObjectMethod(ObjectMember.ObjectMethod member);

  @Nullable
  Result visitMemberPredicate(ObjectMember.MemberPredicate member);

  @Nullable
  Result visitObjectElement(ObjectMember.ObjectElement member);

  @Nullable
  Result visitObjectEntry(ObjectMember.ObjectEntry member);

  @Nullable
  Result visitObjectSpread(ObjectMember.ObjectSpread member);

  @Nullable
  Result visitWhenGenerator(ObjectMember.WhenGenerator member);

  @Nullable
  Result visitForGenerator(ObjectMember.ForGenerator member);

  @Nullable
  Result visitObjectMember(ObjectMember member);

  @Nullable
  Result visitModule(Module module);

  @Nullable
  Result visitModuleDecl(ModuleDecl decl);

  @Nullable
  Result visitExtendsOrAmendsClause(ExtendsOrAmendsClause decl);

  @Nullable
  Result visitImportClause(ImportClause imp);

  @Nullable
  Result visitClass(Class clazz);

  @Nullable
  Result visitModifier(Modifier modifier);

  @Nullable
  Result visitClassProperty(ClassProperty entry);

  @Nullable
  Result visitClassMethod(ClassMethod entry);

  @Nullable
  Result visitClassBody(ClassBody classBody);

  @Nullable
  Result visitTypeAlias(TypeAlias typeAlias);

  @Nullable
  Result visitAnnotation(Annotation annotation);

  @Nullable
  Result visitParameter(Parameter param);

  @Nullable
  Result visitParameterList(ParameterList paramList);

  @Nullable
  Result visitTypeParameter(TypeParameter typeParameter);

  @Nullable
  Result visitTypeParameterList(TypeParameterList typeParameterList);

  @Nullable
  Result visitTypeAnnotation(TypeAnnotation typeAnnotation);

  @Nullable
  Result visitArgumentList(ArgumentList argumentList);

  @Nullable
  Result visitStringPart(StringPart part);

  @Nullable
  Result visitStringConstantPart(StringConstantPart part);

  @Nullable
  Result visitDocComment(DocComment docComment);

  @Nullable
  Result visitIdentifier(Identifier identifier);

  @Nullable
  Result visitQualifiedIdentifier(QualifiedIdentifier qualifiedIdentifier);

  @Nullable
  Result visitObjectBody(ObjectBody objectBody);

  @Nullable
  Result visitReplInput(ReplInput replInput);
}
