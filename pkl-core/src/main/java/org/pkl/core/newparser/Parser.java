/*
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
package org.pkl.core.newparser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.pkl.core.Pair;
import org.pkl.core.newparser.cst.AmendsDecl;
import org.pkl.core.newparser.cst.Annotation;
import org.pkl.core.newparser.cst.ClassEntry;
import org.pkl.core.newparser.cst.Clazz;
import org.pkl.core.newparser.cst.DocComment;
import org.pkl.core.newparser.cst.Expr;
import org.pkl.core.newparser.cst.Expr.OperatorExpr;
import org.pkl.core.newparser.cst.Expr.Parenthesized;
import org.pkl.core.newparser.cst.ExtendsDecl;
import org.pkl.core.newparser.cst.Ident;
import org.pkl.core.newparser.cst.Import;
import org.pkl.core.newparser.cst.Modifier;
import org.pkl.core.newparser.cst.Module;
import org.pkl.core.newparser.cst.ModuleDecl;
import org.pkl.core.newparser.cst.ModuleEntry;
import org.pkl.core.newparser.cst.ObjectBody;
import org.pkl.core.newparser.cst.ObjectMember;
import org.pkl.core.newparser.cst.Operator;
import org.pkl.core.newparser.cst.Parameter;
import org.pkl.core.newparser.cst.Parameter.TypedIdent;
import org.pkl.core.newparser.cst.QualifiedIdent;
import org.pkl.core.newparser.cst.Type;
import org.pkl.core.newparser.cst.Type.DeclaredType;
import org.pkl.core.newparser.cst.Type.DefaultUnionType;
import org.pkl.core.newparser.cst.Type.ParenthesizedType;
import org.pkl.core.newparser.cst.Type.StringConstantType;
import org.pkl.core.newparser.cst.TypeAlias;
import org.pkl.core.newparser.cst.TypeParameter;
import org.pkl.core.util.Nullable;

public class Parser {

  private final Lexer lexer;
  private Token lookahead;
  private Span spanLookahead;
  private boolean backtracking = false;
  private FullToken prev;
  private FullToken _lookahead;
  private final List<Comment> comments;
  private boolean precededBySemicolon = false;

  public Parser(Lexer lexer) {
    this.lexer = lexer;
    comments = new ArrayList<>();
    _lookahead = forceNext();
    lookahead = _lookahead.token;
    spanLookahead = _lookahead.span;
  }

  public List<Comment> getComments() {
    return comments;
  }

  public @Nullable Module parseModule() {
    if (lookahead == Token.EOF) return null;
    var start = spanLookahead;
    Span end = null;
    var header = parseEntryHeader();
    QualifiedIdent moduleName = null;
    ExtendsDecl extendsDecl = null;
    AmendsDecl amendsDecl = null;
    ModuleDecl moduleDecl = null;
    var imports = new ArrayList<Import>();

    if (lookahead == Token.MODULE) {
      moduleName = parseModuleNameDecl();
      end = moduleName.span();
    }
    if (lookahead == Token.EXTENDS) {
      extendsDecl = parseExtendsDecl();
      end = extendsDecl.span();
    }
    if (lookahead == Token.AMENDS) {
      if (extendsDecl != null) {
        throw new ParserError(
            "Cannot have both extends and amends clause in the same module", spanLookahead);
      }
      amendsDecl = parseAmendsDecl();
      end = amendsDecl.span();
    }
    if (moduleName != null || extendsDecl != null || amendsDecl != null) {
      moduleDecl =
          new ModuleDecl(
              header.docComment,
              header.annotations,
              header.modifiers,
              moduleName,
              extendsDecl,
              amendsDecl,
              start.endWith(end));
      header = null;
    }
    // imports
    while (lookahead == Token.IMPORT || lookahead == Token.IMPORT_STAR) {
      if (header != null && !header.isEmpty()) {
        throw new ParserError(
            "Imports cannot have doc comments nor annotations or modifiers", spanLookahead);
      }
      imports.add(parseImportDecl());
    }

    // entries
    var entries = new ArrayList<ModuleEntry>();
    if (header != null && !header.isEmpty()) {
      entries.add(parseModuleEntry(header));
    }

    while (lookahead != Token.EOF) {
      header = parseEntryHeader();
      entries.add(parseModuleEntry(header));
    }
    return new Module(moduleDecl, imports, entries, start.endWith(spanLookahead));
  }

  private QualifiedIdent parseModuleNameDecl() {
    expect(Token.MODULE, "Expected `module`");
    return parseQualifiedIdent();
  }

  private QualifiedIdent parseQualifiedIdent() {
    var idents = parseListOf(Token.DOT, this::parseIdent);
    return new QualifiedIdent(idents);
  }

  private ExtendsDecl parseExtendsDecl() {
    var tk = expect(Token.EXTENDS, "Expected `extends`").span;
    var url = parseStringConstant();
    return new ExtendsDecl(url.value, tk.endWith(url.span));
  }

  private AmendsDecl parseAmendsDecl() {
    var tk = expect(Token.AMENDS, "noError").span;
    var url = parseStringConstant();
    return new AmendsDecl(url.value, tk.endWith(url.span));
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
    var end = str.span;
    Ident alias = null;
    if (lookahead == Token.AS) {
      next();
      alias = parseIdent();
      end = alias.span();
    }
    return new Import(str.value, isGlob, alias, start.endWith(end));
  }

  private EntryHeader parseEntryHeader() {
    DocComment docComment = null;
    var annotations = new ArrayList<Annotation>();
    var modifiers = new ArrayList<Modifier>();
    if (lookahead == Token.DOC_COMMENT) {
      var builder = new StringBuilder();
      var docSpanStart = spanLookahead;
      Span docSpanEnd = null;
      while (lookahead == Token.DOC_COMMENT) {
        var tk = next();
        if (!builder.isEmpty()) {
          builder.append('\n');
          docSpanEnd = tk.span;
        }
        builder.append(tk.text(lexer));
      }
      if (docSpanEnd == null) {
        docSpanEnd = docSpanStart;
      }
      docComment = new DocComment(builder.toString(), docSpanStart.endWith(docSpanEnd));
    }
    while (lookahead == Token.AT) {
      annotations.add(parseAnnotation());
    }
    while (lookahead.isModifier()) {
      modifiers.add(parseModifier());
    }
    return new EntryHeader(docComment, annotations, modifiers);
  }

  private ModuleEntry parseModuleEntry(EntryHeader header) {
    return switch (lookahead) {
      case IDENT -> parseClassProperty(header);
      case TYPE_ALIAS -> parseTypeAlias(header);
      case CLASS -> parseClass(header);
      case FUNCTION -> parseClassMethod(header);
      default ->
          throw new ParserError(
              "Invalid token at position. Valid ones are `typealias`, `class`, `function` or <identifier>.",
              spanLookahead);
    };
  }

  private TypeAlias parseTypeAlias(EntryHeader header) {
    var start = expect(Token.TYPE_ALIAS, "Expected `typealias`").span;
    var startSpan = header.span(start);
    var ident = parseIdent();
    List<TypeParameter> typePars = new ArrayList<>();
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
        startSpan.endWith(type.getSpan()));
  }

  private Clazz parseClass(EntryHeader header) {
    var start = expect(Token.CLASS, "Expected `class`").span;
    var startSpan = header.span(start);
    var name = parseIdent();
    List<TypeParameter> typePars = new ArrayList<>();
    if (lookahead == Token.LT) {
      typePars = parseTypeParameterList();
    }
    QualifiedIdent superClass = null;
    if (lookahead == Token.EXTENDS) {
      next();
      superClass = parseQualifiedIdent();
    }

    var entries = new ArrayList<ClassEntry>();
    if (lookahead == Token.LBRACE) {
      next();
      while (lookahead != Token.RBRACE) {
        var entryHeader = parseEntryHeader();
        if (lookahead == Token.FUNCTION) {
          entries.add(parseClassMethod(entryHeader));
        } else {
          entries.add(parseClassProperty(entryHeader));
        }
      }

      var end = next().span;
      return new Clazz(
          header.docComment,
          header.annotations,
          header.modifiers,
          name,
          typePars,
          superClass,
          entries,
          startSpan.endWith(end));
    } else {
      var end = name.span();
      if (superClass != null) {
        end = superClass.span();
      } else if (!typePars.isEmpty()) {
        end = typePars.get(typePars.size() - 1).span();
      }
      return new Clazz(
          header.docComment,
          header.annotations,
          header.modifiers,
          name,
          typePars,
          superClass,
          entries,
          startSpan.endWith(end));
    }
  }

  private ClassEntry parseClassProperty(EntryHeader header) {
    var name = parseIdent();
    var start = header.span(name.span());
    Type type = null;
    Expr expr = null;
    var bodies = new ArrayList<ObjectBody>();
    if (lookahead == Token.COLON) {
      next();
      type = parseType();
    }
    if (lookahead == Token.ASSIGN) {
      next();
      expr = parseExpr();
    } else if (lookahead == Token.LBRACE) {
      if (type != null) {
        throw new ParserError("Expected expression but got object body", spanLookahead);
      }
      while (lookahead == Token.LBRACE) {
        bodies.add(parseObjectBody());
      }
    }
    if (expr != null) {
      return new ClassEntry.ClassPropertyExpr(
          header.docComment,
          header.annotations,
          header.modifiers,
          name,
          type,
          expr,
          start.endWith(expr.getSpan()));
    }
    if (!bodies.isEmpty()) {
      return new ClassEntry.ClassPropertyBody(
          header.docComment,
          header.annotations,
          header.modifiers,
          name,
          bodies,
          start.endWith(bodies.get(bodies.size() - 1).span()));
    }
    if (type == null) {
      throw new ParserError("Expected type annotation, assignment or amending", spanLookahead);
    }
    return new ClassEntry.ClassProperty(
        header.docComment,
        header.annotations,
        header.modifiers,
        name,
        type,
        start.endWith(type.getSpan()));
  }

  private ClassEntry.ClassMethod parseClassMethod(EntryHeader header) {
    var func = expect(Token.FUNCTION, "Expected `function` keyword");
    var start = header.span(func.span());
    var name = parseIdent();
    Span end;
    List<TypeParameter> typePars = new ArrayList<>();
    if (lookahead == Token.LT) {
      typePars = parseTypeParameterList();
    }
    expect(Token.LPAREN, "Expected `(`");
    List<Parameter> args = new ArrayList<>();
    if (lookahead == Token.RPAREN) {
      end = next().span;
    } else {
      args = parseListOfParameter(Token.COMMA);
      end = expect(Token.RPAREN, "Expected `)`").span;
    }
    Type type = null;
    if (lookahead == Token.COLON) {
      next();
      type = parseType();
      end = type.getSpan();
    }
    Expr expr = null;
    if (lookahead == Token.ASSIGN) {
      next();
      expr = parseExpr();
      end = expr.getSpan();
    }
    return new ClassEntry.ClassMethod(
        header.docComment,
        header.annotations,
        header.modifiers,
        name,
        typePars,
        args,
        type,
        expr,
        start.endWith(end));
  }

  private ObjectBody parseObjectBody() {
    var start = expect(Token.LBRACE, "Expected `{`").span;
    List<Parameter> params = new ArrayList<>();
    List<ObjectMember> members = new ArrayList<>();
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
        next();
        var type = parseType();
        if (lookahead == Token.COMMA) {
          // it's a parameter
          next();
          params.add(new Parameter.TypedIdent(ident, type, ident.span()));
          params.addAll(parseListOfParameter(Token.COMMA));
          expect(Token.ARROW, "Expected `->`");
        } else if (lookahead == Token.ARROW) {
          // it's a parameter
          next();
          params.add(new Parameter.TypedIdent(ident, type, ident.span()));
        } else {
          // it's a member
          expect(Token.ASSIGN, "Expected `=`");
          var expr = parseExpr();
          members.add(
              new ObjectMember.ObjectProperty(
                  new ArrayList<>(), ident, type, expr, ident.span().endWith(expr.getSpan())));
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

  private ObjectMember parseObjectMember() {
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

  private ObjectMember.ObjectElement parseObjectElement() {
    var expr = parseExpr();
    return new ObjectMember.ObjectElement(expr, expr.getSpan());
  }

  private ObjectMember parseObjectProperty(@Nullable List<Modifier> modifiers) {
    var start = spanLookahead;
    var allModifiers = modifiers;
    if (allModifiers == null) {
      allModifiers = parseModifierList();
    }
    var ident = parseIdent();
    Type type = null;
    if (lookahead == Token.COLON) {
      next();
      type = parseType();
    }
    if (type != null || lookahead == Token.ASSIGN) {
      expect(Token.ASSIGN, "Expected `=`");
      var expr = parseExpr();
      return new ObjectMember.ObjectProperty(
          allModifiers, ident, type, expr, start.endWith(expr.getSpan()));
    }
    var bodies = parseBodyList();
    var end = bodies.get(bodies.size() - 1).span();
    return new ObjectMember.ObjectBodyProperty(allModifiers, ident, bodies, start.endWith(end));
  }

  private ObjectMember.ObjectMethod parseObjectMethod(List<Modifier> modifiers) {
    var start = spanLookahead;
    expect(Token.FUNCTION, "Expected `function`");
    var ident = parseIdent();
    List<TypeParameter> params = new ArrayList<>();
    if (lookahead == Token.LT) {
      params = parseTypeParameterList();
    }
    expect(Token.LPAREN, "Expected `(`");
    List<Parameter> args = new ArrayList<>();
    if (lookahead == Token.RPAREN) {
      next();
    } else {
      args = parseListOfParameter(Token.COMMA);
      expect(Token.RPAREN, "Expected `)`");
    }
    Type type = null;
    if (lookahead == Token.COLON) {
      next();
      type = parseType();
    }
    expect(Token.ASSIGN, "Expected `=`");
    var expr = parseExpr();
    return new ObjectMember.ObjectMethod(
        modifiers, ident, params, args, type, expr, start.endWith(expr.getSpan()));
  }

  private ObjectMember parseMemberPredicate() {
    var start = next().span;
    var pred = parseExpr();
    expect(Token.RBRACK, "Expected `]]`");
    expect(Token.RBRACK, "Expected `]]`");
    if (lookahead == Token.ASSIGN) {
      next();
      var expr = parseExpr();
      return new ObjectMember.MemberPredicate(pred, expr, start.endWith(expr.getSpan()));
    }
    var bodies = parseBodyList();
    var end = bodies.get(bodies.size() - 1).span();
    return new ObjectMember.MemberPredicateBody(pred, bodies, start.endWith(end));
  }

  private ObjectMember parseObjectEntry() {
    var start = expect(Token.LBRACK, "Expected `[`").span;
    var key = parseExpr();
    expect(Token.RBRACK, "Expected `]`");
    if (lookahead == Token.ASSIGN) {
      next();
      var expr = parseExpr();
      return new ObjectMember.ObjectEntry(key, expr, start.endWith(expr.getSpan()));
    }
    var bodies = parseBodyList();
    var end = bodies.get(bodies.size() - 1).span();
    return new ObjectMember.ObjectEntryBody(key, bodies, start.endWith(end));
  }

  private ObjectMember.ObjectSpread parseObjectSpread() {
    if (lookahead != Token.SPREAD && lookahead != Token.QSPREAD) {
      throw new ParserError("Expected `...` or `...?`", spanLookahead);
    }
    var peek = next();
    boolean isNullable = peek.token == Token.QSPREAD;
    var expr = parseExpr();
    return new ObjectMember.ObjectSpread(expr, isNullable, peek.span.endWith(expr.getSpan()));
  }

  private ObjectMember.WhenGenerator parseWhenGenerator() {
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
    return new ObjectMember.WhenGenerator(pred, body, elseBody, start.endWith(end));
  }

  private ObjectMember.ForGenerator parseForGenerator() {
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
    return new ObjectMember.ForGenerator(par1, par2, expr, body, start.endWith(body.span()));
  }

  private Expr parseExpr() {
    var exprs = new ArrayList<Expr>();
    exprs.add(parseExprAtom());
    var op = getOperator();
    loop:
    while (op != null) {
      switch (op) {
        case IS, AS -> {
          exprs.add(new OperatorExpr(op, next().span));
          exprs.add(new Expr.TypeExpr(parseType()));
          var expr = OperatorResolver.resolveOperators(exprs);
          exprs.clear();
          exprs.add(expr);
        }
        case MINUS -> {
          if (!precededBySemicolon
              && exprs.get(exprs.size() - 1).getSpan().sameLine(spanLookahead)) {
            exprs.add(new OperatorExpr(op, next().span));
            exprs.add(parseExprAtom());
          } else {
            break loop;
          }
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
      default -> null;
    };
  }

  private Expr parseExprAtom() {
    var expr =
        switch (lookahead) {
          case THIS -> new Expr.This(next().span);
          case OUTER -> new Expr.Outer(next().span);
          case MODULE -> new Expr.Module(next().span);
          case NULL -> new Expr.Null(next().span);
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
            yield new Expr.ImportExpr(strConst.value, start.endWith(end));
          }
          case IMPORT_STAR -> {
            var start = next().span;
            expect(Token.LPAREN, "Expected `(`");
            var strConst = parseStringConstant();
            var end = expect(Token.RPAREN, "Expected `)`").span;
            yield new Expr.ImportGlobExpr(strConst.value, start.endWith(end));
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
            yield new Expr.UnaryMinus(exp, start.endWith(exp.getSpan()));
          }
          case NOT -> {
            var start = next().span;
            // calling `parseExprAtom` here and not `parseExpr` because
            // logical not has higher precendence than binary operators
            var exp = parseExprAtom();
            yield new Expr.LogicalNot(exp, start.endWith(exp.getSpan()));
          }
          case LPAREN -> {
            // can be function literal or parenthesized expression
            var start = next().span;
            yield switch (lookahead) {
              case UNDERSCORE -> parseFunctionLiteral(start);
              case IDENT -> parseFunctionLiteralOrParenthesized(start);
              case RPAREN -> {
                next();
                expect(Token.ARROW, "Expected `->`");
                var exp = parseExpr();
                yield new Expr.FunctionLiteral(
                    new ArrayList<>(), exp, start.endWith(exp.getSpan()));
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
                yield new Expr.SuperAccess(ident, args.getFirst(), start.endWith(args.getSecond()));
              } else {
                yield new Expr.SuperAccess(ident, new ArrayList<>(), start.endWith(ident.span()));
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
            yield new Expr.If(pred, then, elseCase, start.endWith(elseCase.getSpan()));
          }
          case LET -> {
            var start = next().span();
            expect(Token.LPAREN, "Expected `(`");
            var param = parseParameter();
            expect(Token.ASSIGN, "Expected `=`");
            var bindExpr = parseExpr();
            expect(Token.RPAREN, "Expected `)`");
            var exp = parseExpr();
            yield new Expr.Let(param, bindExpr, exp, start.endWith(exp.getSpan()));
          }
          case TRUE -> new Expr.BoolLiteral(true, next().span);
          case FALSE -> new Expr.BoolLiteral(false, next().span);
          case INT -> {
            var tk = next();
            var text = remove_(lexer.textFor(tk.textOffset, tk.textSize));
            yield new Expr.IntLiteral(text, tk.span);
          }
          case FLOAT -> {
            var tk = next();
            var text = remove_(lexer.textFor(tk.textOffset, tk.textSize));
            yield new Expr.FloatLiteral(text, tk.span);
          }
          case HEX, BIN, OCT -> {
            var tk = next();
            var num = remove_(lexer.textFor(tk.textOffset, tk.textSize).substring(2));
            yield new Expr.IntLiteral(num, tk.span);
          }
          case STRING_START, STRING_MULTI_START -> {
            var start = next();
            var parts = new ArrayList<Expr>();
            while (lookahead != Token.STRING_END) {
              if (lookahead == Token.STRING_PART) {
                var tk = next();
                var text = processStringPart(lexer.textFor(tk.textOffset, tk.textSize));
                if (!text.isEmpty()) {
                  parts.add(new Expr.StringConstant(text, tk.span));
                }
              } else {
                parts.add(parseExpr());
              }
            }
            var end = expect(Token.STRING_END, "noError").span;
            if (start.token == Token.STRING_START) {
              yield new Expr.InterpolatedString(parts, start.span.endWith(end));
            } else {
              yield new Expr.InterpolatedMultiString(parts, start.span.endWith(end));
            }
          }
          case IDENT -> {
            var ident = parseIdent();
            if (lookahead == Token.LPAREN
                && !precededBySemicolon
                && ident.span().sameLine(spanLookahead)) {
              var args = parseArgumentList();
              yield new Expr.UnqualifiedAccess(
                  ident, args.getFirst(), ident.span().endWith(args.getSecond()));
            } else {
              yield new Expr.UnqualifiedAccess(ident, null, ident.span());
            }
          }
          default -> throw new ParserError("Invalid token for expression", spanLookahead);
        };
    return parseExprRest(expr);
  }

  private Expr parseExprRest(Expr expr) {
    // non null
    if (lookahead == Token.NON_NULL) {
      var end = next().span;
      var res = new Expr.NonNull(expr, expr.getSpan().endWith(end));
      return parseExprRest(res);
    }
    // amends
    if (lookahead == Token.LBRACE
        && (expr instanceof Parenthesized
            || expr instanceof Expr.Amends
            || expr instanceof Expr.New)) {
      var body = parseObjectBody();
      return parseExprRest(new Expr.Amends(expr, body, expr.getSpan().endWith(body.span())));
    }
    // qualified access
    if (lookahead == Token.DOT || lookahead == Token.QDOT) {
      var isNullable = next().token == Token.QDOT;
      var ident = parseIdent();
      Pair<List<Expr>, Span> argsPair = null;
      if (lookahead == Token.LPAREN
          && !precededBySemicolon
          && ident.span().sameLine(spanLookahead)) {
        argsPair = parseArgumentList();
      }
      var lastSpan = argsPair != null ? argsPair.getSecond() : ident.span();
      var args = argsPair != null ? argsPair.getFirst() : null;
      var res =
          new Expr.QualifiedAccess(expr, ident, isNullable, args, expr.getSpan().endWith(lastSpan));
      return parseExprRest(res);
    }
    // subscript (needs to be in the same line as the expression)
    if (lookahead == Token.LBRACK
        && !precededBySemicolon
        && expr.getSpan().sameLine(spanLookahead)) {
      next();
      var exp = parseExpr();
      expect(Token.RBRACK, "Expected `]`");
      var res = new Expr.Subscript(expr, exp, expr.getSpan().endWith(exp.getSpan()));
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
        expect(Token.RPAREN, "Expected `)`");
        expect(Token.ARROW, "Expected `->`");
        var expr = parseExpr();
        yield new Expr.FunctionLiteral(params, expr, start.endWith(expr.getSpan()));
      }
      case COLON -> {
        next();
        var type = parseType();
        var params = new ArrayList<Parameter>();
        params.add(new Parameter.TypedIdent(ident, type, ident.span()));
        if (lookahead == Token.COMMA) {
          next();
          params.addAll(parseListOfParameter(Token.COMMA));
        }
        expect(Token.RPAREN, "Expected `)`");
        expect(Token.ARROW, "Expected `->`");
        var expr = parseExpr();
        yield new Expr.FunctionLiteral(params, expr, start.endWith(expr.getSpan()));
      }
      case RPAREN -> {
        // still not sure
        var end = next().span;
        if (lookahead == Token.ARROW) {
          next();
          var expr = parseExpr();
          var params = new ArrayList<Parameter>();
          params.add(new Parameter.TypedIdent(ident, null, ident.span()));
          yield new Expr.FunctionLiteral(params, expr, start.endWith(expr.getSpan()));
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
    expect(Token.RPAREN, "Expected `)`");
    expect(Token.ARROW, "Expected `->`");
    var expr = parseExpr();
    return new Expr.FunctionLiteral(params, expr, start.endWith(expr.getSpan()));
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
        typ = new DefaultUnionType(type, tk.span.endWith(type.getSpan()));
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
        typ = new StringConstantType(str.value, str.span);
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
      var res = new Type.NullableType(type, type.getSpan().endWith(end));
      return parseTypeEnd(res, shortCircuit);
    }
    // constrained types: have to start in the same line as the type
    if (lookahead == Token.LPAREN && type.getSpan().sameLine(spanLookahead)) {
      next();
      var constraints = parseListOf(Token.COMMA, this::parseExpr);
      var end = expect(Token.RPAREN, "Expected `)`").span;
      var res = new Type.ConstrainedType(type, constraints, type.getSpan().endWith(end));
      return parseTypeEnd(res, shortCircuit);
    }
    // union types
    if (lookahead == Token.UNION && !shortCircuit) {
      next();
      // union types are left associative
      var right = parseType(true);
      var res = new Type.UnionType(type, right, type.getSpan().endWith(right.getSpan()));
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

  private List<ObjectBody> parseBodyList() {
    var bodies = new ArrayList<ObjectBody>();
    do {
      bodies.add(parseObjectBody());
    } while (lookahead == Token.LBRACE);
    return bodies;
  }

  // TODO: return the span
  private List<TypeParameter> parseTypeParameterList() {
    expect(Token.LT, "Expected `<`");
    var pars = parseListOf(Token.COMMA, this::parseTypeParameter);
    expect(Token.GT, "Expected `>`");
    return pars;
  }

  private Pair<List<Expr>, Span> parseArgumentList() {
    expect(Token.LPAREN, "Expected `(");
    if (lookahead == Token.RPAREN) {
      return new Pair<>(new ArrayList<>(), next().span);
    }
    var exprs = parseListOf(Token.COMMA, this::parseExpr);
    var end = expect(Token.RPAREN, "Expected `)`").span;
    return new Pair<>(exprs, end);
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
    Type type = null;
    var end = ident.span();
    if (lookahead == Token.COLON) {
      next();
      type = parseType();
      end = type.getSpan();
    }
    return new Parameter.TypedIdent(ident, type, ident.span().endWith(end));
  }

  private Ident parseIdent() {
    if (lookahead != Token.IDENT) {
      throw new ParserError("Expected identifier", spanLookahead);
    }
    var tk = next();
    return new Ident(lexer.textFor(tk.textOffset, tk.textSize), tk.span);
  }

  private StringConstant parseStringConstant() {
    var start = spanLookahead;
    expect(Token.STRING_START, "Expected string start");
    if (lookahead == Token.STRING_PART) {
      var tk = next();
      var text = lexer.textFor(tk.textOffset, tk.textSize);
      var end = spanLookahead;
      expect(Token.STRING_END, "Expected string end");
      return new StringConstant(text, start.endWith(end));
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
        return new Span(start.beginLine(), start.beginCol(), end.endLine(), end.endCol());
      }
      if (end != null) {
        return new Span(start.beginLine(), start.beginCol(), end.endLine(), end.endCol());
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
    return new FullToken(tk, lexer.span(), lexer.sCursor, lexer.cursor - lexer.sCursor);
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

  // The lexer will return strings ending with "\[#]+(" right before
  // interpolation starts. This function will clean the text if needed.
  public static String processStringPart(String part) {
    if (part.length() < 2 || part.charAt(part.length() - 1) != '(') return part;
    var i = part.length() - 2;
    while (i >= 0 && part.charAt(i) == '#') {
      i--;
    }
    if (i < 0) return part;
    var cutoff = i;
    if (part.charAt(i) != '\\') return part;
    i--;
    if (i < 0) return part.substring(0, cutoff);
    if (part.charAt(i) == '\\') return part;
    return part.substring(0, cutoff);
  }

  private record FullToken(Token token, Span span, int textOffset, int textSize) {
    String text(Lexer lexer) {
      return lexer.textFor(textOffset, textSize);
    }
  }

  private record StringConstant(String value, Span span) {}
}
