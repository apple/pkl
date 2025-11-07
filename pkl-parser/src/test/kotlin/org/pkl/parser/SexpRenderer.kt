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
package org.pkl.parser

import org.pkl.parser.syntax.*
import org.pkl.parser.syntax.Annotation
import org.pkl.parser.syntax.Expr.*
import org.pkl.parser.syntax.Expr.ModuleExpr
import org.pkl.parser.syntax.ObjectMember.*
import org.pkl.parser.syntax.Parameter.TypedIdentifier
import org.pkl.parser.syntax.Type.*

@Suppress("MemberVisibilityCanBePrivate")
class SexpRenderer {
  private var tab = ""
  private var buf = StringBuilder()

  fun render(mod: Module): String {
    renderModule(mod)
    val res = buf.toString()
    reset()
    return res
  }

  fun renderModule(mod: Module) {
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
        is Class -> renderClass(entry)
        is TypeAlias -> renderTypeAlias(entry)
        is ClassProperty -> renderClassPropertyEntry(entry)
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
      renderExtendsOrAmendsClause(decl.extendsOrAmendsDecl!!)
    }
    tab = oldTab
    buf.append(')')
  }

  fun renderExtendsOrAmendsClause(clause: ExtendsOrAmendsClause) {
    buf.append(tab)
    buf.append("(extendsOrAmendsClause")
    val oldTab = increaseTab()
    buf.append('\n')
    renderStringConstant(clause.url)
    tab = oldTab
    buf.append(')')
  }

  fun renderImport(imp: ImportClause) {
    buf.append(tab)
    if (imp.isGlob) {
      buf.append("(importGlobClause")
    } else {
      buf.append("(importClause")
    }
    val oldTab = increaseTab()
    buf.append('\n')
    renderStringConstant(imp.importStr)
    if (imp.alias !== null) {
      buf.append('\n')
      buf.append(tab)
      buf.append("(identifier)")
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderClass(clazz: Class) {
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
        is ClassProperty -> renderClassPropertyEntry(entry)
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
      buf.append('\n')
      renderTypeParameterList(tparList)
    }
    buf.append('\n')
    renderType(`typealias`.type)
    buf.append(')')
    tab = oldTab
  }

  fun renderClassPropertyEntry(classEntry: ClassProperty) {
    buf.append(tab)
    buf.append("(classProperty")
    val oldTab = increaseTab()
    if (classEntry.docComment !== null) {
      buf.append('\n')
      renderDocComment()
    }
    for (ann in classEntry.annotations) {
      buf.append('\n')
      renderAnnotation(ann)
    }
    for (mod in classEntry.modifiers) {
      buf.append('\n')
      renderModifier(mod)
    }
    buf.append('\n')
    buf.append(tab)
    buf.append("(identifier)")
    classEntry.typeAnnotation?.let { typeAnnotation ->
      buf.append('\n')
      renderTypeAnnotation(typeAnnotation)
    }
    classEntry.expr?.let { expr ->
      buf.append('\n')
      renderExpr(expr)
    }
    classEntry.bodyList?.let { bodyList ->
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
      buf.append('\n')
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
    renderType(ann.type)
    if (ann.body !== null) {
      buf.append('\n')
      renderObjectBody(ann.body!!)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderQualifiedIdent(name: QualifiedIdentifier) {
    buf.append(tab)
    buf.append("(qualifiedIdentifier")
    val oldTab = increaseTab()
    for (i in name.identifiers.indices) {
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
    for (par in body.parameters) {
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
    if (par is TypedIdentifier) {
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
    for (arg in argumentList.arguments) {
      buf.append('\n')
      renderExpr(arg)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderExpr(expr: Expr) {
    when (expr) {
      is ThisExpr -> {
        buf.append(tab)
        buf.append("(thisExpr)")
      }
      is OuterExpr -> {
        buf.append(tab)
        buf.append("(outerExpr)")
      }
      is ModuleExpr -> {
        buf.append(tab)
        buf.append("(moduleExpr)")
      }
      is NullLiteralExpr -> {
        buf.append(tab)
        buf.append("(nullExpr)")
      }
      is BoolLiteralExpr -> {
        buf.append(tab)
        buf.append("(boolLiteralExpr)")
      }
      is IntLiteralExpr -> {
        buf.append(tab)
        buf.append("(intLiteralExpr)")
      }
      is FloatLiteralExpr -> {
        buf.append(tab)
        buf.append("(floatLiteralExpr)")
      }
      is SingleLineStringLiteralExpr -> renderSingleLineStringLiteral(expr)
      is MultiLineStringLiteralExpr -> renderMultiLineStringLiteral(expr)
      is ThrowExpr -> renderThrowExpr(expr)
      is TraceExpr -> renderTraceExpr(expr)
      is ImportExpr -> renderImportExpr(expr)
      is ReadExpr -> renderReadExpr(expr)
      is UnqualifiedAccessExpr -> renderUnqualifiedAccessExpr(expr)
      is QualifiedAccessExpr -> renderQualifiedAccessExpr(expr)
      is SuperAccessExpr -> renderSuperAccessExpr(expr)
      is SuperSubscriptExpr -> renderSuperSubscriptExpr(expr)
      is SubscriptExpr -> renderSubscriptExpr(expr)
      is IfExpr -> renderIfExpr(expr)
      is LetExpr -> renderLetExpr(expr)
      is FunctionLiteralExpr -> renderFunctionLiteralExpr(expr)
      is ParenthesizedExpr -> renderParenthesizedExpr(expr)
      is NewExpr -> renderNewExpr(expr)
      is AmendsExpr -> renderAmendsExpr(expr)
      is NonNullExpr -> renderNonNullExpr(expr)
      is UnaryMinusExpr -> renderUnaryMinusExpr(expr)
      is LogicalNotExpr -> renderLogicalNotExpr(expr)
      is BinaryOperatorExpr -> renderBinaryOpExpr(expr)
      is TypeCheckExpr -> renderTypeCheckExpr(expr)
      is TypeCastExpr -> renderTypeCastExpr(expr)
    }
  }

  fun renderSingleLineStringLiteral(expr: SingleLineStringLiteralExpr) {
    buf.append(tab)
    buf.append("(singleLineStringLiteralExpr")
    val oldTab = increaseTab()
    for (part in expr.parts) {
      if (part is StringPart.StringInterpolation) {
        buf.append('\n')
        renderExpr(part.expr)
      } else {
        buf.append('\n').append(tab)
        buf.append("(stringChars)")
      }
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderMultiLineStringLiteral(expr: MultiLineStringLiteralExpr) {
    buf.append(tab)
    buf.append("(multiLineStringLiteralExpr")
    val oldTab = increaseTab()
    // render only interpolated expressions because
    // the new parser parses string differently
    for (part in expr.parts) {
      if (part is StringPart.StringInterpolation) {
        buf.append('\n')
        renderExpr(part.expr)
      }
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderThrowExpr(expr: ThrowExpr) {
    buf.append(tab)
    buf.append("(throwExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderTraceExpr(expr: TraceExpr) {
    buf.append(tab)
    buf.append("(traceExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderReadExpr(expr: ReadExpr) {
    val name =
      when (expr.readType) {
        ReadType.READ -> "(readExpr"
        ReadType.GLOB -> "(readGlobExpr"
        ReadType.NULL -> "(readNullExpr"
      }
    buf.append(tab)
    buf.append(name)
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderImportExpr(expr: ImportExpr) {
    buf.append(tab)
    val name = if (expr.isGlob) "(importGlobExpr" else "(importExpr"
    buf.append(name)
    val oldTab = increaseTab()
    buf.append('\n')
    renderStringConstant(expr.importStr)
    buf.append(')')
    tab = oldTab
  }

  fun renderUnqualifiedAccessExpr(expr: UnqualifiedAccessExpr) {
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

  fun renderUnqualifiedAccessExprOfQualified(expr: QualifiedAccessExpr) {
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

  fun renderQualifiedAccessExpr(expr: QualifiedAccessExpr) {
    buf.append(tab)
    buf.append(if (expr.isNullable) "(nullableQualifiedAccessExpr" else "(qualifiedAccessExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append('\n')
    renderUnqualifiedAccessExprOfQualified(expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderSuperAccessExpr(expr: SuperAccessExpr) {
    buf.append(tab)
    buf.append("(superAccessExpr")
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

  fun renderSuperSubscriptExpr(expr: SuperSubscriptExpr) {
    buf.append(tab)
    buf.append("(superSubscriptExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.arg)
    buf.append(')')
    tab = oldTab
  }

  fun renderSubscriptExpr(expr: SubscriptExpr) {
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

  fun renderIfExpr(expr: IfExpr) {
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

  fun renderLetExpr(expr: LetExpr) {
    buf.append(tab)
    buf.append("(letExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderParameter(expr.parameter)
    buf.append('\n')
    renderExpr(expr.bindingExpr)
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderFunctionLiteralExpr(expr: FunctionLiteralExpr) {
    buf.append(tab)
    buf.append("(functionLiteralExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderParameterList(expr.parameterList)
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderParenthesizedExpr(expr: ParenthesizedExpr) {
    buf.append(tab)
    buf.append("(parenthesizedExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderNewExpr(expr: NewExpr) {
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

  fun renderAmendsExpr(expr: AmendsExpr) {
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

  fun renderNonNullExpr(expr: NonNullExpr) {
    buf.append(tab)
    buf.append("(nonNullExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderUnaryMinusExpr(expr: UnaryMinusExpr) {
    buf.append(tab)
    buf.append("(unaryMinusExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderLogicalNotExpr(expr: LogicalNotExpr) {
    buf.append(tab)
    buf.append("(logicalNotExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr)
    buf.append(')')
    tab = oldTab
  }

  fun renderBinaryOpExpr(expr: BinaryOperatorExpr) {
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

  fun renderTypeCheckExpr(expr: TypeCheckExpr) {
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

  fun renderTypeCastExpr(expr: TypeCastExpr) {
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

  fun renderStringConstant(str: StringConstant) {
    buf.append(tab)
    buf.append("(stringChars)")
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
      is StringConstantType -> renderStringConstantType(type)
      is DeclaredType -> renderDeclaredType(type)
      is ParenthesizedType -> renderParenthesizedType(type)
      is NullableType -> renderNullableType(type)
      is ConstrainedType -> renderConstrainedType(type)
      is UnionType -> renderUnionType(type)
      is FunctionType -> renderFunctionType(type)
    }
  }

  fun renderStringConstantType(type: StringConstantType) {
    buf.append(tab)
    buf.append("(stringConstantType")
    val oldTab = increaseTab()
    buf.append('\n')
    renderStringConstant(type.str)
    buf.append(')')
    tab = oldTab
  }

  fun renderDeclaredType(type: DeclaredType) {
    buf.append(tab)
    buf.append("(declaredType")
    val oldTab = increaseTab()
    buf.append('\n')
    renderQualifiedIdent(type.name)
    if (type.args !== null) {
      buf.append('\n')
      renderTypeArgumentList(type.args!!)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderTypeArgumentList(typeArgumentList: TypeArgumentList) {
    buf.append(tab)
    buf.append("(typeArgumentList")
    val oldTab = increaseTab()
    for (arg in typeArgumentList.types) {
      buf.append('\n')
      renderType(arg)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderParenthesizedType(type: ParenthesizedType) {
    buf.append(tab)
    buf.append("(parenthesizedType")
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
    for (expr in type.exprs) {
      buf.append('\n')
      renderExpr(expr)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderUnionType(type: UnionType) {
    buf.append(tab)
    buf.append("(unionType")
    val oldTab = increaseTab()
    for (idx in type.types.indices) {
      val typ = type.types[idx]
      buf.append('\n')
      if (type.defaultIndex == idx) {
        buf.append(tab)
        buf.append("(defaultUnionType")
        val oldTab2 = increaseTab()
        buf.append('\n')
        renderType(typ)
        buf.append(')')
        tab = oldTab2
      } else {
        renderType(typ)
      }
    }
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

  fun renderMember(member: ObjectMember) {
    when (member) {
      is ObjectElement -> renderObjectElement(member)
      is ObjectProperty -> renderObjectProperty(member)
      is ObjectMethod -> renderObjectMethod(member)
      is MemberPredicate -> renderMemberPredicate(member)
      is ObjectEntry -> renderObjectEntry(member)
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
    property.expr?.let {
      buf.append('\n')
      renderExpr(it)
    }
    property.bodyList?.let { bodies ->
      for (body in bodies) {
        buf.append('\n')
        renderObjectBody(body)
      }
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderObjectMethod(method: ObjectMethod) {
    buf.append(tab)
    buf.append("(objectMethod")
    val oldTab = increaseTab()
    for (mod in method.modifiers) {
      buf.append('\n')
      renderModifier(mod)
    }
    buf.append('\n').append(tab)
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
    predicate.expr?.let { expr ->
      buf.append('\n')
      renderExpr(expr)
    }
    predicate.bodyList?.let { bodyList ->
      for (body in bodyList) {
        buf.append('\n')
        renderObjectBody(body)
      }
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
    entry.value?.let { value ->
      buf.append('\n')
      renderExpr(value)
    }
    entry.bodyList?.let { bodyList ->
      for (body in bodyList) {
        buf.append('\n')
        renderObjectBody(body)
      }
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
    renderExpr(generator.predicate)
    buf.append('\n')
    renderObjectBody(generator.thenClause)
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
    buf.append("(typeParameterList")
    val oldTab = increaseTab()
    for (tpar in typeParameterList.parameters) {
      buf.append('\n')
      renderTypeParameter(tpar)
    }
    buf.append(')')
    tab = oldTab
  }

  @Suppress("UNUSED_PARAMETER")
  fun renderTypeParameter(ignored: TypeParameter?) {
    buf.append(tab)
    buf.append("(typeParameter\n")
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
    private fun sortModuleEntries(mod: org.pkl.parser.syntax.Module): List<Node> {
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
