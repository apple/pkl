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
package org.pkl.core.newparser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.pkl.core.newparser.cst.Annotation;
import org.pkl.core.newparser.cst.ArgumentList;
import org.pkl.core.newparser.cst.ClassBody;
import org.pkl.core.newparser.cst.ClassMethod;
import org.pkl.core.newparser.cst.ClassPropertyEntry;
import org.pkl.core.newparser.cst.Clazz;
import org.pkl.core.newparser.cst.DocComment;
import org.pkl.core.newparser.cst.Expr;
import org.pkl.core.newparser.cst.Expr.NullLiteral;
import org.pkl.core.newparser.cst.Expr.OperatorExpr;
import org.pkl.core.newparser.cst.Expr.Parenthesized;
import org.pkl.core.newparser.cst.ExtendsOrAmendsDecl;
import org.pkl.core.newparser.cst.Ident;
import org.pkl.core.newparser.cst.Import;
import org.pkl.core.newparser.cst.Modifier;
import org.pkl.core.newparser.cst.Module;
import org.pkl.core.newparser.cst.ModuleDecl;
import org.pkl.core.newparser.cst.ObjectBody;
import org.pkl.core.newparser.cst.ObjectMemberNode;
import org.pkl.core.newparser.cst.Operator;
import org.pkl.core.newparser.cst.Parameter;
import org.pkl.core.newparser.cst.Parameter.TypedIdent;
import org.pkl.core.newparser.cst.ParameterList;
import org.pkl.core.newparser.cst.QualifiedIdent;
import org.pkl.core.newparser.cst.Type;
import org.pkl.core.newparser.cst.Type.DeclaredType;
import org.pkl.core.newparser.cst.Type.DefaultUnionType;
import org.pkl.core.newparser.cst.Type.ParenthesizedType;
import org.pkl.core.newparser.cst.Type.StringConstantType;
import org.pkl.core.newparser.cst.TypeAlias;
import org.pkl.core.newparser.cst.TypeAnnotation;
import org.pkl.core.newparser.cst.TypeParameter;
import org.pkl.core.newparser.cst.TypeParameterList;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.Nullable;

public class Parser {

  private Lexer lexer;
  private Token lookahead;
  private Span spanLookahead;
  private boolean backtracking = false;
  private FullToken prev;
  private FullToken _lookahead;
  private final List<Comment> comments = new ArrayList<>();
  private boolean precededBySemicolon = false;

  public Parser() {}

  public List<Comment> getComments() {
    return comments;
  }

  private void init(String source) {
    this.lexer = new Lexer(source);
    _lookahead = forceNext();
    lookahead = _lookahead.token;
    spanLookahead = _lookahead.span;
  }

  public Module parseModule(String source) {
    init(source);
    if (lookahead == Token.EOF) {
      return new Module(
          null, List.of(), List.of(), List.of(), List.of(), List.of(), new Span(0, 0));
    }
    var start = spanLookahead;
    Span end = null;
    var header = parseEntryHeader();
    QualifiedIdent moduleName = null;
    ModuleDecl moduleDecl = null;
    var imports = new ArrayList<Import>();

    if (lookahead == Token.MODULE) {
      moduleName = parseModuleNameDecl();
      end = moduleName.span();
    }
    var extendsOrAmendsDecl = parseExtendsAmendsDecl();
    if (extendsOrAmendsDecl != null) {
      end = extendsOrAmendsDecl.span();
    }
    if (moduleName != null || extendsOrAmendsDecl != null) {
      moduleDecl =
          new ModuleDecl(
              header.docComment,
              header.annotations,
              header.modifiers,
              moduleName,
              extendsOrAmendsDecl,
              start.endWith(end));
      header = null;
    }
    // imports
    while (lookahead == Token.IMPORT || lookahead == Token.IMPORT_STAR) {
      if (header != null && !header.isEmpty()) {
        throw new ParserError(
            "Imports cannot have doc comments nor annotations or modifiers", spanLookahead);
      }
      var _import = parseImportDecl();
      imports.add(_import);
      end = _import.span();
    }

    // entries
    var classes = new ArrayList<Clazz>();
    var typeAliases = new ArrayList<TypeAlias>();
    var props = new ArrayList<ClassPropertyEntry>();
    var methods = new ArrayList<ClassMethod>();
    if (header != null && !header.isEmpty()) {
      end = parseModuleEntry(header, classes, typeAliases, props, methods);
    }

    while (lookahead != Token.EOF) {
      header = parseEntryHeader();
      end = parseModuleEntry(header, classes, typeAliases, props, methods);
    }
    assert end != null;
    return new Module(
        moduleDecl, imports, classes, typeAliases, props, methods, start.endWith(end));
  }

  private QualifiedIdent parseModuleNameDecl() {
    expect(Token.MODULE, "Expected `module`");
    return parseQualifiedIdent();
  }

  private QualifiedIdent parseQualifiedIdent() {
    var idents = parseListOf(Token.DOT, this::parseIdent);
    return new QualifiedIdent(idents);
  }

  private @Nullable ExtendsOrAmendsDecl parseExtendsAmendsDecl() {
    if (lookahead == Token.EXTENDS) {
      var tk = next().span;
      var url = parseStringConstant();
      return new ExtendsOrAmendsDecl(url, ExtendsOrAmendsDecl.Type.EXTENDS, tk.endWith(url.span()));
    }
    if (lookahead == Token.AMENDS) {
      var tk = next().span;
      var url = parseStringConstant();
      return new ExtendsOrAmendsDecl(url, ExtendsOrAmendsDecl.Type.AMENDS, tk.endWith(url.span()));
    }
    return null;
  }

  private Import parseImportDecl() {
    Span start;
    boolean isGlob = false;
    if (lookahead == Token.IMPORT_STAR) {
      start = next().span;
      isGlob = true;
    } else {
      start = expect(Token.IMPORT, "Expected `import` or `import*`").span;
    }
    var str = parseStringConstant();
    var end = str.span();
    Ident alias = null;
    if (lookahead == Token.AS) {
      next();
      alias = parseIdent();
      end = alias.span();
    }
    return new Import(str, isGlob, alias, start.endWith(end));
  }

  private EntryHeader parseEntryHeader() {
    DocComment docComment = null;
    var annotations = new ArrayList<Annotation>();
    var modifiers = new ArrayList<Modifier>();
    if (lookahead == Token.DOC_COMMENT) {
      var docSpanStart = spanLookahead;
      var docSpanEnd = spanLookahead;
      while (lookahead == Token.DOC_COMMENT) {
        docSpanEnd = next().span;
      }
      docComment = new DocComment(docSpanStart.endWith(docSpanEnd));
    }
    while (lookahead == Token.AT) {
      annotations.add(parseAnnotation());
    }
    while (lookahead.isModifier()) {
      modifiers.add(parseModifier());
    }
    return new EntryHeader(docComment, annotations, modifiers);
  }

  private Span parseModuleEntry(
      EntryHeader header,
      List<Clazz> classes,
      List<TypeAlias> typeAliases,
      List<ClassPropertyEntry> properties,
      List<ClassMethod> methods) {
    switch (lookahead) {
      case IDENT -> {
        var node = parseClassProperty(header);
        properties.add(node);
        return node.span();
      }
      case TYPE_ALIAS -> {
        var node = parseTypeAlias(header);
        typeAliases.add(node);
        return node.span();
      }
      case CLASS -> {
        var node = parseClass(header);
        classes.add(node);
        return node.span();
      }
      case FUNCTION -> {
        var node = parseClassMethod(header);
        methods.add(node);
        return node.span();
      }
      default ->
          throw new ParserError(
              "Invalid token at position. Valid ones are `typealias`, `class`, `function` or <identifier>.",
              spanLookahead);
    }
  }

  private TypeAlias parseTypeAlias(EntryHeader header) {
    var start = expect(Token.TYPE_ALIAS, "Expected `typealias`").span;
    var startSpan = header.span(start);
    var ident = parseIdent();
    TypeParameterList typePars = null;
    if (lookahead == Token.LT) {
      typePars = parseTypeParameterList();
    }
    expect(Token.ASSIGN, "Expected `=`");
    var type = parseType();
    return new TypeAlias(
        header.docComment,
        header.annotations,
        header.modifiers,
        ident,
        typePars,
        type,
        startSpan.endWith(type.span()));
  }

  private Clazz parseClass(EntryHeader header) {
    var start = expect(Token.CLASS, "Expected `class`").span;
    var startSpan = header.span(start);
    var name = parseIdent();
    TypeParameterList typePars = null;
    var end = name.span();
    if (lookahead == Token.LT) {
      typePars = parseTypeParameterList();
      end = typePars.span();
    }
    Type superClass = null;
    if (lookahead == Token.EXTENDS) {
      next();
      superClass = parseType();
      end = superClass.span();
    }

    if (lookahead == Token.LBRACE) {
      var body = parseClassBody();
      return new Clazz(
          header.docComment,
          header.annotations,
          header.modifiers,
          name,
          typePars,
          superClass,
          body,
          startSpan.endWith(body.span()));
    } else {
      return new Clazz(
          header.docComment,
          header.annotations,
          header.modifiers,
          name,
          typePars,
          superClass,
          null,
          startSpan.endWith(end));
    }
  }

  private ClassBody parseClassBody() {
    var start = expect(Token.LBRACE, "Expected `{`").span;
    var props = new ArrayList<ClassPropertyEntry>();
    var methods = new ArrayList<ClassMethod>();
    while (lookahead != Token.RBRACE) {
      var entryHeader = parseEntryHeader();
      if (lookahead == Token.FUNCTION) {
        methods.add(parseClassMethod(entryHeader));
      } else {
        props.add(parseClassProperty(entryHeader));
      }
    }
    var end = expect(Token.RBRACE, "Expected `}`").span;
    return new ClassBody(props, methods, start.endWith(end));
  }

  private ClassPropertyEntry parseClassProperty(EntryHeader header) {
    var name = parseIdent();
    var start = header.span(name.span());
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
        throw new ParserError("Expected expression but got object body", spanLookahead);
      }
      while (lookahead == Token.LBRACE) {
        bodies.add(parseObjectBody());
      }
    }
    if (expr != null) {
      return new ClassPropertyEntry.ClassPropertyExpr(
          header.docComment,
          header.annotations,
          header.modifiers,
          name,
          typeAnnotation,
          expr,
          start.endWith(expr.span()));
    }
    if (!bodies.isEmpty()) {
      return new ClassPropertyEntry.ClassPropertyBody(
          header.docComment,
          header.annotations,
          header.modifiers,
          name,
          bodies,
          start.endWith(bodies.get(bodies.size() - 1).span()));
    }
    if (typeAnnotation == null) {
      throw new ParserError("Expected type annotation, assignment or amending", spanLookahead);
    }
    return new ClassPropertyEntry.ClassProperty(
        header.docComment,
        header.annotations,
        header.modifiers,
        name,
        typeAnnotation,
        start.endWith(typeAnnotation.span()));
  }

  private ClassMethod parseClassMethod(EntryHeader header) {
    var func = expect(Token.FUNCTION, "Expected `function` keyword").span;
    var start = header.span(func);
    var headerSpanStart = header.modifierSpan(func);
    var name = parseIdent();
    TypeParameterList typePars = null;
    if (lookahead == Token.LT) {
      typePars = parseTypeParameterList();
    }
    var parameterList = parseParameterList();
    var end = parameterList.span();
    var endHeader = end;
    TypeAnnotation typeAnnotation = null;
    if (lookahead == Token.COLON) {
      typeAnnotation = parseTypeAnnotation();
      end = typeAnnotation.span();
      endHeader = end;
    }
    Expr expr = null;
    if (lookahead == Token.ASSIGN) {
      next();
      expr = parseExpr();
      end = expr.span();
    }
    return new ClassMethod(
        header.docComment,
        header.annotations,
        header.modifiers,
        name,
        typePars,
        parameterList,
        typeAnnotation,
        expr,
        headerSpanStart.endWith(endHeader),
        start.endWith(end));
  }

  private ObjectBody parseObjectBody() {
    var start = expect(Token.LBRACE, "Expected `{`").span;
    List<Parameter> params = new ArrayList<>();
    List<ObjectMemberNode> members = new ArrayList<>();
    if (lookahead == Token.RBRACE) {
      return new ObjectBody(params, members, start.endWith(next().span));
    } else if (lookahead == Token.UNDERSCORE) {
      // it's a parameter
      params = parseListOfParameter(Token.COMMA);
      expect(Token.ARROW, "Expected `->`");
    } else if (lookahead == Token.IDENT) {
      // not sure what it is yet
      var ident = parseIdent();
      if (lookahead == Token.ARROW) {
        // it's a parameter
        next();
        params.add(new TypedIdent(ident, null, ident.span()));
      } else if (lookahead == Token.COMMA) {
        // it's a parameter
        backtrack();
        params.addAll(parseListOfParameter(Token.COMMA));
        expect(Token.ARROW, "Expected `->`");
      } else if (lookahead == Token.COLON) {
        // still not sure
        var colon = next().span;
        var type = parseType();
        var typeAnnotation = new TypeAnnotation(type, colon.endWith(type.span()));
        if (lookahead == Token.COMMA) {
          // it's a parameter
          next();
          params.add(new Parameter.TypedIdent(ident, typeAnnotation, ident.span()));
          params.addAll(parseListOfParameter(Token.COMMA));
          expect(Token.ARROW, "Expected `->`");
        } else if (lookahead == Token.ARROW) {
          // it's a parameter
          next();
          params.add(new Parameter.TypedIdent(ident, typeAnnotation, ident.span()));
        } else {
          // it's a member
          expect(Token.ASSIGN, "Expected `=`");
          var expr = parseExpr();
          members.add(
              new ObjectMemberNode.ObjectProperty(
                  new ArrayList<>(),
                  ident,
                  typeAnnotation,
                  expr,
                  ident.span().endWith(expr.span())));
        }
      } else {
        // member
        backtrack();
      }
    }

    // members
    while (lookahead != Token.RBRACE) {
      members.add(parseObjectMember());
    }
    var end = expect(Token.RBRACE, "Expected `}`").span;
    return new ObjectBody(params, members, start.endWith(end));
  }

  private ObjectMemberNode parseObjectMember() {
    return switch (lookahead) {
      case IDENT -> {
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
      case LPRED -> parseMemberPredicate();
      case LBRACK -> parseObjectEntry();
      case SPREAD, QSPREAD -> parseObjectSpread();
      case WHEN -> parseWhenGenerator();
      case FOR -> parseForGenerator();
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

  private ObjectMemberNode.ObjectElement parseObjectElement() {
    var expr = parseExpr();
    return new ObjectMemberNode.ObjectElement(expr, expr.span());
  }

  private ObjectMemberNode parseObjectProperty(@Nullable List<Modifier> modifiers) {
    var start = spanLookahead;
    var allModifiers = modifiers;
    if (allModifiers == null) {
      allModifiers = parseModifierList();
    }
    var ident = parseIdent();
    TypeAnnotation typeAnnotation = null;
    if (lookahead == Token.COLON) {
      typeAnnotation = parseTypeAnnotation();
    }
    if (typeAnnotation != null || lookahead == Token.ASSIGN) {
      expect(Token.ASSIGN, "Expected `=`");
      var expr = parseExpr();
      return new ObjectMemberNode.ObjectProperty(
          allModifiers, ident, typeAnnotation, expr, start.endWith(expr.span()));
    }
    var bodies = parseBodyList();
    var end = bodies.get(bodies.size() - 1).span();
    return new ObjectMemberNode.ObjectBodyProperty(allModifiers, ident, bodies, start.endWith(end));
  }

  private ObjectMemberNode.ObjectMethod parseObjectMethod(List<Modifier> modifiers) {
    var start = spanLookahead;
    expect(Token.FUNCTION, "Expected `function`");
    var ident = parseIdent();
    TypeParameterList params = null;
    if (lookahead == Token.LT) {
      params = parseTypeParameterList();
    }
    var args = parseParameterList();
    TypeAnnotation typeAnnotation = null;
    if (lookahead == Token.COLON) {
      typeAnnotation = parseTypeAnnotation();
    }
    expect(Token.ASSIGN, "Expected `=`");
    var expr = parseExpr();
    return new ObjectMemberNode.ObjectMethod(
        modifiers, ident, params, args, typeAnnotation, expr, start.endWith(expr.span()));
  }

  private ObjectMemberNode parseMemberPredicate() {
    var start = next().span;
    var pred = parseExpr();
    var firstBrack = expect(Token.RBRACK, "Expected `]]`").span;
    var secondbrack = expect(Token.RBRACK, "Expected `]]`").span;
    if (firstBrack.charIndex() != secondbrack.charIndex() - 1) {
      // There shouldn't be any whitespace between the first and second ']'.
      throw new ParserError(ErrorMessages.create("wrongDelimiter", "]]", "]"), firstBrack);
    }
    if (lookahead == Token.ASSIGN) {
      next();
      var expr = parseExpr();
      return new ObjectMemberNode.MemberPredicate(pred, expr, start.endWith(expr.span()));
    }
    var bodies = parseBodyList();
    var end = bodies.get(bodies.size() - 1).span();
    return new ObjectMemberNode.MemberPredicateBody(pred, bodies, start.endWith(end));
  }

  private ObjectMemberNode parseObjectEntry() {
    var start = expect(Token.LBRACK, "Expected `[`").span;
    var key = parseExpr();
    expect(Token.RBRACK, "Expected `]`");
    if (lookahead == Token.ASSIGN) {
      next();
      var expr = parseExpr();
      return new ObjectMemberNode.ObjectEntry(key, expr, start.endWith(expr.span()));
    }
    var bodies = parseBodyList();
    var end = bodies.get(bodies.size() - 1).span();
    return new ObjectMemberNode.ObjectEntryBody(key, bodies, start.endWith(end));
  }

  private ObjectMemberNode.ObjectSpread parseObjectSpread() {
    if (lookahead != Token.SPREAD && lookahead != Token.QSPREAD) {
      throw new ParserError("Expected `...` or `...?`", spanLookahead);
    }
    var peek = next();
    boolean isNullable = peek.token == Token.QSPREAD;
    var expr = parseExpr();
    return new ObjectMemberNode.ObjectSpread(expr, isNullable, peek.span.endWith(expr.span()));
  }

  private ObjectMemberNode.WhenGenerator parseWhenGenerator() {
    var start = expect(Token.WHEN, "Expected `when`").span;
    expect(Token.LPAREN, "Expected `(`");
    var pred = parseExpr();
    expect(Token.RPAREN, "Expected `)`");
    var body = parseObjectBody();
    var end = body.span();
    ObjectBody elseBody = null;
    if (lookahead == Token.ELSE) {
      next();
      elseBody = parseObjectBody();
      end = elseBody.span();
    }
    return new ObjectMemberNode.WhenGenerator(pred, body, elseBody, start.endWith(end));
  }

  private ObjectMemberNode.ForGenerator parseForGenerator() {
    var start = expect(Token.FOR, "Expected `for`").span;
    expect(Token.LPAREN, "Expected `(`");
    var par1 = parseParameter();
    Parameter par2 = null;
    if (lookahead == Token.COMMA) {
      next();
      par2 = parseParameter();
    }
    expect(Token.IN, "Expected `in`");
    var expr = parseExpr();
    expect(Token.RPAREN, "Expected `)`");
    var body = parseObjectBody();
    return new ObjectMemberNode.ForGenerator(par1, par2, expr, body, start.endWith(body.span()));
  }

  @SuppressWarnings("DuplicatedCode")
  private Expr parseExpr() {
    List<Expr> exprs = new ArrayList<>();
    exprs.add(parseExprAtom());
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
          if (!precededBySemicolon && !_lookahead.newLineBetween) {
            exprs.add(new OperatorExpr(op, next().span));
            exprs.add(parseExprAtom());
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
          var ident = parseIdent();
          ArgumentList argumentList = null;
          if (lookahead == Token.LPAREN && !precededBySemicolon && !_lookahead.newLineBetween) {
            argumentList = parseArgumentList();
          }
          var lastSpan = argumentList != null ? argumentList.span() : ident.span();
          exprs.add(
              new Expr.QualifiedAccess(
                  expr, ident, isNullable, argumentList, expr.span().endWith(lastSpan)));
        }
        default -> {
          exprs.add(new OperatorExpr(op, next().span));
          exprs.add(parseExprAtom());
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

  private Expr parseExprAtom() {
    var expr =
        switch (lookahead) {
          case THIS -> new Expr.This(next().span);
          case OUTER -> new Expr.Outer(next().span);
          case MODULE -> new Expr.Module(next().span);
          case NULL -> new NullLiteral(next().span);
          case THROW -> {
            var start = next().span;
            expect(Token.LPAREN, "Expected `(`");
            var exp = parseExpr();
            var end = expect(Token.RPAREN, "Expected `)`").span;
            yield new Expr.Throw(exp, start.endWith(end));
          }
          case TRACE -> {
            var start = next().span;
            expect(Token.LPAREN, "Expected `(`");
            var exp = parseExpr();
            var end = expect(Token.RPAREN, "Expected `)`").span;
            yield new Expr.Trace(exp, start.endWith(end));
          }
          case IMPORT -> {
            var start = next().span;
            expect(Token.LPAREN, "Expected `(`");
            var strConst = parseStringConstant();
            var end = expect(Token.RPAREN, "Expected `)`").span;
            yield new Expr.ImportExpr(strConst, false, start.endWith(end));
          }
          case IMPORT_STAR -> {
            var start = next().span;
            expect(Token.LPAREN, "Expected `(`");
            var strConst = parseStringConstant();
            var end = expect(Token.RPAREN, "Expected `)`").span;
            yield new Expr.ImportExpr(strConst, true, start.endWith(end));
          }
          case READ -> {
            var start = next().span;
            expect(Token.LPAREN, "Expected `(`");
            var exp = parseExpr();
            var end = expect(Token.RPAREN, "Expected `)`").span;
            yield new Expr.Read(exp, start.endWith(end));
          }
          case READ_STAR -> {
            var start = next().span;
            expect(Token.LPAREN, "Expected `(`");
            var exp = parseExpr();
            var end = expect(Token.RPAREN, "Expected `)`").span;
            yield new Expr.ReadGlob(exp, start.endWith(end));
          }
          case READ_QUESTION -> {
            var start = next().span;
            expect(Token.LPAREN, "Expected `(`");
            var exp = parseExpr();
            var end = expect(Token.RPAREN, "Expected `)`").span;
            yield new Expr.ReadNull(exp, start.endWith(end));
          }
          case NEW -> {
            var start = next().span;
            Type type = null;
            if (lookahead != Token.LBRACE) {
              type = parseType();
            }
            var body = parseObjectBody();
            yield new Expr.New(type, body, start.endWith(body.span()));
          }
          case MINUS -> {
            var start = next().span;
            // calling `parseExprAtom` here and not `parseExpr` because
            // unary minus has higher precendence than binary operators
            var exp = parseExprAtom();
            yield new Expr.UnaryMinus(exp, start.endWith(exp.span()));
          }
          case NOT -> {
            var start = next().span;
            // calling `parseExprAtom` here and not `parseExpr` because
            // logical not has higher precendence than binary operators
            var exp = parseExprAtom();
            yield new Expr.LogicalNot(exp, start.endWith(exp.span()));
          }
          case LPAREN -> {
            // can be function literal or parenthesized expression
            var start = next().span;
            yield switch (lookahead) {
              case UNDERSCORE -> parseFunctionLiteral(start);
              case IDENT -> parseFunctionLiteralOrParenthesized(start);
              case RPAREN -> {
                var endParen = next().span;
                var paramList = new ParameterList(List.of(), start.endWith(endParen));
                expect(Token.ARROW, "Expected `->`");
                var exp = parseExpr();
                yield new Expr.FunctionLiteral(paramList, exp, start.endWith(exp.span()));
              }
              default -> {
                // expression
                var exp = parseExpr();
                var end = expect(Token.RPAREN, "Expected `)`").span;
                yield new Parenthesized(exp, start.endWith(end));
              }
            };
          }
          case SUPER -> {
            var start = next().span;
            if (lookahead == Token.DOT) {
              next();
              var ident = parseIdent();
              if (lookahead == Token.LPAREN) {
                var args = parseArgumentList();
                yield new Expr.SuperAccess(ident, args, start.endWith(args.span()));
              } else {
                yield new Expr.SuperAccess(ident, null, start.endWith(ident.span()));
              }
            } else {
              expect(Token.LBRACK, "Expected `[`");
              var exp = parseExpr();
              var end = expect(Token.RBRACK, "Expected `]`").span;
              yield new Expr.SuperSubscript(exp, start.endWith(end));
            }
          }
          case IF -> {
            var start = next().span;
            expect(Token.LPAREN, "Expected `(`");
            var pred = parseExpr();
            expect(Token.RPAREN, "Expected `)`");
            var then = parseExpr();
            expect(Token.ELSE, "Expected `else`");
            var elseCase = parseExpr();
            yield new Expr.If(pred, then, elseCase, start.endWith(elseCase.span()));
          }
          case LET -> {
            var start = next().span();
            expect(Token.LPAREN, "Expected `(`");
            var param = parseParameter();
            expect(Token.ASSIGN, "Expected `=`");
            var bindExpr = parseExpr();
            expect(Token.RPAREN, "Expected `)`");
            var exp = parseExpr();
            yield new Expr.Let(param, bindExpr, exp, start.endWith(exp.span()));
          }
          case TRUE -> new Expr.BoolLiteral(true, next().span);
          case FALSE -> new Expr.BoolLiteral(false, next().span);
          case INT, HEX, BIN, OCT -> {
            var tk = next();
            var text = remove_(lexer.textFor(tk.textOffset, tk.textSize));
            yield new Expr.IntLiteral(text, tk.span);
          }
          case FLOAT -> {
            var tk = next();
            var text = remove_(lexer.textFor(tk.textOffset, tk.textSize));
            yield new Expr.FloatLiteral(text, tk.span);
          }
          case STRING_START, STRING_MULTI_START -> {
            var start = next();
            var parts = new ArrayList<Expr>();
            while (lookahead != Token.STRING_END) {
              if (lookahead == Token.STRING_PART) {
                var tk = next();
                var text = lexer.textFor(tk.textOffset, tk.textSize);
                if (!text.isEmpty()) {
                  parts.add(new Expr.StringConstant(text, tk.span));
                }
              } else {
                parts.add(parseExpr());
              }
            }
            var end = expect(Token.STRING_END, "noError").span;
            if (start.token == Token.STRING_START) {
              yield new Expr.InterpolatedString(parts, start.span, end, start.span.endWith(end));
            } else {
              yield new Expr.InterpolatedMultiString(
                  parts, start.span, end, start.span.endWith(end));
            }
          }
          case IDENT -> {
            var ident = parseIdent();
            if (lookahead == Token.LPAREN && !precededBySemicolon && !_lookahead.newLineBetween) {
              var args = parseArgumentList();
              yield new Expr.UnqualifiedAccess(ident, args, ident.span().endWith(args.span()));
            } else {
              yield new Expr.UnqualifiedAccess(ident, null, ident.span());
            }
          }
          default -> throw new ParserError("Invalid token for expression", spanLookahead);
        };
    return parseExprRest(expr);
  }

  @SuppressWarnings("DuplicatedCode")
  private Expr parseExprRest(Expr expr) {
    // non null
    if (lookahead == Token.NON_NULL) {
      var end = next().span;
      var res = new Expr.NonNull(expr, expr.span().endWith(end));
      return parseExprRest(res);
    }
    // amends
    if (lookahead == Token.LBRACE) {
      if (expr instanceof Parenthesized
          || expr instanceof Expr.Amends
          || expr instanceof Expr.New) {
        var body = parseObjectBody();
        return parseExprRest(new Expr.Amends(expr, body, expr.span().endWith(body.span())));
      }
      throw new ParserError(
          ErrorMessages.create(
              "unexpectedCurlyProbablyAmendsExpression", expr.text(lexer.getSource())),
          expr.span());
    }
    // qualified access
    if (lookahead == Token.DOT || lookahead == Token.QDOT) {
      var isNullable = next().token == Token.QDOT;
      var ident = parseIdent();
      ArgumentList argumentList = null;
      if (lookahead == Token.LPAREN && !precededBySemicolon && !_lookahead.newLineBetween) {
        argumentList = parseArgumentList();
      }
      var lastSpan = argumentList != null ? argumentList.span() : ident.span();
      var res =
          new Expr.QualifiedAccess(
              expr, ident, isNullable, argumentList, expr.span().endWith(lastSpan));
      return parseExprRest(res);
    }
    // subscript (needs to be in the same line as the expression)
    if (lookahead == Token.LBRACK && !precededBySemicolon && !_lookahead.newLineBetween) {
      next();
      var exp = parseExpr();
      expect(Token.RBRACK, "Expected `]`");
      var res = new Expr.Subscript(expr, exp, expr.span().endWith(exp.span()));
      return parseExprRest(res);
    }
    return expr;
  }

  private Expr parseFunctionLiteralOrParenthesized(Span start) {
    var ident = parseIdent();
    return switch (lookahead) {
      case COMMA -> {
        next();
        var params = new ArrayList<Parameter>();
        params.add(new Parameter.TypedIdent(ident, null, ident.span()));
        params.addAll(parseListOfParameter(Token.COMMA));
        var endParen = expect(Token.RPAREN, "Expected `)`").span;
        var paramList = new ParameterList(params, start.endWith(endParen));
        expect(Token.ARROW, "Expected `->`");
        var expr = parseExpr();
        yield new Expr.FunctionLiteral(paramList, expr, start.endWith(expr.span()));
      }
      case COLON -> {
        var typeAnnotation = parseTypeAnnotation();
        var params = new ArrayList<Parameter>();
        params.add(new Parameter.TypedIdent(ident, typeAnnotation, ident.span()));
        if (lookahead == Token.COMMA) {
          next();
          params.addAll(parseListOfParameter(Token.COMMA));
        }
        var endParen = expect(Token.RPAREN, "Expected `)`").span;
        var paramList = new ParameterList(params, start.endWith(endParen));
        expect(Token.ARROW, "Expected `->`");
        var expr = parseExpr();
        yield new Expr.FunctionLiteral(paramList, expr, start.endWith(expr.span()));
      }
      case RPAREN -> {
        // still not sure
        var end = next().span;
        if (lookahead == Token.ARROW) {
          next();
          var expr = parseExpr();
          var params = new ArrayList<Parameter>();
          params.add(new Parameter.TypedIdent(ident, null, ident.span()));
          var paramList = new ParameterList(params, start.endWith(end));
          yield new Expr.FunctionLiteral(paramList, expr, start.endWith(expr.span()));
        } else {
          var exp = new Expr.UnqualifiedAccess(ident, null, ident.span());
          yield new Parenthesized(exp, start.endWith(end));
        }
      }
      default -> {
        // this is an expression
        backtrack();
        var expr = parseExpr();
        var end = expect(Token.RPAREN, "Expected `)`").span;
        yield new Parenthesized(expr, start.endWith(end));
      }
    };
  }

  private Expr.FunctionLiteral parseFunctionLiteral(Span start) {
    // the open parens is already parsed
    var params = parseListOfParameter(Token.COMMA);
    var endParen = expect(Token.RPAREN, "Expected `)`").span;
    var paramList = new ParameterList(params, start.endWith(endParen));
    expect(Token.ARROW, "Expected `->`");
    var expr = parseExpr();
    return new Expr.FunctionLiteral(paramList, expr, start.endWith(expr.span()));
  }

  private Type parseType() {
    return parseType(false);
  }

  private Type parseType(boolean shortCircuit) {
    Type typ;
    switch (lookahead) {
      case UNKNOWN -> typ = new Type.UnknownType(next().span);
      case NOTHING -> typ = new Type.NothingType(next().span);
      case MODULE -> typ = new Type.ModuleType(next().span);
      case LPAREN -> {
        var tk = next();
        var types = new ArrayList<Type>();
        Span end;
        if (lookahead == Token.RPAREN) {
          end = next().span;
        } else {
          types.addAll(parseListOf(Token.COMMA, this::parseType));
          end = expect(Token.RPAREN, "Expected `)`").span;
        }
        if (lookahead == Token.ARROW || types.size() > 1) {
          expect(Token.ARROW, "Expected `->`");
          var ret = parseType();
          typ = new Type.FunctionType(types, ret, tk.span.endWith(end));
        } else {
          typ = new ParenthesizedType(types.get(0), tk.span.endWith(end));
        }
      }
      case STAR -> {
        var tk = next();
        var type = parseType(true);
        typ = new DefaultUnionType(type, tk.span.endWith(type.span()));
      }
      case IDENT -> {
        var start = spanLookahead;
        var name = parseQualifiedIdent();
        var types = new ArrayList<Type>();
        var end = name.span();
        if (lookahead == Token.LT) {
          next();
          types.addAll(parseListOf(Token.COMMA, this::parseType));
          end = expect(Token.GT, "Expected `>`").span;
        }
        typ = new DeclaredType(name, types, start.endWith(end));
      }
      case STRING_START -> {
        var str = parseStringConstant();
        typ = new StringConstantType(str.getStr(), str.span());
      }
      default -> throw new ParserError("Invalid token for type: " + lookahead, spanLookahead);
    }

    if (typ instanceof Type.FunctionType) return typ;
    return parseTypeEnd(typ, shortCircuit);
  }

  private Type parseTypeEnd(Type type, boolean shortCircuit) {
    // nullable types
    if (lookahead == Token.QUESTION) {
      var end = spanLookahead;
      next();
      var res = new Type.NullableType(type, type.span().endWith(end));
      return parseTypeEnd(res, shortCircuit);
    }
    // constrained types: have to start in the same line as the type
    if (lookahead == Token.LPAREN && !precededBySemicolon && !_lookahead.newLineBetween) {
      next();
      var constraints = parseListOf(Token.COMMA, this::parseExpr);
      var end = expect(Token.RPAREN, "Expected `)`").span;
      var res = new Type.ConstrainedType(type, constraints, type.span().endWith(end));
      return parseTypeEnd(res, shortCircuit);
    }
    // union types
    if (lookahead == Token.UNION && !shortCircuit) {
      next();
      // union types are left associative
      var right = parseType(true);
      var res = new Type.UnionType(type, right, type.span().endWith(right.span()));
      return parseTypeEnd(res, false);
    }
    return type;
  }

  private Annotation parseAnnotation() {
    var start = next().span;
    var name = parseQualifiedIdent();
    ObjectBody body = null;
    var end = name.span();
    if (lookahead == Token.LBRACE) {
      body = parseObjectBody();
      end = body.span();
    }
    return new Annotation(name, body, start.endWith(end));
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
      default -> throw new ParserError("Expected modifier but got " + lookahead, spanLookahead);
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
    var start = expect(Token.LPAREN, "Expected `(`").span;
    Span end;
    List<Parameter> args = new ArrayList<>();
    if (lookahead == Token.RPAREN) {
      end = next().span;
    } else {
      args = parseListOfParameter(Token.COMMA);
      end = expect(Token.RPAREN, "Expected `)`").span;
    }
    return new ParameterList(args, start.endWith(end));
  }

  private List<ObjectBody> parseBodyList() {
    var bodies = new ArrayList<ObjectBody>();
    do {
      bodies.add(parseObjectBody());
    } while (lookahead == Token.LBRACE);
    return bodies;
  }

  private TypeParameterList parseTypeParameterList() {
    var start = expect(Token.LT, "Expected `<`").span;
    var pars = parseListOf(Token.COMMA, this::parseTypeParameter);
    var end = expect(Token.GT, "Expected `>`").span;
    return new TypeParameterList(pars, start.endWith(end));
  }

  private ArgumentList parseArgumentList() {
    var start = expect(Token.LPAREN, "Expected `(").span;
    if (lookahead == Token.RPAREN) {
      return new ArgumentList(new ArrayList<>(), start.endWith(next().span));
    }
    var exprs = parseListOf(Token.COMMA, this::parseExpr);
    var end = expect(Token.RPAREN, "Expected `)`").span;
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
    var ident = parseIdent();
    return new TypeParameter(variance, ident, start.endWith(ident.span()));
  }

  private Parameter.TypedIdent parseTypedIdentifier() {
    var ident = parseIdent();
    TypeAnnotation typeAnnotation = null;
    var end = ident.span();
    if (lookahead == Token.COLON) {
      typeAnnotation = parseTypeAnnotation();
      end = typeAnnotation.span();
    }
    return new Parameter.TypedIdent(ident, typeAnnotation, ident.span().endWith(end));
  }

  private TypeAnnotation parseTypeAnnotation() {
    var start = expect(Token.COLON, "Expected `:`").span;
    var type = parseType();
    return new TypeAnnotation(type, start.endWith(type.span()));
  }

  private Ident parseIdent() {
    if (lookahead != Token.IDENT) {
      throw new ParserError("Expected identifier", spanLookahead);
    }
    var tk = next();
    return new Ident(lexer.textFor(tk.textOffset, tk.textSize), tk.span);
  }

  private Expr.StringConstant parseStringConstant() {
    var start = spanLookahead;
    expect(Token.STRING_START, "Expected string start");
    if (lookahead == Token.STRING_PART) {
      var tk = next();
      var text = lexer.textFor(tk.textOffset, tk.textSize);
      var end = spanLookahead;
      expect(Token.STRING_END, "Expected string end");
      return new Expr.StringConstant(text, start.endWith(end));
    } else {
      throw new ParserError("Expected constant string", spanLookahead);
    }
  }

  private FullToken expect(Token type, String errorMsg) {
    if (lookahead != type) {
      throw new ParserError(errorMsg, spanLookahead);
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

  private record EntryHeader(
      @Nullable DocComment docComment, List<Annotation> annotations, List<Modifier> modifiers) {
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isEmpty() {
      return docComment == null && annotations.isEmpty() && modifiers.isEmpty();
    }

    Span span(Span or) {
      Span start = null;
      Span end = null;
      if (!annotations().isEmpty()) {
        start = annotations.get(0).span();
        end = annotations.get(annotations.size() - 1).span();
      }
      if (!modifiers().isEmpty()) {
        if (start == null) start = modifiers.get(0).span();
        end = modifiers.get(modifiers.size() - 1).span();
        return start.endWith(end);
      }
      if (end != null) {
        return start.endWith(end);
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
    var prev = tk;
    while (tk == Token.LINE_COMMENT || tk == Token.BLOCK_COMMENT || tk == Token.SEMICOLON) {
      if (tk != Token.SEMICOLON) {
        comments.add(new Comment(lexer.text(), tk, lexer.span()));
      }
      prev = tk;
      tk = lexer.next();
    }
    precededBySemicolon = prev == Token.SEMICOLON;
    return new FullToken(
        tk,
        lexer.span(),
        lexer.sCursor,
        lexer.cursor - lexer.sCursor - lexer.textOffset,
        lexer.newLineBetween);
  }

  // backtrack to the previous token
  private void backtrack() {
    lookahead = prev.token;
    spanLookahead = prev.span;
    backtracking = true;
  }

  private String remove_(String number) {
    var builder = new StringBuilder(number.length());
    for (var i = 0; i < number.length(); i++) {
      var ch = number.charAt(i);
      if (ch == '_') continue;
      builder.append(ch);
    }
    return builder.toString();
  }

  private record FullToken(
      Token token, Span span, int textOffset, int textSize, boolean newLineBetween) {
    String text(Lexer lexer) {
      return lexer.textFor(textOffset, textSize);
    }
  }
}
