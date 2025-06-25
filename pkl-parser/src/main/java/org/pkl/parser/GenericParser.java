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
package org.pkl.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.pkl.parser.syntax.Operator;
import org.pkl.parser.syntax.generic.FullSpan;
import org.pkl.parser.syntax.generic.GenNode;
import org.pkl.parser.syntax.generic.NodeType;
import org.pkl.parser.util.ErrorMessages;
import org.pkl.parser.util.Nullable;

@SuppressWarnings("DuplicatedCode")
public class GenericParser {

  private Lexer lexer;
  private Token lookahead;
  private FullSpan spanLookahead;
  private FullToken _lookahead;
  private int cursor = 0;
  private final List<FullToken> tokens = new ArrayList<>();

  private void init(String source) {
    this.lexer = new Lexer(source);
    cursor = 0;
    while (true) {
      var ft = new FullToken(lexer.next(), lexer.fullSpan(), lexer.newLinesBetween);
      tokens.add(ft);
      if (ft.token == Token.EOF) break;
    }
    _lookahead = tokens.get(cursor);
    lookahead = _lookahead.token;
    spanLookahead = _lookahead.span;
  }

  public GenNode parseModule(String source) {
    init(source);
    if (lookahead == Token.EOF) {
      return new GenNode(NodeType.MODULE, new FullSpan(0, 0, 1, 1, 1, 1), List.of());
    }
    var children = new ArrayList<GenNode>();
    var nodes = new ArrayList<GenNode>();
    ff(nodes);

    var res = parseMemberHeader(children);

    if (isModuleDecl()) {
      nodes.add(parseModuleDecl(children));
      children.clear();
      res = new HeaderResult(false, false, false);
      ff(nodes);
    }

    // imports
    var imports = new ArrayList<GenNode>();
    while (lookahead == Token.IMPORT || lookahead == Token.IMPORT_STAR) {
      if (res.hasDocComment || res.hasAnnotations || res.hasModifiers) {
        throw parserError("wrongHeaders", "Imports");
      }
      var lastImport = parseImportDecl();
      imports.add(lastImport);
      // keep trailling affixes as part of the import
      while (lookahead.isAffix() && lastImport.span.sameLine(spanLookahead)) {
        imports.add(makeAffix(next()));
      }
      if (!isImport()) break;
      ff(imports);
    }
    if (!imports.isEmpty()) {
      nodes.add(new GenNode(NodeType.IMPORT_LIST, imports));
      ff(nodes);
    }

    // entries
    if (res.hasDocComment || res.hasAnnotations || res.hasModifiers) {
      nodes.add(parseModuleMember(children));
      ff(nodes);
    }

    while (lookahead != Token.EOF) {
      children.clear();
      parseMemberHeader(children);
      nodes.add(parseModuleMember(children));
      ff(nodes);
    }
    return new GenNode(NodeType.MODULE, nodes);
  }

  private GenNode parseModuleDecl(List<GenNode> preChildren) {
    var headerParts = getHeaderParts(preChildren);
    var children = new ArrayList<>(headerParts.preffixes);
    var headers = new ArrayList<GenNode>();
    if (headerParts.modifierList != null) {
      headers.add(headerParts.modifierList);
    }
    if (lookahead == Token.MODULE) {
      var subChildren = new ArrayList<>(headers);
      subChildren.add(makeTerminal(next()));
      ff(subChildren);
      subChildren.add(parseQualifiedIdentifier());
      children.add(new GenNode(NodeType.MODULE_DEFINITION, subChildren));
    } else {
      children.addAll(headers);
      if (headerParts.modifierList != null) {
        throw parserError("wrongHeaders", "Amends or extends declaration");
      }
    }
    var looka = lookahead();
    if (looka == Token.AMENDS || looka == Token.EXTENDS) {
      var type = looka == Token.AMENDS ? NodeType.AMENDS_CLAUSE : NodeType.EXTENDS_CLAUSE;
      ff(children);
      var subChildren = new ArrayList<GenNode>();
      subChildren.add(makeTerminal(next()));
      ff(subChildren);
      subChildren.add(parseStringConstant());
      children.add(new GenNode(type, subChildren));
    }
    return new GenNode(NodeType.MODULE_DECLARATION, children);
  }

  private GenNode parseQualifiedIdentifier() {
    var children = new ArrayList<GenNode>();
    children.add(parseIdentifier());
    while (lookahead() == Token.DOT) {
      ff(children);
      children.add(new GenNode(NodeType.TERMINAL, next().span));
      ff(children);
      children.add(parseIdentifier());
    }
    return new GenNode(NodeType.QUALIFIED_IDENTIFIER, children);
  }

  private GenNode parseImportDecl() {
    var children = new ArrayList<GenNode>();
    children.add(makeTerminal(next()));
    ff(children);
    children.add(parseStringConstant());
    if (lookahead() == Token.AS) {
      ff(children);
      var alias = new ArrayList<GenNode>();
      alias.add(makeTerminal(next()));
      ff(alias);
      alias.add(parseIdentifier());
      children.add(new GenNode(NodeType.IMPORT_ALIAS, alias));
    }
    return new GenNode(NodeType.IMPORT, children);
  }

  private HeaderResult parseMemberHeader(List<GenNode> children) {
    var hasDocComment = false;
    var hasAnnotation = false;
    var hasModifier = false;
    var docs = new ArrayList<GenNode>();
    while (lookahead() == Token.DOC_COMMENT) {
      ff(children);
      docs.add(new GenNode(NodeType.DOC_COMMENT_LINE, next().span));
      hasDocComment = true;
    }
    if (hasDocComment) {
      children.add(new GenNode(NodeType.DOC_COMMENT, docs));
    }
    ff(children);
    while (lookahead == Token.AT) {
      children.add(parseAnnotation());
      hasAnnotation = true;
      ff(children);
    }
    var modifiers = new ArrayList<GenNode>();
    while (lookahead.isModifier()) {
      modifiers.add(make(NodeType.MODIFIER, next().span));
      hasModifier = true;
      ff(children);
    }
    if (hasModifier) children.add(new GenNode(NodeType.MODIFIER_LIST, modifiers));
    return new HeaderResult(hasDocComment, hasAnnotation, hasModifier);
  }

  private GenNode parseModuleMember(List<GenNode> preChildren) {
    return switch (lookahead) {
      case IDENTIFIER -> parseClassProperty(preChildren);
      case TYPE_ALIAS -> parseTypeAlias(preChildren);
      case CLASS -> parseClass(preChildren);
      case FUNCTION -> parseClassMethod(preChildren);
      case EOF -> throw parserError("unexpectedEndOfFile");
      default -> {
        if (lookahead.isKeyword()) {
          throw parserError("keywordNotAllowedHere", lookahead.text());
        }
        if (lookahead == Token.DOC_COMMENT) {
          throw parserError("danglingDocComment");
        }
        throw parserError("invalidTopLevelToken");
      }
    };
  }

  private GenNode parseTypeAlias(List<GenNode> preChildren) {
    var headerParts = getHeaderParts(preChildren);
    var children = new ArrayList<>(headerParts.preffixes);
    var headers = new ArrayList<GenNode>();
    if (headerParts.modifierList != null) {
      headers.add(headerParts.modifierList);
    }
    // typealias keyword
    headers.add(makeTerminal(next()));
    ff(headers);
    headers.add(parseIdentifier());
    ff(headers);
    if (lookahead == Token.LT) {
      headers.add(parseTypeParameterList());
      ff(headers);
    }
    expect(Token.ASSIGN, headers, "unexpectedToken", "=");
    children.add(new GenNode(NodeType.TYPEALIAS_HEADER, headers));
    var body = new ArrayList<GenNode>();
    ff(body);
    body.add(parseType());
    children.add(new GenNode(NodeType.TYPEALIAS_BODY, body));
    return new GenNode(NodeType.TYPEALIAS, children);
  }

  private GenNode parseClass(List<GenNode> preChildren) {
    var headerParts = getHeaderParts(preChildren);
    var children = new ArrayList<>(headerParts.preffixes);
    var headers = new ArrayList<GenNode>();
    if (headerParts.modifierList != null) {
      headers.add(headerParts.modifierList);
    }
    // class keyword
    headers.add(makeTerminal(next()));
    ff(headers);
    headers.add(parseIdentifier());
    if (lookahead() == Token.LT) {
      ff(headers);
      headers.add(parseTypeParameterList());
    }
    if (lookahead() == Token.EXTENDS) {
      ff(headers);
      headers.add(makeTerminal(next()));
      ff(headers);
      headers.add(parseType());
    }
    children.add(new GenNode(NodeType.CLASS_HEADER, headers));
    if (lookahead() == Token.LBRACE) {
      ff(children);
      children.add(parseClassBody());
    }
    return new GenNode(NodeType.CLASS, children);
  }

  private GenNode parseClassBody() {
    var children = new ArrayList<GenNode>();
    children.add(makeTerminal(next()));
    var elements = new ArrayList<GenNode>();
    var hasElements = false;
    ff(elements);
    while (lookahead != Token.RBRACE && lookahead != Token.EOF) {
      hasElements = true;
      var preChildren = new ArrayList<GenNode>();
      parseMemberHeader(preChildren);
      if (lookahead == Token.FUNCTION) {
        elements.add(parseClassMethod(preChildren));
      } else {
        elements.add(parseClassProperty(preChildren));
      }
      ff(elements);
    }
    if (lookahead == Token.EOF) {
      throw parserError(ErrorMessages.create("missingDelimiter", "}"), prev().span.stopSpan());
    }
    if (hasElements) {
      children.add(new GenNode(NodeType.CLASS_BODY_ELEMENTS, elements));
    } else if (!elements.isEmpty()) {
      // add affixes
      children.addAll(elements);
    }
    expect(Token.RBRACE, children, "missingDelimiter", "}");
    return new GenNode(NodeType.CLASS_BODY, children);
  }

  private GenNode parseClassProperty(List<GenNode> preChildren) {
    var headerParts = getHeaderParts(preChildren);
    var children = new ArrayList<>(headerParts.preffixes);
    var header = new ArrayList<GenNode>();
    if (headerParts.modifierList != null) {
      header.add(headerParts.modifierList);
    }
    header.add(parseIdentifier());
    var hasTypeAnnotation = false;
    if (lookahead() == Token.COLON) {
      ff(header);
      header.add(parseTypeAnnotation());
      hasTypeAnnotation = true;
    }
    if (lookahead() == Token.ASSIGN) {
      children.add(new GenNode(NodeType.CLASS_PROPERTY_HEADER, header));
      ff(children);
      children.add(makeTerminal(next()));
      ff(children);
      children.add(parseExpr());
    } else if (lookahead() == Token.LBRACE) {
      if (hasTypeAnnotation) {
        throw parserError("typeAnnotationInAmends");
      }
      children.add(new GenNode(NodeType.CLASS_PROPERTY_HEADER, header));
      while (lookahead() == Token.LBRACE) {
        ff(children);
        children.add(parseObjectBody());
      }
    } else {
      children.add(new GenNode(NodeType.CLASS_PROPERTY_HEADER, header));
    }
    return new GenNode(NodeType.CLASS_PROPERTY, children);
  }

  private GenNode parseClassMethod(List<GenNode> preChildren) {
    var headerParts = getHeaderParts(preChildren);
    var children = new ArrayList<>(headerParts.preffixes);
    var headers = new ArrayList<GenNode>();
    if (headerParts.modifierList != null) {
      headers.add(headerParts.modifierList);
    }
    expect(Token.FUNCTION, headers, "unexpectedToken", "function");
    ff(headers);
    headers.add(parseIdentifier());
    children.add(new GenNode(NodeType.CLASS_METHOD_HEADER, headers));
    ff(children);
    if (lookahead == Token.LT) {
      children.add(parseTypeParameterList());
      ff(children);
    }
    children.add(parseParameterList());
    if (lookahead() == Token.COLON) {
      ff(children);
      children.add(parseTypeAnnotation());
    }
    if (lookahead() == Token.ASSIGN) {
      ff(children);
      children.add(makeTerminal(next()));
      var body = new ArrayList<GenNode>();
      ff(body);
      body.add(parseExpr());
      children.add(new GenNode(NodeType.CLASS_METHOD_BODY, body));
    }
    return new GenNode(NodeType.CLASS_METHOD, children);
  }

  private GenNode parseObjectBody() {
    var children = new ArrayList<GenNode>();
    expect(Token.LBRACE, children, "unexpectedToken", "{");
    if (lookahead() == Token.RBRACE) {
      ff(children);
      children.add(makeTerminal(next()));
      return new GenNode(NodeType.OBJECT_BODY, children);
    }
    if (isParameter()) {
      var params = new ArrayList<GenNode>();
      ff(params);
      parseListOf(Token.COMMA, params, this::parseParameter);
      expect(Token.ARROW, params, "unexpectedToken2", ",", "->");
      children.add(new GenNode(NodeType.OBJECT_PARAMETER_LIST, params));
      ff(children);
    }
    var members = new ArrayList<GenNode>();
    ff(members);
    while (lookahead != Token.RBRACE) {
      if (lookahead == Token.EOF) {
        throw parserError(ErrorMessages.create("missingDelimiter", "}"), prev().span.stopSpan());
      }
      members.add(parseObjectMember());
      ff(members);
    }
    if (!members.isEmpty()) {
      children.add(new GenNode(NodeType.OBJECT_MEMBER_LIST, members));
    }
    children.add(makeTerminal(next())); // RBRACE
    return new GenNode(NodeType.OBJECT_BODY, children);
  }

  /** Returns true if the lookahead is a parameter, false if it's a member. May have to backtrack */
  private boolean isParameter() {
    if (lookahead == Token.UNDERSCORE) return true;
    if (lookahead != Token.IDENTIFIER) return false;
    // have to backtrack
    var originalCursor = cursor;
    var result = false;
    next(); // identifier
    ff();
    if (lookahead == Token.ARROW || lookahead == Token.COMMA) {
      result = true;
    } else if (lookahead == Token.COLON) {
      next(); // colon
      ff();
      parseType();
      ff();
      result = lookahead == Token.COMMA || lookahead == Token.ARROW;
    }
    backtrackTo(originalCursor);
    return result;
  }

  private GenNode parseObjectMember() {
    return switch (lookahead) {
      case IDENTIFIER -> {
        var originalCursor = cursor;
        next();
        ff(new ArrayList<>());
        if (lookahead == Token.LBRACE || lookahead == Token.COLON || lookahead == Token.ASSIGN) {
          // it's an objectProperty
          backtrackTo(originalCursor);
          yield parseObjectProperty(null);
        } else {
          backtrackTo(originalCursor);
          // it's an expression
          yield parseObjectElement();
        }
      }
      case FUNCTION -> parseObjectMethod(List.of());
      case LPRED -> parseMemberPredicate();
      case LBRACK -> parseObjectEntry();
      case SPREAD, QSPREAD -> parseObjectSpread();
      case WHEN -> parseWhenGenerator();
      case FOR -> parseForGenerator();
      case TYPE_ALIAS, CLASS ->
          throw parserError(ErrorMessages.create("missingDelimiter", "}"), prev().span.stopSpan());
      default -> {
        var preChildren = new ArrayList<GenNode>();
        while (lookahead.isModifier()) {
          preChildren.add(make(NodeType.MODIFIER, next().span));
          ff(preChildren);
        }
        if (!preChildren.isEmpty()) {
          if (lookahead == Token.FUNCTION) {
            yield parseObjectMethod(List.of(new GenNode(NodeType.MODIFIER_LIST, preChildren)));
          } else {
            yield parseObjectProperty(List.of(new GenNode(NodeType.MODIFIER_LIST, preChildren)));
          }
        } else {
          yield parseObjectElement();
        }
      }
    };
  }

  private GenNode parseObjectElement() {
    return new GenNode(NodeType.OBJECT_ELEMENT, List.of(parseExpr()));
  }

  private GenNode parseObjectProperty(@Nullable List<GenNode> preChildren) {
    var children = new ArrayList<GenNode>();
    var header = new ArrayList<GenNode>();
    if (preChildren != null) {
      header.addAll(preChildren);
    }
    ff(header);
    var modifierList = new ArrayList<GenNode>();
    while (lookahead.isModifier()) {
      modifierList.add(make(NodeType.MODIFIER, next().span));
      ff(modifierList);
    }
    if (!modifierList.isEmpty()) {
      header.add(new GenNode(NodeType.MODIFIER_LIST, modifierList));
    }
    header.add(parseIdentifier());
    var hasTypeAnnotation = false;
    if (lookahead() == Token.COLON) {
      ff(header);
      header.add(parseTypeAnnotation());
      hasTypeAnnotation = true;
    }
    if (hasTypeAnnotation || lookahead() == Token.ASSIGN) {
      children.add(new GenNode(NodeType.OBJECT_PROPERTY_HEADER, header));
      ff(children);
      expect(Token.ASSIGN, children, "unexpectedToken", "=");
      ff(children);
      children.add(parseExpr("}"));
      return new GenNode(NodeType.OBJECT_PROPERTY, children);
    } else {
      children.add(new GenNode(NodeType.OBJECT_PROPERTY_HEADER, header));
    }
    ff(children);
    children.addAll(parseBodyList());
    return new GenNode(NodeType.OBJECT_PROPERTY, children);
  }

  private GenNode parseObjectMethod(List<GenNode> preChildren) {
    var headerParts = getHeaderParts(preChildren);
    var children = new ArrayList<>(headerParts.preffixes);
    var headers = new ArrayList<GenNode>();
    if (headerParts.modifierList != null) {
      headers.add(headerParts.modifierList);
    }
    expect(Token.FUNCTION, headers, "unexpectedToken", "function");
    ff(headers);
    headers.add(parseIdentifier());
    children.add(new GenNode(NodeType.CLASS_METHOD_HEADER, headers));
    ff(children);
    if (lookahead == Token.LT) {
      children.add(parseTypeParameterList());
      ff(children);
    }
    children.add(parseParameterList());
    ff(children);
    if (lookahead == Token.COLON) {
      children.add(parseTypeAnnotation());
      ff(children);
    }
    expect(Token.ASSIGN, children, "unexpectedToken", "=");
    var body = new ArrayList<GenNode>();
    ff(body);
    body.add(parseExpr());
    children.add(new GenNode(NodeType.CLASS_METHOD_BODY, body));
    return new GenNode(NodeType.OBJECT_METHOD, children);
  }

  private GenNode parseMemberPredicate() {
    var children = new ArrayList<GenNode>();
    children.add(makeTerminal(next()));
    ff(children);
    children.add(parseExpr());
    ff(children);
    var firstBrack = expect(Token.RBRACK, "unexpectedToken", "]]");
    children.add(makeTerminal(firstBrack));
    var secondbrack = expect(Token.RBRACK, "unexpectedToken", "]]");
    children.add(makeTerminal(secondbrack));
    if (firstBrack.span.charIndex() != secondbrack.span.charIndex() - 1) {
      // There shouldn't be any whitespace between the first and second ']'.
      var span = firstBrack.span.endWith(secondbrack.span);
      var text = lexer.textFor(span.charIndex(), span.length());
      throw parserError(ErrorMessages.create("unexpectedToken", text, "]]"), firstBrack.span);
    }
    ff(children);
    if (lookahead == Token.ASSIGN) {
      children.add(makeTerminal(next()));
      ff(children);
      children.add(parseExpr("}"));
      return new GenNode(NodeType.MEMBER_PREDICATE, children);
    }
    children.addAll(parseBodyList());
    return new GenNode(NodeType.MEMBER_PREDICATE, children);
  }

  private GenNode parseObjectEntry() {
    var children = new ArrayList<GenNode>();
    var header = new ArrayList<GenNode>();
    expect(Token.LBRACK, header, "unexpectedToken", "[");
    ff(header);
    header.add(parseExpr());
    expect(Token.RBRACK, header, "unexpectedToken", "]");
    if (lookahead() == Token.ASSIGN) {
      ff(header);
      header.add(makeTerminal(next()));
      children.add(new GenNode(NodeType.OBJECT_ENTRY_HEADER, header));
      ff(children);
      children.add(parseExpr());
      return new GenNode(NodeType.OBJECT_ENTRY, children);
    }
    children.add(new GenNode(NodeType.OBJECT_ENTRY_HEADER, header));
    ff(children);
    children.addAll(parseBodyList());
    return new GenNode(NodeType.OBJECT_ENTRY, children);
  }

  private GenNode parseObjectSpread() {
    var children = new ArrayList<GenNode>();
    children.add(makeTerminal(next()));
    ff(children);
    children.add(parseExpr());
    return new GenNode(NodeType.OBJECT_SPREAD, children);
  }

  private GenNode parseWhenGenerator() {
    var children = new ArrayList<GenNode>();
    var header = new ArrayList<GenNode>();
    children.add(makeTerminal(next()));
    ff(children);
    expect(Token.LPAREN, header, "unexpectedToken", "(");
    ff(header);
    header.add(parseExpr());
    ff(header);
    expect(Token.RPAREN, header, "unexpectedToken", ")");
    children.add(new GenNode(NodeType.WHEN_GENERATOR_HEADER, header));
    ff(children);
    children.add(parseObjectBody());
    if (lookahead() == Token.ELSE) {
      ff(children);
      children.add(makeTerminal(next()));
      ff(children);
      children.add(parseObjectBody());
    }
    return new GenNode(NodeType.WHEN_GENERATOR, children);
  }

  private GenNode parseForGenerator() {
    var children = new ArrayList<GenNode>();
    children.add(makeTerminal(next()));
    ff(children);
    var header = new ArrayList<GenNode>();
    expect(Token.LPAREN, header, "unexpectedToken", "(");
    var headerDefinition = new ArrayList<GenNode>();
    var headerDefinitionHeader = new ArrayList<GenNode>();
    ff(headerDefinitionHeader);
    headerDefinitionHeader.add(parseParameter());
    ff(headerDefinitionHeader);
    if (lookahead == Token.COMMA) {
      headerDefinitionHeader.add(makeTerminal(next()));
      ff(headerDefinitionHeader);
      headerDefinitionHeader.add(parseParameter());
      ff(headerDefinitionHeader);
    }
    expect(Token.IN, headerDefinitionHeader, "unexpectedToken", "in");
    headerDefinition.add(
        new GenNode(NodeType.FOR_GENERATOR_HEADER_DEFINITION_HEADER, headerDefinitionHeader));
    ff(headerDefinition);
    headerDefinition.add(parseExpr());
    ff(headerDefinition);
    header.add(new GenNode(NodeType.FOR_GENERATOR_HEADER_DEFINITION, headerDefinition));
    expect(Token.RPAREN, header, "unexpectedToken", ")");
    children.add(new GenNode(NodeType.FOR_GENERATOR_HEADER, header));
    ff(children);
    children.add(parseObjectBody());
    return new GenNode(NodeType.FOR_GENERATOR, children);
  }

  private GenNode parseExpr() {
    return parseExpr(null, 1);
  }

  private GenNode parseExpr(@Nullable String expectation) {
    return parseExpr(expectation, 1);
  }

  private GenNode parseExpr(@Nullable String expectation, int minPrecedence) {
    var expr = parseExprAtom(expectation);
    var fullOpToken = fullLookahead();
    var operator = getOperator(fullOpToken.tk);
    while (operator != null) {
      if (operator.getPrec() < minPrecedence) break;
      // `-` and `[]` must be in the same line as the left operand and have no semicolons inbetween
      if ((operator == Operator.MINUS || operator == Operator.SUBSCRIPT)
          && (fullOpToken.hasSemicolon || !expr.span.sameLine(fullOpToken.tk.span))) break;
      var children = new ArrayList<GenNode>();
      children.add(expr);
      ff(children);
      var op = next();
      children.add(make(NodeType.OPERATOR, op.span, op.text(lexer)));
      ff(children);
      var nodeType = NodeType.BINARY_OP_EXPR;
      var nextMinPrec = operator.isLeftAssoc() ? operator.getPrec() + 1 : operator.getPrec();
      switch (op.token) {
        case IS, AS -> children.add(parseType());
        case LBRACK -> {
          nodeType = NodeType.SUBSCRIPT_EXPR;
          children.add(parseExpr("]"));
          ff(children);
          expect(Token.RBRACK, children, "unexpectedToken", "]");
        }
        case NON_NULL -> nodeType = NodeType.NON_NULL_EXPR;
        default -> children.add(parseExpr(expectation, nextMinPrec));
      }

      expr = new GenNode(nodeType, children);
      fullOpToken = fullLookahead();
      operator = getOperator(fullOpToken.tk);
    }
    return expr;
  }

  private @Nullable Operator getOperator(FullToken tk) {
    return switch (tk.token) {
      case POW -> Operator.POW;
      case STAR -> Operator.MULT;
      case DIV -> Operator.DIV;
      case INT_DIV -> Operator.INT_DIV;
      case MOD -> Operator.MOD;
      case PLUS -> Operator.PLUS;
      case MINUS -> Operator.MINUS;
      case GT -> Operator.GT;
      case GTE -> Operator.GTE;
      case LT -> Operator.LT;
      case LTE -> Operator.LTE;
      case IS -> Operator.IS;
      case AS -> Operator.AS;
      case EQUAL -> Operator.EQ_EQ;
      case NOT_EQUAL -> Operator.NOT_EQ;
      case AND -> Operator.AND;
      case OR -> Operator.OR;
      case PIPE -> Operator.PIPE;
      case COALESCE -> Operator.NULL_COALESCE;
      case DOT -> Operator.DOT;
      case QDOT -> Operator.QDOT;
      case LBRACK -> Operator.SUBSCRIPT;
      case NON_NULL -> Operator.NON_NULL;
      default -> null;
    };
  }

  private GenNode parseExprAtom(@Nullable String expectation) {
    var expr =
        switch (lookahead) {
          case THIS -> new GenNode(NodeType.THIS_EXPR, next().span);
          case OUTER -> new GenNode(NodeType.OUTER_EXPR, next().span);
          case MODULE -> new GenNode(NodeType.MODULE_EXPR, next().span);
          case NULL -> new GenNode(NodeType.NULL_EXPR, next().span);
          case THROW -> {
            var children = new ArrayList<GenNode>();
            children.add(makeTerminal(next()));
            ff(children);
            expect(Token.LPAREN, children, "unexpectedToken", "(");
            ff(children);
            children.add(parseExpr(")"));
            ff(children);
            expect(Token.RPAREN, children, "unexpectedToken", ")");
            yield new GenNode(NodeType.THROW_EXPR, children);
          }
          case TRACE -> {
            var children = new ArrayList<GenNode>();
            children.add(makeTerminal(next()));
            ff(children);
            expect(Token.LPAREN, children, "unexpectedToken", "(");
            ff(children);
            children.add(parseExpr(")"));
            ff(children);
            expect(Token.RPAREN, children, "unexpectedToken", ")");
            yield new GenNode(NodeType.TRACE_EXPR, children);
          }
          case IMPORT, IMPORT_STAR -> {
            var children = new ArrayList<GenNode>();
            children.add(makeTerminal(next()));
            ff(children);
            expect(Token.LPAREN, children, "unexpectedToken", "(");
            ff(children);
            children.add(parseStringConstant());
            ff(children);
            expect(Token.RPAREN, children, "unexpectedToken", ")");
            yield new GenNode(NodeType.IMPORT_EXPR, children);
          }
          case READ, READ_STAR, READ_QUESTION -> {
            var children = new ArrayList<GenNode>();
            children.add(makeTerminal(next()));
            ff(children);
            expect(Token.LPAREN, children, "unexpectedToken", "(");
            ff(children);
            children.add(parseExpr(")"));
            ff(children);
            expect(Token.RPAREN, children, "unexpectedToken", ")");
            yield new GenNode(NodeType.READ_EXPR, children);
          }
          case NEW -> {
            var children = new ArrayList<GenNode>();
            var header = new ArrayList<GenNode>();
            header.add(makeTerminal(next()));
            ff(header);
            if (lookahead != Token.LBRACE) {
              header.add(parseType("{"));
              children.add(new GenNode(NodeType.NEW_HEADER, header));
              ff(children);
            } else {
              children.add(new GenNode(NodeType.NEW_HEADER, header));
            }
            children.add(parseObjectBody());
            yield new GenNode(NodeType.NEW_EXPR, children);
          }
          case MINUS -> {
            var children = new ArrayList<GenNode>();
            children.add(makeTerminal(next()));
            ff(children);
            // unary minus has higher precendence than most binary operators
            children.add(parseExpr(expectation, 12));
            yield new GenNode(NodeType.UNARY_MINUS_EXPR, children);
          }
          case NOT -> {
            var children = new ArrayList<GenNode>();
            children.add(makeTerminal(next()));
            ff(children);
            // unary minus has higher precendence than most binary operators
            children.add(parseExpr(expectation, 11));
            yield new GenNode(NodeType.LOGICAL_NOT_EXPR, children);
          }
          case LPAREN -> {
            // can be function literal or parenthesized expression
            var children = new ArrayList<GenNode>();
            children.add(makeTerminal(next()));
            ff(children);
            yield switch (lookahead) {
              case UNDERSCORE -> parseFunctionLiteral(children);
              case IDENTIFIER -> {
                if (isFunctionLiteral()) {
                  yield parseFunctionLiteral(children);
                } else {
                  children.add(parseExpr(")"));
                  ff(children);
                  expect(Token.RPAREN, children, "unexpectedToken", ")");
                  yield new GenNode(NodeType.PARENTHESIZED_EXPR, children);
                }
              }
              case RPAREN -> {
                children.add(makeTerminal(next()));
                var actualChildren = new ArrayList<GenNode>();
                actualChildren.add(new GenNode(NodeType.PARAMETER_LIST, children));
                ff(actualChildren);
                expect(Token.ARROW, actualChildren, "unexpectedToken", "->");
                var body = new ArrayList<GenNode>();
                ff(body);
                body.add(parseExpr());
                actualChildren.add(new GenNode(NodeType.FUNCTION_LITERAL_BODY, body));
                yield new GenNode(NodeType.FUNCTION_LITERAL_EXPR, actualChildren);
              }
              default -> {
                // expression
                children.add(parseExpr(")"));
                ff(children);
                expect(Token.RPAREN, children, "unexpectedToken", ")");
                yield new GenNode(NodeType.PARENTHESIZED_EXPR, children);
              }
            };
          }
          case SUPER -> {
            var children = new ArrayList<GenNode>();
            children.add(makeTerminal(next()));
            ff(children);
            if (lookahead == Token.DOT) {
              children.add(makeTerminal(next()));
              ff(children);
              children.add(parseIdentifier());
              if (lookahead() == Token.LPAREN) {
                ff(children);
                children.add(parseArgumentList());
              }
              yield new GenNode(NodeType.SUPER_ACCESS_EXPR, children);
            } else {
              expect(Token.LBRACK, children, "unexpectedToken", "[");
              ff(children);
              children.add(parseExpr());
              ff(children);
              expect(Token.RBRACK, children, "unexpectedToken", "]");
              yield new GenNode(NodeType.SUPER_SUBSCRIPT_EXPR, children);
            }
          }
          case IF -> {
            var children = new ArrayList<GenNode>();
            var header = new ArrayList<GenNode>();
            header.add(makeTerminal(next()));
            ff(header);
            var condition = new ArrayList<GenNode>();
            expect(Token.LPAREN, condition, "unexpectedToken", "(");
            ff(condition);
            condition.add(parseExpr(")"));
            ff(condition);
            expect(Token.RPAREN, condition, "unexpectedToken", ")");
            header.add(new GenNode(NodeType.IF_CONDITION, condition));
            children.add(new GenNode(NodeType.IF_HEADER, header));
            var thenExpr = new ArrayList<GenNode>();
            ff(thenExpr);
            thenExpr.add(parseExpr("else"));
            ff(thenExpr);
            children.add(new GenNode(NodeType.IF_THEN_EXPR, thenExpr));
            expect(Token.ELSE, children, "unexpectedToken", "else");
            var elseExpr = new ArrayList<GenNode>();
            ff(elseExpr);
            elseExpr.add(parseExpr(expectation));
            children.add(new GenNode(NodeType.IF_ELSE_EXPR, elseExpr));
            yield new GenNode(NodeType.IF_EXPR, children);
          }
          case LET -> {
            var children = new ArrayList<GenNode>();
            children.add(makeTerminal(next()));
            ff(children);
            var paramDef = new ArrayList<GenNode>();
            expect(Token.LPAREN, paramDef, "unexpectedToken", "(");
            ff(paramDef);
            var param = new ArrayList<GenNode>();
            param.add(parseParameter());
            ff(param);
            expect(Token.ASSIGN, param, "unexpectedToken", "=");
            ff(param);
            param.add(parseExpr(")"));
            paramDef.add(new GenNode(NodeType.LET_PARAMETER, param));
            ff(paramDef);
            expect(Token.RPAREN, paramDef, "unexpectedToken", ")");
            children.add(new GenNode(NodeType.LET_PARAMETER_DEFINITION, paramDef));
            ff(children);
            children.add(parseExpr(expectation));
            yield new GenNode(NodeType.LET_EXPR, children);
          }
          case TRUE, FALSE -> new GenNode(NodeType.BOOL_LITERAL_EXPR, next().span);
          case INT, HEX, BIN, OCT -> new GenNode(NodeType.INT_LITERAL_EXPR, next().span);
          case FLOAT -> new GenNode(NodeType.FLOAT_LITERAL_EXPR, next().span);
          case STRING_START -> parseSingleLineStringLiteralExpr();
          case STRING_MULTI_START -> parseMultiLineStringLiteralExpr();
          case IDENTIFIER -> {
            var children = new ArrayList<GenNode>();
            children.add(parseIdentifier());
            if (lookahead == Token.LPAREN
                && !isPrecededBySemicolon()
                && _lookahead.newLinesBetween == 0) {
              children.add(parseArgumentList());
            }
            yield new GenNode(NodeType.UNQUALIFIED_ACCESS_EXPR, children);
          }
          case EOF ->
              throw parserError(
                  ErrorMessages.create("unexpectedEndOfFile"), prev().span.stopSpan());
          default -> {
            var text = _lookahead.text(lexer);
            if (expectation != null) {
              throw parserError("unexpectedToken", text, expectation);
            }
            throw parserError("unexpectedTokenForExpression", text);
          }
        };
    return parseExprRest(expr);
  }

  @SuppressWarnings("DuplicatedCode")
  private GenNode parseExprRest(GenNode expr) {
    var looka = lookahead();
    // amends
    if (looka == Token.LBRACE) {
      var children = new ArrayList<GenNode>();
      children.add(expr);
      ff(children);
      if (expr.type == NodeType.PARENTHESIZED_EXPR
          || expr.type == NodeType.AMENDS_EXPR
          || expr.type == NodeType.NEW_EXPR) {
        children.add(parseObjectBody());
        return parseExprRest(new GenNode(NodeType.AMENDS_EXPR, children));
      }
      throw parserError("unexpectedCurlyProbablyAmendsExpression", expr.text(lexer.getSource()));
    }
    return expr;
  }

  private boolean isFunctionLiteral() {
    var originalCursor = cursor;
    try {
      next(); // identifier
      ff();
      if (lookahead == Token.COMMA || lookahead == Token.COLON) {
        return true;
      }
      if (lookahead == Token.RPAREN) {
        next();
        ff();
        return lookahead == Token.ARROW;
      }
      return false;
    } finally {
      backtrackTo(originalCursor);
    }
  }

  private GenNode parseSingleLineStringLiteralExpr() {
    var children = new ArrayList<GenNode>();
    var start = next();
    children.add(makeTerminal(start)); // string start
    while (lookahead != Token.STRING_END) {
      switch (lookahead) {
        case STRING_PART -> {
          var tk = next();
          if (!tk.text(lexer).isEmpty()) {
            children.add(make(NodeType.STRING_CONSTANT, tk.span));
          }
        }
        case STRING_ESCAPE_NEWLINE,
            STRING_ESCAPE_TAB,
            STRING_ESCAPE_QUOTE,
            STRING_ESCAPE_BACKSLASH,
            STRING_ESCAPE_RETURN,
            STRING_ESCAPE_UNICODE ->
            children.add(make(NodeType.STRING_ESCAPE, next().span));
        case INTERPOLATION_START -> {
          children.add(makeTerminal(next()));
          ff(children);
          children.add(parseExpr(")"));
          ff(children);
          expect(Token.RPAREN, children, "unexpectedToken", ")");
        }
        case EOF -> {
          var delimiter = new StringBuilder(start.text(lexer)).reverse().toString();
          throw parserError("missingDelimiter", delimiter);
        }
      }
    }
    children.add(makeTerminal(next())); // string end
    return new GenNode(NodeType.SINGLE_LINE_STRING_LITERAL_EXPR, children);
  }

  private GenNode parseMultiLineStringLiteralExpr() {
    var children = new ArrayList<GenNode>();
    var start = next();
    children.add(makeTerminal(start)); // string start
    while (lookahead != Token.STRING_END) {
      switch (lookahead) {
        case STRING_PART -> {
          var tk = next();
          if (!tk.text(lexer).isEmpty()) {
            children.add(make(NodeType.STRING_CONSTANT, tk.span));
          }
        }
        case STRING_NEWLINE -> children.add(make(NodeType.STRING_NEWLINE, next().span));
        case STRING_ESCAPE_NEWLINE,
            STRING_ESCAPE_TAB,
            STRING_ESCAPE_QUOTE,
            STRING_ESCAPE_BACKSLASH,
            STRING_ESCAPE_RETURN,
            STRING_ESCAPE_UNICODE ->
            children.add(make(NodeType.STRING_ESCAPE, next().span));
        case INTERPOLATION_START -> {
          children.add(makeTerminal(next()));
          ff(children);
          children.add(parseExpr(")"));
          ff(children);
          expect(Token.RPAREN, children, "unexpectedToken", ")");
        }
        case EOF -> {
          var delimiter = new StringBuilder(start.text(lexer)).reverse().toString();
          throw parserError("missingDelimiter", delimiter);
        }
      }
    }
    children.add(makeTerminal(next())); // string end
    return new GenNode(NodeType.MULTI_LINE_STRING_LITERAL_EXPR, children);
  }

  private GenNode parseFunctionLiteral(List<GenNode> preChildren) {
    // the open parens is already parsed
    var paramListChildren = new ArrayList<>(preChildren);
    var elements = new ArrayList<GenNode>();
    parseListOf(Token.COMMA, elements, this::parseParameter);
    paramListChildren.add(new GenNode(NodeType.PARAMETER_LIST_ELEMENTS, elements));
    expect(Token.RPAREN, paramListChildren, "unexpectedToken2", ",", ")");
    var children = new ArrayList<GenNode>();
    children.add(new GenNode(NodeType.PARAMETER_LIST, paramListChildren));
    ff(children);
    expect(Token.ARROW, children, "unexpectedToken", "->");
    var body = new ArrayList<GenNode>();
    ff(body);
    body.add(parseExpr());
    children.add(new GenNode(NodeType.FUNCTION_LITERAL_BODY, body));
    return new GenNode(NodeType.FUNCTION_LITERAL_EXPR, children);
  }

  private GenNode parseType() {
    return parseType(null);
  }

  private GenNode parseType(@Nullable String expectation) {
    var children = new ArrayList<GenNode>();
    var hasDefault = false;
    FullSpan start = null;
    if (lookahead == Token.STAR) {
      var tk = next();
      start = tk.span;
      children.add(makeTerminal(tk));
      ff(children);
      hasDefault = true;
    }
    var first = parseTypeAtom(expectation);
    children.add(first);

    if (lookahead != Token.UNION) {
      if (hasDefault) {
        throw parserError(ErrorMessages.create("notAUnion"), start.endWith(first.span));
      }
      return first;
    }

    ff(children);
    while (lookahead == Token.UNION) {
      children.add(makeTerminal(next()));
      ff(children);
      if (lookahead == Token.STAR) {
        if (hasDefault) {
          throw parserError("multipleUnionDefaults");
        }
        children.add(makeTerminal(next()));
        ff(children);
        hasDefault = true;
      }
      var type = parseTypeAtom(expectation);
      children.add(type);
    }
    return new GenNode(NodeType.UNION_TYPE, children);
  }

  private GenNode parseTypeAtom(@Nullable String expectation) {
    var typ =
        switch (lookahead) {
          case UNKNOWN -> make(NodeType.UNKNOWN_TYPE, next().span);
          case NOTHING -> make(NodeType.NOTHING_TYPE, next().span);
          case MODULE -> make(NodeType.MODULE_TYPE, next().span);
          case LPAREN -> {
            var children = new ArrayList<GenNode>();
            children.add(makeTerminal(next()));
            ff(children);
            var totalTypes = 0;
            if (lookahead == Token.RPAREN) {
              children.add(makeTerminal(next()));
            } else {
              children.add(parseType(")"));
              ff(children);
              while (lookahead == Token.COMMA) {
                children.add(makeTerminal(next()));
                ff(children);
                children.add(parseType(")"));
                totalTypes++;
                ff(children);
              }
              expect(Token.RPAREN, children, "unexpectedToken2", ",", ")");
            }
            if (totalTypes > 1 || lookahead() == Token.ARROW) {
              ff(children);
              expect(Token.ARROW, children, "unexpectedToken", "->");
              ff(children);
              children.add(parseType(expectation));
              yield new GenNode(NodeType.FUNCTION_TYPE, children);
            } else {
              yield new GenNode(NodeType.PARENTHESIZED_TYPE, children);
            }
          }
          case IDENTIFIER -> {
            var children = new ArrayList<GenNode>();
            children.add(parseQualifiedIdentifier());
            if (lookahead() == Token.LT) {
              ff(children);
              children.add(parseTypeArgumentList());
            }
            yield new GenNode(NodeType.DECLARED_TYPE, children);
          }
          case STRING_START ->
              new GenNode(NodeType.STRING_CONSTANT_TYPE, List.of(parseStringConstant()));
          default -> {
            var text = _lookahead.text(lexer);
            if (expectation != null) {
              throw parserError("unexpectedTokenForType2", text, expectation);
            }
            throw parserError("unexpectedTokenForType", text);
          }
        };

    if (typ.type == NodeType.FUNCTION_TYPE) return typ;
    return parseTypeEnd(typ);
  }

  private GenNode parseTypeEnd(GenNode type) {
    var children = new ArrayList<GenNode>();
    children.add(type);
    // nullable types
    if (lookahead() == Token.QUESTION) {
      ff(children);
      children.add(makeTerminal(next()));
      var res = new GenNode(NodeType.NULLABLE_TYPE, children);
      return parseTypeEnd(res);
    }
    // constrained types: have to start in the same line as the type
    var fla = fullLookahead();
    if (fla.tk.token == Token.LPAREN && !isPrecededBySemicolon() && fla.tk.newLinesBetween == 0) {
      ff(children);
      var constraint = new ArrayList<GenNode>();
      constraint.add(makeTerminal(next()));
      ff(constraint);
      var elements = new ArrayList<GenNode>();
      parseListOf(Token.COMMA, elements, () -> parseExpr(")"));
      constraint.add(new GenNode(NodeType.CONSTRAINED_TYPE_ELEMENTS, elements));
      expect(Token.RPAREN, constraint, "unexpectedToken2", ",", ")");
      children.add(new GenNode(NodeType.CONSTRAINED_TYPE_CONSTRAINT, constraint));
      var res = new GenNode(NodeType.CONSTRAINED_TYPE, children);
      return parseTypeEnd(res);
    }
    return type;
  }

  private GenNode parseAnnotation() {
    var children = new ArrayList<GenNode>();
    children.add(makeTerminal(next()));
    children.add(parseType());
    if (lookahead() == Token.LBRACE) {
      ff(children);
      children.add(parseObjectBody());
    }
    return new GenNode(NodeType.ANNOTATION, children);
  }

  private GenNode parseParameter() {
    if (lookahead == Token.UNDERSCORE) {
      return new GenNode(NodeType.PARAMETER, List.of(makeTerminal(next())));
    }
    return parseTypedIdentifier();
  }

  private GenNode parseTypedIdentifier() {
    var children = new ArrayList<GenNode>();
    children.add(parseIdentifier());
    if (lookahead() == Token.COLON) {
      ff(children);
      children.add(parseTypeAnnotation());
    }
    return new GenNode(NodeType.PARAMETER, children);
  }

  private GenNode parseParameterList() {
    var children = new ArrayList<GenNode>();
    expect(Token.LPAREN, children, "unexpectedToken", "(");
    ff(children);
    if (lookahead == Token.RPAREN) {
      children.add(makeTerminal(next()));
    } else {
      var elements = new ArrayList<GenNode>();
      parseListOf(Token.COMMA, elements, this::parseParameter);
      children.add(new GenNode(NodeType.PARAMETER_LIST_ELEMENTS, elements));
      expect(Token.RPAREN, children, "unexpectedToken2", ",", ")");
    }
    return new GenNode(NodeType.PARAMETER_LIST, children);
  }

  private List<GenNode> parseBodyList() {
    if (lookahead != Token.LBRACE) {
      throw parserError("unexpectedToken2", _lookahead.text(lexer), "{", "=");
    }
    var bodies = new ArrayList<GenNode>();
    do {
      bodies.add(parseObjectBody());
    } while (lookahead() == Token.LBRACE);
    return bodies;
  }

  private GenNode parseTypeParameterList() {
    var children = new ArrayList<GenNode>();
    expect(Token.LT, children, "unexpectedToken", "<");
    ff(children);
    var elements = new ArrayList<GenNode>();
    parseListOf(Token.COMMA, elements, this::parseTypeParameter);
    children.add(new GenNode(NodeType.TYPE_PARAMETER_LIST_ELEMENTS, elements));
    expect(Token.GT, children, "unexpectedToken2", ",", ">");
    return new GenNode(NodeType.TYPE_PARAMETER_LIST, children);
  }

  private GenNode parseTypeArgumentList() {
    var children = new ArrayList<GenNode>();
    expect(Token.LT, children, "unexpectedToken", "<");
    ff(children);
    var elements = new ArrayList<GenNode>();
    parseListOf(Token.COMMA, elements, this::parseType);
    children.add(new GenNode(NodeType.TYPE_ARGUMENT_LIST_ELEMENTS, elements));
    expect(Token.GT, children, "unexpectedToken2", ",", ">");
    return new GenNode(NodeType.TYPE_ARGUMENT_LIST, children);
  }

  private GenNode parseArgumentList() {
    var children = new ArrayList<GenNode>();
    expect(Token.LPAREN, children, "unexpectedToken", "(");
    if (lookahead() == Token.RPAREN) {
      ff(children);
      children.add(makeTerminal(next()));
      return new GenNode(NodeType.ARGUMENT_LIST, children);
    }
    var elements = new ArrayList<GenNode>();
    ff(elements);
    parseListOf(Token.COMMA, elements, this::parseExpr);
    ff(elements);
    children.add(new GenNode(NodeType.ARGUMENT_LIST_ELEMENTS, elements));
    expect(Token.RPAREN, children, "unexpectedToken2", ",", ")");
    return new GenNode(NodeType.ARGUMENT_LIST, children);
  }

  private GenNode parseTypeParameter() {
    var children = new ArrayList<GenNode>();
    if (lookahead == Token.IN) {
      children.add(makeTerminal(next()));
    } else if (lookahead == Token.OUT) {
      children.add(makeTerminal(next()));
    }
    children.add(parseIdentifier());
    return new GenNode(NodeType.TYPE_PARAMETER, children);
  }

  private GenNode parseTypeAnnotation() {
    var children = new ArrayList<GenNode>();
    expect(Token.COLON, children, "unexpectedToken", ":");
    ff(children);
    children.add(parseType());
    return new GenNode(NodeType.TYPE_ANNOTATION, children);
  }

  private GenNode parseIdentifier() {
    if (lookahead != Token.IDENTIFIER) {
      if (lookahead.isKeyword()) {
        throw parserError("keywordNotAllowedHere", lookahead.text());
      }
      throw parserError("unexpectedToken", _lookahead.text(lexer), "identifier");
    }
    return new GenNode(NodeType.IDENTIFIER, next().span);
  }

  private GenNode parseStringConstant() {
    var children = new ArrayList<GenNode>();
    var startTk = expect(Token.STRING_START, "unexpectedToken", "\"");
    children.add(makeTerminal(startTk));
    while (lookahead != Token.STRING_END) {
      switch (lookahead) {
        case STRING_PART,
            STRING_ESCAPE_NEWLINE,
            STRING_ESCAPE_TAB,
            STRING_ESCAPE_QUOTE,
            STRING_ESCAPE_BACKSLASH,
            STRING_ESCAPE_RETURN,
            STRING_ESCAPE_UNICODE ->
            children.add(makeTerminal(next()));
        case EOF -> {
          var delimiter = new StringBuilder(startTk.text(lexer)).reverse().toString();
          throw parserError("missingDelimiter", delimiter);
        }
        case INTERPOLATION_START -> throw parserError("interpolationInConstant");
        // the lexer makes sure we only get the above tokens inside a string
        default -> throw new RuntimeException("Unreacheable code");
      }
    }
    children.add(makeTerminal(next())); // string end
    return new GenNode(NodeType.STRING_CONSTANT, children);
  }

  private FullToken expect(Token type, String errorKey, Object... messageArgs) {
    if (lookahead != type) {
      var span = spanLookahead;
      if (lookahead == Token.EOF || _lookahead.newLinesBetween > 0) {
        // don't point at the EOF or the next line, but at the end of the last token
        span = prev().span.stopSpan();
      }
      var args = messageArgs;
      if (errorKey.startsWith("unexpectedToken")) {
        args = new Object[messageArgs.length + 1];
        args[0] = lookahead == Token.EOF ? "EOF" : _lookahead.text(lexer);
        System.arraycopy(messageArgs, 0, args, 1, messageArgs.length);
      }
      throw parserError(ErrorMessages.create(errorKey, args), span);
    }
    return next();
  }

  private void expect(Token type, List<GenNode> children, String errorKey, Object... messageArgs) {
    var tk = expect(type, errorKey, messageArgs);
    children.add(makeTerminal(tk));
  }

  // this function may be dangerous as it fast-forwards the cursor after the last element
  private void parseListOf(Token separator, List<GenNode> children, Supplier<GenNode> parser) {
    children.add(parser.get());
    ff(children);
    while (lookahead == separator) {
      children.add(makeTerminal(next()));
      ff(children);
      children.add(parser.get());
      ff(children);
    }
  }

  private GenericParserError parserError(String messageKey, Object... args) {
    return new GenericParserError(ErrorMessages.create(messageKey, args), spanLookahead);
  }

  private GenericParserError parserError(String message, FullSpan span) {
    return new GenericParserError(message, span);
  }

  private boolean isModuleDecl() {
    var _cursor = cursor;
    var ftk = tokens.get(_cursor);
    while (ftk.token.isAffix() || ftk.token.isModifier()) {
      ftk = tokens.get(++_cursor);
    }
    var tk = ftk.token;
    return tk == Token.MODULE || tk == Token.EXTENDS || tk == Token.AMENDS;
  }

  private boolean isImport() {
    var _cursor = cursor;
    var ftk = tokens.get(_cursor);
    while (ftk.token.isAffix()) {
      ftk = tokens.get(++_cursor);
    }
    var tk = ftk.token;
    return tk == Token.IMPORT || tk == Token.IMPORT_STAR;
  }

  private FullToken next() {
    var tmp = tokens.get(cursor++);
    _lookahead = tokens.get(cursor);
    lookahead = _lookahead.token;
    spanLookahead = _lookahead.span;
    return tmp;
  }

  private boolean isPrecededBySemicolon() {
    return tokens.get(cursor - 1).token == Token.SEMICOLON;
  }

  private void backtrack() {
    var tmp = tokens.get(--cursor);
    lookahead = tmp.token;
    spanLookahead = tmp.span;
  }

  private void backtrackTo(int point) {
    cursor = point;
    var tmp = tokens.get(cursor);
    lookahead = tmp.token;
    spanLookahead = tmp.span;
  }

  private FullToken prev() {
    return tokens.get(cursor - 1);
  }

  // Jump over affixes and find the next token
  private Token lookahead() {
    var i = cursor;
    var tmp = tokens.get(i);
    while (tmp.token.isAffix() && tmp.token != Token.EOF) {
      tmp = tokens.get(++i);
    }
    return tmp.token;
  }

  // Jump over affixes and find the next token
  private LookaheadSearch fullLookahead() {
    var i = cursor;
    var hasSemicolon = false;
    var tmp = tokens.get(i);
    while (tmp.token.isAffix() && tmp.token != Token.EOF) {
      if (tmp.token == Token.SEMICOLON) {
        hasSemicolon = true;
      }
      tmp = tokens.get(++i);
    }
    return new LookaheadSearch(tmp, hasSemicolon);
  }

  private record LookaheadSearch(FullToken tk, boolean hasSemicolon) {}

  private record HeaderParts(List<GenNode> preffixes, @Nullable GenNode modifierList) {}

  private HeaderParts getHeaderParts(List<GenNode> nodes) {
    if (nodes.isEmpty()) return new HeaderParts(nodes, null);
    var last = nodes.get(nodes.size() - 1);
    if (last.type == NodeType.MODIFIER_LIST) {
      return new HeaderParts(nodes.subList(0, nodes.size() - 1), last);
    }
    return new HeaderParts(nodes, null);
  }

  private GenNode make(NodeType type, FullSpan span) {
    return new GenNode(type, span);
  }

  private GenNode make(NodeType type, FullSpan span, String text) {
    return new GenNode(type, span, text);
  }

  private GenNode makeAffix(FullToken tk) {
    return new GenNode(nodeTypeForAffix(tk.token), tk.span);
  }

  private GenNode makeTerminal(FullToken tk) {
    return new GenNode(NodeType.TERMINAL, tk.span, tk.text(lexer));
  }

  // fast-forward over affix tokens
  // store children
  private void ff(List<GenNode> children) {
    var tmp = tokens.get(cursor);
    while (tmp.token.isAffix()) {
      children.add(makeAffix(tmp));
      tmp = tokens.get(++cursor);
    }
    _lookahead = tmp;
    lookahead = _lookahead.token;
    spanLookahead = _lookahead.span;
  }

  // fast-forward over affix tokens
  private void ff() {
    var tmp = tokens.get(cursor);
    while (tmp.token.isAffix()) {
      tmp = tokens.get(++cursor);
    }
    _lookahead = tmp;
    lookahead = _lookahead.token;
    spanLookahead = _lookahead.span;
  }

  private NodeType nodeTypeForAffix(Token token) {
    return switch (token) {
      case LINE_COMMENT -> NodeType.LINE_COMMENT;
      case BLOCK_COMMENT -> NodeType.BLOCK_COMMENT;
      case SHEBANG -> NodeType.SHEBANG;
      case SEMICOLON -> NodeType.SEMICOLON;
      default -> throw new RuntimeException("Unreacheable code");
    };
  }

  private record FullToken(Token token, FullSpan span, int newLinesBetween) {
    String text(Lexer lexer) {
      return lexer.textFor(span.charIndex(), span.length());
    }
  }

  private record HeaderResult(
      boolean hasDocComment, boolean hasAnnotations, boolean hasModifiers) {}
}
