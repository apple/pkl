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
package org.pkl.core.newparser;

import org.pkl.core.newparser.cst.Annotation;
import org.pkl.core.newparser.cst.ArgumentList;
import org.pkl.core.newparser.cst.ClassBody;
import org.pkl.core.newparser.cst.ClassMethod;
import org.pkl.core.newparser.cst.ClassPropertyEntry;
import org.pkl.core.newparser.cst.Clazz;
import org.pkl.core.newparser.cst.DocComment;
import org.pkl.core.newparser.cst.Expr;
import org.pkl.core.newparser.cst.Expr.NullLiteral;
import org.pkl.core.newparser.cst.ExtendsOrAmendsDecl;
import org.pkl.core.newparser.cst.Ident;
import org.pkl.core.newparser.cst.Import;
import org.pkl.core.newparser.cst.Modifier;
import org.pkl.core.newparser.cst.Module;
import org.pkl.core.newparser.cst.ModuleDecl;
import org.pkl.core.newparser.cst.ObjectBody;
import org.pkl.core.newparser.cst.ObjectMemberNode;
import org.pkl.core.newparser.cst.Parameter;
import org.pkl.core.newparser.cst.ParameterList;
import org.pkl.core.newparser.cst.QualifiedIdent;
import org.pkl.core.newparser.cst.ReplInput;
import org.pkl.core.newparser.cst.StringConstantPart;
import org.pkl.core.newparser.cst.StringPart;
import org.pkl.core.newparser.cst.Type;
import org.pkl.core.newparser.cst.TypeAlias;
import org.pkl.core.newparser.cst.TypeAnnotation;
import org.pkl.core.newparser.cst.TypeParameter;
import org.pkl.core.newparser.cst.TypeParameterList;
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
  Result visitThisExpr(Expr.This expr);

  @Nullable
  Result visitOuterExpr(Expr.Outer expr);

  @Nullable
  Result visitModuleExpr(Expr.Module expr);

  @Nullable
  Result visitNullLiteralExpr(NullLiteral expr);

  @Nullable
  Result visitBoolLiteralExpr(Expr.BoolLiteral expr);

  @Nullable
  Result visitIntLiteralExpr(Expr.IntLiteral expr);

  @Nullable
  Result visitFloatLiteralExpr(Expr.FloatLiteral expr);

  @Nullable
  Result visitThrowExpr(Expr.Throw expr);

  @Nullable
  Result visitTraceExpr(Expr.Trace expr);

  @Nullable
  Result visitImportExpr(Expr.ImportExpr expr);

  @Nullable
  Result visitReadExpr(Expr.Read expr);

  @Nullable
  Result visitReadNullExpr(Expr.ReadNull expr);

  @Nullable
  Result visitReadGlobExpr(Expr.ReadGlob expr);

  @Nullable
  Result visitUnqualifiedAccessExpr(Expr.UnqualifiedAccess expr);

  @Nullable
  Result visitStringConstantExpr(Expr.StringConstant expr);

  @Nullable
  Result visitInterpolatedStringExpr(Expr.InterpolatedString expr);

  @Nullable
  Result visitInterpolatedMultiStringExpr(Expr.InterpolatedMultiString expr);

  @Nullable
  Result visitNewExpr(Expr.New expr);

  @Nullable
  Result visitAmendsExpr(Expr.Amends expr);

  @Nullable
  Result visitSuperAccessExpr(Expr.SuperAccess expr);

  @Nullable
  Result visitSuperSubscriptExpr(Expr.SuperSubscript expr);

  @Nullable
  Result visitQualifiedAccessExpr(Expr.QualifiedAccess expr);

  @Nullable
  Result visitSubscriptExpr(Expr.Subscript expr);

  @Nullable
  Result visitNonNullExpr(Expr.NonNull expr);

  @Nullable
  Result visitUnaryMinusExpr(Expr.UnaryMinus expr);

  @Nullable
  Result visitLogicalNotExpr(Expr.LogicalNot expr);

  @Nullable
  Result visitBinaryOpExpr(Expr.BinaryOp expr);

  @Nullable
  Result visitTypeCheckExpr(Expr.TypeCheck expr);

  @Nullable
  Result visitTypeCastExpr(Expr.TypeCast expr);

  @Nullable
  Result visitIfExpr(Expr.If expr);

  @Nullable
  Result visitLetExpr(Expr.Let expr);

  @Nullable
  Result visitFunctionLiteralExpr(Expr.FunctionLiteral expr);

  @Nullable
  Result visitParenthesizedExpr(Expr.Parenthesized expr);

  @Nullable
  Result visitExpr(Expr expr);

  @Nullable
  Result visitObjectProperty(ObjectMemberNode.ObjectProperty member);

  @Nullable
  Result visitObjectBodyProperty(ObjectMemberNode.ObjectBodyProperty member);

  @Nullable
  Result visitObjectMethod(ObjectMemberNode.ObjectMethod member);

  @Nullable
  Result visitMemberPredicate(ObjectMemberNode.MemberPredicate member);

  @Nullable
  Result visitMemberPredicateBody(ObjectMemberNode.MemberPredicateBody member);

  @Nullable
  Result visitObjectElement(ObjectMemberNode.ObjectElement member);

  @Nullable
  Result visitObjectEntry(ObjectMemberNode.ObjectEntry member);

  @Nullable
  Result visitObjectEntryBody(ObjectMemberNode.ObjectEntryBody member);

  @Nullable
  Result visitObjectSpread(ObjectMemberNode.ObjectSpread member);

  @Nullable
  Result visitWhenGenerator(ObjectMemberNode.WhenGenerator member);

  @Nullable
  Result visitForGenerator(ObjectMemberNode.ForGenerator member);

  @Nullable
  Result visitObjectMember(ObjectMemberNode member);

  @Nullable
  Result visitModule(Module module);

  @Nullable
  Result visitModuleDecl(ModuleDecl decl);

  @Nullable
  Result visitExtendsOrAmendsDecl(ExtendsOrAmendsDecl decl);

  @Nullable
  Result visitImport(Import imp);

  @Nullable
  Result visitClass(Clazz clazz);

  @Nullable
  Result visitModifier(Modifier modifier);

  @Nullable
  Result visitClassPropertyEntry(ClassPropertyEntry entry);

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
  Result visitIdent(Ident ident);

  @Nullable
  Result visitQualifiedIdent(QualifiedIdent qualifiedIdent);

  @Nullable
  Result visitObjectBody(ObjectBody objectBody);

  @Nullable
  Result visitReplInput(ReplInput replInput);
}
