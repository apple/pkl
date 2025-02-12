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
package org.pkl.core.parser

import org.antlr.v4.runtime.ParserRuleContext
import org.pkl.core.parser.antlr.PklLexer
import org.pkl.core.parser.antlr.PklParser.*

@Suppress("MemberVisibilityCanBePrivate")
class ANTLRSexpRenderer {
  private var tab = ""
  private var buf = StringBuilder()

  fun render(mod: ModuleContext): String {
    renderModule(mod)
    val res = buf.toString()
    reset()
    return res
  }

  fun renderModule(mod: ModuleContext) {
    buf.append(tab)
    buf.append("(module")
    val oldTab = increaseTab()
    if (mod.moduleDecl() != null) {
      buf.append('\n')
      renderModuleDeclaration(mod.moduleDecl())
    }
    for (imp in mod.importClause()) {
      buf.append('\n')
      renderImport(imp)
    }
    for (entry in sortModuleEntries(mod)) {
      buf.append('\n')
      when (entry) {
        is ClazzContext -> renderClass(entry)
        is TypeAliasContext -> renderTypeAlias(entry)
        is ClassPropertyContext -> renderClassProperty(entry)
        is ClassMethodContext -> renderClassMethod(entry)
      }
    }
    tab = oldTab
    buf.append(')')
  }

  fun renderModuleDeclaration(decl: ModuleDeclContext) {
    buf.append(tab)
    buf.append("(moduleHeader")
    val oldTab = increaseTab()
    if (decl.DocComment() != null) {
      buf.append('\n')
      renderDocComment()
    }
    for (ann in decl.annotation()) {
      buf.append('\n')
      renderAnnotation(ann)
    }
    val header = decl.moduleHeader()
    if (header != null) {
      for (modifier in header.modifier()) {
        buf.append('\n')
        renderModifier(modifier)
      }
      if (header.qualifiedIdentifier() != null) {
        buf.append('\n')
        renderQualifiedIdent(header.qualifiedIdentifier())
      }
      if (header.moduleExtendsOrAmendsClause() != null) {
        buf.append('\n')
        buf.append(tab)
        buf.append("(extendsOrAmendsClause)")
      }
    }
    tab = oldTab
    buf.append(')')
  }

  fun renderImport(imp: ImportClauseContext) {
    buf.append(tab)
    if (imp.t.type == PklLexer.IMPORT_GLOB) {
      buf.append("(importGlobClause")
    } else {
      buf.append("(importClause")
    }
    val oldTab = increaseTab()
    if (imp.Identifier() != null) {
      buf.append('\n')
      buf.append(tab)
      buf.append("(identifier)")
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderClass(clazz: ClazzContext) {
    buf.append(tab)
    buf.append("(clazz")
    val oldTab = increaseTab()
    if (clazz.DocComment() != null) {
      buf.append('\n')
      renderDocComment()
    }
    for (ann in clazz.annotation()) {
      buf.append('\n')
      renderAnnotation(ann)
    }
    val header = clazz.classHeader()
    for (mod in header.modifier()) {
      buf.append('\n')
      renderModifier(mod)
    }
    buf.append('\n')
    buf.append(tab)
    buf.append("(identifier)")
    val typePars = header.typeParameterList()
    if (typePars != null) {
      buf.append('\n')
      renderTypeParameterList(typePars)
    }
    if (header.type() != null) {
      buf.append('\n')
      renderType(header.type())
    }
    val body = clazz.classBody()
    if (body != null) {
      buf.append('\n')
      renderClassBody(body)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderClassBody(body: ClassBodyContext) {
    buf.append(tab)
    buf.append("(classBody")
    val oldTab = increaseTab()
    for (entry in sortClassEntries(body)) {
      buf.append('\n')
      when (entry) {
        is ClassPropertyContext -> renderClassProperty(entry)
        is ClassMethodContext -> renderClassMethod(entry)
      }
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderTypeAlias(`typealias`: TypeAliasContext) {
    buf.append(tab)
    buf.append("(typeAlias")
    val oldTab = increaseTab()
    if (`typealias`.DocComment() != null) {
      buf.append('\n')
      renderDocComment()
    }
    for (ann in `typealias`.annotation()) {
      buf.append('\n')
      renderAnnotation(ann)
    }
    val header = `typealias`.typeAliasHeader()
    for (mod in header.modifier()) {
      buf.append('\n')
      renderModifier(mod)
    }
    buf.append('\n')
    buf.append(tab)
    buf.append("(identifier)")
    val typePars = header.typeParameterList()
    if (typePars != null) {
      renderTypeParameterList(typePars)
    }
    buf.append('\n')
    renderType(`typealias`.type())
    buf.append(')')
    tab = oldTab
  }

  fun renderClassProperty(classProperty: ClassPropertyContext) {
    buf.append(tab)
    buf.append("(classProperty")
    val oldTab = increaseTab()
    if (classProperty.DocComment() != null) {
      buf.append('\n')
      renderDocComment()
    }
    for (ann in classProperty.annotation()) {
      buf.append('\n')
      renderAnnotation(ann)
    }
    for (mod in classProperty.modifier()) {
      buf.append('\n')
      renderModifier(mod)
    }
    buf.append('\n')
    buf.append(tab)
    buf.append("(identifier)")
    if (classProperty.typeAnnotation() != null) {
      buf.append('\n')
      renderTypeAnnotation(classProperty.typeAnnotation())
    }
    if (classProperty.expr() != null) {
      buf.append('\n')
      renderExpr(classProperty.expr())
    }
    if (classProperty.objectBody() != null) {
      for (body in classProperty.objectBody()) {
        buf.append('\n')
        renderObjectBody(body)
      }
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderClassMethod(classMethod: ClassMethodContext) {
    buf.append(tab)
    buf.append("(classMethod")
    val oldTab = increaseTab()
    if (classMethod.DocComment() != null) {
      buf.append('\n')
      renderDocComment()
    }
    for (ann in classMethod.annotation()) {
      buf.append('\n')
      renderAnnotation(ann)
    }
    val header = classMethod.methodHeader()
    for (mod in header.modifier()) {
      buf.append('\n')
      renderModifier(mod)
    }
    buf.append('\n')
    buf.append(tab)
    buf.append("(identifier)")
    if (header.typeParameterList() != null) {
      renderTypeParameterList(header.typeParameterList())
    }
    buf.append('\n')
    renderParameterList(header.parameterList())
    if (header.typeAnnotation() != null) {
      buf.append('\n')
      renderTypeAnnotation(header.typeAnnotation())
    }
    if (classMethod.expr() != null) {
      buf.append('\n')
      renderExpr(classMethod.expr())
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderAnnotation(ann: AnnotationContext) {
    buf.append(tab)
    buf.append("(annotation")
    val oldTab = increaseTab()
    buf.append('\n')
    renderType(ann.type())
    if (ann.objectBody() != null) {
      buf.append('\n')
      renderObjectBody(ann.objectBody())
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderQualifiedIdent(name: QualifiedIdentifierContext) {
    buf.append(tab)
    buf.append("(qualifiedIdentifier")
    val oldTab = increaseTab()
    for (i in name.Identifier().indices) {
      buf.append('\n')
      buf.append(tab)
      buf.append("(identifier)")
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderTypeParameterList(typeParameterList: TypeParameterListContext) {
    buf.append(tab)
    buf.append("(TypeParameterList\n")
    val oldTab = increaseTab()
    for (tpar in typeParameterList.typeParameter()) {
      buf.append('\n')
      renderTypeParameter(tpar)
    }
    tab = oldTab
  }

  @Suppress("UNUSED_PARAMETER")
  fun renderTypeParameter(tpar: TypeParameterContext?) {
    buf.append(tab)
    buf.append("(TypeParameter\n")
    val oldTab = increaseTab()
    buf.append(tab)
    buf.append("(identifier))")
    tab = oldTab
  }

  fun renderTypeAnnotation(typeAnnotation: TypeAnnotationContext) {
    buf.append(tab)
    buf.append("(typeAnnotation")
    val oldTab = increaseTab()
    buf.append('\n')
    renderType(typeAnnotation.type())
    buf.append(')')
    tab = oldTab
  }

  fun renderType(type: TypeContext?) {
    when (type) {
      is UnknownTypeContext -> {
        buf.append(tab)
        buf.append("(unknownType)")
      }
      is NothingTypeContext -> {
        buf.append(tab)
        buf.append("(nothingType)")
      }
      is ModuleTypeContext -> {
        buf.append(tab)
        buf.append("(moduleType)")
      }
      is StringLiteralTypeContext -> {
        buf.append(tab)
        buf.append("(stringConstantType)")
      }
      is DeclaredTypeContext -> renderDeclaredType(type)
      is ParenthesizedTypeContext -> renderParenthesizedType(type)
      is NullableTypeContext -> renderNullableType(type)
      is ConstrainedTypeContext -> renderConstrainedType(type)
      is DefaultUnionTypeContext -> renderDefaultUnionType(type)
      is UnionTypeContext -> renderUnionType(type)
      is FunctionTypeContext -> renderFunctionType(type)
    }
  }

  fun renderDeclaredType(type: DeclaredTypeContext) {
    buf.append(tab)
    buf.append("(declaredType")
    val oldTab = increaseTab()
    buf.append('\n')
    renderQualifiedIdent(type.qualifiedIdentifier())
    val args = type.typeArgumentList()
    if (args != null) {
      for (arg in args.type()) {
        buf.append('\n')
        renderType(arg)
      }
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderParenthesizedType(type: ParenthesizedTypeContext) {
    buf.append(tab)
    buf.append("(parenthesisedType")
    val oldTab = increaseTab()
    buf.append('\n')
    renderType(type.type())
    buf.append(')')
    tab = oldTab
  }

  fun renderNullableType(type: NullableTypeContext) {
    buf.append(tab)
    buf.append("(nullableType")
    val oldTab = increaseTab()
    buf.append('\n')
    renderType(type.type())
    buf.append(')')
    tab = oldTab
  }

  fun renderConstrainedType(type: ConstrainedTypeContext) {
    buf.append(tab)
    buf.append("(constrainedType")
    val oldTab = increaseTab()
    buf.append('\n')
    renderType(type.type())
    for (expr in type.expr()) {
      buf.append('\n')
      renderExpr(expr)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderDefaultUnionType(type: DefaultUnionTypeContext) {
    buf.append(tab)
    buf.append("(defaultUnionType")
    val oldTab = increaseTab()
    buf.append('\n')
    renderType(type.type())
    buf.append(')')
    tab = oldTab
  }

  fun renderUnionType(type: UnionTypeContext) {
    buf.append(tab)
    buf.append("(unionType")
    val oldTab = increaseTab()
    val types = flattenUnion(type)
    for (typ in types) {
      buf.append('\n')
      renderType(typ)
    }
    buf.append(')')
    tab = oldTab
  }

  private fun flattenUnion(type: UnionTypeContext): List<TypeContext> {
    val types = mutableListOf<TypeContext>()
    val toCheck = mutableListOf(type.l, type.r)
    while (toCheck.isNotEmpty()) {
      val typ = toCheck.removeAt(0)
      if (typ is UnionTypeContext) {
        toCheck.addFirst(typ.r)
        toCheck.addFirst(typ.l)
      } else {
        types += typ
      }
    }
    return types
  }

  fun renderFunctionType(type: FunctionTypeContext) {
    buf.append(tab)
    buf.append("(functionType")
    val oldTab = increaseTab()
    for (arg in type.ps) {
      buf.append('\n')
      renderType(arg)
    }
    buf.append('\n')
    renderType(type.r)
    buf.append(')')
    tab = oldTab
  }

  fun renderParameterList(parList: ParameterListContext) {
    buf.append(tab)
    buf.append("(parameterList")
    val oldTab = increaseTab()
    for (par in parList.parameter()) {
      buf.append('\n')
      renderParameter(par)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderParameter(par: ParameterContext) {
    buf.append(tab)
    buf.append("(parameter")
    val oldTab = increaseTab()
    val typedIdent = par.typedIdentifier()
    if (typedIdent != null) {
      buf.append('\n')
      buf.append(tab)
      buf.append("(identifier)")
      if (typedIdent.typeAnnotation() != null) {
        buf.append('\n')
        renderTypeAnnotation(typedIdent.typeAnnotation())
      }
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderObjectBody(body: ObjectBodyContext) {
    buf.append(tab)
    buf.append("(objectBody")
    val oldTab = increaseTab()
    for (par in body.parameter()) {
      buf.append('\n')
      renderParameter(par)
    }
    for (member in body.objectMember()) {
      buf.append('\n')
      renderMember(member)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderMember(member: ObjectMemberContext) {
    when (member) {
      is ObjectPropertyContext -> renderObjectProperty(member)
      is ObjectMethodContext -> renderObjectMethod(member)
      is MemberPredicateContext -> renderMemberPredicate(member)
      is ObjectEntryContext -> renderObjectEntry(member)
      is ObjectElementContext -> renderObjectElement(member)
      is ObjectSpreadContext -> renderObjectSpread(member)
      is WhenGeneratorContext -> renderWhenGenerator(member)
      is ForGeneratorContext -> renderForGenerator(member)
    }
  }

  fun renderObjectElement(element: ObjectElementContext) {
    buf.append(tab)
    buf.append("(objectElement")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(element.expr())
    buf.append(')')
    tab = oldTab
  }

  fun renderObjectProperty(property: ObjectPropertyContext) {
    buf.append(tab)
    buf.append("(objectProperty")
    val oldTab = increaseTab()
    for (mod in property.modifier()) {
      buf.append('\n')
      renderModifier(mod)
    }
    buf.append('\n')
    buf.append(tab)
    buf.append("(identifier)")
    val typeAnn = property.typeAnnotation()
    if (typeAnn != null) {
      buf.append('\n')
      renderTypeAnnotation(typeAnn)
    }
    if (property.expr() != null) {
      buf.append('\n')
      renderExpr(property.expr())
    }
    if (property.objectBody() != null) {
      for (body in property.objectBody()) {
        buf.append('\n')
        renderObjectBody(body)
      }
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderObjectMethod(method: ObjectMethodContext) {
    buf.append(tab)
    buf.append("(objectMethod")
    val oldTab = increaseTab()
    buf.append('\n')
    val header = method.methodHeader()
    for (mod in header.modifier()) {
      buf.append('\n')
      renderModifier(mod)
    }
    buf.append('\n')
    buf.append("(identifier)")
    if (header.typeParameterList() != null) {
      renderTypeParameterList(header.typeParameterList())
    }
    buf.append('\n')
    renderParameterList(header.parameterList())
    val typeAnn = header.typeAnnotation()
    if (typeAnn != null) {
      buf.append('\n')
      renderTypeAnnotation(typeAnn)
    }
    buf.append('\n')
    renderExpr(method.expr())
    buf.append(')')
    tab = oldTab
  }

  fun renderMemberPredicate(predicate: MemberPredicateContext) {
    buf.append(tab)
    buf.append("(memberPredicate")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(predicate.k)
    if (predicate.v != null) {
      buf.append('\n')
      renderExpr(predicate.v)
    }
    if (predicate.objectBody() != null) {
      for (body in predicate.objectBody()) {
        buf.append('\n')
        renderObjectBody(body)
      }
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderObjectEntry(entry: ObjectEntryContext) {
    buf.append(tab)
    buf.append("(objectEntry")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(entry.k)
    if (entry.v != null) {
      buf.append('\n')
      renderExpr(entry.v)
    }
    if (entry.objectBody() != null) {
      for (body in entry.objectBody()) {
        buf.append('\n')
        renderObjectBody(body)
      }
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderObjectSpread(spread: ObjectSpreadContext) {
    buf.append(tab)
    buf.append("(objectSpread")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(spread.expr())
    buf.append(')')
    tab = oldTab
  }

  fun renderWhenGenerator(generator: WhenGeneratorContext) {
    buf.append(tab)
    buf.append("(whenGenerator")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(generator.expr())
    buf.append('\n')
    renderObjectBody(generator.b1)
    if (generator.b2 != null) {
      buf.append('\n')
      renderObjectBody(generator.b2)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderForGenerator(generator: ForGeneratorContext) {
    buf.append(tab)
    buf.append("(forGenerator")
    val oldTab = increaseTab()
    buf.append('\n')
    renderParameter(generator.t1)
    if (generator.t2 != null) {
      buf.append('\n')
      renderParameter(generator.t2)
    }
    buf.append('\n')
    renderExpr(generator.expr())
    buf.append('\n')
    renderObjectBody(generator.objectBody())
    buf.append(')')
    tab = oldTab
  }

  fun renderArgumentList(argumentList: ArgumentListContext) {
    buf.append(tab)
    buf.append("(argumentList")
    val oldTab = increaseTab()
    for (arg in argumentList.expr()) {
      buf.append('\n')
      renderExpr(arg)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderExpr(expr: ExprContext) {
    when (expr) {
      is ThisExprContext -> {
        buf.append(tab)
        buf.append("(thisExpr)")
      }
      is OuterExprContext -> {
        buf.append(tab)
        buf.append("(outerExpr)")
      }
      is ModuleExprContext -> {
        buf.append(tab)
        buf.append("(moduleExpr)")
      }
      is NullLiteralContext -> {
        buf.append(tab)
        buf.append("(nullExpr)")
      }
      is TrueLiteralContext,
      is FalseLiteralContext -> {
        buf.append(tab)
        buf.append("(boolLiteralExpr)")
      }
      is IntLiteralContext -> {
        buf.append(tab)
        buf.append("(intLiteralExpr)")
      }
      is FloatLiteralContext -> {
        buf.append(tab)
        buf.append("(floatLiteralExpr)")
      }
      is ThrowExprContext -> renderThrowExpr(expr)
      is TraceExprContext -> renderTraceExpr(expr)
      is ImportExprContext -> {
        buf.append(tab)
        val name = if (expr.t.type == PklLexer.IMPORT_GLOB) "(importGlobExpr)" else "(importExpr)"
        buf.append(name)
      }
      is ReadExprContext -> renderReadExpr(expr)
      is UnqualifiedAccessExprContext -> renderUnqualifiedAccessExpr(expr)
      is SingleLineStringLiteralContext -> renderSingleLineStringExpr(expr)
      is MultiLineStringLiteralContext -> renderMultiLineStringExpr(expr)
      is NewExprContext -> renderNewExpr(expr)
      is AmendExprContext -> renderAmendsExpr(expr)
      is SuperAccessExprContext -> renderSuperAccessExpr(expr)
      is SuperSubscriptExprContext -> renderSuperSubscriptExpr(expr)
      is QualifiedAccessExprContext -> renderQualifiedAccessExpr(expr)
      is SubscriptExprContext -> renderSubscriptExpr(expr)
      is NonNullExprContext -> renderNonNullExpr(expr)
      is UnaryMinusExprContext -> renderUnaryMinusExpr(expr)
      is LogicalNotExprContext -> renderLogicalNotExpr(expr)
      is ExponentiationExprContext -> renderBinaryOpExpr("exponentiationExpr", expr.expr())
      is MultiplicativeExprContext -> renderBinaryOpExpr("multiplicativeExpr", expr.expr())
      is AdditiveExprContext -> renderBinaryOpExpr("additiveExpr", expr.expr())
      is ComparisonExprContext -> renderBinaryOpExpr("comparisonExpr", expr.expr())
      is TypeTestExprContext -> renderTypeTestExpr(expr)
      is EqualityExprContext -> renderBinaryOpExpr("equalityExpr", expr.expr())
      is LogicalAndExprContext -> renderBinaryOpExpr("logicalAndExpr", expr.expr())
      is LogicalOrExprContext -> renderBinaryOpExpr("logicalOrExpr", expr.expr())
      is PipeExprContext -> renderBinaryOpExpr("pipeExpr", expr.expr())
      is NullCoalesceExprContext -> renderBinaryOpExpr("nullCoalesceExpr", expr.expr())
      is IfExprContext -> renderIfExpr(expr)
      is LetExprContext -> renderLetExpr(expr)
      is FunctionLiteralContext -> renderFunctionLiteralExpr(expr)
      is ParenthesizedExprContext -> renderParenthesisedExpr(expr)
    }
  }

  fun renderThrowExpr(expr: ThrowExprContext) {
    buf.append(tab)
    buf.append("(throwExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr())
    buf.append(')')
    tab = oldTab
  }

  fun renderTraceExpr(expr: TraceExprContext) {
    buf.append(tab)
    buf.append("(traceExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr())
    buf.append(')')
    tab = oldTab
  }

  fun renderReadExpr(expr: ReadExprContext) {
    buf.append(tab)
    var name = "(readExpr"
    if (expr.t.type == PklLexer.READ_GLOB) {
      name = "(readGlobExpr"
    } else if (expr.t.type == PklLexer.READ_OR_NULL) {
      name = "(readNullExpr"
    }
    buf.append(name)
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr())
    buf.append(')')
    tab = oldTab
  }

  fun renderUnqualifiedAccessExpr(expr: UnqualifiedAccessExprContext) {
    buf.append(tab)
    buf.append("(unqualifiedAccessExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    buf.append(tab)
    buf.append("(identifier)")
    val args = expr.argumentList()
    if (args != null) {
      buf.append('\n')
      renderArgumentList(args)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderSingleLineStringExpr(expr: SingleLineStringLiteralContext) {
    buf.append(tab)
    buf.append("(interpolatedStringExpr")
    val oldTab = increaseTab()
    for (part in expr.singleLineStringPart()) {
      if (part.expr() != null) {
        buf.append('\n')
        renderExpr(part.expr())
      } else {
        buf.append('\n').append(tab)
        buf.append("(stringConstantExpr)")
      }
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderMultiLineStringExpr(expr: MultiLineStringLiteralContext) {
    buf.append(tab)
    buf.append("(interpolatedMultiStringExpr")
    val oldTab = increaseTab()
    for (part in expr.multiLineStringPart()) {
      if (part.expr() != null) {
        buf.append('\n')
        renderExpr(part.expr())
      } else {
        buf.append('\n').append(tab)
        buf.append("(stringConstantExpr)")
      }
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderNewExpr(expr: NewExprContext) {
    buf.append(tab)
    buf.append("(newExpr")
    val oldTab = increaseTab()
    if (expr.type() != null) {
      buf.append('\n')
      renderType(expr.type())
    }
    buf.append('\n')
    renderObjectBody(expr.objectBody())
    buf.append(')')
    tab = oldTab
  }

  fun renderAmendsExpr(expr: AmendExprContext) {
    buf.append(tab)
    buf.append("(amendsExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr())
    buf.append('\n')
    renderObjectBody(expr.objectBody())
    buf.append(')')
    tab = oldTab
  }

  fun renderSuperAccessExpr(expr: SuperAccessExprContext) {
    buf.append(tab)
    buf.append("(superAccessExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    buf.append("(identifier)")
    val args = expr.argumentList()
    if (args != null) {
      buf.append('\n')
      renderArgumentList(args)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderSuperSubscriptExpr(expr: SuperSubscriptExprContext) {
    buf.append(tab)
    buf.append("(superSubscriptExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr())
    buf.append(')')
    tab = oldTab
  }

  fun renderQualifiedAccessExpr(expr: QualifiedAccessExprContext) {
    buf.append(tab)
    val name =
      if (expr.t.type == PklLexer.QDOT) "(nullableQualifiedAccessExpr" else "(qualifiedAccessExpr"
    buf.append(name)
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr())
    buf.append('\n')
    buf.append(tab)
    buf.append("(identifier)")
    val args = expr.argumentList()
    if (args != null) {
      buf.append('\n')
      renderArgumentList(args)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderSubscriptExpr(expr: SubscriptExprContext) {
    buf.append(tab)
    buf.append("(subscriptExpr")
    val oldTab = increaseTab()
    for (e in expr.expr()) {
      buf.append('\n')
      renderExpr(e)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderNonNullExpr(expr: NonNullExprContext) {
    buf.append(tab)
    buf.append("(nonNullExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr())
    buf.append(')')
    tab = oldTab
  }

  fun renderUnaryMinusExpr(expr: UnaryMinusExprContext) {
    buf.append(tab)
    buf.append("(unaryMinusExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr())
    buf.append(')')
    tab = oldTab
  }

  fun renderLogicalNotExpr(expr: LogicalNotExprContext) {
    buf.append(tab)
    buf.append("(logicalNotExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr())
    buf.append(')')
    tab = oldTab
  }

  fun <E : ExprContext> renderBinaryOpExpr(name: String?, exprs: List<E>) {
    buf.append(tab)
    buf.append("(").append(name)
    val oldTab = increaseTab()
    for (expr in exprs) {
      buf.append('\n')
      renderExpr(expr)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderTypeTestExpr(expr: TypeTestExprContext) {
    buf.append(tab)
    val name = if (expr.t.type == PklLexer.IS) "(typeCheckExpr" else "(typeCastExpr"
    buf.append(name)
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr())
    buf.append('\n')
    renderType(expr.type())
    buf.append(')')
    tab = oldTab
  }

  fun renderIfExpr(expr: IfExprContext) {
    buf.append(tab)
    buf.append("(ifExpr")
    val oldTab = increaseTab()
    for (e in expr.expr()) {
      buf.append('\n')
      renderExpr(e)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderLetExpr(expr: LetExprContext) {
    buf.append(tab)
    buf.append("(letExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderParameter(expr.parameter())
    for (e in expr.expr()) {
      buf.append('\n')
      renderExpr(e)
    }
    buf.append(')')
    tab = oldTab
  }

  fun renderFunctionLiteralExpr(expr: FunctionLiteralContext) {
    buf.append(tab)
    buf.append("(functionLiteralExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderParameterList(expr.parameterList())
    buf.append('\n')
    renderExpr(expr.expr())
    buf.append(')')
    tab = oldTab
  }

  fun renderParenthesisedExpr(expr: ParenthesizedExprContext) {
    buf.append(tab)
    buf.append("(parenthesizedExpr")
    val oldTab = increaseTab()
    buf.append('\n')
    renderExpr(expr.expr())
    buf.append(')')
    tab = oldTab
  }

  @Suppress("UNUSED_PARAMETER")
  fun renderModifier(mod: ModifierContext?) {
    buf.append(tab)
    buf.append("(modifier)")
  }

  fun renderDocComment() {
    buf.append(tab)
    buf.append("(docComment)")
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
    private fun sortModuleEntries(mod: ModuleContext): List<ParserRuleContext> {
      val res = mutableListOf<ParserRuleContext>()
      res += mod.clazz()
      res += mod.typeAlias()
      res += mod.classProperty()
      res += mod.classMethod()
      res.sortWith(compareBy { it.sourceInterval.a })
      return res
    }

    private fun sortClassEntries(body: ClassBodyContext): List<ParserRuleContext> {
      val res = mutableListOf<ParserRuleContext>()
      res += body.classProperty()
      res += body.classMethod()
      res.sortWith(compareBy { it.sourceInterval.a })
      return res
    }
  }
}
