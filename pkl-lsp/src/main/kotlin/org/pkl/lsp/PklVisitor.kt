/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.lsp

import org.pkl.lsp.ast.*

open class PklVisitor<R> : NodeVisitor() {

  open fun visitAccessExpr(o: PklAccessExpr): R? {
    return visitExpr(o)
  }

  open fun visitAdditiveExpr(o: PklAdditiveExpr): R? {
    return visitExpr(o)
  }

  open fun visitAmendEpxr(o: PklAmendExpr): R? {
    return visitExpr(o)
  }

  open fun visitAnnotation(o: PklAnnotation): R? {
    return visitObjectBodyOwner(o)
  }

  open fun visitArgumentList(o: PklArgumentList): R? {
    return visitElement(o)
  }

  open fun visitClass(o: PklClass): R? {
    return visitModuleMember(o)
  }

  open fun visitClassBody(o: PklClassBody): R? {
    return visitElement(o)
  }

  open fun visitClassHeader(o: PklClassHeader): R? {
    return visitModifierListOwner(o)
  }

  open fun visitClassMember(o: PklClassMember): R? {
    return visitModuleMember(o)
  }

  open fun visitClassMethod(o: PklClassMethod): R? {
    return visitClassMember(o)
  }

  open fun visitClassProperty(o: PklClassProperty): R? {
    return visitClassMember(o)
  }

  open fun visitComparisonExpr(o: PklComparisonExpr): R? {
    return visitExpr(o)
  }

  open fun visitConstrainedType(o: PklConstrainedType): R? {
    return visitType(o)
  }

  open fun visitDeclaredType(o: PklDeclaredType): R? {
    return visitType(o)
  }

  open fun visitDefaultUnionType(o: PklDefaultUnionType): R? {
    return visitType(o)
  }

  open fun visitElement(o: Node): R? {
    visitInnerElement(o)
    return null
  }

  open fun visitEqualityExpr(o: PklEqualityExpr): R? {
    return visitExpr(o)
  }

  open fun visitExponentiationExpr(o: PklExponentiationExpr): R? {
    return visitExpr(o)
  }

  open fun visitExpr(o: PklExpr): R? {
    return visitElement(o)
  }

  open fun visitFalseLiteralExpr(o: PklFalseLiteralExpr): R? {
    return visitExpr(o)
  }

  open fun visitFloatLiteralExpr(o: PklFloatLiteralExpr): R? {
    return visitExpr(o)
  }

  open fun visitForGenerator(o: PklForGenerator): R? {
    return visitObjectMember(o)
  }

  open fun visitFunctionLiteral(o: PklFunctionLiteralExpr): R? {
    return visitExpr(o)
  }

  open fun visitFunctionType(o: PklFunctionType): R? {
    return visitType(o)
  }

  open fun visitIdentifierOwner(o: IdentifierOwner): R? {
    return visitElement(o)
  }

  open fun visitIfExpr(o: PklIfExpr): R? {
    return visitExpr(o)
  }

  open fun visitImport(o: PklImport): R? {
    return visitElement(o)
  }

  open fun visitImportExpr(o: PklImportExpr): R? {
    return visitExpr(o)
  }

  open fun visitIntLiteralExpr(o: PklIntLiteralExpr): R? {
    return visitExpr(o)
  }

  open fun visitLetExpr(o: PklLetExpr): R? {
    return visitExpr(o)
  }

  open fun visitLogicalAndExpr(o: PklLogicalAndExpr): R? {
    return visitExpr(o)
  }

  open fun visitLogicalNotExpr(o: PklLogicalNotExpr): R? {
    return visitExpr(o)
  }

  open fun visitLogicalOrExpr(o: PklLogicalOrExpr): R? {
    return visitExpr(o)
  }

  open fun visitMemberPredicate(o: PklMemberPredicate): R? {
    return visitObjectMember(o)
  }

  open fun visitMethodHeader(o: PklMethodHeader): R? {
    return visitModifierListOwner(o)
  }

  open fun visitMlStringLiteral(o: PklMultiLineStringLiteral): R? {
    return visitExpr(o)
  }

  open fun visitModifierListOwner(o: ModifierListOwner): R? {
    return visitElement(o)
  }

  open fun visitModule(o: PklModule): R? {
    return visitElement(o)
  }

  open fun visitModuleDeclaration(o: PklModuleDeclaration): R? {
    return visitDocCommentOwner(o)
  }

  open fun visitModuleExpr(o: PklModuleExpr): R? {
    return visitExpr(o)
  }

  open fun visitModuleExtendsAmendsClause(o: PklModuleExtendsAmendsClause): R? {
    return visitElement(o)
  }

  open fun visitModuleHeader(o: PklModuleHeader): R? {
    return visitElement(o)
  }

  open fun visitModuleMember(o: PklModuleMember): R? {
    return visitElement(o)
  }

  open fun visitModuleType(o: PklModuleType): R? {
    return visitType(o)
  }

  open fun visitModuleUri(o: PklModuleUri): R? {
    return visitElement(o)
  }

  open fun visitMlStringPart(o: MultiLineStringPart): R? {
    return visitElement(o)
  }

  open fun visitMultiplicativeExpr(o: PklMultiplicativeExpr): R? {
    return visitExpr(o)
  }

  open fun visitNewExpr(o: PklNewExpr): R? {
    return visitExpr(o)
  }

  open fun visitNonNullExpr(o: PklNonNullExpr): R? {
    return visitExpr(o)
  }

  open fun visitNothingType(o: PklNothingType): R? {
    return visitType(o)
  }

  open fun visitNullCoalesceExpr(o: PklNullCoalesceExpr): R? {
    return visitExpr(o)
  }

  open fun visitNullLiteralExpr(o: PklNullLiteralExpr): R? {
    return visitExpr(o)
  }

  open fun visitNullableType(o: PklNullableType): R? {
    return visitType(o)
  }

  open fun visitObjectBody(o: PklObjectBody): R? {
    return visitElement(o)
  }

  open fun visitObjectBodyOwner(o: PklObjectBodyOwner): R? {
    return visitElement(o)
  }

  open fun visitObjectElement(o: PklObjectElement): R? {
    return visitObjectMember(o)
  }

  open fun visitObjectEntry(o: PklObjectEntry): R? {
    return visitObjectMember(o)
  }

  open fun visitObjectMember(o: PklObjectMember): R? {
    return visitElement(o)
  }

  open fun visitObjectMethod(o: PklObjectMethod): R? {
    return visitObjectMember(o)
  }

  open fun visitObjectProperty(o: PklObjectProperty): R? {
    return visitObjectMember(o)
  }

  open fun visitObjectSpread(o: PklObjectSpread): R? {
    return visitObjectMember(o)
  }

  open fun visitOuterExpr(o: PklOuterExpr): R? {
    return visitExpr(o)
  }

  open fun visitParenthesizedExpr(o: PklParenthesizedExpr): R? {
    return visitExpr(o)
  }

  open fun visitParenthesizedType(o: PklParenthesizedType): R? {
    return visitType(o)
  }

  open fun visitParameter(o: PklParameter): R? {
    return visitElement(o)
  }

  open fun visitParameterList(o: PklParameterList): R? {
    return visitElement(o)
  }

  open fun visitPipeExpr(o: PklPipeExpr): R? {
    return visitExpr(o)
  }

  open fun visitQualifiedAccessExpr(o: PklQualifiedAccessExpr): R? {
    return visitAccessExpr(o)
  }

  open fun visitQualifiedIdentifier(o: PklQualifiedIdentifier): R? {
    return visitElement(o)
  }

  open fun visitReadExpr(o: PklReadExpr): R? {
    return visitExpr(o)
  }

  open fun visitSimpleTypeName(o: PklSimpleTypeName): R? {
    return visitIdentifierOwner(o)
  }

  open fun visitModuleName(o: PklModuleName): R? {
    return visitIdentifierOwner(o)
  }

  open fun visitSingleLineStringPart(o: SingleLineStringPart): R? {
    return visitElement(o)
  }

  open fun visitStringConstant(o: PklStringConstant): R? {
    return visitElement(o)
  }

  open fun visitStringLiteral(o: PklSingleLineStringLiteral): R? {
    return visitExpr(o)
  }

  open fun visitStringLiteralType(o: PklStringLiteralType): R? {
    return visitType(o)
  }

  open fun visitSubscriptExpr(o: PklSubscriptExpr): R? {
    return visitExpr(o)
  }

  open fun visitSuperAccessExpr(o: PklSuperAccessExpr): R? {
    return visitAccessExpr(o)
  }

  open fun visitSuperSubscriptExpr(o: PklSuperSubscriptExpr): R? {
    return visitExpr(o)
  }

  open fun visitTerminal(o: Terminal): R? {
    return visitElement(o)
  }

  open fun visitThisExpr(o: PklThisExpr): R? {
    return visitExpr(o)
  }

  open fun visitThrowExpr(o: PklThrowExpr): R? {
    return visitExpr(o)
  }

  open fun visitTraceExpr(o: PklTraceExpr): R? {
    return visitExpr(o)
  }

  open fun visitTrueLiteralExpr(o: PklTrueLiteralExpr): R? {
    return visitExpr(o)
  }

  open fun visitType(o: PklType): R? {
    return visitElement(o)
  }

  open fun visitTypeAlias(o: PklTypeAlias): R? {
    return visitModuleMember(o)
  }

  open fun visitTypeAliasHeader(o: PklTypeAliasHeader): R? {
    return visitModifierListOwner(o)
  }

  open fun visitTypeAnnotation(o: PklTypeAnnotation): R? {
    return visitElement(o)
  }

  open fun visitTypeArgumentList(o: PklTypeArgumentList): R? {
    return visitElement(o)
  }

  open fun visitTypeName(o: PklTypeName): R? {
    return visitElement(o)
  }

  open fun visitTypeParameter(o: PklTypeParameter): R? {
    return visitElement(o)
  }

  open fun visitTypeParameterList(o: PklTypeParameterList): R? {
    return visitElement(o)
  }

  open fun visitTypeTestExpr(o: PklTypeTestExpr): R? {
    return visitExpr(o)
  }

  open fun visitTypedIdentifier(o: PklTypedIdentifier): R? {
    return visitIdentifierOwner(o)
  }

  open fun visitUnaryMinusExpr(o: PklUnaryMinusExpr): R? {
    return visitExpr(o)
  }

  open fun visitUnionType(o: PklUnionType): R? {
    return visitType(o)
  }

  open fun visitUnknownType(o: PklUnknownType): R? {
    return visitType(o)
  }

  open fun visitUnqualifiedAccessExpr(o: PklUnqualifiedAccessExpr): R? {
    return visitAccessExpr(o)
  }

  open fun visitWhenGenerator(o: PklWhenGenerator): R? {
    return visitObjectMember(o)
  }

  open fun visitDocCommentOwner(o: PklDocCommentOwner): R? {
    return visitElement(o)
  }
}
