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
package org.pkl.core.newparser

import org.pkl.core.newparser.cst.*
import org.pkl.core.newparser.cst.Annotation
import org.pkl.core.newparser.cst.Expr.*
import org.pkl.core.newparser.cst.Expr.Module
import org.pkl.core.newparser.cst.ObjectMemberNode.*
import org.pkl.core.newparser.cst.Parameter.TypedIdent
import org.pkl.core.newparser.cst.Type.*

@Suppress("MemberVisibilityCanBePrivate")
class SexpRenderer {
  private var tab = ""
  private var buf = StringBuilder()

  fun render(mod: org.pkl.core.newparser.cst.Module): String {
    renderModule(mod)
    val res = buf.toString()
    reset()
    return res
  }

  fun renderModule(mod: org.pkl.core.newparser.cst.Module) {
    buf.append(tab)
    buf.append("(module")
    val oldTab = increaseTab()
    if (mod.decl !== null) {
      buf.append('\n')
      renderModuleDeclaration(mod.decl!!)
    }
    for (imp in mod.imports) {
      buf.append('\n')
      renderImport(imp)
    }
    for (entry in sortModuleEntries(mod)) {
      buf.append('\n')
      when (entry) {
        is Clazz -> renderClass(entry)
        is TypeAlias -> renderTypeAlias(entry)
        is ClassPropertyEntry -> renderClassPropertyEntry(entry)
        is ClassMethod -> renderClassMethod(entry)
      }
    }
    tab = oldTab
    buf.append(')')
  }

  fun renderModuleDeclaration(decl: ModuleDecl) {
    buf.append(tab)
    buf.append("(moduleHeader")
    val oldTab = increaseTab()
    if (decl.docComment !== null) {
      buf.append('\n')
      renderDocComment()
    }
    for (ann in decl.annotations) {
      buf.append('\n')
      renderAnnotation(ann)
    }
    for (mod in decl.modifiers) {
      buf.append('\n')
      renderModifier(mod)
    }
    if (decl.name !== null) {
      buf.append('\n')
      renderQualifiedIdent(decl.name!!)
    }
    if (decl.extendsOrAmendsDecl !== null) {
      buf.append('\n')
      buf.append(tab)
      buf.append("(extendsOrAmendsClause)")
    }
    tab = oldTab
    buf.append(')')
  }

  fun renderImport(imp: Import) {
    buf.append(tab)
    if (imp.isGlob) {
      buf.append("(importGlobClause")
    } else {
      buf.append("(importClause")
    }
    val oldTab = increaseTab()
    if (imp.alias !== null) {
      buf.append('\n')
      buf.append(tab)
      buf.append("(identifier)")
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderClass(clazz: Clazz) {
    buf.append(tab)
    buf.append("(clazz")
    val oldTab = increaseTab()
    if (clazz.docComment !== null) {
      buf.append('\n')
      renderDocComment()
    }
    for (ann in clazz.annotations) {
      buf.append('\n')
      renderAnnotation(ann)
    }
    for (mod in clazz.modifiers) {
      buf.append('\n')
      renderModifier(mod)
    }
    buf.append('\n')
    buf.append(tab)
    buf.append("(identifier)")
    val tparList = clazz.typeParameterList
    if (tparList !== null) {
      buf.append('\n')
      renderTypeParameterList(tparList)
    }
    if (clazz.superClass !== null) {
      buf.append('\n')
      renderType(clazz.superClass!!)
    }
    if (clazz.body !== null) {
      buf.append('\n')
      renderClassBody(clazz.body!!)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderClassBody(classBody: ClassBody) {
    buf.append(tab)
    buf.append("(classBody")
    val oldTab = increaseTab()
    for (entry in sortClassEntries(classBody)) {
      buf.append('\n')
      when (entry) {
        is ClassPropertyEntry -> renderClassPropertyEntry(entry)
        is ClassMethod -> renderClassMethod(entry)
      }
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderTypeAlias(`typealias`: TypeAlias) {
    buf.append(tab)
    buf.append("(typeAlias")
    val oldTab = increaseTab()
    if (`typealias`.docComment !== null) {
      buf.append('\n')
      renderDocComment()
    }
    for (ann in `typealias`.annotations) {
      buf.append('\n')
      renderAnnotation(ann)
    }
    for (mod in `typealias`.modifiers) {
      buf.append('\n')
      renderModifier(mod)
    }
    buf.append('\n')
    buf.append(tab)
    buf.append("(identifier)")
    val tparList = `typealias`.typeParameterList
    if (tparList !== null) {
      renderTypeParameterList(tparList)
    }
    buf.append('\n')
    renderType(`typealias`.type)
    buf.append(')')
    tab = oldTab
  }

  fun renderClassPropertyEntry(classEntry: ClassPropertyEntry) {
    buf.append(tab)
    buf.append("(classProperty")
    val oldTab = increaseTab()
    if (classEntry.docComment() !== null) {
      buf.append('\n')
      renderDocComment()
    }
    for (ann in classEntry.annotations()) {
      buf.append('\n')
      renderAnnotation(ann)
    }
    for (mod in classEntry.modifiers()) {
      buf.append('\n')
      renderModifier(mod)
    }
    buf.append('\n')
    buf.append(tab)
    buf.append("(identifier)")
    classEntry.typeAnnotation()?.let { typeAnnotation ->
      buf.append('\n')
      renderTypeAnnotation(typeAnnotation)
    }
    classEntry.expr()?.let { expr ->
      buf.append('\n')
      renderExpr(expr)
    }
    classEntry.bodyList()?.let { bodyList ->
      for (body in bodyList) {
        buf.append('\n')
        renderObjectBody(body)
      }
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderClassMethod(classMethod: ClassMethod) {
    buf.append(tab)
    buf.append("(classMethod")
    val oldTab = increaseTab()
    if (classMethod.docComment !== null) {
      buf.append('\n')
      renderDocComment()
    }
    for (ann in classMethod.annotations) {
      buf.append('\n')
      renderAnnotation(ann)
    }
    for (mod in classMethod.modifiers) {
      buf.append('\n')
      renderModifier(mod)
    }
    buf.append('\n')
    buf.append(tab)
    buf.append("(identifier)")
    val tparList = classMethod.typeParameterList
    if (tparList !== null) {
      renderTypeParameterList(tparList)
    }
    buf.append('\n')
    renderParameterList(classMethod.parameterList)
    if (classMethod.typeAnnotation !== null) {
      buf.append('\n')
      renderTypeAnnotation(classMethod.typeAnnotation!!)
    }
    if (classMethod.expr != null) {
      buf.append('\n')
      renderExpr(classMethod.expr!!)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderDocComment() {
    buf.append(tab)
    buf.append("(docComment)")
  }

  fun renderAnnotation(ann: Annotation) {
    buf.append(tab)
    buf.append("(annotation")
    val oldTab = increaseTab()
    buf.append('\n')
    renderQualifiedIdent(ann.name)
    if (ann.body !== null) {
      buf.append('\n')
      renderObjectBody(ann.body!!)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderQualifiedIdent(name: QualifiedIdent) {
    buf.append(tab)
    buf.append("(qualifiedIdentifier")
    val oldTab = increaseTab()
    for (i in name.idents.indices) {
      buf.append('\n')
      buf.append(tab)
      buf.append("(identifier)")
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderObjectBody(body: ObjectBody) {
    buf.append(tab)
    buf.append("(objectBody")
    val oldTab = increaseTab()
    for (par in body.pars) {
      buf.append('\n')
      renderParameter(par)
    }
    for (member in body.members) {
      buf.append('\n')
      renderMember(member)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderParameterList(parList: ParameterList) {
    buf.append(tab)
    buf.append("(parameterList")
    val oldTab = increaseTab()
    for (par in parList.parameters) {
      buf.append('\n')
      renderParameter(par)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderParameter(par: Parameter) {
    buf.append(tab)
    if (par is TypedIdent) {
      buf.append("(parameter")
      val oldTab = increaseTab()
      buf.append('\n')
      buf.append(tab)
      buf.append("(identifier)")
      if (par.typeAnnotation !== null) {
        buf.append('\n')
        renderTypeAnnotation(par.typeAnnotation!!)
      }
      buf.append(')')
      tab = oldTab
    } else {
      buf.append("(parameter)")
    }
  }

  fun renderArgumentList(argumentList: ArgumentList) {
    buf.append(tab)
    buf.append("(argumentList")
    val oldTab = increaseTab()
    for (arg in argumentList.args) {
      buf.append('\n')
      renderExpr(arg)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderExpr(expr: Expr) {
    when (expr) {
      is This -> {
        buf.append(tab)
        buf.append("(thisExpr)")
      }
      is Outer -> {
        buf.append(tab)
        buf.append("(outerExpr)")
      }
      is Module -> {
        buf.append(tab)
        buf.append("(moduleExpr)")
      }
      is NullLiteral -> {
        buf.append(tab)
        buf.append("(nullExpr)")
      }
      is BoolLiteral -> {
        buf.append(tab)
        buf.append("(boolLiteralExpr)")
      }
      is IntLiteral -> {
        buf.append(tab)
        buf.append("(intLiteralExpr)")
      }
      is FloatLiteral -> {
        buf.append(tab)
        buf.append("(floatLiteralExpr)")
      }
      is StringConstant -> {
        buf.append(tab)
        buf.append("(stringConstantExpr)")
      }
      is InterpolatedString -> renderInterpolatedStringExpr(expr)
      is InterpolatedMultiString -> renderInterpolatedMultiStringExpr(expr)
      is Throw -> renderThrowExpr(expr)
      is Trace -> renderTraceExpr(expr)
      is ImportExpr -> {
        buf.append(tab)
        val name = if (expr.isGlob) "(importGlobExpr)" else "(importExpr)"
        buf.append(name)
      }
      is Read -> renderReadExpr(expr)
      is ReadGlob -> renderReadGlobExpr(expr)
      is ReadNull -> renderReadNullExpr(expr)
      is UnqualifiedAccess -> renderUnqualifiedAccessExpr(expr)
      is QualifiedAccess -> renderQualifiedAccessExpr(expr)
      is SuperAccess -> renderSuperAccessExpr(expr)
      is SuperSubscript -> renderSuperSubscriptExpr(expr)
      is Subscript -> renderSubscriptExpr(expr)
      is If -> renderIfExpr(expr)
      is Let -> renderLetExpr(expr)
      is FunctionLiteral -> renderFunctionLiteralExpr(expr)
      is Parenthesized -> renderParenthesisedExpr(expr)
      is New -> renderNewExpr(expr)
      is Amends -> renderAmendsExpr(expr)
      is NonNull -> renderNonNullExpr(expr)
      is UnaryMinus -> renderUnaryMinusExpr(expr)
      is LogicalNot -> renderLogicalNotExpr(expr)
      is BinaryOp -> renderBinaryOpExpr(expr)
      is TypeCheck -> renderTypeCheckExpr(expr)
      is TypeCast -> renderTypeCastExpr(expr)
      is OperatorExpr -> throw RuntimeException("Operator expr should not exist after parsing")
      is TypeExpr -> throw RuntimeException("Type expr should not exist after parsing")
    }
  }

  fun renderInterpolatedStringExpr(expr: InterpolatedString) {
    buf.append(tab)
    buf.append("(interpolatedStringExpr")
    val oldTab = increaseTab()
    for (exp in expr.exprs) {
      buf.append('\n')
      renderExpr(exp)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderInterpolatedMultiStringExpr(expr: InterpolatedMultiString) {
    buf.append(tab)
    buf.append("(interpolatedMultiStringExpr")
    val oldTab = increaseTab()
    for (exp in expr.exprs) {
      buf.append('\n')
      renderExpr(exp)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderThrowExpr(expr: Throw) {
    buf.append(tab)
    buf.append("(throwExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderTraceExpr(expr: Trace) {
    buf.append(tab)
    buf.append("(traceExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderReadExpr(expr: Read) {
    buf.append(tab)
    buf.append("(readExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderReadGlobExpr(expr: ReadGlob) {
    buf.append(tab)
    buf.append("(readGlobExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderReadNullExpr(expr: ReadNull) {
    buf.append(tab)
    buf.append("(readNullExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderUnqualifiedAccessExpr(expr: UnqualifiedAccess) {
    buf.append(tab)
    buf.append("(unqualifiedAccessExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    buf.append(tab)
    buf.append("(identifier)")
    if (expr.argumentList !== null) {
      buf.append('\n')
      renderArgumentList(expr.argumentList!!)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderQualifiedAccessExpr(expr: QualifiedAccess) {
    buf.append(tab)
    buf.append(if (expr.isNullable) "(nullableQualifiedAccessExpr" else "(qualifiedAccessExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append('\n')
    buf.append(tab)
    buf.append("(identifier)")
    if (expr.argumentList !== null) {
      buf.append('\n')
      renderArgumentList(expr.argumentList!!)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderSuperAccessExpr(expr: SuperAccess) {
    buf.append(tab)
    buf.append("(superAccessExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    buf.append("(identifier)")
    if (expr.argumentList !== null) {
      buf.append('\n')
      renderArgumentList(expr.argumentList!!)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderSuperSubscriptExpr(expr: SuperSubscript) {
    buf.append(tab)
    buf.append("(superSubscriptExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.arg)
    buf.append(')')
    tab = oldTab
  }

  fun renderSubscriptExpr(expr: Subscript) {
    buf.append(tab)
    buf.append("(subscriptExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append('\n')
    renderExpr(expr.arg)
    buf.append(')')
    tab = oldTab
  }

  fun renderIfExpr(expr: If) {
    buf.append(tab)
    buf.append("(ifExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.cond)
    buf.append('\n')
    renderExpr(expr.then)
    buf.append('\n')
    renderExpr(expr.els)
    buf.append(')')
    tab = oldTab
  }

  fun renderLetExpr(expr: Let) {
    buf.append(tab)
    buf.append("(letExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderParameter(expr.par)
    buf.append('\n')
    renderExpr(expr.bindingExpr)
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderFunctionLiteralExpr(expr: FunctionLiteral) {
    buf.append(tab)
    buf.append("(functionLiteralExpr")
    val oldTab = increaseTab()
    renderParameterList(expr.parameterList)
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderParenthesisedExpr(expr: Parenthesized) {
    buf.append(tab)
    buf.append("(parenthesizedExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderNewExpr(expr: New) {
    buf.append(tab)
    buf.append("(newExpr")
    val oldTab = increaseTab()
    if (expr.type != null) {
      buf.append('\n')
      renderType(expr.type!!)
    }
    buf.append('\n')
    renderObjectBody(expr.body)
    buf.append(')')
    tab = oldTab
  }

  fun renderAmendsExpr(expr: Amends) {
    buf.append(tab)
    buf.append("(amendsExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append('\n')
    renderObjectBody(expr.body)
    buf.append(')')
    tab = oldTab
  }

  fun renderNonNullExpr(expr: NonNull) {
    buf.append(tab)
    buf.append("(nonNullExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderUnaryMinusExpr(expr: UnaryMinus) {
    buf.append(tab)
    buf.append("(unaryMinusExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderLogicalNotExpr(expr: LogicalNot) {
    buf.append(tab)
    buf.append("(logicalNotExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderBinaryOpExpr(expr: BinaryOp) {
    buf.append(tab)
    val name =
      when (expr.op) {
        Operator.POW -> "(exponentiationExpr"
        Operator.MULT,
        Operator.DIV,
        Operator.INT_DIV,
        Operator.MOD -> "(multiplicativeExpr"
        Operator.PLUS,
        Operator.MINUS -> "(additiveExpr"
        Operator.LT,
        Operator.GT,
        Operator.LTE,
        Operator.GTE -> "(comparisonExpr"
        Operator.IS,
        Operator.AS -> "(typeTestExpr"
        Operator.EQ_EQ,
        Operator.NOT_EQ -> "(equalityExpr"
        Operator.AND -> "(logicalAndExpr"
        Operator.OR -> "(logicalOrExpr"
        Operator.PIPE -> "(pipeExpr"
        Operator.NULL_COALESCE -> "(nullCoalesceExpr"
        else -> throw RuntimeException("Should never receive a dot operator here")
      }
    buf.append(name)
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.left)
    buf.append('\n')
    renderExpr(expr.right)
    buf.append(')')
    tab = oldTab
  }

  fun renderTypeCheckExpr(expr: TypeCheck) {
    buf.append(tab)
    buf.append("(typeCheckExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append('\n')
    renderType(expr.type)
    buf.append(')')
    tab = oldTab
  }

  fun renderTypeCastExpr(expr: TypeCast) {
    buf.append(tab)
    buf.append("(typeCastExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append('\n')
    renderType(expr.type)
    buf.append(')')
    tab = oldTab
  }

  fun renderTypeAnnotation(typeAnnotation: TypeAnnotation) {
    buf.append(tab)
    buf.append("(typeAnnotation")
    val oldTab = increaseTab()
    buf.append('\n')
    renderType(typeAnnotation.type)
    buf.append(')')
    tab = oldTab
  }

  fun renderType(type: Type) {
    when (type) {
      is UnknownType -> {
        buf.append(tab)
        buf.append("(unknownType)")
      }
      is NothingType -> {
        buf.append(tab)
        buf.append("(nothingType)")
      }
      is ModuleType -> {
        buf.append(tab)
        buf.append("(moduleType)")
      }
      is StringConstantType -> {
        buf.append(tab)
        buf.append("(stringConstantType)")
      }
      is DeclaredType -> renderDeclaredType(type)
      is ParenthesizedType -> renderParenthesizedType(type)
      is NullableType -> renderNullableType(type)
      is ConstrainedType -> renderConstrainedType(type)
      is DefaultUnionType -> renderDefaultUnionType(type)
      is UnionType -> renderUnionType(type)
      is FunctionType -> renderFunctionType(type)
    }
  }

  fun renderDeclaredType(type: DeclaredType) {
    buf.append(tab)
    buf.append("(declaredType")
    val oldTab = increaseTab()
    buf.append('\n')
    renderQualifiedIdent(type.name)
    for (arg in type.args) {
      buf.append('\n')
      renderType(arg)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderParenthesizedType(type: ParenthesizedType) {
    buf.append(tab)
    buf.append("(parenthesisedType")
    val oldTab = increaseTab()
    buf.append('\n')
    renderType(type.type)
    buf.append(')')
    tab = oldTab
  }

  fun renderNullableType(type: NullableType) {
    buf.append(tab)
    buf.append("(nullableType")
    val oldTab = increaseTab()
    buf.append('\n')
    renderType(type.type)
    buf.append(')')
    tab = oldTab
  }

  fun renderConstrainedType(type: ConstrainedType) {
    buf.append(tab)
    buf.append("(constrainedType")
    val oldTab = increaseTab()
    buf.append('\n')
    renderType(type.type)
    for (expr in type.expr) {
      buf.append('\n')
      renderExpr(expr)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderDefaultUnionType(type: DefaultUnionType) {
    buf.append(tab)
    buf.append("(defaultUnionType")
    val oldTab = increaseTab()
    buf.append('\n')
    renderType(type.type)
    buf.append(')')
    tab = oldTab
  }

  fun renderUnionType(type: UnionType) {
    buf.append(tab)
    buf.append("(unionType")
    val oldTab = increaseTab()
    buf.append('\n')
    renderType(type.left)
    buf.append('\n')
    renderType(type.right)
    buf.append(')')
    tab = oldTab
  }

  fun renderFunctionType(type: FunctionType) {
    buf.append(tab)
    buf.append("(functionType")
    val oldTab = increaseTab()
    for (arg in type.args) {
      buf.append('\n')
      renderType(arg)
    }
    buf.append('\n')
    renderType(type.ret)
    buf.append(')')
    tab = oldTab
  }

  fun renderMember(member: ObjectMemberNode) {
    when (member) {
      is ObjectElement -> renderObjectElement(member)
      is ObjectProperty -> renderObjectProperty(member)
      is ObjectBodyProperty -> renderObjectBodyProperty(member)
      is ObjectMethod -> renderObjectMethod(member)
      is MemberPredicate -> renderMemberPredicate(member)
      is MemberPredicateBody -> renderMemberPredicateBody(member)
      is ObjectEntry -> renderObjectEntry(member)
      is ObjectEntryBody -> renderObjectEntryBody(member)
      is ObjectSpread -> renderObjectSpread(member)
      is WhenGenerator -> renderWhenGenerator(member)
      is ForGenerator -> renderForGenerator(member)
    }
  }

  fun renderObjectElement(element: ObjectElement) {
    buf.append(tab)
    buf.append("(objectElement")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(element.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderObjectProperty(property: ObjectProperty) {
    buf.append(tab)
    buf.append("(objectProperty")
    val oldTab = increaseTab()
    for (mod in property.modifiers) {
      buf.append('\n')
      renderModifier(mod)
    }
    buf.append('\n')
    buf.append(tab)
    buf.append("(identifier)")
    if (property.typeAnnotation !== null) {
      buf.append('\n')
      renderTypeAnnotation(property.typeAnnotation!!)
    }
    buf.append('\n')
    renderExpr(property.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderObjectBodyProperty(property: ObjectBodyProperty) {
    buf.append(tab)
    buf.append("(objectProperty")
    val oldTab = increaseTab()
    for (mod in property.modifiers) {
      buf.append('\n')
      renderModifier(mod)
    }
    buf.append('\n')
    buf.append(tab)
    buf.append("(identifier)")
    for (body in property.bodyList) {
      buf.append('\n')
      renderObjectBody(body)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderObjectMethod(method: ObjectMethod) {
    buf.append(tab)
    buf.append("(objectMethod")
    val oldTab = increaseTab()
    buf.append('\n')
    for (mod in method.modifiers) {
      buf.append('\n')
      renderModifier(mod)
    }
    buf.append('\n')
    buf.append("(identifier)")
    val tparList = method.typeParameterList
    if (tparList !== null) {
      renderTypeParameterList(tparList)
    }
    buf.append('\n')
    renderParameterList(method.paramList)
    if (method.typeAnnotation !== null) {
      buf.append('\n')
      renderTypeAnnotation(method.typeAnnotation!!)
    }
    buf.append('\n')
    renderExpr(method.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderMemberPredicate(predicate: MemberPredicate) {
    buf.append(tab)
    buf.append("(memberPredicate")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(predicate.pred)
    buf.append('\n')
    renderExpr(predicate.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderMemberPredicateBody(predicate: MemberPredicateBody) {
    buf.append(tab)
    buf.append("(memberPredicate")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(predicate.key)
    for (body in predicate.bodyList) {
      buf.append('\n')
      renderObjectBody(body)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderObjectEntry(entry: ObjectEntry) {
    buf.append(tab)
    buf.append("(objectEntry")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(entry.key)
    buf.append('\n')
    renderExpr(entry.value)
    buf.append(')')
    tab = oldTab
  }

  fun renderObjectEntryBody(entry: ObjectEntryBody) {
    buf.append(tab)
    buf.append("(objectEntry")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(entry.key)
    for (body in entry.bodyList) {
      buf.append('\n')
      renderObjectBody(body)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderObjectSpread(spread: ObjectSpread) {
    buf.append(tab)
    buf.append("(objectSpread")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(spread.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderWhenGenerator(generator: WhenGenerator) {
    buf.append(tab)
    buf.append("(whenGenerator")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(generator.cond)
    buf.append('\n')
    renderObjectBody(generator.body)
    if (generator.elseClause !== null) {
      buf.append('\n')
      renderObjectBody(generator.elseClause!!)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderForGenerator(generator: ForGenerator) {
    buf.append(tab)
    buf.append("(forGenerator")
    val oldTab = increaseTab()
    buf.append('\n')
    renderParameter(generator.p1)
    if (generator.p2 != null) {
      buf.append('\n')
      renderParameter(generator.p2!!)
    }
    buf.append('\n')
    renderExpr(generator.expr)
    buf.append('\n')
    renderObjectBody(generator.body)
    buf.append(')')
    tab = oldTab
  }

  fun renderTypeParameterList(typeParameterList: TypeParameterList) {
    buf.append(tab)
    buf.append("(TypeParameterList\n")
    val oldTab = increaseTab()
    for (tpar in typeParameterList.params) {
      buf.append('\n')
      renderTypeParameter(tpar)
    }
    tab = oldTab
  }

  @Suppress("UNUSED_PARAMETER")
  fun renderTypeParameter(ignored: TypeParameter?) {
    buf.append(tab)
    buf.append("(TypeParameter\n")
    val oldTab = increaseTab()
    buf.append(tab)
    buf.append("(identifier))")
    tab = oldTab
  }

  @Suppress("UNUSED_PARAMETER")
  fun renderModifier(ignored: Modifier?) {
    buf.append(tab)
    buf.append("(modifier)")
  }

  fun reset() {
    tab = ""
    buf = StringBuilder()
  }

  private fun increaseTab(): String {
    val old = tab
    tab += "  "
    return old
  }

  companion object {
    private fun sortModuleEntries(mod: org.pkl.core.newparser.cst.Module): List<Node> {
      val res = mutableListOf<Node>()
      res += mod.classes
      res += mod.typeAliases
      res += mod.properties
      res += mod.methods
      res.sortWith(compareBy { it.span().charIndex })
      return res
    }

    private fun sortClassEntries(body: ClassBody): List<Node> {
      val res = mutableListOf<Node>()
      res += body.properties
      res += body.methods
      res.sortWith(compareBy { it.span().charIndex })
      return res
    }
  }
}
