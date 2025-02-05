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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.pkl.core.newparser.ParserVisitor;
import org.pkl.core.newparser.Span;
import org.pkl.core.newparser.cst.Expr.StringConstant;

public sealed interface Type extends Node {

  final class UnknownType implements Type {
    private final Span span;
    private Node parent;

    public UnknownType(Span span) {
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
    public List<Node> children() {
      return List.of();
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitUnknownType(this);
    }

    @Override
    public String toString() {
      return "UnknownType{" + "span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      UnknownType that = (UnknownType) o;
      return Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(span);
    }
  }

  final class NothingType implements Type {
    private final Span span;
    private Node parent;

    public NothingType(Span span) {
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
    public List<Node> children() {
      return List.of();
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitNothingType(this);
    }

    @Override
    public String toString() {
      return "NothingType{" + "span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      NothingType that = (NothingType) o;
      return Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(span);
    }
  }

  final class ModuleType implements Type {
    private final Span span;
    private Node parent;

    public ModuleType(Span span) {
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
    public List<Node> children() {
      return List.of();
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitModuleType(this);
    }

    @Override
    public String toString() {
      return "ModuleType{" + "span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ModuleType that = (ModuleType) o;
      return Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(span);
    }
  }

  final class StringConstantType implements Type {
    private final StringConstant str;
    private final Span span;
    private Node parent;

    public StringConstantType(StringConstant str, Span span) {
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

    @Override
    public List<Node> children() {
      return List.of(str);
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitStringConstantType(this);
    }

    public StringConstant getStr() {
      return str;
    }

    @Override
    public String toString() {
      return "StringConstantType{" + "str='" + str + '\'' + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      StringConstantType that = (StringConstantType) o;
      return Objects.equals(str, that.str) && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(str, span);
    }
  }

  final class DeclaredType implements Type {
    private final QualifiedIdent name;
    private final List<Type> args;
    private final Span span;
    private Node parent;

    public DeclaredType(QualifiedIdent name, List<Type> args, Span span) {
      this.name = name;
      this.args = args;
      this.span = span;

      name.setParent(this);
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

    @Override
    public List<Node> children() {
      var children = new ArrayList<Node>(args.size() + 1);
      children.add(name);
      children.addAll(args);
      return children;
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitDeclaredType(this);
    }

    public QualifiedIdent getName() {
      return name;
    }

    public List<Type> getArgs() {
      return args;
    }

    @Override
    public String toString() {
      return "DeclaredType{" + "name=" + name + ", args=" + args + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      DeclaredType that = (DeclaredType) o;
      return Objects.equals(name, that.name)
          && Objects.equals(args, that.args)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, args, span);
    }
  }

  final class ParenthesizedType implements Type {
    private final Type type;
    private final Span span;
    private Node parent;

    public ParenthesizedType(Type type, Span span) {
      this.type = type;
      this.span = span;

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

    @Override
    public List<Node> children() {
      return List.of(type);
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitParenthesizedType(this);
    }

    public Type getType() {
      return type;
    }

    @Override
    public String toString() {
      return "ParenthesizedType{" + "type=" + type + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ParenthesizedType that = (ParenthesizedType) o;
      return Objects.equals(type, that.type) && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, span);
    }
  }

  final class NullableType implements Type {
    private final Type type;
    private final Span span;
    private Node parent;

    public NullableType(Type type, Span span) {
      this.type = type;
      this.span = span;

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

    @Override
    public List<Node> children() {
      return List.of(type);
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitNullableType(this);
    }

    public Type getType() {
      return type;
    }

    @Override
    public String toString() {
      return "NullableType{" + "type=" + type + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      NullableType that = (NullableType) o;
      return Objects.equals(type, that.type) && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, span);
    }
  }

  final class ConstrainedType implements Type {
    private final Type type;
    private final List<Expr> expr;
    private final Span span;
    private Node parent;

    public ConstrainedType(Type type, List<Expr> expr, Span span) {
      this.type = type;
      this.expr = expr;
      this.span = span;

      type.setParent(this);
      for (var exp : expr) {
        exp.setParent(this);
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

    @Override
    public List<Node> children() {
      var children = new ArrayList<Node>(expr.size() + 1);
      children.add(type);
      children.addAll(expr);
      return children;
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitConstrainedType(this);
    }

    public Type getType() {
      return type;
    }

    public List<Expr> getExpr() {
      return expr;
    }

    @Override
    public String toString() {
      return "ConstrainedType{" + "type=" + type + ", expr=" + expr + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ConstrainedType that = (ConstrainedType) o;
      return Objects.equals(type, that.type)
          && Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, expr, span);
    }
  }

  final class DefaultUnionType implements Type {
    private final Type type;
    private final Span span;
    private Node parent;

    public DefaultUnionType(Type type, Span span) {
      this.type = type;
      this.span = span;

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

    @Override
    public List<Node> children() {
      return List.of(type);
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitDefaultUnionType(this);
    }

    public Type getType() {
      return type;
    }

    @Override
    public String toString() {
      return "DefaultUnionType{" + "type=" + type + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      DefaultUnionType that = (DefaultUnionType) o;
      return Objects.equals(type, that.type) && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, span);
    }
  }

  final class UnionType implements Type {
    private final Type left;
    private final Type right;
    private final Span span;
    private Node parent;

    public UnionType(Type left, Type right, Span span) {
      this.left = left;
      this.right = right;
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

    @Override
    public List<Node> children() {
      return List.of(left, right);
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitUnionType(this);
    }

    public Type getLeft() {
      return left;
    }

    public Type getRight() {
      return right;
    }

    @Override
    public String toString() {
      return "UnionType{" + "left=" + left + ", right=" + right + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      UnionType unionType = (UnionType) o;
      return Objects.equals(left, unionType.left)
          && Objects.equals(right, unionType.right)
          && Objects.equals(span, unionType.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(left, right, span);
    }
  }

  final class FunctionType implements Type {
    private final List<Type> args;
    private final Type ret;
    private final Span span;
    private Node parent;

    public FunctionType(List<Type> args, Type ret, Span span) {
      this.args = args;
      this.ret = ret;
      this.span = span;

      for (var arg : args) {
        arg.setParent(this);
      }
      ret.setParent(this);
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
    public List<Node> children() {
      var children = new ArrayList<Node>(args.size() + 1);
      children.addAll(args);
      children.add(ret);
      return children;
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitFunctionType(this);
    }

    public List<Type> getArgs() {
      return args;
    }

    public Type getRet() {
      return ret;
    }

    @Override
    public String toString() {
      return "FunctionType{" + "args=" + args + ", ret=" + ret + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      FunctionType that = (FunctionType) o;
      return Objects.equals(args, that.args)
          && Objects.equals(ret, that.ret)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(args, ret, span);
    }
  }
}
