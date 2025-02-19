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

import org.pkl.core.parser.syntax.Annotation;
import org.pkl.core.parser.syntax.ArgumentList;
import org.pkl.core.parser.syntax.Class;
import org.pkl.core.parser.syntax.ClassBody;
import org.pkl.core.parser.syntax.ClassMethod;
import org.pkl.core.parser.syntax.ClassProperty;
import org.pkl.core.parser.syntax.DocComment;
import org.pkl.core.parser.syntax.Expr;
import org.pkl.core.parser.syntax.Expr.AmendsExpr;
import org.pkl.core.parser.syntax.Expr.BinaryOperatorExpr;
import org.pkl.core.parser.syntax.Expr.BoolLiteralExpr;
import org.pkl.core.parser.syntax.Expr.FloatLiteralExpr;
import org.pkl.core.parser.syntax.Expr.FunctionLiteralExpr;
import org.pkl.core.parser.syntax.Expr.IfExpr;
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
import org.pkl.core.parser.syntax.Module;
import org.pkl.core.parser.syntax.ModuleDecl;
import org.pkl.core.parser.syntax.ObjectBody;
import org.pkl.core.parser.syntax.ObjectMember;
import org.pkl.core.parser.syntax.Parameter;
import org.pkl.core.parser.syntax.ParameterList;
import org.pkl.core.parser.syntax.QualifiedIdentifier;
import org.pkl.core.parser.syntax.ReplInput;
import org.pkl.core.parser.syntax.StringConstant;
import org.pkl.core.parser.syntax.StringPart;
import org.pkl.core.parser.syntax.Type;
import org.pkl.core.parser.syntax.TypeAlias;
import org.pkl.core.parser.syntax.TypeAnnotation;
import org.pkl.core.parser.syntax.TypeArgumentList;
import org.pkl.core.parser.syntax.TypeParameter;
import org.pkl.core.parser.syntax.TypeParameterList;

public interface ParserVisitor<Result> {

  Result visitUnknownType(Type.UnknownType type);

  Result visitNothingType(Type.NothingType type);

  Result visitModuleType(Type.ModuleType type);

  Result visitStringConstantType(Type.StringConstantType type);

  Result visitDeclaredType(Type.DeclaredType type);

  Result visitParenthesizedType(Type.ParenthesizedType type);

  Result visitNullableType(Type.NullableType type);

  Result visitConstrainedType(Type.ConstrainedType type);

  Result visitUnionType(Type.UnionType type);

  Result visitFunctionType(Type.FunctionType type);

  Result visitThisExpr(ThisExpr expr);

  Result visitOuterExpr(OuterExpr expr);

  Result visitModuleExpr(ModuleExpr expr);

  Result visitNullLiteralExpr(NullLiteralExpr expr);

  Result visitBoolLiteralExpr(BoolLiteralExpr expr);

  Result visitIntLiteralExpr(IntLiteralExpr expr);

  Result visitFloatLiteralExpr(FloatLiteralExpr expr);

  Result visitThrowExpr(ThrowExpr expr);

  Result visitTraceExpr(TraceExpr expr);

  Result visitImportExpr(Expr.ImportExpr expr);

  Result visitReadExpr(ReadExpr expr);

  Result visitUnqualifiedAccessExpr(UnqualifiedAccessExpr expr);

  Result visitStringConstant(StringConstant expr);

  Result visitSingleLineStringLiteralExpr(SingleLineStringLiteralExpr expr);

  Result visitMultiLineStringLiteralExpr(MultiLineStringLiteralExpr expr);

  Result visitNewExpr(NewExpr expr);

  Result visitAmendsExpr(AmendsExpr expr);

  Result visitSuperAccessExpr(SuperAccessExpr expr);

  Result visitSuperSubscriptExpr(SuperSubscriptExpr expr);

  Result visitQualifiedAccessExpr(QualifiedAccessExpr expr);

  Result visitSubscriptExpr(SubscriptExpr expr);

  Result visitNonNullExpr(NonNullExpr expr);

  Result visitUnaryMinusExpr(UnaryMinusExpr expr);

  Result visitLogicalNotExpr(LogicalNotExpr expr);

  Result visitBinaryOperatorExpr(BinaryOperatorExpr expr);

  Result visitTypeCheckExpr(TypeCheckExpr expr);

  Result visitTypeCastExpr(TypeCastExpr expr);

  Result visitIfExpr(IfExpr expr);

  Result visitLetExpr(LetExpr expr);

  Result visitFunctionLiteralExpr(FunctionLiteralExpr expr);

  Result visitParenthesizedExpr(ParenthesizedExpr expr);

  Result visitObjectProperty(ObjectMember.ObjectProperty member);

  Result visitObjectMethod(ObjectMember.ObjectMethod member);

  Result visitMemberPredicate(ObjectMember.MemberPredicate member);

  Result visitObjectElement(ObjectMember.ObjectElement member);

  Result visitObjectEntry(ObjectMember.ObjectEntry member);

  Result visitObjectSpread(ObjectMember.ObjectSpread member);

  Result visitWhenGenerator(ObjectMember.WhenGenerator member);

  Result visitForGenerator(ObjectMember.ForGenerator member);

  Result visitModule(Module module);

  Result visitModuleDecl(ModuleDecl decl);

  Result visitExtendsOrAmendsClause(ExtendsOrAmendsClause decl);

  Result visitImportClause(ImportClause imp);

  Result visitClass(Class clazz);

  Result visitModifier(Modifier modifier);

  Result visitClassProperty(ClassProperty entry);

  Result visitClassMethod(ClassMethod entry);

  Result visitClassBody(ClassBody classBody);

  Result visitTypeAlias(TypeAlias typeAlias);

  Result visitAnnotation(Annotation annotation);

  Result visitParameter(Parameter param);

  Result visitParameterList(ParameterList paramList);

  Result visitTypeParameter(TypeParameter typeParameter);

  Result visitTypeParameterList(TypeParameterList typeParameterList);

  Result visitTypeAnnotation(TypeAnnotation typeAnnotation);

  Result visitArgumentList(ArgumentList argumentList);

  Result visitStringPart(StringPart part);

  Result visitDocComment(DocComment docComment);

  Result visitIdentifier(Identifier identifier);

  Result visitQualifiedIdentifier(QualifiedIdentifier qualifiedIdentifier);

  Result visitObjectBody(ObjectBody objectBody);

  Result visitReplInput(ReplInput replInput);

  Result visitKeyword(Keyword keyword);

  Result visitTypeArgumentList(TypeArgumentList typeArgumentList);
}
