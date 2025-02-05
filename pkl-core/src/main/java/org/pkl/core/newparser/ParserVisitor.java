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

import org.pkl.core.PklBugException;
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

public interface ParserVisitor<Result> {

  Result visitUnknownType(Type.UnknownType type);

  Result visitNothingType(Type.NothingType type);

  Result visitModuleType(Type.ModuleType type);

  Result visitStringConstantType(Type.StringConstantType type);

  Result visitDeclaredType(Type.DeclaredType type);

  Result visitParenthesizedType(Type.ParenthesizedType type);

  Result visitNullableType(Type.NullableType type);

  Result visitConstrainedType(Type.ConstrainedType type);

  Result visitDefaultUnionType(Type.DefaultUnionType type);

  Result visitUnionType(Type.UnionType type);

  Result visitFunctionType(Type.FunctionType type);

  default Result visitType(Type type) {
    if (type instanceof Type.UnknownType t) return visitUnknownType(t);
    if (type instanceof Type.NothingType t) return visitNothingType(t);
    if (type instanceof Type.ModuleType t) return visitModuleType(t);
    if (type instanceof Type.StringConstantType t) return visitStringConstantType(t);
    if (type instanceof Type.DeclaredType t) return visitDeclaredType(t);
    if (type instanceof Type.ParenthesizedType t) return visitParenthesizedType(t);
    if (type instanceof Type.NullableType t) return visitNullableType(t);
    if (type instanceof Type.ConstrainedType t) return visitConstrainedType(t);
    if (type instanceof Type.DefaultUnionType t) return visitDefaultUnionType(t);
    if (type instanceof Type.UnionType t) return visitUnionType(t);
    if (type instanceof Type.FunctionType t) return visitFunctionType(t);
    throw PklBugException.unreachableCode();
  }

  Result visitThisExpr(Expr.This expr);

  Result visitOuterExpr(Expr.Outer expr);

  Result visitModuleExpr(Expr.Module expr);

  Result visitNullLiteralExpr(NullLiteral expr);

  Result visitBoolLiteralExpr(Expr.BoolLiteral expr);

  Result visitIntLiteralExpr(Expr.IntLiteral expr);

  Result visitFloatLiteralExpr(Expr.FloatLiteral expr);

  Result visitThrowExpr(Expr.Throw expr);

  Result visitTraceExpr(Expr.Trace expr);

  Result visitImportExpr(Expr.ImportExpr expr);

  Result visitReadExpr(Expr.Read expr);

  Result visitReadNullExpr(Expr.ReadNull expr);

  Result visitReadGlobExpr(Expr.ReadGlob expr);

  Result visitUnqualifiedAccessExpr(Expr.UnqualifiedAccess expr);

  Result visitStringConstantExpr(Expr.StringConstant expr);

  Result visitInterpolatedStringExpr(Expr.InterpolatedString expr);

  Result visitInterpolatedMultiStringExpr(Expr.InterpolatedMultiString expr);

  Result visitNewExpr(Expr.New expr);

  Result visitAmendsExpr(Expr.Amends expr);

  Result visitSuperAccessExpr(Expr.SuperAccess expr);

  Result visitSuperSubscriptExpr(Expr.SuperSubscript expr);

  Result visitQualifiedAccessExpr(Expr.QualifiedAccess expr);

  Result visitSubscriptExpr(Expr.Subscript expr);

  Result visitNonNullExpr(Expr.NonNull expr);

  Result visitUnaryMinusExpr(Expr.UnaryMinus expr);

  Result visitLogicalNotExpr(Expr.LogicalNot expr);

  Result visitBinaryOpExpr(Expr.BinaryOp expr);

  Result visitTypeCheckExpr(Expr.TypeCheck expr);

  Result visitTypeCastExpr(Expr.TypeCast expr);

  Result visitIfExpr(Expr.If expr);

  Result visitLetExpr(Expr.Let expr);

  Result visitFunctionLiteralExpr(Expr.FunctionLiteral expr);

  Result visitParenthesizedExpr(Expr.Parenthesized expr);

  default Result visitExpr(Expr expr) {
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

  Result visitObjectProperty(ObjectMemberNode.ObjectProperty member);

  Result visitObjectBodyProperty(ObjectMemberNode.ObjectBodyProperty member);

  Result visitObjectMethod(ObjectMemberNode.ObjectMethod member);

  Result visitMemberPredicate(ObjectMemberNode.MemberPredicate member);

  Result visitMemberPredicateBody(ObjectMemberNode.MemberPredicateBody member);

  Result visitObjectElement(ObjectMemberNode.ObjectElement member);

  Result visitObjectEntry(ObjectMemberNode.ObjectEntry member);

  Result visitObjectEntryBody(ObjectMemberNode.ObjectEntryBody member);

  Result visitObjectSpread(ObjectMemberNode.ObjectSpread member);

  Result visitWhenGenerator(ObjectMemberNode.WhenGenerator member);

  Result visitForGenerator(ObjectMemberNode.ForGenerator member);

  default Result visitObjectMember(ObjectMemberNode member) {
    if (member instanceof ObjectMemberNode.ObjectElement o) return visitObjectElement(o);
    if (member instanceof ObjectMemberNode.ObjectProperty o) return visitObjectProperty(o);
    if (member instanceof ObjectMemberNode.ObjectBodyProperty o) return visitObjectBodyProperty(o);
    if (member instanceof ObjectMemberNode.ObjectMethod o) return visitObjectMethod(o);
    if (member instanceof ObjectMemberNode.MemberPredicate o) return visitMemberPredicate(o);
    if (member instanceof ObjectMemberNode.MemberPredicateBody o)
      return visitMemberPredicateBody(o);
    if (member instanceof ObjectMemberNode.ObjectEntry o) return visitObjectEntry(o);
    if (member instanceof ObjectMemberNode.ObjectEntryBody o) return visitObjectEntryBody(o);
    if (member instanceof ObjectMemberNode.ObjectSpread o) return visitObjectSpread(o);
    if (member instanceof ObjectMemberNode.WhenGenerator o) return visitWhenGenerator(o);
    if (member instanceof ObjectMemberNode.ForGenerator o) return visitForGenerator(o);
    throw PklBugException.unreachableCode();
  }

  Result visitModule(Module module);

  Result visitModuleDecl(ModuleDecl decl);

  Result visitExtendsOrAmendsDecl(ExtendsOrAmendsDecl decl);

  Result visitImport(Import imp);

  Result visitClass(Clazz clazz);

  Result visitModifier(Modifier modifier);

  Result visitClassPropertyEntry(ClassPropertyEntry entry);

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

  Result visitIdent(Ident ident);

  Result visitQualifiedIdent(QualifiedIdent qualifiedIdent);

  Result visitObjectBody(ObjectBody objectBody);

  Result visitReplInput(ReplInput replInput);
}
