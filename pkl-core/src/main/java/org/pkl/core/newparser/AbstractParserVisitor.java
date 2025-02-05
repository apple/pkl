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
package org.pkl.core.newparser;

import org.pkl.core.newparser.cst.Annotation;
import org.pkl.core.newparser.cst.ArgumentList;
import org.pkl.core.newparser.cst.ClassBody;
import org.pkl.core.newparser.cst.ClassMethod;
import org.pkl.core.newparser.cst.ClassPropertyEntry;
import org.pkl.core.newparser.cst.Clazz;
import org.pkl.core.newparser.cst.DocComment;
import org.pkl.core.newparser.cst.Expr;
import org.pkl.core.newparser.cst.Expr.Amends;
import org.pkl.core.newparser.cst.Expr.BinaryOp;
import org.pkl.core.newparser.cst.Expr.BoolLiteral;
import org.pkl.core.newparser.cst.Expr.FloatLiteral;
import org.pkl.core.newparser.cst.Expr.FunctionLiteral;
import org.pkl.core.newparser.cst.Expr.If;
import org.pkl.core.newparser.cst.Expr.ImportExpr;
import org.pkl.core.newparser.cst.Expr.IntLiteral;
import org.pkl.core.newparser.cst.Expr.InterpolatedMultiString;
import org.pkl.core.newparser.cst.Expr.InterpolatedString;
import org.pkl.core.newparser.cst.Expr.Let;
import org.pkl.core.newparser.cst.Expr.LogicalNot;
import org.pkl.core.newparser.cst.Expr.Module;
import org.pkl.core.newparser.cst.Expr.New;
import org.pkl.core.newparser.cst.Expr.NonNull;
import org.pkl.core.newparser.cst.Expr.NullLiteral;
import org.pkl.core.newparser.cst.Expr.Outer;
import org.pkl.core.newparser.cst.Expr.Parenthesized;
import org.pkl.core.newparser.cst.Expr.QualifiedAccess;
import org.pkl.core.newparser.cst.Expr.Read;
import org.pkl.core.newparser.cst.Expr.ReadGlob;
import org.pkl.core.newparser.cst.Expr.ReadNull;
import org.pkl.core.newparser.cst.Expr.StringConstant;
import org.pkl.core.newparser.cst.Expr.Subscript;
import org.pkl.core.newparser.cst.Expr.SuperAccess;
import org.pkl.core.newparser.cst.Expr.SuperSubscript;
import org.pkl.core.newparser.cst.Expr.This;
import org.pkl.core.newparser.cst.Expr.Throw;
import org.pkl.core.newparser.cst.Expr.Trace;
import org.pkl.core.newparser.cst.Expr.TypeCast;
import org.pkl.core.newparser.cst.Expr.TypeCheck;
import org.pkl.core.newparser.cst.Expr.UnaryMinus;
import org.pkl.core.newparser.cst.Expr.UnqualifiedAccess;
import org.pkl.core.newparser.cst.ExtendsOrAmendsDecl;
import org.pkl.core.newparser.cst.Ident;
import org.pkl.core.newparser.cst.Import;
import org.pkl.core.newparser.cst.Modifier;
import org.pkl.core.newparser.cst.ModuleDecl;
import org.pkl.core.newparser.cst.Node;
import org.pkl.core.newparser.cst.ObjectBody;
import org.pkl.core.newparser.cst.ObjectMemberNode;
import org.pkl.core.newparser.cst.ObjectMemberNode.ForGenerator;
import org.pkl.core.newparser.cst.ObjectMemberNode.MemberPredicate;
import org.pkl.core.newparser.cst.ObjectMemberNode.MemberPredicateBody;
import org.pkl.core.newparser.cst.ObjectMemberNode.ObjectBodyProperty;
import org.pkl.core.newparser.cst.ObjectMemberNode.ObjectElement;
import org.pkl.core.newparser.cst.ObjectMemberNode.ObjectEntry;
import org.pkl.core.newparser.cst.ObjectMemberNode.ObjectEntryBody;
import org.pkl.core.newparser.cst.ObjectMemberNode.ObjectMethod;
import org.pkl.core.newparser.cst.ObjectMemberNode.ObjectProperty;
import org.pkl.core.newparser.cst.ObjectMemberNode.ObjectSpread;
import org.pkl.core.newparser.cst.ObjectMemberNode.WhenGenerator;
import org.pkl.core.newparser.cst.Parameter;
import org.pkl.core.newparser.cst.ParameterList;
import org.pkl.core.newparser.cst.QualifiedIdent;
import org.pkl.core.newparser.cst.ReplInput;
import org.pkl.core.newparser.cst.StringConstantPart;
import org.pkl.core.newparser.cst.StringPart;
import org.pkl.core.newparser.cst.Type;
import org.pkl.core.newparser.cst.Type.ConstrainedType;
import org.pkl.core.newparser.cst.Type.DeclaredType;
import org.pkl.core.newparser.cst.Type.DefaultUnionType;
import org.pkl.core.newparser.cst.Type.FunctionType;
import org.pkl.core.newparser.cst.Type.ModuleType;
import org.pkl.core.newparser.cst.Type.NothingType;
import org.pkl.core.newparser.cst.Type.NullableType;
import org.pkl.core.newparser.cst.Type.ParenthesizedType;
import org.pkl.core.newparser.cst.Type.StringConstantType;
import org.pkl.core.newparser.cst.Type.UnionType;
import org.pkl.core.newparser.cst.Type.UnknownType;
import org.pkl.core.newparser.cst.TypeAlias;
import org.pkl.core.newparser.cst.TypeAnnotation;
import org.pkl.core.newparser.cst.TypeParameter;
import org.pkl.core.newparser.cst.TypeParameterList;
import org.pkl.core.util.Nullable;

@SuppressWarnings("DataFlowIssue")
public class AbstractParserVisitor<T> implements ParserVisitor<T> {

  @Override
  public T visitUnknownType(UnknownType type) {
    return visitChildren(type);
  }

  @Override
  public T visitNothingType(NothingType type) {
    return visitChildren(type);
  }

  @Override
  public T visitModuleType(ModuleType type) {
    return visitChildren(type);
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
  public T visitThisExpr(This expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitOuterExpr(Outer expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitModuleExpr(Module expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitNullLiteralExpr(NullLiteral expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitBoolLiteralExpr(BoolLiteral expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitIntLiteralExpr(IntLiteral expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitFloatLiteralExpr(FloatLiteral expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitThrowExpr(Throw expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitTraceExpr(Trace expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitImportExpr(ImportExpr expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitReadExpr(Read expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitReadNullExpr(ReadNull expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitReadGlobExpr(ReadGlob expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitUnqualifiedAccessExpr(UnqualifiedAccess expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitStringConstantExpr(StringConstant expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitInterpolatedStringExpr(InterpolatedString expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitInterpolatedMultiStringExpr(InterpolatedMultiString expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitNewExpr(New expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitAmendsExpr(Amends expr) {
    return null;
  }

  @Override
  public T visitSuperAccessExpr(SuperAccess expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitSuperSubscriptExpr(SuperSubscript expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitQualifiedAccessExpr(QualifiedAccess expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitSubscriptExpr(Subscript expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitNonNullExpr(NonNull expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitUnaryMinusExpr(UnaryMinus expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitLogicalNotExpr(LogicalNot expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitBinaryOpExpr(BinaryOp expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitTypeCheckExpr(TypeCheck expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitTypeCastExpr(TypeCast expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitIfExpr(If expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitLetExpr(Let expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitFunctionLiteralExpr(FunctionLiteral expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitParenthesizedExpr(Parenthesized expr) {
    return visitChildren(expr);
  }

  @Override
  public T visitExpr(Expr expr) {
    return expr.accept(this);
  }

  @Override
  public T visitObjectProperty(ObjectProperty member) {
    return visitChildren(member);
  }

  @Override
  public T visitObjectBodyProperty(ObjectBodyProperty member) {
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
  public T visitMemberPredicateBody(MemberPredicateBody member) {
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
  public T visitObjectEntryBody(ObjectEntryBody member) {
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
  public T visitObjectMember(ObjectMemberNode member) {
    return member.accept(this);
  }

  @Override
  public T visitModule(org.pkl.core.newparser.cst.Module module) {
    return visitChildren(module);
  }

  @Override
  public T visitModuleDecl(ModuleDecl decl) {
    return visitChildren(decl);
  }

  @Override
  public T visitExtendsOrAmendsDecl(ExtendsOrAmendsDecl decl) {
    return visitChildren(decl);
  }

  @Override
  public T visitImport(Import imp) {
    return visitChildren(imp);
  }

  @Override
  public T visitClass(Clazz clazz) {
    return visitChildren(clazz);
  }

  @Override
  public T visitModifier(Modifier modifier) {
    return visitChildren(modifier);
  }

  @Override
  public T visitClassPropertyEntry(ClassPropertyEntry entry) {
    return visitChildren(entry);
  }

  @Override
  public T visitClassMethod(ClassMethod entry) {
    return visitChildren(entry);
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
    return visitChildren(part);
  }

  @Override
  public T visitClassBody(ClassBody classBody) {
    return visitChildren(classBody);
  }

  @Override
  public T visitDocComment(DocComment docComment) {
    return visitChildren(docComment);
  }

  @Override
  public T visitIdent(Ident ident) {
    return visitChildren(ident);
  }

  @Override
  public T visitQualifiedIdent(QualifiedIdent qualifiedIdent) {
    return visitChildren(qualifiedIdent);
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
    T result = null;
    for (var child : node.children()) {
      result = child.accept(this);
    }
    return result;
  }
}
