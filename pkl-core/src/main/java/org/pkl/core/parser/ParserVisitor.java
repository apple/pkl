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

  Result visitStringConstantPart(StringConstantPart part);

  Result visitDocComment(DocComment docComment);

  Result visitIdentifier(Identifier identifier);

  Result visitQualifiedIdentifier(QualifiedIdentifier qualifiedIdentifier);

  Result visitObjectBody(ObjectBody objectBody);

  Result visitReplInput(ReplInput replInput);
}
