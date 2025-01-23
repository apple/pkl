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
package org.pkl.core.newparser.cst;

import java.util.List;
import java.util.Objects;
import org.pkl.core.newparser.Span;
import org.pkl.core.util.Nullable;

public sealed interface Expr extends Node {

  final class This implements Expr {
    private final Span span;
    private Node parent;

    public This(Span span) {
      this.span = span;
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    @Override
    public String toString() {
      return "This{" + "span=" + span + ", parent=" + parent + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      This aThis = (This) o;
      return Objects.equals(span, aThis.span) && Objects.equals(parent, aThis.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(span, parent);
    }
  }

  final class Outer implements Expr {
    private final Span span;
    private Node parent;

    public Outer(Span span) {
      this.span = span;
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    @Override
    public String toString() {
      return "Outer{" + "span=" + span + ", parent=" + parent + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Outer aOuter = (Outer) o;
      return Objects.equals(span, aOuter.span) && Objects.equals(parent, aOuter.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(span, parent);
    }
  }

  final class Module implements Expr {
    private final Span span;
    private Node parent;

    public Module(Span span) {
      this.span = span;
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    @Override
    public String toString() {
      return "Module{" + "span=" + span + ", parent=" + parent + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Module aModule = (Module) o;
      return Objects.equals(span, aModule.span) && Objects.equals(parent, aModule.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(span, parent);
    }
  }

  final class NullLiteral implements Expr {
    private final Span span;
    private Node parent;

    public NullLiteral(Span span) {
      this.span = span;
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    @Override
    public String toString() {
      return "NullLiteral{" + "span=" + span + ", parent=" + parent + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      NullLiteral aNullLiteral = (NullLiteral) o;
      return Objects.equals(span, aNullLiteral.span) && Objects.equals(parent, aNullLiteral.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(span, parent);
    }
  }

  final class BoolLiteral implements Expr {
    private final boolean b;
    private final Span span;
    private Node parent;

    public BoolLiteral(boolean b, Span span) {
      this.b = b;
      this.span = span;
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public boolean isB() {
      return b;
    }

    @Override
    public String toString() {
      return "BoolLiteral{" + "b=" + b + ", span=" + span + ", parent=" + parent + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      BoolLiteral that = (BoolLiteral) o;
      return b == that.b && Objects.equals(span, that.span) && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(b, span, parent);
    }
  }

  final class IntLiteral implements Expr {
    private final String number;
    private final Span span;
    private Node parent;

    public IntLiteral(String number, Span span) {
      this.number = number;
      this.span = span;
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public String getNumber() {
      return number;
    }

    @Override
    public String toString() {
      return "IntLiteral{"
          + "number='"
          + number
          + '\''
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      IntLiteral that = (IntLiteral) o;
      return Objects.equals(number, that.number)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(number, span, parent);
    }
  }

  final class FloatLiteral implements Expr {
    private final String number;
    private final Span span;
    private Node parent;

    public FloatLiteral(String number, Span span) {
      this.number = number;
      this.span = span;
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public String getNumber() {
      return number;
    }

    @Override
    public String toString() {
      return "FloatLiteral{"
          + "number='"
          + number
          + '\''
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      FloatLiteral that = (FloatLiteral) o;
      return Objects.equals(number, that.number)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(number, span, parent);
    }
  }

  final class StringConstant implements Expr {
    private final String str;
    private final Span span;
    private Node parent;

    public StringConstant(String str, Span span) {
      this.str = str;
      this.span = span;
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public String getStr() {
      return str;
    }

    @Override
    public String toString() {
      return "StringConstant{"
          + "str='"
          + str
          + '\''
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      StringConstant that = (StringConstant) o;
      return Objects.equals(str, that.str)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(str, span, parent);
    }
  }

  final class InterpolatedString implements Expr {
    private final List<Expr> exprs;
    private final Span span;
    private Node parent;

    public InterpolatedString(List<Expr> exprs, Span span) {
      this.exprs = exprs;
      this.span = span;

      for (var expr : exprs) {
        expr.setParent(this);
      }
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public List<Expr> getExprs() {
      return exprs;
    }

    @Override
    public String toString() {
      return "InterpolatedString{"
          + "exprs="
          + exprs
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      InterpolatedString that = (InterpolatedString) o;
      return Objects.equals(exprs, that.exprs)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(exprs, span, parent);
    }
  }

  final class InterpolatedMultiString implements Expr {
    private final List<Expr> exprs;
    private final Span span;
    private Node parent;

    public InterpolatedMultiString(List<Expr> exprs, Span span) {
      this.exprs = exprs;
      this.span = span;

      for (var expr : exprs) {
        expr.setParent(this);
      }
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public List<Expr> getExprs() {
      return exprs;
    }

    @Override
    public String toString() {
      return "InterpolatedMultiString{"
          + "exprs="
          + exprs
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      InterpolatedMultiString that = (InterpolatedMultiString) o;
      return Objects.equals(exprs, that.exprs)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(exprs, span, parent);
    }
  }

  final class Throw implements Expr {
    private final Expr expr;
    private final Span span;
    private Node parent;

    public Throw(Expr expr, Span span) {
      this.expr = expr;
      this.span = span;

      expr.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Expr getExpr() {
      return expr;
    }

    @Override
    public String toString() {
      return "Throw{" + "expr=" + expr + ", span=" + span + ", parent=" + parent + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Throw that = (Throw) o;
      return Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(expr, span, parent);
    }
  }

  final class Trace implements Expr {
    private final Expr expr;
    private final Span span;
    private Node parent;

    public Trace(Expr expr, Span span) {
      this.expr = expr;
      this.span = span;

      expr.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Expr getExpr() {
      return expr;
    }

    @Override
    public String toString() {
      return "Trace{" + "expr=" + expr + ", span=" + span + ", parent=" + parent + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Trace that = (Trace) o;
      return Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(expr, span, parent);
    }
  }

  final class ImportExpr implements Expr {
    private final String importStr;
    private final Span span;
    private Node parent;

    public ImportExpr(String importStr, Span span) {
      this.importStr = importStr;
      this.span = span;
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public String getImportStr() {
      return importStr;
    }

    @Override
    public String toString() {
      return "ImportExpr{"
          + "importStr='"
          + importStr
          + '\''
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ImportExpr that = (ImportExpr) o;
      return Objects.equals(importStr, that.importStr)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(importStr, span, parent);
    }
  }

  final class ImportGlobExpr implements Expr {
    private final String importStr;
    private final Span span;
    private Node parent;

    public ImportGlobExpr(String importStr, Span span) {
      this.importStr = importStr;
      this.span = span;
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public String getImportStr() {
      return importStr;
    }

    @Override
    public String toString() {
      return "ImportGlobExpr{"
          + "importStr='"
          + importStr
          + '\''
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ImportGlobExpr that = (ImportGlobExpr) o;
      return Objects.equals(importStr, that.importStr)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(importStr, span, parent);
    }
  }

  final class Read implements Expr {
    private final Expr expr;
    private final Span span;
    private Node parent;

    public Read(Expr expr, Span span) {
      this.expr = expr;
      this.span = span;

      expr.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Expr getExpr() {
      return expr;
    }

    @Override
    public String toString() {
      return "Read{" + "expr=" + expr + ", span=" + span + ", parent=" + parent + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Read that = (Read) o;
      return Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(expr, span, parent);
    }
  }

  final class ReadGlob implements Expr {
    private final Expr expr;
    private final Span span;
    private Node parent;

    public ReadGlob(Expr expr, Span span) {
      this.expr = expr;
      this.span = span;

      expr.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Expr getExpr() {
      return expr;
    }

    @Override
    public String toString() {
      return "ReadGlob{" + "expr=" + expr + ", span=" + span + ", parent=" + parent + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ReadGlob that = (ReadGlob) o;
      return Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(expr, span, parent);
    }
  }

  final class ReadNull implements Expr {
    private final Expr expr;
    private final Span span;
    private Node parent;

    public ReadNull(Expr expr, Span span) {
      this.expr = expr;
      this.span = span;

      expr.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Expr getExpr() {
      return expr;
    }

    @Override
    public String toString() {
      return "ReadNull{" + "expr=" + expr + ", span=" + span + ", parent=" + parent + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ReadNull that = (ReadNull) o;
      return Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(expr, span, parent);
    }
  }

  final class UnqualifiedAccess implements Expr {
    private final Ident ident;
    private final @Nullable List<Expr> args;
    private final Span span;
    private Node parent;

    public UnqualifiedAccess(Ident ident, @Nullable List<Expr> args, Span span) {
      this.ident = ident;
      this.args = args;
      this.span = span;

      ident.setParent(this);
      if (args != null) {
        for (var arg : args) {
          arg.setParent(this);
        }
      }
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Ident getIdent() {
      return ident;
    }

    public @Nullable List<Expr> getArgs() {
      return args;
    }

    @Override
    public String toString() {
      return "UnqualifiedAccess{"
          + "ident="
          + ident
          + ", args="
          + args
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      UnqualifiedAccess that = (UnqualifiedAccess) o;
      return Objects.equals(ident, that.ident)
          && Objects.equals(args, that.args)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(ident, args, span, parent);
    }
  }

  final class QualifiedAccess implements Expr {
    private final Expr expr;
    private final Ident ident;
    private final boolean isNullable;
    private final @Nullable List<Expr> args;
    private final Span span;
    private Node parent;

    public QualifiedAccess(
        Expr expr, Ident ident, boolean isNullable, @Nullable List<Expr> args, Span span) {
      this.expr = expr;
      this.ident = ident;
      this.isNullable = isNullable;
      this.args = args;
      this.span = span;

      expr.setParent(this);
      ident.setParent(this);
      if (args != null) {
        for (var arg : args) {
          arg.setParent(this);
        }
      }
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Expr getExpr() {
      return expr;
    }

    public Ident getIdent() {
      return ident;
    }

    public boolean isNullable() {
      return isNullable;
    }

    public @Nullable List<Expr> getArgs() {
      return args;
    }

    @Override
    public String toString() {
      return "QualifiedAccess{"
          + "expr="
          + expr
          + ", ident="
          + ident
          + ", isNullable="
          + isNullable
          + ", args="
          + args
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      QualifiedAccess that = (QualifiedAccess) o;
      return isNullable == that.isNullable
          && Objects.equals(expr, that.expr)
          && Objects.equals(ident, that.ident)
          && Objects.equals(args, that.args)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(expr, ident, isNullable, args, span, parent);
    }
  }

  final class SuperAccess implements Expr {
    private final Ident ident;
    private final List<Expr> args;
    private final Span span;
    private Node parent;

    public SuperAccess(Ident ident, List<Expr> args, Span span) {
      this.ident = ident;
      this.args = args;
      this.span = span;

      ident.setParent(this);
      for (var arg : args) {
        arg.setParent(this);
      }
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Ident getIdent() {
      return ident;
    }

    public List<Expr> getArgs() {
      return args;
    }

    @Override
    public String toString() {
      return "SuperAccess{"
          + "ident="
          + ident
          + ", args="
          + args
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      SuperAccess that = (SuperAccess) o;
      return Objects.equals(ident, that.ident)
          && Objects.equals(args, that.args)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(ident, args, span, parent);
    }
  }

  final class SuperSubscript implements Expr {
    private final Expr arg;
    private final Span span;
    private Node parent;

    public SuperSubscript(Expr arg, Span span) {
      this.arg = arg;
      this.span = span;

      arg.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Expr getArg() {
      return arg;
    }

    @Override
    public String toString() {
      return "SuperSubscript{" + "arg=" + arg + ", span=" + span + ", parent=" + parent + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      SuperSubscript that = (SuperSubscript) o;
      return Objects.equals(arg, that.arg)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(arg, span, parent);
    }
  }

  final class Subscript implements Expr {
    private final Expr expr;
    private final Expr arg;
    private final Span span;
    private Node parent;

    public Subscript(Expr expr, Expr arg, Span span) {
      this.expr = expr;
      this.arg = arg;
      this.span = span;

      expr.setParent(this);
      arg.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Expr getExpr() {
      return expr;
    }

    public Expr getArg() {
      return arg;
    }

    @Override
    public String toString() {
      return "Subscript{"
          + "expr="
          + expr
          + ", arg="
          + arg
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Subscript subscript = (Subscript) o;
      return Objects.equals(expr, subscript.expr)
          && Objects.equals(arg, subscript.arg)
          && Objects.equals(span, subscript.span)
          && Objects.equals(parent, subscript.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(expr, arg, span, parent);
    }
  }

  final class If implements Expr {
    private final Expr cond;
    private final Expr then;
    private final Expr els;
    private final Span span;
    private Node parent;

    public If(Expr cond, Expr then, Expr els, Span span) {
      this.cond = cond;
      this.then = then;
      this.els = els;
      this.span = span;

      cond.setParent(this);
      then.setParent(this);
      els.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Expr getCond() {
      return cond;
    }

    public Expr getThen() {
      return then;
    }

    public Expr getEls() {
      return els;
    }

    @Override
    public String toString() {
      return "If{"
          + "cond="
          + cond
          + ", then="
          + then
          + ", els="
          + els
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      If anIf = (If) o;
      return Objects.equals(cond, anIf.cond)
          && Objects.equals(then, anIf.then)
          && Objects.equals(els, anIf.els)
          && Objects.equals(span, anIf.span)
          && Objects.equals(parent, anIf.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(cond, then, els, span, parent);
    }
  }

  final class Let implements Expr {
    private final Parameter par;
    private final Expr bindingExpr;
    private final Expr expr;
    private final Span span;
    private Node parent;

    public Let(Parameter par, Expr bindingExpr, Expr expr, Span span) {
      this.par = par;
      this.bindingExpr = bindingExpr;
      this.expr = expr;
      this.span = span;

      par.setParent(this);
      bindingExpr.setParent(this);
      expr.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Parameter getPar() {
      return par;
    }

    public Expr getBindingExpr() {
      return bindingExpr;
    }

    public Expr getExpr() {
      return expr;
    }

    @Override
    public String toString() {
      return "Let{"
          + "par="
          + par
          + ", bindingExpr="
          + bindingExpr
          + ", expr="
          + expr
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Let let = (Let) o;
      return Objects.equals(par, let.par)
          && Objects.equals(bindingExpr, let.bindingExpr)
          && Objects.equals(expr, let.expr)
          && Objects.equals(span, let.span)
          && Objects.equals(parent, let.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(par, bindingExpr, expr, span, parent);
    }
  }

  final class FunctionLiteral implements Expr {
    private final List<Parameter> args;
    private final Expr expr;
    private final Span span;
    private Node parent;

    public FunctionLiteral(List<Parameter> args, Expr expr, Span span) {
      this.args = args;
      this.expr = expr;
      this.span = span;

      for (var arg : args) {
        arg.setParent(this);
      }
      expr.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public List<Parameter> getArgs() {
      return args;
    }

    public Expr getExpr() {
      return expr;
    }

    @Override
    public String toString() {
      return "FunctionLiteral{"
          + "args="
          + args
          + ", expr="
          + expr
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      FunctionLiteral that = (FunctionLiteral) o;
      return Objects.equals(args, that.args)
          && Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(args, expr, span, parent);
    }
  }

  final class Parenthesized implements Expr {
    private final Expr expr;
    private final Span span;
    private Node parent;

    public Parenthesized(Expr expr, Span span) {
      this.expr = expr;
      this.span = span;

      expr.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Expr getExpr() {
      return expr;
    }

    @Override
    public String toString() {
      return "Parenthesized{" + "expr=" + expr + ", span=" + span + ", parent=" + parent + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Parenthesized that = (Parenthesized) o;
      return Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(expr, span, parent);
    }
  }

  final class New implements Expr {
    private final @Nullable Type type;
    private final ObjectBody body;
    private final Span span;
    private Node parent;

    public New(@Nullable Type type, ObjectBody body, Span span) {
      this.type = type;
      this.body = body;
      this.span = span;

      if (type != null) {
        type.setParent(this);
      }
      body.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public @Nullable Type getType() {
      return type;
    }

    public ObjectBody getBody() {
      return body;
    }

    @Override
    public String toString() {
      return "New{"
          + "type="
          + type
          + ", body="
          + body
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      New aNew = (New) o;
      return Objects.equals(type, aNew.type)
          && Objects.equals(body, aNew.body)
          && Objects.equals(span, aNew.span)
          && Objects.equals(parent, aNew.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, body, span, parent);
    }
  }

  final class Amends implements Expr {
    private final Expr expr;
    private final ObjectBody body;
    private final Span span;
    private Node parent;

    public Amends(Expr expr, ObjectBody body, Span span) {
      this.expr = expr;
      this.body = body;
      this.span = span;

      expr.setParent(this);
      body.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Expr getExpr() {
      return expr;
    }

    public ObjectBody getBody() {
      return body;
    }

    @Override
    public String toString() {
      return "Amends{"
          + "expr="
          + expr
          + ", body="
          + body
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Amends amends = (Amends) o;
      return Objects.equals(expr, amends.expr)
          && Objects.equals(body, amends.body)
          && Objects.equals(span, amends.span)
          && Objects.equals(parent, amends.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(expr, body, span, parent);
    }
  }

  final class NonNull implements Expr {
    private final Expr expr;
    private final Span span;
    private Node parent;

    public NonNull(Expr expr, Span span) {
      this.expr = expr;
      this.span = span;

      expr.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Expr getExpr() {
      return expr;
    }

    @Override
    public String toString() {
      return "NonNull{" + "expr=" + expr + ", span=" + span + ", parent=" + parent + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      NonNull that = (NonNull) o;
      return Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(expr, span, parent);
    }
  }

  final class UnaryMinus implements Expr {
    private final Expr expr;
    private final Span span;
    private Node parent;

    public UnaryMinus(Expr expr, Span span) {
      this.expr = expr;
      this.span = span;

      expr.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Expr getExpr() {
      return expr;
    }

    @Override
    public String toString() {
      return "UnaryMinus{" + "expr=" + expr + ", span=" + span + ", parent=" + parent + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      UnaryMinus that = (UnaryMinus) o;
      return Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(expr, span, parent);
    }
  }

  final class LogicalNot implements Expr {
    private final Expr expr;
    private final Span span;
    private Node parent;

    public LogicalNot(Expr expr, Span span) {
      this.expr = expr;
      this.span = span;

      expr.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Expr getExpr() {
      return expr;
    }

    @Override
    public String toString() {
      return "LogicalNot{" + "expr=" + expr + ", span=" + span + ", parent=" + parent + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      LogicalNot that = (LogicalNot) o;
      return Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(expr, span, parent);
    }
  }

  final class BinaryOp implements Expr {
    private final Expr left;
    private final Expr right;
    private final Operator op;
    private final Span span;
    private Node parent;

    public BinaryOp(Expr left, Expr right, Operator op, Span span) {
      this.left = left;
      this.right = right;
      this.op = op;
      this.span = span;

      left.setParent(this);
      right.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Expr getLeft() {
      return left;
    }

    public Expr getRight() {
      return right;
    }

    public Operator getOp() {
      return op;
    }

    @Override
    public String toString() {
      return "BinaryOp{"
          + "left="
          + left
          + ", right="
          + right
          + ", op="
          + op
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      BinaryOp binaryOp = (BinaryOp) o;
      return Objects.equals(left, binaryOp.left)
          && Objects.equals(right, binaryOp.right)
          && op == binaryOp.op
          && Objects.equals(span, binaryOp.span)
          && Objects.equals(parent, binaryOp.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(left, right, op, span, parent);
    }
  }

  final class TypeCheck implements Expr {
    private final Expr expr;
    private final Type type;
    private final Span span;
    private Node parent;

    public TypeCheck(Expr expr, Type type, Span span) {
      this.expr = expr;
      this.type = type;
      this.span = span;

      expr.setParent(this);
      type.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Expr getExpr() {
      return expr;
    }

    public Type getType() {
      return type;
    }

    @Override
    public String toString() {
      return "TypeCheck{"
          + "expr="
          + expr
          + ", type="
          + type
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      TypeCheck typeCheck = (TypeCheck) o;
      return Objects.equals(expr, typeCheck.expr)
          && Objects.equals(type, typeCheck.type)
          && Objects.equals(span, typeCheck.span)
          && Objects.equals(parent, typeCheck.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(expr, type, span, parent);
    }
  }

  final class TypeCast implements Expr {
    private final Expr expr;
    private final Type type;
    private final Span span;
    private Node parent;

    public TypeCast(Expr expr, Type type, Span span) {
      this.expr = expr;
      this.type = type;
      this.span = span;

      expr.setParent(this);
      type.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Expr getExpr() {
      return expr;
    }

    public Type getType() {
      return type;
    }

    @Override
    public String toString() {
      return "TypeCast{"
          + "expr="
          + expr
          + ", type="
          + type
          + ", span="
          + span
          + ", parent="
          + parent
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      TypeCast typeCheck = (TypeCast) o;
      return Objects.equals(expr, typeCheck.expr)
          && Objects.equals(type, typeCheck.type)
          && Objects.equals(span, typeCheck.span)
          && Objects.equals(parent, typeCheck.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(expr, type, span, parent);
    }
  }

  /** This is a synthetic class only used at parse time. */
  final class OperatorExpr implements Expr {
    private final Operator op;
    private final Span span;
    private Node parent;

    public OperatorExpr(Operator op, Span span) {
      this.op = op;
      this.span = span;
    }

    @Override
    public Span span() {
      return span;
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Operator getOp() {
      return op;
    }

    @Override
    public String toString() {
      return "OperatorExpr{" + "op=" + op + ", span=" + span + ", parent=" + parent + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      OperatorExpr that = (OperatorExpr) o;
      return op == that.op
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(op, span, parent);
    }
  }

  /** This is a synthetic class only used at parse time. */
  final class TypeExpr implements Expr {
    private final Type type;
    private Node parent;

    public TypeExpr(Type type) {
      this.type = type;

      type.setParent(this);
    }

    @Override
    public Span span() {
      return type.span();
    }

    @Override
    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    public Type getType() {
      return type;
    }

    @Override
    public String toString() {
      return "TypeExpr{" + "type=" + type + ", parent=" + parent + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      TypeExpr typeExpr = (TypeExpr) o;
      return Objects.equals(type, typeExpr.type) && Objects.equals(parent, typeExpr.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, parent);
    }
  }
}
