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
package org.pkl.formatter

import org.pkl.formatter.ast.CommentLine
import org.pkl.formatter.ast.Empty
import org.pkl.formatter.ast.ForceLine
import org.pkl.formatter.ast.FormatNode
import org.pkl.formatter.ast.Group
import org.pkl.formatter.ast.IfWrap
import org.pkl.formatter.ast.Indent
import org.pkl.formatter.ast.Line
import org.pkl.formatter.ast.MultilineStringGroup
import org.pkl.formatter.ast.Nodes
import org.pkl.formatter.ast.Space
import org.pkl.formatter.ast.SpaceOrLine
import org.pkl.formatter.ast.Text
import org.pkl.formatter.ast.group
import org.pkl.formatter.ast.indent
import org.pkl.formatter.ast.nodes
import org.pkl.formatter.ast.twoNewlines
import org.pkl.parser.ParserVisitor
import org.pkl.parser.Span
import org.pkl.parser.syntax.Annotation
import org.pkl.parser.syntax.ArgumentList
import org.pkl.parser.syntax.Class
import org.pkl.parser.syntax.ClassBody
import org.pkl.parser.syntax.ClassMethod
import org.pkl.parser.syntax.ClassProperty
import org.pkl.parser.syntax.DocComment
import org.pkl.parser.syntax.Expr
import org.pkl.parser.syntax.ExtendsOrAmendsClause
import org.pkl.parser.syntax.Identifier
import org.pkl.parser.syntax.ImportClause
import org.pkl.parser.syntax.Keyword
import org.pkl.parser.syntax.Modifier
import org.pkl.parser.syntax.Module
import org.pkl.parser.syntax.ModuleDecl
import org.pkl.parser.syntax.Node
import org.pkl.parser.syntax.ObjectBody
import org.pkl.parser.syntax.ObjectMember
import org.pkl.parser.syntax.Parameter
import org.pkl.parser.syntax.ParameterList
import org.pkl.parser.syntax.QualifiedIdentifier
import org.pkl.parser.syntax.ReplInput
import org.pkl.parser.syntax.StringConstant
import org.pkl.parser.syntax.StringPart
import org.pkl.parser.syntax.Type
import org.pkl.parser.syntax.TypeAlias
import org.pkl.parser.syntax.TypeAnnotation
import org.pkl.parser.syntax.TypeArgumentList
import org.pkl.parser.syntax.TypeParameter
import org.pkl.parser.syntax.TypeParameterList
import org.pkl.parser.syntax.generic.AffixFixity
import org.pkl.parser.syntax.generic.AffixType

@Suppress("DuplicatedCode")
class Builder(sourceText: String, private val newlines: IntArray) : ParserVisitor<FormatNode> {
  private var id: Int = 0
  private val source: CharArray = sourceText.toCharArray()

  override fun visitUnknownType(type: Type.UnknownType): FormatNode {
    return withPrefixesAndSuffixes(type) { Text(type.text(source)) }
  }

  override fun visitNothingType(type: Type.NothingType): FormatNode {
    return withPrefixesAndSuffixes(type) { Text(type.text(source)) }
  }

  override fun visitModuleType(type: Type.ModuleType): FormatNode {
    return withPrefixesAndSuffixes(type) { Text(type.text(source)) }
  }

  override fun visitStringConstantType(type: Type.StringConstantType): FormatNode {
    return withPrefixesAndSuffixes(type) { Text(type.text(source)) }
  }

  override fun visitDeclaredType(type: Type.DeclaredType): FormatNode {
    return withPrefixesAndSuffixes(type) {
      val args = type.args
      if (args != null) {
        group(newId(), visitQualifiedIdentifier(type.name), visitTypeArgumentList(args))
      } else {
        group(newId(), visitQualifiedIdentifier(type.name))
      }
    }
  }

  override fun visitParenthesizedType(type: Type.ParenthesizedType): FormatNode {
    return withPrefixesAndSuffixes(type) {
      grouping(Text("("), Line, indent(type.type.visit()), Line, Text(")"))
    }
  }

  override fun visitNullableType(type: Type.NullableType): FormatNode {
    return withPrefixesAndSuffixes(type) { nodes(type.type.visit(), Text("?")) }
  }

  override fun visitConstrainedType(type: Type.ConstrainedType): FormatNode {
    return withPrefixesAndSuffixes(type) {
      nodes(
        type.type.visit(),
        grouping(Text("("), indent(Line, withSeparator(type.exprs, SpaceOrLine)), Line, Text(")")),
      )
    }
  }

  override fun visitUnionType(type: Type.UnionType): FormatNode {
    return withPrefixesAndSuffixes(type) {
      val res = mutableListOf<FormatNode>()
      val afterFirst = mutableListOf<FormatNode>()
      val default = type.defaultIndex
      for ((i, type) in type.types.withIndex()) {
        if (i > 0) {
          afterFirst += SpaceOrLine
          afterFirst += Text("|")
          afterFirst += Space
          if (i == default) afterFirst += Text("*")
          afterFirst += type.visit()
        } else {
          if (i == default) res += Text("*")
          res += type.visit()
        }
      }
      res += Indent(afterFirst)
      group(newId(), res)
    }
  }

  override fun visitFunctionType(type: Type.FunctionType): FormatNode {
    return withPrefixesAndSuffixes(type) {
      grouping(
        grouping(Text("("), indent(Line, withSeparator(type.args, SpaceOrLine)), Line, Text(")")),
        Space,
        Text("->"),
        indent(SpaceOrLine, type.ret.visit()),
      )
    }
  }

  override fun visitThisExpr(expr: Expr.ThisExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) { Text(expr.text(source)) }
  }

  override fun visitOuterExpr(expr: Expr.OuterExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) { Text(expr.text(source)) }
  }

  override fun visitModuleExpr(expr: Expr.ModuleExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) { Text(expr.text(source)) }
  }

  override fun visitNullLiteralExpr(expr: Expr.NullLiteralExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) { Text(expr.text(source)) }
  }

  override fun visitBoolLiteralExpr(expr: Expr.BoolLiteralExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) { Text(expr.text(source)) }
  }

  override fun visitIntLiteralExpr(expr: Expr.IntLiteralExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) { Text(expr.text(source)) }
  }

  override fun visitFloatLiteralExpr(expr: Expr.FloatLiteralExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) { Text(expr.text(source)) }
  }

  override fun visitThrowExpr(expr: Expr.ThrowExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      grouping(Text("throw("), Line, indent(expr.expr.visit()), Line, Text(")"))
    }
  }

  override fun visitTraceExpr(expr: Expr.TraceExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      grouping(Text("trace("), Line, indent(expr.expr.visit()), Line, Text(")"))
    }
  }

  override fun visitImportExpr(expr: Expr.ImportExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      grouping(
        Text(if (expr.isGlob) "import*(" else "import("),
        Line,
        indent(visitStringConstant(expr.importStr)),
        Line,
        Text(")"),
      )
    }
  }

  override fun visitReadExpr(expr: Expr.ReadExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      val name =
        when (expr.readType) {
          Expr.ReadType.READ -> "read("
          Expr.ReadType.NULL -> "read?("
          Expr.ReadType.GLOB -> "read*("
        }
      grouping(Text(name), Line, indent(expr.expr.visit()), Line, Text(")"))
    }
  }

  override fun visitUnqualifiedAccessExpr(expr: Expr.UnqualifiedAccessExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      val args = expr.argumentList
      if (args == null) {
        visitIdentifier(expr.identifier)
      } else {
        val func = expr.identifier.text(source)
        nodes(visitIdentifier(expr.identifier), visitArgumentList(args, twoByTwo = func == "Map"))
      }
    }
  }

  override fun visitStringConstant(expr: StringConstant): FormatNode {
    return withPrefixesAndSuffixes(expr) { Text(expr.text(source)) }
  }

  override fun visitSingleLineStringLiteralExpr(
    expr: Expr.SingleLineStringLiteralExpr
  ): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      nodes(Text("\""), Nodes(expr.parts.map { it.visit() }), Text("\""))
    }
  }

  override fun visitMultiLineStringLiteralExpr(expr: Expr.MultiLineStringLiteralExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      MultilineStringGroup(expr.endDelimiterSpan.columnBegin(), expr.parts.map(::visitStringPart))
    }
  }

  override fun visitNewExpr(expr: Expr.NewExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      val res = mutableListOf<FormatNode>()
      res += Text("new")
      res += Space
      val type = expr.type
      if (type != null) {
        res += type.visit()
        res += Space
      }
      res += visitObjectBody(expr.body)
      group(newId(), res)
    }
  }

  override fun visitAmendsExpr(expr: Expr.AmendsExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      nodes(expr.expr.visit(), Space, visitObjectBody(expr.body))
    }
  }

  override fun visitSuperAccessExpr(expr: Expr.SuperAccessExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      val res = mutableListOf<FormatNode>()
      res += Line
      res += Text(".")
      res += visitIdentifier(expr.identifier)
      val args = expr.argumentList
      if (args != null) {
        res += visitArgumentList(args)
      }
      group(newId(), Text("super"), Indent(res))
    }
  }

  override fun visitSuperSubscriptExpr(expr: Expr.SuperSubscriptExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      grouping(Text("super["), Line, indent(expr.arg.visit()), Line, Text("]"))
    }
  }

  override fun visitQualifiedAccessExpr(expr: Expr.QualifiedAccessExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      val args = expr.argumentList
      val hasLambda =
        expr.expr is Expr.QualifiedAccessExpr && args != null && hasFunctionLiteral(args, depth = 2)
      val res = mutableListOf<FormatNode>()
      res += expr.expr.visit()
      res += if (hasLambda) ForceLine else Line
      res +=
        indent(
          Text("."),
          visitIdentifier(expr.identifier),
          if (args != null) grouping(visitArgumentList(args)) else Empty,
        )
      group(newId(), res)
    }
  }

  override fun visitSubscriptExpr(expr: Expr.SubscriptExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      grouping(expr.expr.visit(), Text("["), Line, indent(expr.arg.visit()), Line, Text("]"))
    }
  }

  override fun visitNonNullExpr(expr: Expr.NonNullExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) { nodes(expr.expr.visit(), Text("!!")) }
  }

  override fun visitUnaryMinusExpr(expr: Expr.UnaryMinusExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) { nodes(Text("-"), expr.expr.visit()) }
  }

  override fun visitLogicalNotExpr(expr: Expr.LogicalNotExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) { nodes(Text("!"), expr.expr.visit()) }
  }

  override fun visitBinaryOperatorExpr(expr: Expr.BinaryOperatorExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      val op = expr.op.toString()
      val res = mutableListOf<FormatNode>()
      res += expr.left.visit()
      if (op == "-") {
        res += Space
        res += Text(op)
        res += indent(SpaceOrLine, expr.right.visit())
      } else {
        res += indent(SpaceOrLine, Text(op), Space, expr.right.visit())
      }
      group(newId(), res)
    }
  }

  override fun visitTypeCheckExpr(expr: Expr.TypeCheckExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      group(newId(), expr.expr.visit(), SpaceOrLine, Text("is"), Space, expr.type.visit())
    }
  }

  override fun visitTypeCastExpr(expr: Expr.TypeCastExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      group(newId(), expr.expr.visit(), SpaceOrLine, Text("as"), Space, expr.type.visit())
    }
  }

  override fun visitIfExpr(expr: Expr.IfExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      val res = mutableListOf<FormatNode>()
      res += Text("if ")
      res += group(newId(), Text("("), Line, expr.cond.visit(), Line, Text(")"))
      res += SpaceOrLine
      res += indent(expr.then.visit())
      res += SpaceOrLine
      res += Text("else")
      val els = expr.els
      // flatten the ifs
      val fnode = els.visit()
      res +=
        if (els is Expr.IfExpr && fnode is Group) {
          res += Space
          nodes(fnode.nodes)
        } else {
          res += SpaceOrLine
          indent(fnode)
        }
      group(newId(), res)
    }
  }

  override fun visitLetExpr(expr: Expr.LetExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      val res = mutableListOf<FormatNode>()
      res += Text("let ")
      res +=
        group(
          newId(),
          Text("("),
          indent(
            Line,
            grouping(
              visitParameter(expr.parameter),
              Space,
              Text("="),
              indent(SpaceOrLine, expr.bindingExpr.visit()),
            ),
          ),
          Text(")"),
        )
      val exp = expr.expr
      // flatten the lets
      val fnode = exp.visit()
      res += SpaceOrLine
      res +=
        if (exp is Expr.LetExpr && fnode is Group) {
          nodes(fnode.nodes)
        } else {
          indent(fnode)
        }
      group(newId(), res)
    }
  }

  override fun visitFunctionLiteralExpr(expr: Expr.FunctionLiteralExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      val res = mutableListOf<FormatNode>()
      res += grouping(visitParameterList(expr.parameterList))
      res += Space
      res += Text("->")
      res += processSameLineExpr(expr.expr)
      group(newId(), res)
    }
  }

  override fun visitParenthesizedExpr(expr: Expr.ParenthesizedExpr): FormatNode {
    return withPrefixesAndSuffixes(expr) {
      grouping(Text("("), indent(Line, expr.expr.visit()), Line, Text(")"))
    }
  }

  override fun visitObjectProperty(member: ObjectMember.ObjectProperty): FormatNode {
    return withPrefixesAndSuffixes(member) {
      val res = mutableListOf<FormatNode>()
      if (member.modifiers.isNotEmpty()) {
        res += visitModifierList(member.modifiers)
      }
      res += visitIdentifier(member.identifier)
      if (member.typeAnnotation != null) {
        res += visitTypeAnnotation(member.typeAnnotation!!)
      }
      val expr = member.expr
      if (expr != null) {
        res += Space
        res += Text("=")
        res += processSameLineExpr(expr)
      } else {
        for (body in member.bodyList) {
          res += Space
          res += visitObjectBody(body)
        }
      }

      group(newId(), res)
    }
  }

  override fun visitObjectMethod(method: ObjectMember.ObjectMethod): FormatNode {
    return withPrefixesAndSuffixes(method) {
      val res = mutableListOf<FormatNode>()
      if (method.modifiers.isNotEmpty()) {
        res += visitModifierList(method.modifiers)
      }
      res += Text("function ")
      res += visitIdentifier(method.identifier)
      if (method.typeParameterList != null) {
        res += visitTypeParameterList(method.typeParameterList!!)
      }
      res += visitParameterList(method.paramList)
      if (method.typeAnnotation != null) {
        res += visitTypeAnnotation(method.typeAnnotation!!)
      }
      if (method.typeAnnotation != null) {
        res += visitTypeAnnotation(method.typeAnnotation!!)
      }
      val expr = method.expr
      res += Space
      res += Text("=")
      res += processSameLineExpr(expr)

      group(newId(), res)
    }
  }

  override fun visitMemberPredicate(pred: ObjectMember.MemberPredicate): FormatNode {
    return withPrefixesAndSuffixes(pred) {
      val res = mutableListOf<FormatNode>()
      res += grouping(Text("[["), Line, indent(pred.pred.visit()), Line, Text("]]"))
      val expr = pred.expr
      if (expr != null) {
        res += Space
        res += Text("=")
        res += processSameLineExpr(expr)
      } else {
        for (body in pred.bodyList) {
          res += Space
          res += visitObjectBody(body)
        }
      }
      group(newId(), res)
    }
  }

  override fun visitObjectElement(member: ObjectMember.ObjectElement): FormatNode {
    return withPrefixesAndSuffixes(member) { member.expr.visit() }
  }

  override fun visitObjectEntry(member: ObjectMember.ObjectEntry): FormatNode {
    return withPrefixesAndSuffixes(member) {
      val res = mutableListOf<FormatNode>()
      res += grouping(Text("["), Line, indent(member.key.visit()), Line, Text("]"))
      val expr = member.value
      if (expr != null) {
        res += Space
        res += Text("=")
        res += processSameLineExpr(expr)
      } else {
        for (body in member.bodyList) {
          res += Space
          res += visitObjectBody(body)
        }
      }
      group(newId(), res)
    }
  }

  override fun visitObjectSpread(member: ObjectMember.ObjectSpread): FormatNode {
    return withPrefixesAndSuffixes(member) {
      nodes(Text(if (member.isNullable) "...?" else "..."), member.expr.visit())
    }
  }

  override fun visitWhenGenerator(member: ObjectMember.WhenGenerator): FormatNode {
    return withPrefixesAndSuffixes(member) {
      val els = member.elseClause
      grouping(
        Text("when "),
        grouping(Text("("), indent(Line, member.predicate.visit()), Text(")")),
        Space,
        visitObjectBody(member.thenClause),
        if (els == null) {
          Empty
        } else {
          nodes(Space, Text("else"), Space, visitObjectBody(els))
        },
      )
    }
  }

  override fun visitForGenerator(member: ObjectMember.ForGenerator): FormatNode {
    return withPrefixesAndSuffixes(member) {
      val predicate =
        indent(
          Line,
          visitParameter(member.p1),
          if (member.p2 == null) Empty else nodes(Text(", "), visitParameter(member.p2!!)),
          Text(" in"),
          processSameLineExpr(member.expr),
        )
      grouping(
        Text("for "),
        grouping(Text("("), predicate, Text(")")),
        Space,
        visitObjectBody(member.body),
      )
    }
  }

  override fun visitModule(module: Module): FormatNode {
    return withPrefixesAndSuffixes(module) {
      val res = mutableListOf<FormatNode>()
      if (module.decl != null) {
        res += visitModuleDecl(module.decl!!)
      }
      val imports = visitImportList(module.imports)
      if (imports != null) {
        if (res.isNotEmpty()) res += twoNewlines
        res += imports
      }
      val members = module.moduleMembers
      if (members.isNotEmpty()) {
        if (res.isNotEmpty()) res += twoNewlines
        res +=
          withSeparator(module.moduleMembers) { prev, next ->
            if (prev.span().linesBetween(next.span()) > 1) twoNewlines else ForceLine
          }
      }
      Nodes(res)
    }
  }

  override fun visitModuleDecl(decl: ModuleDecl): FormatNode {
    return withPrefixesAndSuffixes(decl) {
      val res = mutableListOf<FormatNode>()
      val preamble = formatPreamble(decl.docComment, decl.annotations)
      var shouldNewline = preamble !is Empty
      if (decl.modifiers.isNotEmpty()) {
        if (shouldNewline) res += ForceLine
        res += visitModifierList(decl.modifiers)
        shouldNewline = true
      }
      if (decl.moduleKeyword != null) {
        res +=
          grouping(
            visitKeyword(decl.moduleKeyword!!),
            SpaceOrLine,
            indent(visitQualifiedIdentifier(decl.name!!)), // cannot be null
          )
        shouldNewline = true
      }

      val extendsAmends = decl.extendsOrAmendsDecl
      if (extendsAmends != null) {
        if (shouldNewline) res += ForceLine
        res += visitExtendsOrAmendsClause(extendsAmends)
      }
      nodes(preamble, *res.toTypedArray())
    }
  }

  override fun visitExtendsOrAmendsClause(decl: ExtendsOrAmendsClause): FormatNode {
    return withPrefixesAndSuffixes(decl) {
      val type = if (decl.type == ExtendsOrAmendsClause.Type.AMENDS) "amends" else "extends"
      grouping(Text(type), indent(SpaceOrLine, visitStringConstant(decl.url)))
    }
  }

  private fun visitImportList(imps: List<ImportClause>): FormatNode? {
    if (imps.isEmpty()) return null
    val nodes = mutableListOf<FormatNode>()
    val imports =
      imps.groupBy { imp ->
        val url = getImportUrl(imp)
        when {
          ABSOLUTE_URL_REGEX.matches(url) -> 0
          url.startsWith('@') -> 1
          else -> 2
        }
      }
    val absolute = imports[0]?.sortedWith(ImportComparator(source))
    val projects = imports[1]?.sortedWith(ImportComparator(source))
    val relatives = imports[2]?.sortedWith(ImportComparator(source))
    var shouldNewline = false

    if (absolute != null) {
      for ((i, imp) in absolute.withIndex()) {
        if (i > 0) nodes += ForceLine
        nodes += visitImportClause(imp)
      }
      shouldNewline = true
    }

    if (projects != null) {
      if (shouldNewline) nodes += twoNewlines
      for ((i, imp) in projects.withIndex()) {
        if (i > 0) nodes += ForceLine
        nodes += visitImportClause(imp)
      }
      shouldNewline = true
    }

    if (relatives != null) {
      if (shouldNewline) nodes += twoNewlines
      for ((i, imp) in relatives.withIndex()) {
        if (i > 0) nodes += ForceLine
        nodes += visitImportClause(imp)
      }
    }
    return Nodes(nodes)
  }

  override fun visitImportClause(imp: ImportClause): FormatNode {
    return withPrefixesAndSuffixes(imp) {
      grouping(
        Text(if (imp.isGlob) "import* " else "import "),
        visitStringConstant(imp.importStr),
        if (imp.alias != null) {
          indent(SpaceOrLine, Text("as "), visitIdentifier(imp.alias!!))
        } else Empty,
      )
    }
  }

  override fun visitClass(clazz: Class): FormatNode {
    return withPrefixesAndSuffixes(clazz) {
      val res = mutableListOf<FormatNode>()
      val preamble = formatPreamble(clazz.docComment, clazz.annotations)
      res += preamble
      val shouldNewline = preamble !is Empty
      if (clazz.modifiers.isNotEmpty()) {
        if (shouldNewline) res += ForceLine
        res += visitModifierList(clazz.modifiers)
      }
      res += visitKeyword(clazz.classKeyword)
      res += Space
      res += visitIdentifier(clazz.name)
      if (clazz.typeParameterList != null) {
        res += grouping(visitTypeParameterList(clazz.typeParameterList!!))
      }
      if (clazz.superClass != null) {
        res += grouping(indent(SpaceOrLine, Text("extends "), clazz.superClass!!.visit()))
      }
      if (clazz.body != null) {
        res += Space
        res += visitClassBody(clazz.body!!)
      }
      nodes(res)
    }
  }

  private fun visitModifierList(modifiers: List<Modifier>): FormatNode {
    val sorted = modifiers.sortedBy(::modifierPrecedence)
    val nodes = sorted.map { visitModifier(it) }
    return Nodes(nodes)
  }

  override fun visitModifier(modifier: Modifier): FormatNode {
    return withPrefixesAndSuffixes(modifier) { Text(modifier.text(source) + " ") }
  }

  override fun visitClassProperty(prop: ClassProperty): FormatNode {
    return withPrefixesAndSuffixes(prop) {
      val res = mutableListOf<FormatNode>()
      val preamble = formatPreamble(prop.docComment, prop.annotations)
      val shouldNewline = preamble !is Empty
      if (prop.modifiers.isNotEmpty()) {
        if (shouldNewline) res += ForceLine
        res += visitModifierList(prop.modifiers)
      }
      res += visitIdentifier(prop.name)
      if (prop.typeAnnotation != null) {
        res += visitTypeAnnotation(prop.typeAnnotation!!)
      }
      val expr = prop.expr
      if (expr != null) {
        res += Space
        res += Text("=")
        res += processSameLineExpr(expr)
      } else if (prop.bodyList.isNotEmpty()) {
        res += Space
        res += withSeparator(prop.bodyList, Space)
      }

      if (preamble !is Empty) {
        nodes(preamble, group(newId(), res))
      } else {
        group(newId(), res)
      }
    }
  }

  override fun visitClassMethod(method: ClassMethod): FormatNode {
    return withPrefixesAndSuffixes(method) {
      val res = mutableListOf<FormatNode>()
      val preamble = formatPreamble(method.docComment, method.annotations)
      val shouldNewline = preamble !is Empty
      if (method.modifiers.isNotEmpty()) {
        if (shouldNewline) res += ForceLine
        res += visitModifierList(method.modifiers)
      }
      res += Text("function ")
      res += visitIdentifier(method.name)
      if (method.typeParameterList != null) {
        res += visitTypeParameterList(method.typeParameterList!!)
      }
      res += visitParameterList(method.parameterList)
      if (method.typeAnnotation != null) {
        res += visitTypeAnnotation(method.typeAnnotation!!)
      }
      val expr = method.expr
      if (expr != null) {
        res += Space
        res += Text("=")
        res += processSameLineExpr(expr)
      }

      if (preamble !is Empty) {
        nodes(preamble, group(newId(), res))
      } else {
        group(newId(), res)
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun visitClassBody(classBody: ClassBody): FormatNode {
    return withPrefixesAndSuffixes(classBody) {
      val children = classBody.children()!!
      if (children.isEmpty()) {
        Text("{}")
      } else {
        val elements = mutableListOf<FormatNode>()
        elements += ForceLine
        elements +=
          withSeparator(children as List<Node>) { prev, next ->
            if (prev.span().linesBetween(next.span()) > 1) twoNewlines else ForceLine
          }
        elements += ForceLine
        grouping(Text("{"), Indent(elements), Text("}"))
      }
    }
  }

  override fun visitTypeAlias(typeAlias: TypeAlias): FormatNode {
    return withPrefixesAndSuffixes(typeAlias) {
      val res = mutableListOf<FormatNode>()
      val preamble = formatPreamble(typeAlias.docComment, typeAlias.annotations)
      val shouldNewline = preamble !is Empty
      if (typeAlias.modifiers.isNotEmpty()) {
        if (shouldNewline) res += ForceLine
        res += visitModifierList(typeAlias.modifiers)
      }
      res += visitKeyword(typeAlias.typealiasKeyword)
      res += Space
      res += visitIdentifier(typeAlias.name)
      if (typeAlias.typeParameterList != null) {
        visitTypeParameterList(typeAlias.typeParameterList!!)
      }
      res += Space
      res += Text("=")
      res += indent(SpaceOrLine, typeAlias.type.visit())
      if (preamble !is Empty) {
        nodes(preamble, group(newId(), res))
      } else group(newId(), res)
    }
  }

  override fun visitAnnotation(annotation: Annotation): FormatNode {
    return withPrefixesAndSuffixes(annotation) {
      nodes(
        Text("@"),
        annotation.type.visit(),
        Space,
        if (annotation.body != null) visitObjectBody(annotation.body!!) else Empty,
      )
    }
  }

  override fun visitParameter(param: Parameter): FormatNode {
    return withPrefixesAndSuffixes(param) {
      when (param) {
        is Parameter.Underscore -> Text("_")
        is Parameter.TypedIdentifier -> {
          val typeAnnotation = param.typeAnnotation
          if (typeAnnotation == null) {
            nodes(visitIdentifier(param.identifier))
          } else {
            nodes(visitIdentifier(param.identifier), visitTypeAnnotation(typeAnnotation))
          }
        }
      }
    }
  }

  override fun visitParameterList(paramList: ParameterList): FormatNode {
    return visitParameterList(paramList, false)
  }

  fun visitParameterList(paramList: ParameterList, shouldGroup: Boolean): FormatNode {
    return withPrefixesAndSuffixes(paramList) {
      val params = paramList.parameters
      if (params.isEmpty()) {
        Text("()")
      } else {
        val indented = indent(Line, withSeparator(params, SpaceOrLine), Line)
        if (shouldGroup) {
          grouping(Text("("), indented, Text(")"))
        } else {
          nodes(Text("("), indented, Text(")"))
        }
      }
    }
  }

  override fun visitTypeParameter(typeParameter: TypeParameter): FormatNode {
    return withPrefixesAndSuffixes(typeParameter) {
      val variance =
        when (typeParameter.variance) {
          TypeParameter.Variance.IN -> Text("in ")
          TypeParameter.Variance.OUT -> Text("out ")
          else -> Empty
        }
      nodes(variance, visitIdentifier(typeParameter.identifier))
    }
  }

  override fun visitTypeParameterList(typeParameterList: TypeParameterList): FormatNode {
    return withPrefixesAndSuffixes(typeParameterList) {
      val params = typeParameterList.parameters
      if (params.isEmpty()) {
        Text("<>")
      } else {
        nodes(Text("<"), indent(Line, withSeparator(params, SpaceOrLine), Line), Text(">"))
      }
    }
  }

  override fun visitTypeAnnotation(typeAnnotation: TypeAnnotation): FormatNode {
    return withPrefixesAndSuffixes(typeAnnotation) {
      nodes(Text(":"), Space, typeAnnotation.type.visit())
    }
  }

  override fun visitArgumentList(argumentList: ArgumentList): FormatNode {
    return visitArgumentList(argumentList, false)
  }

  private fun visitArgumentList(argumentList: ArgumentList, twoByTwo: Boolean): FormatNode {
    return withPrefixesAndSuffixes(argumentList) {
      val args = argumentList.arguments
      when {
        args.isEmpty() -> Text("()")
        !twoByTwo ->
          nodes(Text("("), indent(Line, withSeparator(args, SpaceOrLine), Line), Text(")"))
        else -> {
          val fargs = mutableListOf<FormatNode>()
          for (i in args.indices) {
            val arg = args[i]
            if (i > 0) {
              fargs += if (i % 2 == 0) SpaceOrLine else Space
            }
            fargs += arg.visit()
          }
          nodes(Text("("), indent(Line, Nodes(fargs), Line), Text(")"))
        }
      }
    }
  }

  override fun visitStringPart(part: StringPart): FormatNode {
    // string parts cannot have affixes
    return when (part) {
      is StringPart.StringChars -> Text(part.text(source))
      is StringPart.StringInterpolation -> {
        nodes(Text("\\("), part.expr.visit(), Text(")"))
      }
    }
  }

  override fun visitDocComment(docComment: DocComment): FormatNode {
    return withPrefixesAndSuffixes(docComment) {
      val txts = docComment.span().text()
      val lines =
        txts.trimEnd().lines().map { line ->
          if (line.startsWith("///")) processDocComment(line) else Text(line)
        }
      nodes(*lines.interleave(CommentLine).toTypedArray(), CommentLine)
    }
  }

  private fun processDocComment(txt: String): FormatNode {
    if (txt == "///" || txt == "/// ") return Text("///")

    var comment = txt.substring(3)
    if (comment.isStrictBlank()) return Text("///")

    if (comment.isNotEmpty() && comment[0] != ' ') comment = " $comment"
    return Text("///$comment")
  }

  override fun visitIdentifier(identifier: Identifier): FormatNode {
    return withPrefixesAndSuffixes(identifier) { Text(identifier.text(source)) }
  }

  override fun visitQualifiedIdentifier(qualifiedIdentifier: QualifiedIdentifier): FormatNode {
    return withPrefixesAndSuffixes(qualifiedIdentifier) {
      val idents = qualifiedIdentifier.identifiers
      when (idents.size) {
        0 -> Empty
        1 -> visitIdentifier(idents[0])
        else -> {
          grouping(
            visitIdentifier(idents[0]),
            Indent(idents.drop(1).map { nodes(Line, Text("."), visitIdentifier(it)) }),
          )
        }
      }
    }
  }

  override fun visitObjectBody(body: ObjectBody): FormatNode {
    return withPrefixesAndSuffixes(body) {
      val paramNodes = body.parameters
      val members = body.members
      if (paramNodes.isEmpty() && members.isEmpty()) {
        Text("{}")
      } else {
        val pars =
          when (paramNodes.size) {
            0 -> Empty
            1 -> nodes(Space, visitParameter(paramNodes[0]), Space, Text("->"))
            else -> {
              val rest = withSeparator(paramNodes.drop(1), SpaceOrLine)
              grouping(
                Space,
                visitParameter(paramNodes[0]),
                // double indent
                indent(indent(SpaceOrLine, rest, Space, Text("->"))),
              )
            }
          }

        val groupId = newId()
        val res =
          withSeparator(members) { prev, next ->
            when (prev.span().linesBetween(next.span())) {
              0 -> IfWrap(groupId, Line, Text("; "))
              1 -> ForceLine
              else -> twoNewlines
            }
          }
        val separator = if (body.span().linesBetween() > 0) ForceLine else SpaceOrLine
        group(groupId, Text("{"), pars, indent(separator, res), separator, Text("}"))
      }
    }
  }

  override fun visitReplInput(replInput: ReplInput): FormatNode {
    throw RuntimeException("Formatter should not be called on a repl input")
  }

  override fun visitKeyword(keyword: Keyword): FormatNode {
    return withPrefixesAndSuffixes(keyword) { Text(keyword.text(source)) }
  }

  override fun visitTypeArgumentList(typeArgumentList: TypeArgumentList): FormatNode {
    return withPrefixesAndSuffixes(typeArgumentList) {
      val types = typeArgumentList.types
      if (types.isEmpty()) {
        Text("<>")
      } else {
        nodes(Text("<"), indent(Line, withSeparator(types, SpaceOrLine), Line), Text(">"))
      }
    }
  }

  private fun formatPreamble(doc: DocComment?, annotations: List<Annotation>): FormatNode {
    val res = mutableListOf<FormatNode>()
    var shouldNewline = false
    if (doc != null) {
      res += visitDocComment(doc)
    }
    for (ann in annotations) {
      if (shouldNewline) res += ForceLine
      res += visitAnnotation(ann)
      res += ForceLine
      shouldNewline = true
    }
    return nodes(res)
  }

  private fun formatPrefixes(node: Node): List<FormatNode> {
    val affixes = node.affixes()?.filter { it.fixity() == AffixFixity.PREFIX }
    if (affixes == null || affixes.isEmpty()) return emptyList()
    val res = mutableListOf<FormatNode>()
    for (affix in affixes) {
      res += Text(affix.span.text())
      res +=
        when (affix.type) {
          AffixType.LINE_COMMENT -> ForceLine
          AffixType.BLOCK_COMMENT -> if (node.span().trails(affix.span())) Space else ForceLine
          AffixType.COMMA -> throw RuntimeException("cannot have comma as prefix")
        }
    }
    return res
  }

  private fun formatSuffixes(node: Node): List<FormatNode> {
    val affixes = node.affixes()?.filter { it.fixity() == AffixFixity.SUFFIX }
    if (affixes == null || affixes.isEmpty()) return emptyList()
    val res = mutableListOf<FormatNode>()
    for ((i, affix) in affixes.withIndex()) {
      if (i == 0) {
        res +=
          if (affix.span().trails(node.span())) {
            if (affix.type == AffixType.COMMA) Empty else Space
          } else ForceLine
      }
      res += Text(affix.span.text())
      when (affix.type) {
        AffixType.LINE_COMMENT -> res += CommentLine
        AffixType.BLOCK_COMMENT -> {
          if (!affix.span().trails(node.span())) res += CommentLine
          else if (i < affixes.lastIndex) res += Space
        }
        AffixType.COMMA -> if (i < affixes.lastIndex) res += Space
      }
    }
    return res
  }

  private inline fun withPrefixesAndSuffixes(node: Node, fn: () -> FormatNode): FormatNode {
    return if (node.affixes()?.isEmpty() ?: true) {
      fn()
    } else {
      val prefixes = formatPrefixes(node)
      val suffixes = formatSuffixes(node)
      Nodes(prefixes + fn() + suffixes)
    }
  }

  private fun modifierPrecedence(modifier: Modifier): Int {
    val text = modifier.text(source)
    return when (text) {
      "abstract",
      "open" -> 0
      "external" -> 1
      "local",
      "hidden" -> 2
      "fixed",
      "const" -> 3
      else -> throw RuntimeException("Unknown modifier `$text`")
    }
  }

  private fun withSeparator(nodes: List<Node>, separator: FormatNode): FormatNode {
    return withSeparator(nodes) { _, _ -> separator }
  }

  private inline fun withSeparator(
    nodes: List<Node>,
    separator: (Node, Node) -> FormatNode,
  ): FormatNode {
    val res = mutableListOf<FormatNode>()
    var prev: Node? = null
    for (node in nodes) {
      if (prev != null) {
        res += separator(prev, node)
      }
      res += node.visit()
      prev = node
    }
    return Nodes(res)
  }

  private fun processSameLineExpr(expr: Expr): FormatNode {
    return if (isSameLineExpr(expr)) {
      nodes(Space, expr.visit())
    } else {
      indent(SpaceOrLine, expr.visit())
    }
  }

  private fun getImportUrl(imp: ImportClause): String {
    val text = imp.importStr.text(source)
    return text.substring(1, text.length - 1)
  }

  private fun newId() = id++

  private class ImportComparator(private val source: CharArray) : Comparator<ImportClause> {
    override fun compare(o1: ImportClause, o2: ImportClause): Int {
      val import1 = o1.importStr.text(source)
      val import2 = o2.importStr.text(source)

      return NaturalOrderComparator(ignoreCase = true).compare(import1, import2)
    }
  }

  private fun hasFunctionLiteral(node: Node, depth: Int): Boolean {
    if (node is Expr.FunctionLiteralExpr) return true
    val children = node.children() ?: emptyList()
    for (child in children) {
      if (child == null) continue
      if (child is Expr.FunctionLiteralExpr) return true
      if (depth > 0 && hasFunctionLiteral(child, depth - 1)) return true
    }
    return false
  }

  private fun Node.visit(): FormatNode = accept(this@Builder)

  fun <T> List<T>.interleave(separator: T): List<T> = buildList {
    this@interleave.forEachIndexed { index, item ->
      if (index > 0) add(separator)
      add(item)
    }
  }

  private fun grouping(vararg nodes: FormatNode) = Group(newId(), nodes.toList())

  private fun isSameLineExpr(expr: Expr): Boolean =
    expr is Expr.NewExpr || expr is Expr.AmendsExpr || expr is Expr.FunctionLiteralExpr

  private fun Span.text(): String = String(source, charIndex, length)

  private fun Span.trails(other: Span): Boolean {
    val otherEnd = other.charIndex + other.length - 1
    val thisStart = this.charIndex

    if (thisStart < otherEnd) return false

    for (newlinePos in newlines) {
      return when {
        newlinePos < otherEnd -> continue // Before other ends, keep looking
        newlinePos >= thisStart -> true // Past this start, no newlines between
        else -> false // Found a newline between otherEnd and thisStart
      }
    }
    return true
  }

  private fun Span.linesBetween(other: Span): Int {
    val start = minOf(this.charIndex + this.length - 1, other.charIndex + other.length - 1)
    val end = maxOf(this.charIndex, other.charIndex)

    // If spans overlap or are adjacent, there are no lines between them
    if (start >= end) return 0

    var total = 0
    for (pos in newlines) {
      if (pos > end) break
      if (pos >= start) total++
    }
    return total
  }

  private fun Span.linesBetween(): Int {
    val start = charIndex
    val end = charIndex + length - 1

    var total = 0
    for (pos in newlines) {
      if (pos > end) break
      if (pos >= start) total++
    }
    return total
  }

  private fun Span.columnBegin(): Int {
    if (newlines.isEmpty() || charIndex < newlines[0]) {
      return charIndex
    }

    var left = 0
    var right = newlines.lastIndex
    var lastNewlineIndex = -1

    while (left <= right) {
      val mid = left + (right - left) / 2
      if (newlines[mid] <= charIndex) {
        lastNewlineIndex = newlines[mid]
        left = mid + 1
      } else {
        right = mid - 1
      }
    }

    return charIndex - lastNewlineIndex
  }

  companion object {
    private val ABSOLUTE_URL_REGEX = Regex("""\w+:.*""")

    fun String.isStrictBlank(): Boolean {
      for (ch in this) {
        if (ch != ' ' && ch != '\t') return false
      }
      return true
    }
  }
}
