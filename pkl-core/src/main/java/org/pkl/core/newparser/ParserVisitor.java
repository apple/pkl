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
import org.pkl.core.newparser.cst.AmendsDecl;
import org.pkl.core.newparser.cst.ClassEntry;
import org.pkl.core.newparser.cst.Clazz;
import org.pkl.core.newparser.cst.Expr;
import org.pkl.core.newparser.cst.Expr.NullLiteral;
import org.pkl.core.newparser.cst.ExtendsDecl;
import org.pkl.core.newparser.cst.Import;
import org.pkl.core.newparser.cst.Modifier;
import org.pkl.core.newparser.cst.Module;
import org.pkl.core.newparser.cst.ModuleDecl;
import org.pkl.core.newparser.cst.ObjectMember;
import org.pkl.core.newparser.cst.Type;
import org.pkl.core.newparser.cst.TypeAlias;
import org.pkl.core.util.Nullable;

public abstract class ParserVisitor<Result> {

  public @Nullable Result visitUnknownType(Type.UnknownType type) {
    return null;
  }

  public @Nullable Result visitNothingType(Type.NothingType type) {
    return null;
  }

  public @Nullable Result visitModuleType(Type.ModuleType type) {
    return null;
  }

  public @Nullable Result visitStringConstantType(Type.StringConstantType type) {
    return null;
  }

  public @Nullable Result visitDeclaredType(Type.DeclaredType type) {
    return null;
  }

  public @Nullable Result visitParenthesizedType(Type.ParenthesizedType type) {
    return null;
  }

  public @Nullable Result visitNullableType(Type.NullableType type) {
    return null;
  }

  public @Nullable Result visitConstrainedType(Type.ConstrainedType type) {
    return null;
  }

  public @Nullable Result visitDefaultUnionType(Type.DefaultUnionType type) {
    return null;
  }

  public @Nullable Result visitUnionType(Type.UnionType type) {
    return null;
  }

  public @Nullable Result visitFunctionType(Type.FunctionType type) {
    return null;
  }

  public @Nullable Result visitType(Type type) {
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

  public @Nullable Result visitThisExpr(Expr.This expr) {
    return null;
  }

  public @Nullable Result visitOuterExpr(Expr.Outer expr) {
    return null;
  }

  public @Nullable Result visitModuleExpr(Expr.Module expr) {
    return null;
  }

  public @Nullable Result visitNullLiteralExpr(NullLiteral expr) {
    return null;
  }

  public @Nullable Result visitBoolLiteralExpr(Expr.BoolLiteral expr) {
    return null;
  }

  public @Nullable Result visitIntLiteralExpr(Expr.IntLiteral expr) {
    return null;
  }

  public @Nullable Result visitFloatLiteralExpr(Expr.FloatLiteral expr) {
    return null;
  }

  public @Nullable Result visitThrowExpr(Expr.Throw expr) {
    return null;
  }

  public @Nullable Result visitTraceExpr(Expr.Trace expr) {
    return null;
  }

  public @Nullable Result visitImportExpr(Expr.ImportExpr expr) {
    return null;
  }

  public @Nullable Result visitImportGlobExpr(Expr.ImportGlobExpr expr) {
    return null;
  }

  public @Nullable Result visitReadExpr(Expr.Read expr) {
    return null;
  }

  public @Nullable Result visitReadNullExpr(Expr.ReadNull expr) {
    return null;
  }

  public @Nullable Result visitReadGlobExpr(Expr.ReadGlob expr) {
    return null;
  }

  public @Nullable Result visitUnqualifiedAccessExpr(Expr.UnqualifiedAccess expr) {
    return null;
  }

  public @Nullable Result visitStringConstantExpr(Expr.StringConstant expr) {
    return null;
  }

  public @Nullable Result visitInterpolatedStringExpr(Expr.InterpolatedString expr) {
    return null;
  }

  public @Nullable Result visitInterpolatedMultiStringExpr(Expr.InterpolatedMultiString expr) {
    return null;
  }

  public @Nullable Result visitNewExpr(Expr.New expr) {
    return null;
  }

  public @Nullable Result visitAmendsExpr(Expr.Amends expr) {
    return null;
  }

  public @Nullable Result visitSuperAccessExpr(Expr.SuperAccess expr) {
    return null;
  }

  public @Nullable Result visitSuperSubscriptExpr(Expr.SuperSubscript expr) {
    return null;
  }

  public @Nullable Result visitQualifiedAccessExpr(Expr.QualifiedAccess expr) {
    return null;
  }

  public @Nullable Result visitSubscriptExpr(Expr.Subscript expr) {
    return null;
  }

  public @Nullable Result visitNonNullExpr(Expr.NonNull expr) {
    return null;
  }

  public @Nullable Result visitUnaryMinusExpr(Expr.UnaryMinus expr) {
    return null;
  }

  public @Nullable Result visitLogicalNotExpr(Expr.LogicalNot expr) {
    return null;
  }

  public @Nullable Result visitBinaryOpExpr(Expr.BinaryOp expr) {
    return null;
  }

  public @Nullable Result visitTypeCheckExpr(Expr.TypeCheck expr) {
    return null;
  }

  public @Nullable Result visitTypeCastExpr(Expr.TypeCast expr) {
    return null;
  }

  public @Nullable Result visitIfExpr(Expr.If expr) {
    return null;
  }

  public @Nullable Result visitLetExpr(Expr.Let expr) {
    return null;
  }

  public @Nullable Result visitFunctionLiteralExpr(Expr.FunctionLiteral expr) {
    return null;
  }

  public @Nullable Result visitParenthesizedExpr(Expr.Parenthesized expr) {
    return null;
  }

  public @Nullable Result visitExpr(Expr expr) {
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
    if (expr instanceof Expr.ImportGlobExpr e) return visitImportGlobExpr(e);
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

  public @Nullable Result visitObjectProperty(ObjectMember.ObjectProperty member) {
    return null;
  }

  public @Nullable Result visitObjectBodyProperty(ObjectMember.ObjectBodyProperty member) {
    return null;
  }

  public @Nullable Result visitObjectMethod(ObjectMember.ObjectMethod member) {
    return null;
  }

  public @Nullable Result visitMemberPredicate(ObjectMember.MemberPredicate member) {
    return null;
  }

  public @Nullable Result visitMemberPredicateBody(ObjectMember.MemberPredicateBody member) {
    return null;
  }

  public @Nullable Result visitObjectElement(ObjectMember.ObjectElement member) {
    return null;
  }

  public @Nullable Result visitObjectEntry(ObjectMember.ObjectEntry member) {
    return null;
  }

  public @Nullable Result visitObjectEntryBody(ObjectMember.ObjectEntryBody member) {
    return null;
  }

  public @Nullable Result visitObjectSpread(ObjectMember.ObjectSpread member) {
    return null;
  }

  public @Nullable Result visitWhenGenerator(ObjectMember.WhenGenerator member) {
    return null;
  }

  public @Nullable Result visitForGenerator(ObjectMember.ForGenerator member) {
    return null;
  }

  public @Nullable Result visitObjectMember(ObjectMember member) {
    if (member instanceof ObjectMember.ObjectElement o) return visitObjectElement(o);
    if (member instanceof ObjectMember.ObjectProperty o) return visitObjectProperty(o);
    if (member instanceof ObjectMember.ObjectBodyProperty o) return visitObjectBodyProperty(o);
    if (member instanceof ObjectMember.ObjectMethod o) return visitObjectMethod(o);
    if (member instanceof ObjectMember.MemberPredicate o) return visitMemberPredicate(o);
    if (member instanceof ObjectMember.MemberPredicateBody o) return visitMemberPredicateBody(o);
    if (member instanceof ObjectMember.ObjectEntry o) return visitObjectEntry(o);
    if (member instanceof ObjectMember.ObjectEntryBody o) return visitObjectEntryBody(o);
    if (member instanceof ObjectMember.ObjectSpread o) return visitObjectSpread(o);
    if (member instanceof ObjectMember.WhenGenerator o) return visitWhenGenerator(o);
    if (member instanceof ObjectMember.ForGenerator o) return visitForGenerator(o);
    throw PklBugException.unreachableCode();
  }

  public @Nullable Result visitModule(Module module) {
    return null;
  }

  public @Nullable Result visitModuleDecl(ModuleDecl decl) {
    return null;
  }

  public @Nullable Result visitExtendsDecl(ExtendsDecl decl) {
    return null;
  }

  public @Nullable Result visitAmendsDecl(AmendsDecl decl) {
    return null;
  }

  public @Nullable Result visitImport(Import imp) {
    return null;
  }

  public @Nullable Result visitClazz(Clazz clazz) {
    return null;
  }

  public @Nullable Result visitModifier(Modifier modifier) {
    return null;
  }

  public @Nullable Result visitClassProperty(ClassEntry.ClassProperty entry) {
    return null;
  }

  public @Nullable Result visitClassPropertyBody(ClassEntry.ClassPropertyBody entry) {
    return null;
  }

  public @Nullable Result visitClassMethod(ClassEntry.ClassMethod entry) {
    return null;
  }

  public @Nullable Result visitClassPropertyExpr(ClassEntry.ClassPropertyExpr entry) {
    return null;
  }

  public @Nullable Result visitTypeAlias(TypeAlias typeAlias) {
    return null;
  }
}
