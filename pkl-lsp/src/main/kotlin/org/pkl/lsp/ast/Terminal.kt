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
package org.pkl.lsp.ast

import org.antlr.v4.runtime.tree.TerminalNode
import org.pkl.core.parser.antlr.PklParser
import org.pkl.lsp.PklVisitor

class TerminalImpl(
  override val parent: Node,
  override val ctx: TerminalNode,
  override val type: TokenType
) : AbstractNode(parent, ctx), Terminal {

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTerminal(this)
  }
}

val Terminal.isModifier: Boolean
  get() {
    return modifierTypes.contains(type)
  }

fun TerminalNode.toTerminal(parent: Node): Terminal? {
  val tokenType =
    when (symbol.type) {
      PklParser.ABSTRACT -> TokenType.ABSTRACT
      PklParser.AMENDS -> TokenType.AMENDS
      PklParser.AS -> TokenType.AS
      PklParser.CLASS -> TokenType.CLASS
      PklParser.CONST -> TokenType.CONST
      PklParser.ELSE -> TokenType.ELSE
      PklParser.EXTENDS -> TokenType.EXTENDS
      PklParser.EXTERNAL -> TokenType.EXTERNAL
      PklParser.FALSE -> TokenType.FALSE
      PklParser.FIXED -> TokenType.FIXED
      PklParser.FOR -> TokenType.FOR
      PklParser.FUNCTION -> TokenType.FUNCTION
      PklParser.HIDDEN_ -> TokenType.HIDDEN
      PklParser.IF -> TokenType.IF
      PklParser.IMPORT -> TokenType.IMPORT
      PklParser.IMPORT_GLOB -> TokenType.IMPORT_GLOB
      PklParser.IN -> TokenType.IN
      PklParser.IS -> TokenType.IS
      PklParser.LET -> TokenType.LET
      PklParser.LOCAL -> TokenType.LOCAL
      PklParser.MODULE -> TokenType.MODULE
      PklParser.NEW -> TokenType.NEW
      PklParser.NOTHING -> TokenType.NOTHING
      PklParser.NULL -> TokenType.NULL
      PklParser.OPEN -> TokenType.OPEN
      PklParser.OUT -> TokenType.OUT
      PklParser.OUTER -> TokenType.OUTER
      PklParser.READ -> TokenType.READ
      PklParser.READ_GLOB -> TokenType.READ_GLOB
      PklParser.READ_OR_NULL -> TokenType.READ_OR_NULL
      PklParser.SUPER -> TokenType.SUPER
      PklParser.THIS -> TokenType.THIS
      PklParser.THROW -> TokenType.THROW
      PklParser.TRACE -> TokenType.TRACE
      PklParser.TRUE -> TokenType.TRUE
      PklParser.TYPE_ALIAS -> TokenType.TYPE_ALIAS
      PklParser.UNKNOWN -> TokenType.UNKNOWN
      PklParser.WHEN -> TokenType.WHEN
      PklParser.PROTECTED -> TokenType.PROTECTED
      PklParser.OVERRIDE -> TokenType.OVERRIDE
      PklParser.RECORD -> TokenType.RECORD
      PklParser.DELETE -> TokenType.DELETE
      PklParser.CASE -> TokenType.CASE
      PklParser.SWITCH -> TokenType.SWITCH
      PklParser.VARARG -> TokenType.VARARG
      PklParser.LPAREN -> TokenType.LPAREN
      PklParser.RPAREN -> TokenType.RPAREN
      PklParser.LBRACE -> TokenType.LBRACE
      PklParser.RBRACE -> TokenType.RBRACE
      PklParser.LBRACK -> TokenType.LBRACK
      PklParser.RBRACK -> TokenType.RBRACK
      PklParser.LPRED -> TokenType.LPRED
      PklParser.COMMA -> TokenType.COMMA
      PklParser.DOT -> TokenType.DOT
      PklParser.QDOT -> TokenType.QDOT
      PklParser.COALESCE -> TokenType.COALESCE
      PklParser.NON_NULL -> TokenType.NON_NULL
      PklParser.AT -> TokenType.AT
      PklParser.ASSIGN -> TokenType.ASSIGN
      PklParser.GT -> TokenType.GT
      PklParser.LT -> TokenType.LT
      PklParser.NOT -> TokenType.NOT
      PklParser.QUESTION -> TokenType.QUESTION
      PklParser.COLON -> TokenType.COLON
      PklParser.ARROW -> TokenType.ARROW
      PklParser.EQUAL -> TokenType.EQUAL
      PklParser.NOT_EQUAL -> TokenType.NOT_EQUAL
      PklParser.LTE -> TokenType.LTE
      PklParser.GTE -> TokenType.GTE
      PklParser.AND -> TokenType.AND
      PklParser.OR -> TokenType.OR
      PklParser.PLUS -> TokenType.PLUS
      PklParser.MINUS -> TokenType.MINUS
      PklParser.POW -> TokenType.POW
      PklParser.STAR -> TokenType.STAR
      PklParser.DIV -> TokenType.DIV
      PklParser.INT_DIV -> TokenType.INT_DIV
      PklParser.MOD -> TokenType.MOD
      PklParser.UNION -> TokenType.UNION
      PklParser.PIPE -> TokenType.PIPE
      PklParser.SPREAD -> TokenType.SPREAD
      PklParser.QSPREAD -> TokenType.QSPREAD
      PklParser.UNDERSCORE -> TokenType.UNDERSCORE
      PklParser.SLQuote -> TokenType.SLQuote
      PklParser.MLQuote -> TokenType.MLQuote
      PklParser.IntLiteral -> TokenType.IntLiteral
      PklParser.FloatLiteral -> TokenType.FloatLiteral
      PklParser.Identifier -> TokenType.Identifier
      PklParser.NewlineSemicolon -> TokenType.NewlineSemicolon
      PklParser.Whitespace -> TokenType.Whitespace
      PklParser.DocComment -> TokenType.DocComment
      PklParser.BlockComment -> TokenType.BlockComment
      PklParser.LineComment -> TokenType.LineComment
      PklParser.ShebangComment -> TokenType.ShebangComment
      PklParser.SLEndQuote -> TokenType.SLEndQuote
      PklParser.SLInterpolation -> TokenType.SLInterpolation
      PklParser.SLUnicodeEscape -> TokenType.SLUnicodeEscape
      PklParser.SLCharacterEscape -> TokenType.SLCharacterEscape
      PklParser.SLCharacters -> TokenType.SLCharacters
      PklParser.MLEndQuote -> TokenType.MLEndQuote
      PklParser.MLInterpolation -> TokenType.MLInterpolation
      PklParser.MLUnicodeEscape -> TokenType.MLUnicodeEscape
      PklParser.MLCharacterEscape -> TokenType.MLCharacterEscape
      PklParser.MLNewline -> TokenType.MLNewline
      PklParser.MLCharacters -> TokenType.MLCharacters
      else -> return null
    }
  return TerminalImpl(parent, this, tokenType)
}
