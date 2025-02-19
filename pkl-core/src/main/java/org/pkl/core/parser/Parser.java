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
package org.pkl.core.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.pkl.core.PklBugException;
import org.pkl.core.parser.syntax.Annotation;
import org.pkl.core.parser.syntax.ArgumentList;
import org.pkl.core.parser.syntax.Class;
import org.pkl.core.parser.syntax.ClassBody;
import org.pkl.core.parser.syntax.ClassMethod;
import org.pkl.core.parser.syntax.ClassProperty;
import org.pkl.core.parser.syntax.DocComment;
import org.pkl.core.parser.syntax.Expr;
import org.pkl.core.parser.syntax.Expr.AmendsExpr;
import org.pkl.core.parser.syntax.Expr.BoolLiteralExpr;
import org.pkl.core.parser.syntax.Expr.FloatLiteralExpr;
import org.pkl.core.parser.syntax.Expr.FunctionLiteralExpr;
import org.pkl.core.parser.syntax.Expr.IfExpr;
import org.pkl.core.parser.syntax.Expr.IntLiteralExpr;
import org.pkl.core.parser.syntax.Expr.LetExpr;
import org.pkl.core.parser.syntax.Expr.LogicalNotExpr;
import org.pkl.core.parser.syntax.Expr.ModuleExpr;
import org.pkl.core.parser.syntax.Expr.MultiLineStringLiteralExpr;
import org.pkl.core.parser.syntax.Expr.NewExpr;
import org.pkl.core.parser.syntax.Expr.NonNullExpr;
import org.pkl.core.parser.syntax.Expr.NullLiteralExpr;
import org.pkl.core.parser.syntax.Expr.OperatorExpr;
import org.pkl.core.parser.syntax.Expr.OuterExpr;
import org.pkl.core.parser.syntax.Expr.ParenthesizedExpr;
import org.pkl.core.parser.syntax.Expr.QualifiedAccessExpr;
import org.pkl.core.parser.syntax.Expr.ReadExpr;
import org.pkl.core.parser.syntax.Expr.ReadType;
import org.pkl.core.parser.syntax.Expr.SingleLineStringLiteralExpr;
import org.pkl.core.parser.syntax.Expr.SubscriptExpr;
import org.pkl.core.parser.syntax.Expr.SuperAccessExpr;
import org.pkl.core.parser.syntax.Expr.SuperSubscriptExpr;
import org.pkl.core.parser.syntax.Expr.ThisExpr;
import org.pkl.core.parser.syntax.Expr.ThrowExpr;
import org.pkl.core.parser.syntax.Expr.TraceExpr;
import org.pkl.core.parser.syntax.Expr.UnaryMinusExpr;
import org.pkl.core.parser.syntax.Expr.UnqualifiedAccessExpr;
import org.pkl.core.parser.syntax.ExtendsOrAmendsClause;
import org.pkl.core.parser.syntax.Identifier;
import org.pkl.core.parser.syntax.ImportClause;
import org.pkl.core.parser.syntax.Keyword;
import org.pkl.core.parser.syntax.Modifier;
import org.pkl.core.parser.syntax.Module;
import org.pkl.core.parser.syntax.ModuleDecl;
import org.pkl.core.parser.syntax.Node;
import org.pkl.core.parser.syntax.ObjectBody;
import org.pkl.core.parser.syntax.ObjectMember;
import org.pkl.core.parser.syntax.Operator;
import org.pkl.core.parser.syntax.Parameter;
import org.pkl.core.parser.syntax.Parameter.TypedIdentifier;
import org.pkl.core.parser.syntax.ParameterList;
import org.pkl.core.parser.syntax.QualifiedIdentifier;
import org.pkl.core.parser.syntax.ReplInput;
import org.pkl.core.parser.syntax.StringConstant;
import org.pkl.core.parser.syntax.StringPart;
import org.pkl.core.parser.syntax.StringPart.StringChars;
import org.pkl.core.parser.syntax.Type;
import org.pkl.core.parser.syntax.Type.DeclaredType;
import org.pkl.core.parser.syntax.Type.ParenthesizedType;
import org.pkl.core.parser.syntax.Type.StringConstantType;
import org.pkl.core.parser.syntax.TypeAlias;
import org.pkl.core.parser.syntax.TypeAnnotation;
import org.pkl.core.parser.syntax.TypeArgumentList;
import org.pkl.core.parser.syntax.TypeParameter;
import org.pkl.core.parser.syntax.TypeParameterList;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.Nullable;

@SuppressWarnings("DuplicatedCode")
public class Parser {

  private Lexer lexer;
  private Token lookahead;
  private Span spanLookahead;
  private boolean backtracking = false;
  private FullToken prev;
  private FullToken _lookahead;
  private boolean precededBySemicolon = false;

  public Parser() {}

  private void init(String source) {
    this.lexer = new Lexer(source);
    _lookahead = forceNext();
    lookahead = _lookahead.token;
    spanLookahead = _lookahead.span;
  }

  public Module parseModule(String source) {
    init(source);
    if (lookahead == Token.EOF) {
      return new Module(Collections.singletonList(null), new Span(0, 0));
    }
    var start = spanLookahead;
    Span end = null;
    ModuleDecl moduleDecl;
    var nodes = new ArrayList<Node>();
    try {
      var header = parseMemberHeader();

      moduleDecl = parseModuleDecl(header);
      if (moduleDecl != null) {
        end = moduleDecl.span();
        header = null;
      }
      nodes.add(moduleDecl);
      // imports
      while (lookahead == Token.IMPORT || lookahead == Token.IMPORT_STAR) {
        if (header != null && header.isNotEmpty()) {
          throw parserError("wrongHeaders", "Imports");
        }
        var _import = parseImportDecl();
        nodes.add(_import);
        end = _import.span();
      }

      // entries
      if (header != null && header.isNotEmpty()) {
        end = parseModuleMember(header, nodes);
      }

      while (lookahead != Token.EOF) {
        header = parseMemberHeader();
        end = parseModuleMember(header, nodes);
      }
      return new Module(nodes, start.endWith(spanLookahead));
    } catch (ParserError pe) {
      var spanEnd = end != null ? end : start;
      pe.setPartialParseResult(new Module(nodes, start.endWith(spanEnd)));
      throw pe;
    }
  }

  public Expr parseExpressionInput(String source) {
    init(source);
    var expr = parseExpr();
    expect(Token.EOF, "unexpectedToken", "end of file");
    return expr;
  }

  public ReplInput parseReplInput(String source) {
    init(source);
    var nodes = new ArrayList<Node>();
    while (lookahead != Token.EOF) {
      var header = parseMemberHeader();
      switch (lookahead) {
        case IMPORT, IMPORT_STAR -> {
          ensureEmptyHeaders(header, "Imports");
          nodes.add(parseImportDecl());
        }
        case MODULE, AMENDS, EXTENDS -> nodes.add(parseModuleDecl(header));
        case CLASS -> nodes.add(parseClass(header));
        case TYPE_ALIAS -> nodes.add(parseTypeAlias(header));
        case FUNCTION -> nodes.add(parseClassMethod(header));
        case IDENTIFIER -> {
          next();
          switch (lookahead) {
            case COLON, ASSIGN, LBRACE -> {
              backtrack();
              nodes.add(parseClassProperty(header));
            }
            default -> {
              backtrack();
              ensureEmptyHeaders(header, "Expressions");
              nodes.add(parseExpr());
            }
          }
        }
        default -> {
          ensureEmptyHeaders(header, "Expressions");
          nodes.add(parseExpr());
        }
      }
    }
    Span span;
    if (nodes.isEmpty()) {
      span = new Span(0, 0);
    } else {
      span = nodes.get(0).span().endWith(nodes.get(nodes.size() - 1).span());
    }
    return new ReplInput(nodes, span);
  }

  private @Nullable ModuleDecl parseModuleDecl(MemberHeader header) {
    QualifiedIdentifier moduleName = null;
    Keyword moduleKeyword = null;
    var start = header.span();
    Span end = null;
    if (lookahead == Token.MODULE) {
      var module = expect(Token.MODULE, "unexpectedToken", "module");
      moduleKeyword = new Keyword(module.span);
      if (start == null) {
        start = module.span;
      }
      moduleName = parseQualifiedIdentifier();
      end = moduleName.span();
    }
    var extendsOrAmendsDecl = parseExtendsAmendsDecl();
    if (extendsOrAmendsDecl != null) {
      if (start == null) {
        start = extendsOrAmendsDecl.span();
      }
      end = extendsOrAmendsDecl.span();
    }
    if (moduleName != null || extendsOrAmendsDecl != null) {
      var children = new ArrayList<Node>();
      children.add(header.docComment);
      children.addAll(header.annotations);
      var modifiersOffset = children.size();
      children.addAll(header.modifiers);
      var nameOffset = children.size();
      children.add(moduleKeyword);
      children.add(moduleName);
      children.add(extendsOrAmendsDecl);
      return new ModuleDecl(children, modifiersOffset, nameOffset, start.endWith(end));
    }
    return null;
  }

  private QualifiedIdentifier parseQualifiedIdentifier() {
    var idents = parseListOf(Token.DOT, this::parseIdentifier);
    return new QualifiedIdentifier(idents);
  }

  private @Nullable ExtendsOrAmendsClause parseExtendsAmendsDecl() {
    if (lookahead == Token.EXTENDS) {
      var tk = next().span;
      var url = parseStringConstant();
      return new ExtendsOrAmendsClause(
          url, ExtendsOrAmendsClause.Type.EXTENDS, tk.endWith(url.span()));
    }
    if (lookahead == Token.AMENDS) {
      var tk = next().span;
      var url = parseStringConstant();
      return new ExtendsOrAmendsClause(
          url, ExtendsOrAmendsClause.Type.AMENDS, tk.endWith(url.span()));
    }
    return null;
  }

  private ImportClause parseImportDecl() {
    Span start;
    boolean isGlob = false;
    if (lookahead == Token.IMPORT_STAR) {
      start = next().span;
      isGlob = true;
    } else {
      start = expect(Token.IMPORT, "unexpectedToken2", "import", "import*").span;
    }
    var str = parseStringConstant();
    var end = str.span();
    Identifier alias = null;
    if (lookahead == Token.AS) {
      next();
      alias = parseIdentifier();
      end = alias.span();
    }
    return new ImportClause(str, isGlob, alias, start.endWith(end));
  }

  private MemberHeader parseMemberHeader() {
    DocComment docComment = null;
    var annotations = new ArrayList<Annotation>();
    var modifiers = new ArrayList<Modifier>();
    if (lookahead == Token.DOC_COMMENT) {
      docComment = parseDocComment();
    }
    while (lookahead == Token.AT) {
      annotations.add(parseAnnotation());
    }
    while (lookahead.isModifier()) {
      modifiers.add(parseModifier());
    }
    return new MemberHeader(docComment, annotations, modifiers);
  }

  private DocComment parseDocComment() {
    var spans = new ArrayList<Span>();
    spans.add(nextComment().span);
    while (lookahead == Token.DOC_COMMENT
        || lookahead == Token.LINE_COMMENT
        || lookahead == Token.BLOCK_COMMENT) {
      var next = nextComment();
      // newlines are not allowed in doc comments
      if (next.newLinesBetween > 1) {
        if (next.token == Token.DOC_COMMENT) {
          backtrack();
        }
        break;
      }
      if (next.token == Token.DOC_COMMENT) {
        spans.add(next.span);
      }
    }
    while (lookahead == Token.LINE_COMMENT || lookahead == Token.BLOCK_COMMENT) {
      nextComment();
    }
    return new DocComment(spans);
  }

  private Span parseModuleMember(MemberHeader header, List<Node> nodes) {
    switch (lookahead) {
      case IDENTIFIER -> {
        var node = parseClassProperty(header);
        nodes.add(node);
        return node.span();
      }
      case TYPE_ALIAS -> {
        var node = parseTypeAlias(header);
        nodes.add(node);
        return node.span();
      }
      case CLASS -> {
        var node = parseClass(header);
        nodes.add(node);
        return node.span();
      }
      case FUNCTION -> {
        var node = parseClassMethod(header);
        nodes.add(node);
        return node.span();
      }
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
    }
  }

  private TypeAlias parseTypeAlias(MemberHeader header) {
    var typeAlias = next().span;
    var startSpan = header.span(typeAlias);

    var identifier = parseIdentifier();
    TypeParameterList typePars = null;
    if (lookahead == Token.LT) {
      typePars = parseTypeParameterList();
    }
    expect(Token.ASSIGN, "unexpectedToken", "=");
    var type = parseType();
    var children = new ArrayList<Node>(header.annotations.size() + header.modifiers.size() + 5);
    children.add(header.docComment);
    children.addAll(header.annotations);
    var modifiersOffset = header.annotations.size() + 1;
    children.addAll(header.modifiers);
    var nameOffset = modifiersOffset + header.modifiers.size();
    children.add(new Keyword(typeAlias));
    children.add(identifier);
    children.add(typePars);
    children.add(type);
    return new TypeAlias(children, modifiersOffset, nameOffset, startSpan.endWith(type.span()));
  }

  private Class parseClass(MemberHeader header) {
    var classKeyword = next();
    var startSpan = header.span(classKeyword.span);
    var children = new ArrayList<Node>();
    children.add(header.docComment);
    children.addAll(header.annotations);
    var modifiersOffset = header.annotations.size() + 1;
    children.addAll(header.modifiers);
    var nameOffset = modifiersOffset + header.modifiers.size();
    children.add(new Keyword(classKeyword.span));
    var name = parseIdentifier();
    children.add(name);
    TypeParameterList typePars = null;
    var end = name.span();
    if (lookahead == Token.LT) {
      typePars = parseTypeParameterList();
      end = typePars.span();
    }
    children.add(typePars);
    Type superClass = null;
    if (lookahead == Token.EXTENDS) {
      next();
      superClass = parseType();
      end = superClass.span();
    }
    children.add(superClass);

    ClassBody body = null;
    if (lookahead == Token.LBRACE) {
      body = parseClassBody();
      end = body.span();
    }
    children.add(body);

    return new Class(children, modifiersOffset, nameOffset, startSpan.endWith(end));
  }

  private ClassBody parseClassBody() {
    var start = expect(Token.LBRACE, "missingDelimiter", "{").span;
    var children = new ArrayList<Node>();
    while (lookahead != Token.RBRACE && lookahead != Token.EOF) {
      var entryHeader = parseMemberHeader();
      if (lookahead == Token.FUNCTION) {
        children.add(parseClassMethod(entryHeader));
      } else {
        children.add(parseClassProperty(entryHeader));
      }
    }
    if (lookahead == Token.EOF) {
      throw new ParserError(
          ErrorMessages.create("missingDelimiter", "}"), prev.span.stopSpan().move(1));
    }
    var end = expect(Token.RBRACE, "missingDelimiter", "}").span;
    return new ClassBody(children, start.endWith(end));
  }

  private ClassProperty parseClassProperty(MemberHeader header) {
    var name = parseIdentifier();
    var start = header.span(name.span());
    var children = new ArrayList<Node>();
    children.add(header.docComment);
    children.addAll(header.annotations);
    var modifiersOffset = header.annotations.size() + 1;
    children.addAll(header.modifiers);
    var nameOffset = modifiersOffset + header.modifiers.size();
    TypeAnnotation typeAnnotation = null;
    Expr expr = null;
    var bodies = new ArrayList<ObjectBody>();
    if (lookahead == Token.COLON) {
      typeAnnotation = parseTypeAnnotation();
    }
    if (lookahead == Token.ASSIGN) {
      next();
      expr = parseExpr();
    } else if (lookahead == Token.LBRACE) {
      if (typeAnnotation != null) {
        throw parserError("typeAnnotationInAmends");
      }
      while (lookahead == Token.LBRACE) {
        bodies.add(parseObjectBody());
      }
    }
    children.add(name);
    children.add(typeAnnotation);
    children.add(expr);
    children.addAll(bodies);
    if (expr != null) {
      return new ClassProperty(children, modifiersOffset, nameOffset, start.endWith(expr.span()));
    }
    if (!bodies.isEmpty()) {
      return new ClassProperty(
          children,
          modifiersOffset,
          nameOffset,
          start.endWith(bodies.get(bodies.size() - 1).span()));
    }
    if (typeAnnotation == null) {
      throw new ParserError(ErrorMessages.create("invalidProperty"), name.span());
    }
    return new ClassProperty(
        children, modifiersOffset, nameOffset, start.endWith(typeAnnotation.span()));
  }

  private ClassMethod parseClassMethod(MemberHeader header) {
    var func = expect(Token.FUNCTION, "unexpectedToken", "function").span;
    var start = header.span(func);
    var headerSpanStart = header.modifierSpan(func);
    var children = new ArrayList<Node>();
    children.add(header.docComment);
    children.addAll(header.annotations);
    var modifiersOffset = header.annotations.size() + 1;
    children.addAll(header.modifiers);
    var nameOffset = modifiersOffset + header.modifiers.size();
    var name = parseIdentifier();
    children.add(name);
    TypeParameterList typePars = null;
    if (lookahead == Token.LT) {
      typePars = parseTypeParameterList();
    }
    children.add(typePars);
    var parameterList = parseParameterList();
    children.add(parameterList);
    var end = parameterList.span();
    var endHeader = end;
    TypeAnnotation typeAnnotation = null;
    if (lookahead == Token.COLON) {
      typeAnnotation = parseTypeAnnotation();
      end = typeAnnotation.span();
      endHeader = end;
    }
    children.add(typeAnnotation);
    Expr expr = null;
    if (lookahead == Token.ASSIGN) {
      next();
      expr = parseExpr();
      end = expr.span();
    }
    children.add(expr);
    return new ClassMethod(
        children,
        modifiersOffset,
        nameOffset,
        headerSpanStart.endWith(endHeader),
        start.endWith(end));
  }

  private ObjectBody parseObjectBody() {
    var start = expect(Token.LBRACE, "unexpectedToken", "{").span;
    List<Node> nodes = new ArrayList<>();
    var membersOffset = -1;
    if (lookahead == Token.RBRACE) {
      return new ObjectBody(List.of(), 0, start.endWith(next().span));
    } else if (lookahead == Token.UNDERSCORE) {
      // it's a parameter
      nodes.addAll(parseListOfParameter(Token.COMMA));
      expect(Token.ARROW, "unexpectedToken2", ",", "->");
    } else if (lookahead == Token.IDENTIFIER) {
      // not sure what it is yet
      var identifier = parseIdentifier();
      if (lookahead == Token.ARROW) {
        // it's a parameter
        next();
        nodes.add(new TypedIdentifier(identifier, null, identifier.span()));
      } else if (lookahead == Token.COMMA) {
        // it's a parameter
        backtrack();
        nodes.addAll(parseListOfParameter(Token.COMMA));
        expect(Token.ARROW, "unexpectedToken2", ",", "->");
      } else if (lookahead == Token.COLON) {
        // still not sure
        var colon = next().span;
        var type = parseType();
        var typeAnnotation = new TypeAnnotation(type, colon.endWith(type.span()));
        if (lookahead == Token.COMMA) {
          // it's a parameter
          next();
          nodes.add(
              new TypedIdentifier(
                  identifier, typeAnnotation, identifier.span().endWith(type.span())));
          nodes.addAll(parseListOfParameter(Token.COMMA));
          expect(Token.ARROW, "unexpectedToken2", ",", "->");
        } else if (lookahead == Token.ARROW) {
          // it's a parameter
          next();
          nodes.add(
              new TypedIdentifier(
                  identifier, typeAnnotation, identifier.span().endWith(type.span())));
        } else {
          // it's a member
          expect(Token.ASSIGN, "unexpectedToken", "=");
          var expr = parseExpr();
          membersOffset = 0;
          nodes.add(
              new ObjectMember.ObjectProperty(
                  Arrays.asList(identifier, typeAnnotation, expr),
                  0,
                  identifier.span().endWith(expr.span())));
        }
      } else {
        // member
        backtrack();
      }
    }

    if (membersOffset < 0) {
      membersOffset = nodes.size();
    }
    // members
    while (lookahead != Token.RBRACE) {
      if (lookahead == Token.EOF) {
        throw new ParserError(
            ErrorMessages.create("missingDelimiter", "}"), prev.span.stopSpan().move(1));
      }
      nodes.add(parseObjectMember());
    }
    var end = next().span;
    return new ObjectBody(nodes, membersOffset, start.endWith(end));
  }

  private ObjectMember parseObjectMember() {
    return switch (lookahead) {
      case IDENTIFIER -> {
        next();
        if (lookahead == Token.LBRACE || lookahead == Token.COLON || lookahead == Token.ASSIGN) {
          // it's an objectProperty
          backtrack();
          yield parseObjectProperty(null);
        } else {
          backtrack();
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
          throw new ParserError(
              ErrorMessages.create("missingDelimiter", "}"), prev.span.stopSpan().move(1));
      default -> {
        var modifiers = new ArrayList<Modifier>();
        while (lookahead.isModifier()) {
          modifiers.add(parseModifier());
        }
        if (!modifiers.isEmpty()) {
          if (lookahead == Token.FUNCTION) {
            yield parseObjectMethod(modifiers);
          } else {
            yield parseObjectProperty(modifiers);
          }
        } else {
          yield parseObjectElement();
        }
      }
    };
  }

  private ObjectMember.ObjectElement parseObjectElement() {
    var expr = parseExpr("}");
    return new ObjectMember.ObjectElement(expr, expr.span());
  }

  private ObjectMember parseObjectProperty(@Nullable List<Modifier> modifiers) {
    var start = spanLookahead;
    if (modifiers != null && !modifiers.isEmpty()) {
      start = modifiers.get(0).span();
    }
    var allModifiers = modifiers;
    if (allModifiers == null) {
      allModifiers = parseModifierList();
    }
    var identifier = parseIdentifier();
    TypeAnnotation typeAnnotation = null;
    if (lookahead == Token.COLON) {
      typeAnnotation = parseTypeAnnotation();
    }
    if (typeAnnotation != null || lookahead == Token.ASSIGN) {
      expect(Token.ASSIGN, "unexpectedToken", "=");
      var expr = parseExpr("}");
      var nodes = new ArrayList<Node>(allModifiers.size() + 4);
      nodes.addAll(allModifiers);
      nodes.add(identifier);
      nodes.add(typeAnnotation);
      nodes.add(expr);
      return new ObjectMember.ObjectProperty(
          nodes, allModifiers.size(), start.endWith(expr.span()));
    }
    var bodies = parseBodyList();
    var end = bodies.get(bodies.size() - 1).span();
    var nodes = new ArrayList<Node>(allModifiers.size() + 4);
    nodes.addAll(allModifiers);
    nodes.add(identifier);
    nodes.add(null);
    nodes.add(null);
    nodes.addAll(bodies);
    return new ObjectMember.ObjectProperty(nodes, allModifiers.size(), start.endWith(end));
  }

  private ObjectMember.ObjectMethod parseObjectMethod(List<Modifier> modifiers) {
    var start = spanLookahead;
    if (!modifiers.isEmpty()) {
      start = modifiers.get(0).span();
    }
    var function = expect(Token.FUNCTION, "unexpectedToken", "function").span;
    var identifier = parseIdentifier();
    TypeParameterList params = null;
    if (lookahead == Token.LT) {
      params = parseTypeParameterList();
    }
    var args = parseParameterList();
    TypeAnnotation typeAnnotation = null;
    if (lookahead == Token.COLON) {
      typeAnnotation = parseTypeAnnotation();
    }
    expect(Token.ASSIGN, "unexpectedToken", "=");
    var expr = parseExpr("}");
    var nodes = new ArrayList<Node>(modifiers.size() + 6);
    nodes.addAll(modifiers);
    nodes.add(new Keyword(function));
    nodes.add(identifier);
    nodes.add(params);
    nodes.add(args);
    nodes.add(typeAnnotation);
    nodes.add(expr);
    return new ObjectMember.ObjectMethod(nodes, modifiers.size(), start.endWith(expr.span()));
  }

  private ObjectMember parseMemberPredicate() {
    var start = next().span;
    var pred = parseExpr("]]");
    var firstBrack = expect(Token.RBRACK, "unexpectedToken", "]]").span;
    Span secondbrack;
    if (lookahead != Token.RBRACK) {
      var text = _lookahead.text(lexer);
      throw new ParserError(ErrorMessages.create("unexpectedToken", text, "]]"), firstBrack);
    } else {
      secondbrack = next().span;
    }
    if (firstBrack.charIndex() != secondbrack.charIndex() - 1) {
      // There shouldn't be any whitespace between the first and second ']'.
      var span = firstBrack.endWith(secondbrack);
      var text = lexer.textFor(span.charIndex(), span.length());
      throw new ParserError(ErrorMessages.create("unexpectedToken", text, "]]"), firstBrack);
    }
    if (lookahead == Token.ASSIGN) {
      next();
      var expr = parseExpr("}");
      return new ObjectMember.MemberPredicate(List.of(pred, expr), start.endWith(expr.span()));
    }
    var bodies = parseBodyList();
    var end = bodies.get(bodies.size() - 1).span();
    var nodes = new ArrayList<Node>(bodies.size() + 2);
    nodes.add(pred);
    nodes.add(null);
    nodes.addAll(bodies);
    return new ObjectMember.MemberPredicate(nodes, start.endWith(end));
  }

  private ObjectMember parseObjectEntry() {
    var start = expect(Token.LBRACK, "unexpectedToken", "[").span;
    var key = parseExpr("]");
    expect(Token.RBRACK, "unexpectedToken", "]");
    if (lookahead == Token.ASSIGN) {
      next();
      var expr = parseExpr("}");
      return new ObjectMember.ObjectEntry(List.of(key, expr), start.endWith(expr.span()));
    }
    var bodies = parseBodyList();
    var end = bodies.get(bodies.size() - 1).span();
    var nodes = new ArrayList<Node>(bodies.size() + 2);
    nodes.add(key);
    nodes.add(null);
    nodes.addAll(bodies);
    return new ObjectMember.ObjectEntry(nodes, start.endWith(end));
  }

  private ObjectMember.ObjectSpread parseObjectSpread() {
    var start = next();
    boolean isNullable = start.token == Token.QSPREAD;
    var expr = parseExpr("}");
    return new ObjectMember.ObjectSpread(expr, isNullable, start.span.endWith(expr.span()));
  }

  private ObjectMember.WhenGenerator parseWhenGenerator() {
    var start = next().span;
    expect(Token.LPAREN, "unexpectedToken", "(");
    var pred = parseExpr(")");
    expect(Token.RPAREN, "unexpectedToken", ")");
    var body = parseObjectBody();
    var end = body.span();
    ObjectBody elseBody = null;
    if (lookahead == Token.ELSE) {
      next();
      elseBody = parseObjectBody();
      end = elseBody.span();
    }
    return new ObjectMember.WhenGenerator(pred, body, elseBody, start.endWith(end));
  }

  private ObjectMember.ForGenerator parseForGenerator() {
    var start = next().span;
    expect(Token.LPAREN, "unexpectedToken", "(");
    var par1 = parseParameter();
    Parameter par2 = null;
    if (lookahead == Token.COMMA) {
      next();
      par2 = parseParameter();
    }
    expect(Token.IN, "unexpectedToken", "in");
    var expr = parseExpr(")");
    expect(Token.RPAREN, "unexpectedToken", ")");
    var body = parseObjectBody();
    return new ObjectMember.ForGenerator(par1, par2, expr, body, start.endWith(body.span()));
  }

  private Expr parseExpr() {
    return parseExpr(null);
  }

  @SuppressWarnings("DuplicatedCode")
  private Expr parseExpr(@Nullable String expectation) {
    List<Expr> exprs = new ArrayList<>();
    exprs.add(parseExprAtom(expectation));
    var op = getOperator();
    loop:
    while (op != null) {
      switch (op) {
        case IS, AS -> {
          exprs.add(new OperatorExpr(op, next().span));
          exprs.add(new Expr.TypeExpr(parseType()));
          var precedence = OperatorResolver.getPrecedence(op);
          exprs = OperatorResolver.resolveOperatorsHigherThan(exprs, precedence);
        }
        case MINUS -> {
          if (!precededBySemicolon && _lookahead.newLinesBetween == 0) {
            exprs.add(new OperatorExpr(op, next().span));
            exprs.add(parseExprAtom(expectation));
          } else {
            break loop;
          }
        }
        case DOT, QDOT -> {
          // this exists just to keep backward compatibility with code as `x + y as List.distinct`
          // which should be removed at some point
          next();
          var expr = exprs.remove(exprs.size() - 1);
          var isNullable = op == Operator.QDOT;
          var identifier = parseIdentifier();
          ArgumentList argumentList = null;
          if (lookahead == Token.LPAREN
              && !precededBySemicolon
              && _lookahead.newLinesBetween == 0) {
            argumentList = parseArgumentList();
          }
          var lastSpan = argumentList != null ? argumentList.span() : identifier.span();
          exprs.add(
              new QualifiedAccessExpr(
                  expr, identifier, isNullable, argumentList, expr.span().endWith(lastSpan)));
        }
        default -> {
          exprs.add(new OperatorExpr(op, next().span));
          exprs.add(parseExprAtom(expectation));
        }
      }
      op = getOperator();
    }
    return OperatorResolver.resolveOperators(exprs);
  }

  private @Nullable Operator getOperator() {
    return switch (lookahead) {
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
      default -> null;
    };
  }

  private Expr parseExprAtom(@Nullable String expectation) {
    var expr =
        switch (lookahead) {
          case THIS -> new ThisExpr(next().span);
          case OUTER -> new OuterExpr(next().span);
          case MODULE -> new ModuleExpr(next().span);
          case NULL -> new NullLiteralExpr(next().span);
          case THROW -> {
            var start = next().span;
            expect(Token.LPAREN, "unexpectedToken", "(");
            var exp = parseExpr(")");
            var end = expect(Token.RPAREN, "unexpectedToken", ")").span;
            yield new ThrowExpr(exp, start.endWith(end));
          }
          case TRACE -> {
            var start = next().span;
            expect(Token.LPAREN, "unexpectedToken", "(");
            var exp = parseExpr(")");
            var end = expect(Token.RPAREN, "unexpectedToken", ")").span;
            yield new TraceExpr(exp, start.endWith(end));
          }
          case IMPORT -> {
            var start = next().span;
            expect(Token.LPAREN, "unexpectedToken", "(");
            var strConst = parseStringConstant();
            var end = expect(Token.RPAREN, "unexpectedToken", ")").span;
            yield new Expr.ImportExpr(strConst, false, start.endWith(end));
          }
          case IMPORT_STAR -> {
            var start = next().span;
            expect(Token.LPAREN, "unexpectedToken", "(");
            var strConst = parseStringConstant();
            var end = expect(Token.RPAREN, "unexpectedToken", ")").span;
            yield new Expr.ImportExpr(strConst, true, start.endWith(end));
          }
          case READ, READ_STAR, READ_QUESTION -> {
            var readType =
                switch (lookahead) {
                  case READ_QUESTION -> ReadType.NULL;
                  case READ_STAR -> ReadType.GLOB;
                  default -> ReadType.READ;
                };
            var start = next().span;
            expect(Token.LPAREN, "unexpectedToken", "(");
            var exp = parseExpr(")");
            var end = expect(Token.RPAREN, "unexpectedToken", ")").span;
            yield new ReadExpr(exp, readType, start.endWith(end));
          }
          case NEW -> {
            var start = next().span;
            Type type = null;
            if (lookahead != Token.LBRACE) {
              type = parseType("{");
            }
            var body = parseObjectBody();
            yield new NewExpr(type, body, start.endWith(body.span()));
          }
          case MINUS -> {
            var start = next().span;
            // calling `parseExprAtom` here and not `parseExpr` because
            // unary minus has higher precendence than binary operators
            var exp = parseExprAtom(expectation);
            yield new UnaryMinusExpr(exp, start.endWith(exp.span()));
          }
          case NOT -> {
            var start = next().span;
            // calling `parseExprAtom` here and not `parseExpr` because
            // logical not has higher precendence than binary operators
            var exp = parseExprAtom(expectation);
            yield new LogicalNotExpr(exp, start.endWith(exp.span()));
          }
          case LPAREN -> {
            // can be function literal or parenthesized expression
            var start = next().span;
            yield switch (lookahead) {
              case UNDERSCORE -> parseFunctionLiteral(start);
              case IDENTIFIER -> parseFunctionLiteralOrParenthesized(start);
              case RPAREN -> {
                var endParen = next().span;
                var paramList = new ParameterList(List.of(), start.endWith(endParen));
                expect(Token.ARROW, "unexpectedToken", "->");
                var exp = parseExpr(expectation);
                yield new FunctionLiteralExpr(paramList, exp, start.endWith(exp.span()));
              }
              default -> {
                // expression
                var exp = parseExpr(")");
                var end = expect(Token.RPAREN, "unexpectedToken", ")").span;
                yield new ParenthesizedExpr(exp, start.endWith(end));
              }
            };
          }
          case SUPER -> {
            var start = next().span;
            if (lookahead == Token.DOT) {
              next();
              var identifier = parseIdentifier();
              if (lookahead == Token.LPAREN) {
                var args = parseArgumentList();
                yield new SuperAccessExpr(identifier, args, start.endWith(args.span()));
              } else {
                yield new SuperAccessExpr(identifier, null, start.endWith(identifier.span()));
              }
            } else {
              expect(Token.LBRACK, "unexpectedToken", "[");
              var exp = parseExpr("]");
              var end = expect(Token.RBRACK, "unexpectedToken", "]").span;
              yield new SuperSubscriptExpr(exp, start.endWith(end));
            }
          }
          case IF -> {
            var start = next().span;
            expect(Token.LPAREN, "unexpectedToken", "(");
            var pred = parseExpr(")");
            expect(Token.RPAREN, "unexpectedToken", ")");
            var then = parseExpr("else");
            expect(Token.ELSE, "unexpectedToken", "else");
            var elseCase = parseExpr(expectation);
            yield new IfExpr(pred, then, elseCase, start.endWith(elseCase.span()));
          }
          case LET -> {
            var start = next().span();
            expect(Token.LPAREN, "unexpectedToken", "(");
            var param = parseParameter();
            expect(Token.ASSIGN, "unexpectedToken", "=");
            var bindExpr = parseExpr(")");
            expect(Token.RPAREN, "unexpectedToken", ")");
            var exp = parseExpr(expectation);
            yield new LetExpr(param, bindExpr, exp, start.endWith(exp.span()));
          }
          case TRUE -> new BoolLiteralExpr(true, next().span);
          case FALSE -> new BoolLiteralExpr(false, next().span);
          case INT, HEX, BIN, OCT -> {
            var tk = next();
            yield new IntLiteralExpr(tk.text(lexer), tk.span);
          }
          case FLOAT -> {
            var tk = next();
            yield new FloatLiteralExpr(tk.text(lexer), tk.span);
          }
          case STRING_START -> parseSingleLineStringLiteralExpr();
          case STRING_MULTI_START -> parseMultiLineStringLiteralExpr();
          case IDENTIFIER -> {
            var identifier = parseIdentifier();
            if (lookahead == Token.LPAREN
                && !precededBySemicolon
                && _lookahead.newLinesBetween == 0) {
              var args = parseArgumentList();
              yield new UnqualifiedAccessExpr(
                  identifier, args, identifier.span().endWith(args.span()));
            } else {
              yield new UnqualifiedAccessExpr(identifier, null, identifier.span());
            }
          }
          case EOF ->
              throw new ParserError(
                  ErrorMessages.create("unexpectedEndOfFile"), prev.span.stopSpan().move(1));
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
  private Expr parseExprRest(Expr expr) {
    // non null
    if (lookahead == Token.NON_NULL) {
      var end = next().span;
      var res = new NonNullExpr(expr, expr.span().endWith(end));
      return parseExprRest(res);
    }
    // amends
    if (lookahead == Token.LBRACE) {
      if (expr instanceof ParenthesizedExpr
          || expr instanceof AmendsExpr
          || expr instanceof NewExpr) {
        var body = parseObjectBody();
        return parseExprRest(new AmendsExpr(expr, body, expr.span().endWith(body.span())));
      }
      throw parserError("unexpectedCurlyProbablyAmendsExpression", expr.text(lexer.getSource()));
    }
    // qualified access
    if (lookahead == Token.DOT || lookahead == Token.QDOT) {
      var isNullable = next().token == Token.QDOT;
      var identifier = parseIdentifier();
      ArgumentList argumentList = null;
      if (lookahead == Token.LPAREN && !precededBySemicolon && _lookahead.newLinesBetween == 0) {
        argumentList = parseArgumentList();
      }
      var lastSpan = argumentList != null ? argumentList.span() : identifier.span();
      var res =
          new QualifiedAccessExpr(
              expr, identifier, isNullable, argumentList, expr.span().endWith(lastSpan));
      return parseExprRest(res);
    }
    // subscript (needs to be in the same line as the expression)
    if (lookahead == Token.LBRACK && !precededBySemicolon && _lookahead.newLinesBetween == 0) {
      next();
      var exp = parseExpr("]");
      var end = expect(Token.RBRACK, "unexpectedToken", "]").span;
      var res = new SubscriptExpr(expr, exp, expr.span().endWith(end));
      return parseExprRest(res);
    }
    return expr;
  }

  private Expr parseSingleLineStringLiteralExpr() {
    var start = next();
    var parts = new ArrayList<StringPart>();
    var builder = new StringBuilder();
    var startSpan = spanLookahead;
    var end = spanLookahead;
    while (lookahead != Token.STRING_END) {
      switch (lookahead) {
        case STRING_PART -> {
          var tk = next();
          end = tk.span;
          builder.append(tk.text(lexer));
        }
        case STRING_ESCAPE_NEWLINE -> {
          end = next().span;
          builder.append('\n');
        }
        case STRING_ESCAPE_TAB -> {
          end = next().span;
          builder.append('\t');
        }
        case STRING_ESCAPE_QUOTE -> {
          end = next().span;
          builder.append('"');
        }
        case STRING_ESCAPE_BACKSLASH -> {
          end = next().span;
          builder.append('\\');
        }
        case STRING_ESCAPE_RETURN -> {
          end = next().span;
          builder.append('\r');
        }
        case STRING_ESCAPE_UNICODE -> {
          var tk = next();
          end = tk.span;
          builder.append(parseUnicodeEscape(tk));
        }
        case INTERPOLATION_START -> {
          var istart = next().span;
          if (!builder.isEmpty()) {
            assert startSpan != null;
            parts.add(new StringChars(builder.toString(), startSpan.endWith(end)));
            builder = new StringBuilder();
          }
          var exp = parseExpr(")");
          end = expect(Token.RPAREN, "unexpectedToken", ")").span;
          parts.add(new StringPart.StringInterpolation(exp, istart.endWith(end)));
          startSpan = spanLookahead;
        }
        case EOF -> {
          var delimiter = new StringBuilder(start.text(lexer)).reverse().toString();
          throw parserError("missingDelimiter", delimiter);
        }
      }
    }
    if (!builder.isEmpty()) {
      parts.add(new StringChars(builder.toString(), startSpan.endWith(end)));
    }
    end = next().span;
    return new SingleLineStringLiteralExpr(parts, start.span, end, start.span.endWith(end));
  }

  private Expr parseMultiLineStringLiteralExpr() {
    var start = next();
    var stringTokens = new ArrayList<TempNode>();
    while (lookahead != Token.STRING_END) {
      switch (lookahead) {
        case STRING_PART,
            STRING_NEWLINE,
            STRING_ESCAPE_NEWLINE,
            STRING_ESCAPE_TAB,
            STRING_ESCAPE_QUOTE,
            STRING_ESCAPE_BACKSLASH,
            STRING_ESCAPE_RETURN,
            STRING_ESCAPE_UNICODE ->
            stringTokens.add(new TempNode(next(), null));
        case INTERPOLATION_START -> {
          var istart = next();
          var exp = parseExpr(")");
          var end = expect(Token.RPAREN, "unexpectedToken", ")").span;
          var interpolation = new StringPart.StringInterpolation(exp, istart.span.endWith(end));
          stringTokens.add(new TempNode(null, interpolation));
        }
        case EOF -> {
          var delimiter = new StringBuilder(start.text(lexer)).reverse().toString();
          throw parserError("missingDelimiter", delimiter);
        }
      }
    }
    var end = next().span;
    var fullSpan = start.span.endWith(end);
    var parts = validateMultiLineString(stringTokens, fullSpan);
    return new MultiLineStringLiteralExpr(parts, start.span, end, fullSpan);
  }

  private List<StringPart> validateMultiLineString(List<TempNode> nodes, Span span) {
    var firstNode = nodes.isEmpty() ? null : nodes.get(0);
    if (firstNode == null
        || firstNode.token == null
        || firstNode.token.token != Token.STRING_NEWLINE) {
      var errorSpan = firstNode == null ? span : firstNode.span();
      throw new ParserError(ErrorMessages.create("stringContentMustBeginOnNewLine"), errorSpan);
    }
    // only contains a newline
    if (nodes.size() == 1) {
      return List.of(new StringChars("", firstNode.span()));
    }
    var indent = getCommonIndent(nodes, span);
    return renderString(nodes, indent);
  }

  @SuppressWarnings("DataFlowIssue")
  private List<StringPart> renderString(List<TempNode> nodes, String commonIndent) {
    var parts = new ArrayList<StringPart>();
    var builder = new StringBuilder();
    var endOffset = nodes.get(nodes.size() - 1).token.token == Token.STRING_NEWLINE ? 1 : 2;
    var isNewLine = true;
    Span start = null;
    Span end = null;
    for (var i = 1; i < nodes.size() - endOffset; i++) {
      var node = nodes.get(i);
      if (node.node != null) {
        if (!builder.isEmpty()) {
          parts.add(new StringChars(builder.toString(), start.endWith(end)));
          builder = new StringBuilder();
          start = null;
        }
        parts.add(node.node);
      } else {
        var token = node.token;
        assert token != null;
        if (start == null) {
          start = token.span;
        }
        end = token.span;
        switch (token.token) {
          case STRING_NEWLINE -> {
            builder.append('\n');
            isNewLine = true;
          }
          case STRING_PART -> {
            var text = token.text(lexer);
            if (isNewLine) {
              if (text.startsWith(commonIndent)) {
                builder.append(text, commonIndent.length(), text.length());
              } else {
                var actualIndent = getLeadingIndentCount(text);
                var textSpan = token.span.move(actualIndent).grow(-actualIndent);
                throw new ParserError(
                    ErrorMessages.create("stringIndentationMustMatchLastLine"), textSpan);
              }
            } else {
              builder.append(text);
            }
            isNewLine = false;
          }
          default -> {
            if (isNewLine && !commonIndent.isEmpty()) {
              throw new ParserError(
                  ErrorMessages.create("stringIndentationMustMatchLastLine"), token.span);
            }
            builder.append(getEscapeText(token));
            isNewLine = false;
          }
        }
      }
    }
    if (!builder.isEmpty()) {
      parts.add(new StringChars(builder.toString(), start.endWith(end)));
    }
    return parts;
  }

  @SuppressWarnings("DuplicatedCode")
  private Expr parseFunctionLiteralOrParenthesized(Span start) {
    var identifier = parseIdentifier();
    return switch (lookahead) {
      case COMMA -> {
        next();
        var params = new ArrayList<Parameter>();
        params.add(new TypedIdentifier(identifier, null, identifier.span()));
        params.addAll(parseListOfParameter(Token.COMMA));
        var endParen = expect(Token.RPAREN, "unexpectedToken2", ",", ")").span;
        var paramList = new ParameterList(params, start.endWith(endParen));
        expect(Token.ARROW, "unexpectedToken", "->");
        var expr = parseExpr();
        yield new FunctionLiteralExpr(paramList, expr, start.endWith(expr.span()));
      }
      case COLON -> {
        var typeAnnotation = parseTypeAnnotation();
        var params = new ArrayList<Parameter>();
        params.add(
            new TypedIdentifier(
                identifier, typeAnnotation, identifier.span().endWith(typeAnnotation.span())));
        if (lookahead == Token.COMMA) {
          next();
          params.addAll(parseListOfParameter(Token.COMMA));
        }
        var endParen = expect(Token.RPAREN, "unexpectedToken2", ",", ")").span;
        var paramList = new ParameterList(params, start.endWith(endParen));
        expect(Token.ARROW, "unexpectedToken", "->");
        var expr = parseExpr(")");
        yield new FunctionLiteralExpr(paramList, expr, start.endWith(expr.span()));
      }
      case RPAREN -> {
        // still not sure
        var end = next().span;
        if (lookahead == Token.ARROW) {
          next();
          var expr = parseExpr();
          var params = new ArrayList<Parameter>();
          params.add(new TypedIdentifier(identifier, null, identifier.span()));
          var paramList = new ParameterList(params, start.endWith(end));
          yield new FunctionLiteralExpr(paramList, expr, start.endWith(expr.span()));
        } else {
          var exp = new UnqualifiedAccessExpr(identifier, null, identifier.span());
          yield new ParenthesizedExpr(exp, start.endWith(end));
        }
      }
      default -> {
        // this is an expression
        backtrack();
        var expr = parseExpr(")");
        var end = expect(Token.RPAREN, "unexpectedToken", ")").span;
        yield new ParenthesizedExpr(expr, start.endWith(end));
      }
    };
  }

  private FunctionLiteralExpr parseFunctionLiteral(Span start) {
    // the open parens is already parsed
    var params = parseListOfParameter(Token.COMMA);
    var endParen = expect(Token.RPAREN, "unexpectedToken2", ",", ")").span;
    var paramList = new ParameterList(params, start.endWith(endParen));
    expect(Token.ARROW, "unexpectedToken", "->");
    var expr = parseExpr();
    return new FunctionLiteralExpr(paramList, expr, start.endWith(expr.span()));
  }

  private Type parseType() {
    return parseType(null);
  }

  private Type parseType(@Nullable String expectation) {
    var defaultIndex = -1;
    Span start = null;
    if (lookahead == Token.STAR) {
      defaultIndex = 0;
      start = next().span;
    }
    var first = parseTypeAtom(expectation);
    if (start == null) {
      start = first.span();
    }

    if (lookahead != Token.UNION) {
      if (defaultIndex == 0) {
        throw new ParserError(ErrorMessages.create("notAUnion"), start.endWith(first.span()));
      }
      return first;
    }

    var types = new ArrayList<Type>();
    types.add(first);
    var end = start;
    var i = 1;
    while (lookahead == Token.UNION) {
      next();
      if (lookahead == Token.STAR) {
        if (defaultIndex != -1) {
          throw parserError("multipleUnionDefaults");
        }
        defaultIndex = i;
        next();
      }
      var type = parseTypeAtom(expectation);
      types.add(type);
      end = type.span();
      i++;
    }
    return new Type.UnionType(types, defaultIndex, start.endWith(end));
  }

  private Type parseTypeAtom(@Nullable String expectation) {
    Type typ;
    switch (lookahead) {
      case UNKNOWN -> typ = new Type.UnknownType(next().span);
      case NOTHING -> typ = new Type.NothingType(next().span);
      case MODULE -> typ = new Type.ModuleType(next().span);
      case LPAREN -> {
        var tk = next();
        var children = new ArrayList<Node>();
        Span end;
        if (lookahead == Token.RPAREN) {
          end = next().span;
        } else {
          children.addAll(parseListOf(Token.COMMA, () -> parseType(")")));
          end = expect(Token.RPAREN, "unexpectedToken2", ",", ")").span;
        }
        if (lookahead == Token.ARROW || children.size() > 1) {
          expect(Token.ARROW, "unexpectedToken", "->");
          var ret = parseType(expectation);
          children.add(ret);
          typ = new Type.FunctionType(children, tk.span.endWith(ret.span()));
        } else {
          typ = new ParenthesizedType((Type) children.get(0), tk.span.endWith(end));
        }
      }
      case IDENTIFIER -> {
        var start = spanLookahead;
        var name = parseQualifiedIdentifier();
        var end = name.span();
        TypeArgumentList typeArgumentList = null;
        if (lookahead == Token.LT) {
          typeArgumentList = parseTypeArgumentList();
          end = typeArgumentList.span();
        }
        typ = new DeclaredType(name, typeArgumentList, start.endWith(end));
      }
      case STRING_START -> {
        var str = parseStringConstant();
        typ = new StringConstantType(str, str.span());
      }
      default -> {
        var text = _lookahead.text(lexer);
        if (expectation != null) {
          throw parserError("unexpectedTokenForType2", text, expectation);
        }
        throw parserError("unexpectedTokenForType", text);
      }
    }

    if (typ instanceof Type.FunctionType) return typ;
    return parseTypeEnd(typ);
  }

  private Type parseTypeEnd(Type type) {
    // nullable types
    if (lookahead == Token.QUESTION) {
      var end = spanLookahead;
      next();
      var res = new Type.NullableType(type, type.span().endWith(end));
      return parseTypeEnd(res);
    }
    // constrained types: have to start in the same line as the type
    if (lookahead == Token.LPAREN && !precededBySemicolon && _lookahead.newLinesBetween == 0) {
      next();
      var constraints = parseListOf(Token.COMMA, () -> parseExpr(")"));
      var end = expect(Token.RPAREN, "unexpectedToken2", ",", ")").span;
      var children = new ArrayList<Node>(constraints.size() + 1);
      children.add(type);
      children.addAll(constraints);
      var res = new Type.ConstrainedType(children, type.span().endWith(end));
      return parseTypeEnd(res);
    }
    return type;
  }

  private Annotation parseAnnotation() {
    var start = next().span;
    var children = new ArrayList<Node>(2);
    var type = parseType();
    children.add(type);
    ObjectBody body = null;
    var end = type.span();
    if (lookahead == Token.LBRACE) {
      body = parseObjectBody();
      end = body.span();
    }
    children.add(body);
    return new Annotation(children, start.endWith(end));
  }

  private Parameter parseParameter() {
    if (lookahead == Token.UNDERSCORE) {
      var span = next().span;
      return new Parameter.Underscore(span);
    }
    return parseTypedIdentifier();
  }

  private Modifier parseModifier() {
    return switch (lookahead) {
      case EXTERNAL -> new Modifier(Modifier.ModifierValue.EXTERNAL, next().span);
      case ABSTRACT -> new Modifier(Modifier.ModifierValue.ABSTRACT, next().span);
      case OPEN -> new Modifier(Modifier.ModifierValue.OPEN, next().span);
      case LOCAL -> new Modifier(Modifier.ModifierValue.LOCAL, next().span);
      case HIDDEN -> new Modifier(Modifier.ModifierValue.HIDDEN, next().span);
      case FIXED -> new Modifier(Modifier.ModifierValue.FIXED, next().span);
      case CONST -> new Modifier(Modifier.ModifierValue.CONST, next().span);
      default -> throw PklBugException.unreachableCode();
    };
  }

  private List<Modifier> parseModifierList() {
    var modifiers = new ArrayList<Modifier>();
    while (lookahead.isModifier()) {
      modifiers.add(parseModifier());
    }
    return modifiers;
  }

  private ParameterList parseParameterList() {
    var start = expect(Token.LPAREN, "unexpectedToken", "(").span;
    Span end;
    List<Parameter> args = new ArrayList<>();
    if (lookahead == Token.RPAREN) {
      end = next().span;
    } else {
      args = parseListOfParameter(Token.COMMA);
      end = expect(Token.RPAREN, "unexpectedToken2", ",", ")").span;
    }
    return new ParameterList(args, start.endWith(end));
  }

  private List<ObjectBody> parseBodyList() {
    if (lookahead != Token.LBRACE) {
      throw parserError("unexpectedToken2", _lookahead.text(lexer), "{", "=");
    }
    var bodies = new ArrayList<ObjectBody>();
    do {
      bodies.add(parseObjectBody());
    } while (lookahead == Token.LBRACE);
    return bodies;
  }

  private TypeParameterList parseTypeParameterList() {
    var start = expect(Token.LT, "unexpectedToken", "<").span;
    var pars = parseListOf(Token.COMMA, this::parseTypeParameter);
    var end = expect(Token.GT, "unexpectedToken2", ",", ">").span;
    return new TypeParameterList(pars, start.endWith(end));
  }

  private TypeArgumentList parseTypeArgumentList() {
    var start = expect(Token.LT, "unexpectedToken", "<").span;
    var pars = parseListOf(Token.COMMA, this::parseType);
    var end = expect(Token.GT, "unexpectedToken2", ",", ">").span;
    return new TypeArgumentList(pars, start.endWith(end));
  }

  private ArgumentList parseArgumentList() {
    var start = expect(Token.LPAREN, "unexpectedToken", "(").span;
    if (lookahead == Token.RPAREN) {
      return new ArgumentList(new ArrayList<>(), start.endWith(next().span));
    }
    var exprs = parseListOf(Token.COMMA, this::parseExpr);
    var end = expect(Token.RPAREN, "unexpectedToken2", ",", ")").span;
    return new ArgumentList(exprs, start.endWith(end));
  }

  private TypeParameter parseTypeParameter() {
    TypeParameter.Variance variance = null;
    var start = spanLookahead;
    if (lookahead == Token.IN) {
      next();
      variance = TypeParameter.Variance.IN;
    } else if (lookahead == Token.OUT) {
      next();
      variance = TypeParameter.Variance.OUT;
    }
    var identifier = parseIdentifier();
    return new TypeParameter(variance, identifier, start.endWith(identifier.span()));
  }

  private TypedIdentifier parseTypedIdentifier() {
    var identifier = parseIdentifier();
    TypeAnnotation typeAnnotation = null;
    var end = identifier.span();
    if (lookahead == Token.COLON) {
      typeAnnotation = parseTypeAnnotation();
      end = typeAnnotation.span();
    }
    return new TypedIdentifier(identifier, typeAnnotation, identifier.span().endWith(end));
  }

  private TypeAnnotation parseTypeAnnotation() {
    var start = expect(Token.COLON, "unexpectedToken", ":").span;
    var type = parseType();
    return new TypeAnnotation(type, start.endWith(type.span()));
  }

  private Identifier parseIdentifier() {
    if (lookahead != Token.IDENTIFIER) {
      if (lookahead.isKeyword()) {
        throw parserError("keywordNotAllowedHere", lookahead.text());
      }
      throw parserError("unexpectedToken", _lookahead.text(lexer), "identifier");
    }
    var tk = next();
    var text = tk.text(lexer);
    return new Identifier(text, tk.span);
  }

  private StringConstant parseStringConstant() {
    var start = spanLookahead;
    var startTk = expect(Token.STRING_START, "unexpectedToken", "\"");
    var builder = new StringBuilder();
    while (lookahead != Token.STRING_END) {
      switch (lookahead) {
        case STRING_PART -> builder.append(next().text(lexer));
        case STRING_ESCAPE_NEWLINE -> {
          next();
          builder.append('\n');
        }
        case STRING_ESCAPE_TAB -> {
          next();
          builder.append('\t');
        }
        case STRING_ESCAPE_QUOTE -> {
          next();
          builder.append('"');
        }
        case STRING_ESCAPE_BACKSLASH -> {
          next();
          builder.append('\\');
        }
        case STRING_ESCAPE_RETURN -> {
          next();
          builder.append('\r');
        }
        case STRING_ESCAPE_UNICODE -> builder.append(parseUnicodeEscape(next()));
        case EOF -> {
          var delimiter = new StringBuilder(startTk.text(lexer)).reverse().toString();
          throw parserError("missingDelimiter", delimiter);
        }
        case INTERPOLATION_START -> throw parserError("interpolationInConstant");
        // the lexer makes sure we only get the above tokens inside a string
        default -> throw PklBugException.unreachableCode();
      }
    }
    var end = next().span;
    return new StringConstant(builder.toString(), start.endWith(end));
  }

  private String getEscapeText(FullToken tk) {
    return switch (tk.token) {
      case STRING_ESCAPE_NEWLINE -> "\n";
      case STRING_ESCAPE_QUOTE -> "\"";
      case STRING_ESCAPE_BACKSLASH -> "\\";
      case STRING_ESCAPE_TAB -> "\t";
      case STRING_ESCAPE_RETURN -> "\r";
      case STRING_ESCAPE_UNICODE -> parseUnicodeEscape(tk);
      default -> throw PklBugException.unreachableCode();
    };
  }

  private String parseUnicodeEscape(FullToken tk) {
    var text = tk.text(lexer);
    var lastIndex = text.length() - 1;
    var startIndex = text.indexOf('{', 2);
    try {
      var codepoint = Integer.parseInt(text.substring(startIndex + 1, lastIndex), 16);
      return Character.toString(codepoint);
    } catch (NumberFormatException e) {
      throw new ParserError(
          ErrorMessages.create("invalidUnicodeEscapeSequence", text, text.substring(0, startIndex)),
          tk.span);
    }
  }

  private String getCommonIndent(List<TempNode> nodes, Span span) {
    var lastNode = nodes.get(nodes.size() - 1);
    if (lastNode.token == null) {
      throw new ParserError(
          ErrorMessages.create("closingStringDelimiterMustBeginOnNewLine"), lastNode.span());
    }
    if (lastNode.token.token == Token.STRING_NEWLINE) return "";
    var beforeLast = nodes.get(nodes.size() - 2);
    if (beforeLast.token != null && beforeLast.token.token == Token.STRING_NEWLINE) {
      var indent = getTrailingIndent(lastNode);
      if (indent != null) {
        return indent;
      }
    }
    throw new ParserError(ErrorMessages.create("closingStringDelimiterMustBeginOnNewLine"), span);
  }

  private @Nullable String getTrailingIndent(TempNode node) {
    var token = node.token;
    if (token == null || token.token != Token.STRING_PART) return null;
    var text = token.text(lexer);
    for (var i = 0; i < text.length(); i++) {
      var ch = text.charAt(i);
      if (ch != ' ' && ch != '\t') return null;
    }
    return text;
  }

  private int getLeadingIndentCount(String text) {
    if (text.isEmpty()) return 0;
    for (var i = 0; i < text.length(); i++) {
      var ch = text.charAt(i);
      if (ch != ' ' && ch != '\t') {
        return i;
      }
    }
    return text.length();
  }

  private record TempNode(
      @Nullable FullToken token, @Nullable StringPart.StringInterpolation node) {
    Span span() {
      if (token != null) return token.span;
      assert node != null;
      return node.span();
    }
  }

  private FullToken expect(Token type, String errorKey, Object... messageArgs) {
    if (lookahead != type) {
      var span = spanLookahead;
      if (lookahead == Token.EOF || _lookahead.newLinesBetween > 0) {
        // don't point at the EOF or the next line, but at the end of the last token
        span = prev.span.stopSpan().move(1);
      }
      var args = messageArgs;
      if (errorKey.startsWith("unexpectedToken")) {
        args = new Object[messageArgs.length + 1];
        args[0] = lookahead == Token.EOF ? "EOF" : _lookahead.text(lexer);
        System.arraycopy(messageArgs, 0, args, 1, messageArgs.length);
      }
      throw new ParserError(ErrorMessages.create(errorKey, args), span);
    }
    return next();
  }

  private <T> List<T> parseListOf(Token separator, Supplier<T> parser) {
    var res = new ArrayList<T>();
    res.add(parser.get());
    while (lookahead == separator) {
      next();
      res.add(parser.get());
    }
    return res;
  }

  private List<Parameter> parseListOfParameter(Token separator) {
    var res = new ArrayList<Parameter>();
    res.add(parseParameter());
    while (lookahead == separator) {
      next();
      res.add(parseParameter());
    }
    return res;
  }

  private ParserError parserError(String messageKey, Object... args) {
    return new ParserError(ErrorMessages.create(messageKey, args), spanLookahead);
  }

  private record MemberHeader(
      @Nullable DocComment docComment, List<Annotation> annotations, List<Modifier> modifiers) {
    boolean isNotEmpty() {
      return !(docComment == null && annotations.isEmpty() && modifiers.isEmpty());
    }

    @SuppressWarnings("DataFlowIssue")
    @Nullable
    Span span() {
      return span(null);
    }

    Span span(Span or) {
      if (docComment != null) {
        return docComment.span();
      }
      if (!annotations().isEmpty()) {
        return annotations.get(0).span();
      }
      if (!modifiers().isEmpty()) {
        return modifiers.get(0).span();
      }
      return or;
    }

    Span modifierSpan(Span or) {
      if (!modifiers.isEmpty()) {
        return modifiers.get(0).span();
      }
      return or;
    }
  }

  private FullToken next() {
    if (backtracking) {
      backtracking = false;
      lookahead = _lookahead.token;
      spanLookahead = _lookahead.span;
      return prev;
    }
    prev = _lookahead;
    _lookahead = forceNext();
    lookahead = _lookahead.token;
    spanLookahead = _lookahead.span;
    return prev;
  }

  private FullToken forceNext() {
    var tk = lexer.next();
    precededBySemicolon = false;
    while (tk == Token.LINE_COMMENT
        || tk == Token.BLOCK_COMMENT
        || tk == Token.SEMICOLON
        || tk == Token.SHEBANG) {
      precededBySemicolon = precededBySemicolon || tk == Token.SEMICOLON;
      tk = lexer.next();
    }
    return new FullToken(
        tk, lexer.span(), lexer.sCursor, lexer.cursor - lexer.sCursor, lexer.newLinesBetween);
  }

  // Like next, but don't ignore comments
  private FullToken nextComment() {
    prev = _lookahead;
    _lookahead = forceNextComment();
    lookahead = _lookahead.token;
    spanLookahead = _lookahead.span;
    return prev;
  }

  private FullToken forceNextComment() {
    var tk = lexer.next();
    precededBySemicolon = false;
    while (tk == Token.SEMICOLON) {
      precededBySemicolon = true;
      tk = lexer.next();
    }
    return new FullToken(
        tk, lexer.span(), lexer.sCursor, lexer.cursor - lexer.sCursor, lexer.newLinesBetween);
  }

  /**
   * Backtrack to the previous token.
   *
   * <p>Can only backtrack one token.
   */
  private void backtrack() {
    assert !backtracking;
    lookahead = prev.token;
    spanLookahead = prev.span;
    backtracking = true;
  }

  private void ensureEmptyHeaders(MemberHeader header, String messageArg) {
    if (header.isNotEmpty()) {
      throw new ParserError(
          ErrorMessages.create("wrongHeaders", messageArg), header.span(spanLookahead));
    }
  }

  private record FullToken(
      Token token, Span span, int textOffset, int textSize, int newLinesBetween) {
    String text(Lexer lexer) {
      return lexer.textFor(textOffset, textSize);
    }
  }
}
