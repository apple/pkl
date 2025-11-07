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
import org.pkl.parser.syntax.generic.Node;
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

  public Node parseModule(String source) {
    init(source);
    if (lookahead == Token.EOF) {
      return new Node(NodeType.MODULE, new FullSpan(0, 0, 1, 1, 1, 1), List.of());
    }
    var children = new ArrayList<Node>();
    var nodes = new ArrayList<Node>();
    if (lookahead == Token.SHEBANG) {
      nodes.add(makeAffix(next()));
    }
    ff(nodes);

    var res = parseMemberHeader(children);

    if (isModuleDecl()) {
      nodes.add(parseModuleDecl(children));
      children.clear();
      res = new HeaderResult(false, false, false);
      ff(nodes);
    }

    // imports
    var imports = new ArrayList<Node>();
    while (lookahead == Token.IMPORT || lookahead == Token.IMPORT_STAR) {
      if (res.hasDocComment || res.hasAnnotations || res.hasModifiers) {
        throw parserError("wrongHeaders", "Imports");
      }
      var lastImport = parseImportDecl();
      imports.add(lastImport);
      // keep trailing affixes as part of the import
      while (lookahead.isAffix() && lastImport.span.isSameLine(spanLookahead)) {
        imports.add(makeAffix(next()));
      }
      if (!isImport()) break;
      ff(imports);
    }
    if (!imports.isEmpty()) {
      nodes.add(new Node(NodeType.IMPORT_LIST, imports));
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
    return new Node(NodeType.MODULE, nodes);
  }

  private Node parseModuleDecl(List<Node> preChildren) {
    var headerParts = getHeaderParts(preChildren);
    var children = new ArrayList<>(headerParts.preffixes);
    var headers = new ArrayList<Node>();
    if (headerParts.modifierList != null) {
      headers.add(headerParts.modifierList);
    }
    if (lookahead == Token.MODULE) {
      var subChildren = new ArrayList<>(headers);
      subChildren.add(makeTerminal(next()));
      ff(subChildren);
      subChildren.add(parseQualifiedIdentifier());
      children.add(new Node(NodeType.MODULE_DEFINITION, subChildren));
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
      var subChildren = new ArrayList<Node>();
      subChildren.add(makeTerminal(next()));
      ff(subChildren);
      subChildren.add(parseStringConstant());
      children.add(new Node(type, subChildren));
    }
    return new Node(NodeType.MODULE_DECLARATION, children);
  }

  private Node parseQualifiedIdentifier() {
    var children = new ArrayList<Node>();
    children.add(parseIdentifier());
    while (lookahead() == Token.DOT) {
      ff(children);
      children.add(new Node(NodeType.TERMINAL, next().span));
      ff(children);
      children.add(parseIdentifier());
    }
    return new Node(NodeType.QUALIFIED_IDENTIFIER, children);
  }

  private Node parseImportDecl() {
    var children = new ArrayList<Node>();
    children.add(makeTerminal(next()));
    ff(children);
    children.add(parseStringConstant());
    if (lookahead() == Token.AS) {
      ff(children);
      var alias = new ArrayList<Node>();
      alias.add(makeTerminal(next()));
      ff(alias);
      alias.add(parseIdentifier());
      children.add(new Node(NodeType.IMPORT_ALIAS, alias));
    }
    return new Node(NodeType.IMPORT, children);
  }

  private HeaderResult parseMemberHeader(List<Node> children) {
    var hasDocComment = false;
    var hasAnnotation = false;
    var hasModifier = false;
    var docs = new ArrayList<Node>();
    ff(children);
    while (lookahead() == Token.DOC_COMMENT) {
      ff(docs);
      docs.add(new Node(NodeType.DOC_COMMENT_LINE, next().span));
      hasDocComment = true;
    }
    if (hasDocComment) {
      children.add(new Node(NodeType.DOC_COMMENT, docs));
    }
    ff(children);
    while (lookahead == Token.AT) {
      children.add(parseAnnotation());
      hasAnnotation = true;
      ff(children);
    }
    var modifiers = new ArrayList<Node>();
    while (lookahead.isModifier()) {
      modifiers.add(make(NodeType.MODIFIER, next().span));
      hasModifier = true;
      ff(children);
    }
    if (hasModifier) children.add(new Node(NodeType.MODIFIER_LIST, modifiers));
    return new HeaderResult(hasDocComment, hasAnnotation, hasModifier);
  }

  private Node parseModuleMember(List<Node> preChildren) {
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

  private Node parseTypeAlias(List<Node> preChildren) {
    var headerParts = getHeaderParts(preChildren);
    var children = new ArrayList<>(headerParts.preffixes);
    var headers = new ArrayList<Node>();
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
    children.add(new Node(NodeType.TYPEALIAS_HEADER, headers));
    var body = new ArrayList<Node>();
    ff(body);
    body.add(parseType());
    children.add(new Node(NodeType.TYPEALIAS_BODY, body));
    return new Node(NodeType.TYPEALIAS, children);
  }

  private Node parseClass(List<Node> preChildren) {
    var headerParts = getHeaderParts(preChildren);
    var children = new ArrayList<>(headerParts.preffixes);
    var headers = new ArrayList<Node>();
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
      var extend = new ArrayList<Node>();
      ff(extend);
      extend.add(makeTerminal(next()));
      ff(extend);
      extend.add(parseType());
      headers.add(new Node(NodeType.CLASS_HEADER_EXTENDS, extend));
    }
    children.add(new Node(NodeType.CLASS_HEADER, headers));
    if (lookahead() == Token.LBRACE) {
      ff(children);
      children.add(parseClassBody());
    }
    return new Node(NodeType.CLASS, children);
  }

  private Node parseClassBody() {
    var children = new ArrayList<Node>();
    children.add(makeTerminal(next()));
    var elements = new ArrayList<Node>();
    var hasElements = false;
    ff(elements);
    while (lookahead != Token.RBRACE && lookahead != Token.EOF) {
      hasElements = true;
      var preChildren = new ArrayList<Node>();
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
      children.add(new Node(NodeType.CLASS_BODY_ELEMENTS, elements));
    } else if (!elements.isEmpty()) {
      // add affixes
      children.addAll(elements);
    }
    expect(Token.RBRACE, children, "missingDelimiter", "}");
    return new Node(NodeType.CLASS_BODY, children);
  }

  private Node parseClassProperty(List<Node> preChildren) {
    var headerParts = getHeaderParts(preChildren);
    var children = new ArrayList<>(headerParts.preffixes);
    var header = new ArrayList<Node>();
    var headerBegin = new ArrayList<Node>();
    if (headerParts.modifierList != null) {
      headerBegin.add(headerParts.modifierList);
    }
    headerBegin.add(parseIdentifier());
    header.add(new Node(NodeType.CLASS_PROPERTY_HEADER_BEGIN, headerBegin));
    var hasTypeAnnotation = false;
    if (lookahead() == Token.COLON) {
      ff(header);
      header.add(parseTypeAnnotation());
      hasTypeAnnotation = true;
    }
    children.add(new Node(NodeType.CLASS_PROPERTY_HEADER, header));
    if (lookahead() == Token.ASSIGN) {
      ff(children);
      children.add(makeTerminal(next()));
      var body = new ArrayList<Node>();
      ff(body);
      body.add(parseExpr());
      children.add(new Node(NodeType.CLASS_PROPERTY_BODY, body));
    } else if (lookahead() == Token.LBRACE) {
      if (hasTypeAnnotation) {
        throw parserError("typeAnnotationInAmends");
      }
      while (lookahead() == Token.LBRACE) {
        ff(children);
        children.add(parseObjectBody());
      }
    }
    return new Node(NodeType.CLASS_PROPERTY, children);
  }

  private Node parseClassMethod(List<Node> preChildren) {
    var headerParts = getHeaderParts(preChildren);
    var children = new ArrayList<>(headerParts.preffixes);
    var headers = new ArrayList<Node>();
    if (headerParts.modifierList != null) {
      headers.add(headerParts.modifierList);
    }
    expect(Token.FUNCTION, headers, "unexpectedToken", "function");
    ff(headers);
    headers.add(parseIdentifier());
    children.add(new Node(NodeType.CLASS_METHOD_HEADER, headers));
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
      var body = new ArrayList<Node>();
      ff(body);
      body.add(parseExpr());
      children.add(new Node(NodeType.CLASS_METHOD_BODY, body));
    }
    return new Node(NodeType.CLASS_METHOD, children);
  }

  private Node parseObjectBody() {
    var children = new ArrayList<Node>();
    expect(Token.LBRACE, children, "unexpectedToken", "{");
    if (lookahead() == Token.RBRACE) {
      ff(children);
      children.add(makeTerminal(next()));
      return new Node(NodeType.OBJECT_BODY, children);
    }
    if (isParameter()) {
      var params = new ArrayList<Node>();
      ff(params);
      parseListOf(Token.ARROW, params, this::parseParameter);
      expect(Token.ARROW, params, "unexpectedToken2", ",", "->");
      children.add(new Node(NodeType.OBJECT_PARAMETER_LIST, params));
      ff(children);
    }
    var members = new ArrayList<Node>();
    ff(members);
    while (lookahead != Token.RBRACE) {
      if (lookahead == Token.EOF) {
        throw parserError(ErrorMessages.create("missingDelimiter", "}"), prev().span.stopSpan());
      }
      members.add(parseObjectMember());
      ff(members);
    }
    if (!members.isEmpty()) {
      children.add(new Node(NodeType.OBJECT_MEMBER_LIST, members));
    }
    children.add(makeTerminal(next())); // RBRACE
    return new Node(NodeType.OBJECT_BODY, children);
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

  private Node parseObjectMember() {
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
        var preChildren = new ArrayList<Node>();
        while (lookahead.isModifier()) {
          preChildren.add(make(NodeType.MODIFIER, next().span));
          ff(preChildren);
        }
        if (!preChildren.isEmpty()) {
          if (lookahead == Token.FUNCTION) {
            yield parseObjectMethod(List.of(new Node(NodeType.MODIFIER_LIST, preChildren)));
          } else {
            yield parseObjectProperty(List.of(new Node(NodeType.MODIFIER_LIST, preChildren)));
          }
        } else {
          yield parseObjectElement();
        }
      }
    };
  }

  private Node parseObjectElement() {
    return new Node(NodeType.OBJECT_ELEMENT, List.of(parseExpr()));
  }

  private Node parseObjectProperty(@Nullable List<Node> preChildren) {
    var children = new ArrayList<Node>();
    var header = new ArrayList<Node>();
    var headerBegin = new ArrayList<Node>();
    if (preChildren != null) {
      headerBegin.addAll(preChildren);
    }
    ff(headerBegin);
    var modifierList = new ArrayList<Node>();
    while (lookahead.isModifier()) {
      modifierList.add(make(NodeType.MODIFIER, next().span));
      ff(modifierList);
    }
    if (!modifierList.isEmpty()) {
      headerBegin.add(new Node(NodeType.MODIFIER_LIST, modifierList));
    }
    headerBegin.add(parseIdentifier());
    header.add(new Node(NodeType.OBJECT_PROPERTY_HEADER_BEGIN, headerBegin));
    var hasTypeAnnotation = false;
    if (lookahead() == Token.COLON) {
      ff(header);
      header.add(parseTypeAnnotation());
      hasTypeAnnotation = true;
    }
    children.add(new Node(NodeType.OBJECT_PROPERTY_HEADER, header));
    if (hasTypeAnnotation || lookahead() == Token.ASSIGN) {
      ff(children);
      expect(Token.ASSIGN, children, "unexpectedToken", "=");
      var body = new ArrayList<Node>();
      ff(body);
      body.add(parseExpr("}"));
      children.add(new Node(NodeType.OBJECT_PROPERTY_BODY, body));
      return new Node(NodeType.OBJECT_PROPERTY, children);
    }
    ff(children);
    children.addAll(parseBodyList());
    return new Node(NodeType.OBJECT_PROPERTY, children);
  }

  private Node parseObjectMethod(List<Node> preChildren) {
    var headerParts = getHeaderParts(preChildren);
    var children = new ArrayList<>(headerParts.preffixes);
    var headers = new ArrayList<Node>();
    if (headerParts.modifierList != null) {
      headers.add(headerParts.modifierList);
    }
    expect(Token.FUNCTION, headers, "unexpectedToken", "function");
    ff(headers);
    headers.add(parseIdentifier());
    children.add(new Node(NodeType.CLASS_METHOD_HEADER, headers));
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
    var body = new ArrayList<Node>();
    ff(body);
    body.add(parseExpr());
    children.add(new Node(NodeType.CLASS_METHOD_BODY, body));
    return new Node(NodeType.OBJECT_METHOD, children);
  }

  private Node parseMemberPredicate() {
    var children = new ArrayList<Node>();
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
      return new Node(NodeType.MEMBER_PREDICATE, children);
    }
    children.addAll(parseBodyList());
    return new Node(NodeType.MEMBER_PREDICATE, children);
  }

  private Node parseObjectEntry() {
    var children = new ArrayList<Node>();
    var header = new ArrayList<Node>();
    expect(Token.LBRACK, header, "unexpectedToken", "[");
    ff(header);
    header.add(parseExpr());
    expect(Token.RBRACK, header, "unexpectedToken", "]");
    if (lookahead() == Token.ASSIGN) {
      ff(header);
      header.add(makeTerminal(next()));
      children.add(new Node(NodeType.OBJECT_ENTRY_HEADER, header));
      ff(children);
      children.add(parseExpr());
      return new Node(NodeType.OBJECT_ENTRY, children);
    }
    children.add(new Node(NodeType.OBJECT_ENTRY_HEADER, header));
    ff(children);
    children.addAll(parseBodyList());
    return new Node(NodeType.OBJECT_ENTRY, children);
  }

  private Node parseObjectSpread() {
    var children = new ArrayList<Node>();
    children.add(makeTerminal(next()));
    ff(children);
    children.add(parseExpr());
    return new Node(NodeType.OBJECT_SPREAD, children);
  }

  private Node parseWhenGenerator() {
    var children = new ArrayList<Node>();
    var header = new ArrayList<Node>();
    children.add(makeTerminal(next()));
    ff(children);
    expect(Token.LPAREN, header, "unexpectedToken", "(");
    ff(header);
    header.add(parseExpr());
    ff(header);
    expect(Token.RPAREN, header, "unexpectedToken", ")");
    children.add(new Node(NodeType.WHEN_GENERATOR_HEADER, header));
    ff(children);
    children.add(parseObjectBody());
    if (lookahead() == Token.ELSE) {
      ff(children);
      children.add(makeTerminal(next()));
      ff(children);
      children.add(parseObjectBody());
    }
    return new Node(NodeType.WHEN_GENERATOR, children);
  }

  private Node parseForGenerator() {
    var children = new ArrayList<Node>();
    children.add(makeTerminal(next()));
    ff(children);
    var header = new ArrayList<Node>();
    expect(Token.LPAREN, header, "unexpectedToken", "(");
    var headerDefinition = new ArrayList<Node>();
    var headerDefinitionHeader = new ArrayList<Node>();
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
        new Node(NodeType.FOR_GENERATOR_HEADER_DEFINITION_HEADER, headerDefinitionHeader));
    ff(headerDefinition);
    headerDefinition.add(parseExpr());
    ff(headerDefinition);
    header.add(new Node(NodeType.FOR_GENERATOR_HEADER_DEFINITION, headerDefinition));
    expect(Token.RPAREN, header, "unexpectedToken", ")");
    children.add(new Node(NodeType.FOR_GENERATOR_HEADER, header));
    ff(children);
    children.add(parseObjectBody());
    return new Node(NodeType.FOR_GENERATOR, children);
  }

  private Node parseExpr() {
    return parseExpr(null, 1);
  }

  private Node parseExpr(@Nullable String expectation) {
    return parseExpr(expectation, 1);
  }

  private Node parseExpr(@Nullable String expectation, int minPrecedence) {
    var expr = parseExprAtom(expectation);
    var fullOpToken = fullLookahead();
    var operator = getOperator(fullOpToken.tk);
    while (operator != null) {
      if (operator.getPrec() < minPrecedence) break;
      // `-` and `[]` must be in the same line as the left operand and have no semicolons inbetween
      if ((operator == Operator.MINUS || operator == Operator.SUBSCRIPT)
          && (fullOpToken.hasSemicolon || !expr.span.isSameLine(fullOpToken.tk.span))) break;
      var children = new ArrayList<Node>();
      children.add(expr);
      ff(children);
      var op = next();
      children.add(make(NodeType.OPERATOR, op.span));
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
        case DOT, QDOT -> {
          nodeType = NodeType.QUALIFIED_ACCESS_EXPR;
          children.add(parseUnqualifiedAccessExpr());
        }
        case NON_NULL -> nodeType = NodeType.NON_NULL_EXPR;
        default -> children.add(parseExpr(expectation, nextMinPrec));
      }

      expr = new Node(nodeType, children);
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

  private Node parseUnqualifiedAccessExpr() {
    var children = new ArrayList<Node>();
    children.add(parseIdentifier());
    if (lookahead() == Token.LPAREN && noSemicolonInbetween() && _lookahead.newLinesBetween == 0) {
      ff(children);
      children.add(parseArgumentList());
    }
    return new Node(NodeType.UNQUALIFIED_ACCESS_EXPR, children);
  }

  private Node parseExprAtom(@Nullable String expectation) {
    var expr =
        switch (lookahead) {
          case THIS -> new Node(NodeType.THIS_EXPR, next().span);
          case OUTER -> new Node(NodeType.OUTER_EXPR, next().span);
          case MODULE -> new Node(NodeType.MODULE_EXPR, next().span);
          case NULL -> new Node(NodeType.NULL_EXPR, next().span);
          case THROW -> {
            var children = new ArrayList<Node>();
            children.add(makeTerminal(next()));
            ff(children);
            expect(Token.LPAREN, children, "unexpectedToken", "(");
            ff(children);
            children.add(parseExpr(")"));
            ff(children);
            expect(Token.RPAREN, children, "unexpectedToken", ")");
            yield new Node(NodeType.THROW_EXPR, children);
          }
          case TRACE -> {
            var children = new ArrayList<Node>();
            children.add(makeTerminal(next()));
            ff(children);
            expect(Token.LPAREN, children, "unexpectedToken", "(");
            ff(children);
            children.add(parseExpr(")"));
            ff(children);
            expect(Token.RPAREN, children, "unexpectedToken", ")");
            yield new Node(NodeType.TRACE_EXPR, children);
          }
          case IMPORT, IMPORT_STAR -> {
            var children = new ArrayList<Node>();
            children.add(makeTerminal(next()));
            ff(children);
            expect(Token.LPAREN, children, "unexpectedToken", "(");
            ff(children);
            children.add(parseStringConstant());
            ff(children);
            expect(Token.RPAREN, children, "unexpectedToken", ")");
            yield new Node(NodeType.IMPORT_EXPR, children);
          }
          case READ, READ_STAR, READ_QUESTION -> {
            var children = new ArrayList<Node>();
            children.add(makeTerminal(next()));
            ff(children);
            expect(Token.LPAREN, children, "unexpectedToken", "(");
            ff(children);
            children.add(parseExpr(")"));
            ff(children);
            expect(Token.RPAREN, children, "unexpectedToken", ")");
            yield new Node(NodeType.READ_EXPR, children);
          }
          case NEW -> {
            var children = new ArrayList<Node>();
            var header = new ArrayList<Node>();
            header.add(makeTerminal(next()));
            ff(header);
            if (lookahead != Token.LBRACE) {
              header.add(parseType("{"));
              children.add(new Node(NodeType.NEW_HEADER, header));
              ff(children);
            } else {
              children.add(new Node(NodeType.NEW_HEADER, header));
            }
            children.add(parseObjectBody());
            yield new Node(NodeType.NEW_EXPR, children);
          }
          case MINUS -> {
            var children = new ArrayList<Node>();
            children.add(makeTerminal(next()));
            ff(children);
            // unary minus has higher precendence than most binary operators
            children.add(parseExpr(expectation, 12));
            yield new Node(NodeType.UNARY_MINUS_EXPR, children);
          }
          case NOT -> {
            var children = new ArrayList<Node>();
            children.add(makeTerminal(next()));
            ff(children);
            // logical not has higher precendence than most binary operators
            children.add(parseExpr(expectation, 11));
            yield new Node(NodeType.LOGICAL_NOT_EXPR, children);
          }
          case LPAREN -> {
            // can be function literal or parenthesized expression
            if (isFunctionLiteral()) {
              yield parseFunctionLiteral();
            } else {
              yield parseParenthesizedExpr();
            }
          }
          case SUPER -> {
            var children = new ArrayList<Node>();
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
              yield new Node(NodeType.SUPER_ACCESS_EXPR, children);
            } else {
              expect(Token.LBRACK, children, "unexpectedToken", "[");
              ff(children);
              children.add(parseExpr());
              ff(children);
              expect(Token.RBRACK, children, "unexpectedToken", "]");
              yield new Node(NodeType.SUPER_SUBSCRIPT_EXPR, children);
            }
          }
          case IF -> {
            var children = new ArrayList<Node>();
            var header = new ArrayList<Node>();
            header.add(makeTerminal(next()));
            ff(header);
            var condition = new ArrayList<Node>();
            var conditionExpr = new ArrayList<Node>();
            expect(Token.LPAREN, condition, "unexpectedToken", "(");
            ff(conditionExpr);
            conditionExpr.add(parseExpr(")"));
            ff(conditionExpr);
            condition.add(new Node(NodeType.IF_CONDITION_EXPR, conditionExpr));
            expect(Token.RPAREN, condition, "unexpectedToken", ")");
            header.add(new Node(NodeType.IF_CONDITION, condition));
            children.add(new Node(NodeType.IF_HEADER, header));
            var thenExpr = new ArrayList<Node>();
            ff(thenExpr);
            thenExpr.add(parseExpr("else"));
            ff(thenExpr);
            children.add(new Node(NodeType.IF_THEN_EXPR, thenExpr));
            expect(Token.ELSE, children, "unexpectedToken", "else");
            var elseExpr = new ArrayList<Node>();
            ff(elseExpr);
            elseExpr.add(parseExpr(expectation));
            children.add(new Node(NodeType.IF_ELSE_EXPR, elseExpr));
            yield new Node(NodeType.IF_EXPR, children);
          }
          case LET -> {
            var children = new ArrayList<Node>();
            children.add(makeTerminal(next()));
            ff(children);
            var paramDef = new ArrayList<Node>();
            expect(Token.LPAREN, paramDef, "unexpectedToken", "(");
            var param = new ArrayList<Node>();
            ff(param);
            param.add(parseParameter());
            ff(param);
            expect(Token.ASSIGN, param, "unexpectedToken", "=");
            ff(param);
            param.add(parseExpr(")"));
            paramDef.add(new Node(NodeType.LET_PARAMETER, param));
            ff(paramDef);
            expect(Token.RPAREN, paramDef, "unexpectedToken", ")");
            children.add(new Node(NodeType.LET_PARAMETER_DEFINITION, paramDef));
            ff(children);
            children.add(parseExpr(expectation));
            yield new Node(NodeType.LET_EXPR, children);
          }
          case TRUE, FALSE -> new Node(NodeType.BOOL_LITERAL_EXPR, next().span);
          case INT, HEX, BIN, OCT -> new Node(NodeType.INT_LITERAL_EXPR, next().span);
          case FLOAT -> new Node(NodeType.FLOAT_LITERAL_EXPR, next().span);
          case STRING_START -> parseSingleLineStringLiteralExpr();
          case STRING_MULTI_START -> parseMultiLineStringLiteralExpr();
          case IDENTIFIER -> parseUnqualifiedAccessExpr();
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
  private Node parseExprRest(Node expr) {
    // amends
    if (lookahead() == Token.LBRACE) {
      var children = new ArrayList<Node>();
      children.add(expr);
      ff(children);
      if (expr.type == NodeType.PARENTHESIZED_EXPR
          || expr.type == NodeType.AMENDS_EXPR
          || expr.type == NodeType.NEW_EXPR) {
        children.add(parseObjectBody());
        return parseExprRest(new Node(NodeType.AMENDS_EXPR, children));
      }
      throw parserError("unexpectedCurlyProbablyAmendsExpression", expr.text(lexer.getSource()));
    }
    return expr;
  }

  private boolean isFunctionLiteral() {
    var originalCursor = cursor;
    try {
      next(); // open (
      ff();
      var token = next().token;
      ff();
      if (token == Token.RPAREN) {
        return lookahead == Token.ARROW;
      }
      if (token == Token.UNDERSCORE) {
        return true;
      }
      if (token != Token.IDENTIFIER) {
        return false;
      }
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

  private Node parseSingleLineStringLiteralExpr() {
    var children = new ArrayList<Node>();
    var start = next();
    children.add(makeTerminal(start)); // string start
    while (lookahead != Token.STRING_END) {
      switch (lookahead) {
        case STRING_PART -> {
          var tk = next();
          if (!tk.text(lexer).isEmpty()) {
            children.add(make(NodeType.STRING_CHARS, tk.span));
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
    return new Node(NodeType.SINGLE_LINE_STRING_LITERAL_EXPR, children);
  }

  private Node parseMultiLineStringLiteralExpr() {
    var children = new ArrayList<Node>();
    var start = next();
    children.add(makeTerminal(start)); // string start
    if (lookahead != Token.STRING_NEWLINE) {
      throw parserError(ErrorMessages.create("stringContentMustBeginOnNewLine"), spanLookahead);
    }
    while (lookahead != Token.STRING_END) {
      switch (lookahead) {
        case STRING_PART -> {
          var tk = next();
          if (!tk.text(lexer).isEmpty()) {
            children.add(make(NodeType.STRING_CHARS, tk.span));
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
    validateStringEndDelimiter(children);
    validateStringIndentation(children);
    return new Node(NodeType.MULTI_LINE_STRING_LITERAL_EXPR, children);
  }

  private void validateStringEndDelimiter(List<Node> nodes) {
    var beforeLast = nodes.get(nodes.size() - 2);
    if (beforeLast.type == NodeType.STRING_NEWLINE) return;
    var text = beforeLast.text(lexer.getSource());
    if (!text.isBlank()) {
      throw parserError(
          ErrorMessages.create("closingStringDelimiterMustBeginOnNewLine"), beforeLast.span);
    }
  }

  private void validateStringIndentation(List<Node> nodes) {
    var indentNode = nodes.get(nodes.size() - 2);
    if (indentNode.type == NodeType.STRING_NEWLINE) return;
    var indent = indentNode.text(lexer.getSource());
    var previousNewline = false;
    for (var i = 1; i < nodes.size() - 2; i++) {
      var child = nodes.get(i);
      if (child.type != NodeType.STRING_NEWLINE && previousNewline) {
        var text = child.text(lexer.getSource());
        if (!text.startsWith(indent)) {
          throw parserError(ErrorMessages.create("stringIndentationMustMatchLastLine"), child.span);
        }
      }
      previousNewline = child.type == NodeType.STRING_NEWLINE;
    }
  }

  private Node parseParenthesizedExpr() {
    var children = new ArrayList<Node>();
    expect(Token.LPAREN, children, "unexpectedToken", "(");
    if (lookahead() == Token.RPAREN) {
      ff(children);
      children.add(makeTerminal(next()));
      return new Node(NodeType.PARENTHESIZED_EXPR, children);
    }
    var elements = new ArrayList<Node>();
    ff(elements);
    elements.add(parseExpr(")"));
    ff(elements);
    children.add(new Node(NodeType.PARENTHESIZED_EXPR_ELEMENTS, elements));
    expect(Token.RPAREN, children, "unexpectedToken", ")");
    return new Node(NodeType.PARENTHESIZED_EXPR, children);
  }

  private Node parseFunctionLiteral() {
    var paramListChildren = new ArrayList<Node>();
    expect(Token.LPAREN, paramListChildren, "unexpectedToken", "(");
    if (lookahead() == Token.RPAREN) {
      ff(paramListChildren);
      paramListChildren.add(makeTerminal(next()));
    } else {
      var elements = new ArrayList<Node>();
      ff(elements);
      parseListOf(Token.RPAREN, elements, this::parseParameter);
      paramListChildren.add(new Node(NodeType.PARAMETER_LIST_ELEMENTS, elements));
      expect(Token.RPAREN, paramListChildren, "unexpectedToken2", ",", ")");
    }
    var children = new ArrayList<Node>();
    children.add(new Node(NodeType.PARAMETER_LIST, paramListChildren));
    ff(children);
    expect(Token.ARROW, children, "unexpectedToken", "->");
    var body = new ArrayList<Node>();
    ff(body);
    body.add(parseExpr());
    children.add(new Node(NodeType.FUNCTION_LITERAL_BODY, body));
    return new Node(NodeType.FUNCTION_LITERAL_EXPR, children);
  }

  private Node parseType() {
    return parseType(null);
  }

  private Node parseType(@Nullable String expectation) {
    var children = new ArrayList<Node>();
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

    if (lookahead() != Token.UNION) {
      if (hasDefault) {
        throw parserError(ErrorMessages.create("notAUnion"), start.endWith(first.span));
      }
      return first;
    }

    while (lookahead() == Token.UNION) {
      ff(children);
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
    return new Node(NodeType.UNION_TYPE, children);
  }

  private Node parseTypeAtom(@Nullable String expectation) {
    var typ =
        switch (lookahead) {
          case UNKNOWN -> make(NodeType.UNKNOWN_TYPE, next().span);
          case NOTHING -> make(NodeType.NOTHING_TYPE, next().span);
          case MODULE -> make(NodeType.MODULE_TYPE, next().span);
          case LPAREN -> {
            var children = new ArrayList<Node>();
            children.add(makeTerminal(next()));
            var totalTypes = 0;
            if (lookahead() == Token.RPAREN) {
              ff(children);
              children.add(makeTerminal(next()));
            } else {
              var elements = new ArrayList<Node>();
              ff(elements);
              elements.add(parseType(")"));
              ff(elements);
              while (lookahead == Token.COMMA) {
                var comma = next();
                if (lookahead() == Token.RPAREN) {
                  ff(elements);
                  break;
                }
                elements.add(makeTerminal(comma));
                ff(elements);
                elements.add(parseType(")"));
                totalTypes++;
                ff(elements);
              }
              children.add(new Node(NodeType.PARENTHESIZED_TYPE_ELEMENTS, elements));
              expect(Token.RPAREN, children, "unexpectedToken2", ",", ")");
            }
            if (totalTypes > 1 || lookahead() == Token.ARROW) {
              var actualChildren = new ArrayList<Node>();
              actualChildren.add(new Node(NodeType.FUNCTION_TYPE_PARAMETERS, children));
              ff(actualChildren);
              expect(Token.ARROW, actualChildren, "unexpectedToken", "->");
              ff(actualChildren);
              actualChildren.add(parseType(expectation));
              yield new Node(NodeType.FUNCTION_TYPE, actualChildren);
            } else {
              yield new Node(NodeType.PARENTHESIZED_TYPE, children);
            }
          }
          case IDENTIFIER -> {
            var children = new ArrayList<Node>();
            children.add(parseQualifiedIdentifier());
            if (lookahead() == Token.LT) {
              ff(children);
              children.add(parseTypeArgumentList());
            }
            yield new Node(NodeType.DECLARED_TYPE, children);
          }
          case STRING_START ->
              new Node(NodeType.STRING_CONSTANT_TYPE, List.of(parseStringConstant()));
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

  private Node parseTypeEnd(Node type) {
    var children = new ArrayList<Node>();
    children.add(type);
    // nullable types
    if (lookahead() == Token.QUESTION) {
      ff(children);
      children.add(makeTerminal(next()));
      var res = new Node(NodeType.NULLABLE_TYPE, children);
      return parseTypeEnd(res);
    }
    // constrained types: have to start in the same line as the type
    var fla = fullLookahead();
    if (fla.tk.token == Token.LPAREN && noSemicolonInbetween() && fla.tk.newLinesBetween == 0) {
      ff(children);
      var constraint = new ArrayList<Node>();
      constraint.add(makeTerminal(next()));
      var elements = new ArrayList<Node>();
      ff(elements);
      parseListOf(Token.RPAREN, elements, () -> parseExpr(")"));
      constraint.add(new Node(NodeType.CONSTRAINED_TYPE_ELEMENTS, elements));
      expect(Token.RPAREN, constraint, "unexpectedToken2", ",", ")");
      children.add(new Node(NodeType.CONSTRAINED_TYPE_CONSTRAINT, constraint));
      var res = new Node(NodeType.CONSTRAINED_TYPE, children);
      return parseTypeEnd(res);
    }
    return type;
  }

  private Node parseAnnotation() {
    var children = new ArrayList<Node>();
    children.add(makeTerminal(next()));
    children.add(parseType());
    if (lookahead() == Token.LBRACE) {
      ff(children);
      children.add(parseObjectBody());
    }
    return new Node(NodeType.ANNOTATION, children);
  }

  private Node parseParameter() {
    if (lookahead == Token.UNDERSCORE) {
      return new Node(NodeType.PARAMETER, List.of(makeTerminal(next())));
    }
    return parseTypedIdentifier();
  }

  private Node parseTypedIdentifier() {
    var children = new ArrayList<Node>();
    children.add(parseIdentifier());
    if (lookahead() == Token.COLON) {
      ff(children);
      children.add(parseTypeAnnotation());
    }
    return new Node(NodeType.PARAMETER, children);
  }

  private Node parseParameterList() {
    var children = new ArrayList<Node>();
    expect(Token.LPAREN, children, "unexpectedToken", "(");
    ff(children);
    if (lookahead == Token.RPAREN) {
      children.add(makeTerminal(next()));
    } else {
      var elements = new ArrayList<Node>();
      parseListOf(Token.RPAREN, elements, this::parseParameter);
      children.add(new Node(NodeType.PARAMETER_LIST_ELEMENTS, elements));
      expect(Token.RPAREN, children, "unexpectedToken2", ",", ")");
    }
    return new Node(NodeType.PARAMETER_LIST, children);
  }

  private List<Node> parseBodyList() {
    if (lookahead != Token.LBRACE) {
      throw parserError("unexpectedToken2", _lookahead.text(lexer), "{", "=");
    }
    var bodies = new ArrayList<Node>();
    do {
      bodies.add(parseObjectBody());
    } while (lookahead() == Token.LBRACE);
    return bodies;
  }

  private Node parseTypeParameterList() {
    var children = new ArrayList<Node>();
    expect(Token.LT, children, "unexpectedToken", "<");
    ff(children);
    var elements = new ArrayList<Node>();
    parseListOf(Token.GT, elements, this::parseTypeParameter);
    children.add(new Node(NodeType.TYPE_PARAMETER_LIST_ELEMENTS, elements));
    expect(Token.GT, children, "unexpectedToken2", ",", ">");
    return new Node(NodeType.TYPE_PARAMETER_LIST, children);
  }

  private Node parseTypeArgumentList() {
    var children = new ArrayList<Node>();
    expect(Token.LT, children, "unexpectedToken", "<");
    ff(children);
    var elements = new ArrayList<Node>();
    parseListOf(Token.GT, elements, () -> parseType(">"));
    children.add(new Node(NodeType.TYPE_ARGUMENT_LIST_ELEMENTS, elements));
    expect(Token.GT, children, "unexpectedToken2", ",", ">");
    return new Node(NodeType.TYPE_ARGUMENT_LIST, children);
  }

  private Node parseArgumentList() {
    var children = new ArrayList<Node>();
    expect(Token.LPAREN, children, "unexpectedToken", "(");
    if (lookahead() == Token.RPAREN) {
      ff(children);
      children.add(makeTerminal(next()));
      return new Node(NodeType.ARGUMENT_LIST, children);
    }
    var elements = new ArrayList<Node>();
    ff(elements);
    parseListOf(Token.RPAREN, elements, () -> parseExpr(")"));
    ff(elements);
    children.add(new Node(NodeType.ARGUMENT_LIST_ELEMENTS, elements));
    expect(Token.RPAREN, children, "unexpectedToken2", ",", ")");
    return new Node(NodeType.ARGUMENT_LIST, children);
  }

  private Node parseTypeParameter() {
    var children = new ArrayList<Node>();
    if (lookahead == Token.IN) {
      children.add(makeTerminal(next()));
    } else if (lookahead == Token.OUT) {
      children.add(makeTerminal(next()));
    }
    children.add(parseIdentifier());
    return new Node(NodeType.TYPE_PARAMETER, children);
  }

  private Node parseTypeAnnotation() {
    var children = new ArrayList<Node>();
    expect(Token.COLON, children, "unexpectedToken", ":");
    ff(children);
    children.add(parseType());
    return new Node(NodeType.TYPE_ANNOTATION, children);
  }

  private Node parseIdentifier() {
    if (lookahead != Token.IDENTIFIER) {
      if (lookahead.isKeyword()) {
        throw parserError("keywordNotAllowedHere", lookahead.text());
      }
      throw parserError("unexpectedToken", _lookahead.text(lexer), "identifier");
    }
    return new Node(NodeType.IDENTIFIER, next().span);
  }

  private Node parseStringConstant() {
    var children = new ArrayList<Node>();
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
    return new Node(NodeType.STRING_CHARS, children);
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

  private void expect(Token type, List<Node> children, String errorKey, Object... messageArgs) {
    var tk = expect(type, errorKey, messageArgs);
    children.add(makeTerminal(tk));
  }

  private void parseListOf(Token terminator, List<Node> children, Supplier<Node> parser) {
    children.add(parser.get());
    ff(children);
    while (lookahead == Token.COMMA) {
      // don't store the last comma
      var comma = makeTerminal(next());
      if (lookahead() == terminator) break;
      children.add(comma);
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

  private boolean noSemicolonInbetween() {
    return tokens.get(cursor - 1).token != Token.SEMICOLON;
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

  private record HeaderParts(List<Node> preffixes, @Nullable Node modifierList) {}

  private HeaderParts getHeaderParts(List<Node> nodes) {
    if (nodes.isEmpty()) return new HeaderParts(nodes, null);
    var last = nodes.get(nodes.size() - 1);
    if (last.type == NodeType.MODIFIER_LIST) {
      return new HeaderParts(nodes.subList(0, nodes.size() - 1), last);
    }
    return new HeaderParts(nodes, null);
  }

  private Node make(NodeType type, FullSpan span) {
    return new Node(type, span);
  }

  private Node makeAffix(FullToken tk) {
    return new Node(nodeTypeForAffix(tk.token), tk.span);
  }

  private Node makeTerminal(FullToken tk) {
    return new Node(NodeType.TERMINAL, tk.span);
  }

  // fast-forward over affix tokens
  // store children
  private void ff(List<Node> children) {
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
