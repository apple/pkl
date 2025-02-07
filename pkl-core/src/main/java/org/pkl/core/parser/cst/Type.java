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
package org.pkl.core.parser.cst;

import java.util.List;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

@SuppressWarnings("ALL")
public abstract sealed class Type extends AbstractNode {

  public Type(Span span, @Nullable List<? extends @Nullable Node> children) {
    super(span, children);
  }

  public static final class UnknownType extends Type {
    public UnknownType(Span span) {
      super(span, null);
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitUnknownType(this);
    }
  }

  public static final class NothingType extends Type {
    public NothingType(Span span) {
      super(span, null);
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitNothingType(this);
    }
  }

  public static final class ModuleType extends Type {
    public ModuleType(Span span) {
      super(span, null);
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitModuleType(this);
    }
  }

  public static final class StringConstantType extends Type {
    public StringConstantType(StringConstant str, Span span) {
      super(span, List.of(str));
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitStringConstantType(this);
    }

    public StringConstant getStr() {
      return (StringConstant) children.get(0);
    }
  }

  public static final class DeclaredType extends Type {
    public DeclaredType(List<Node> nodes, Span span) {
      super(span, nodes);
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitDeclaredType(this);
    }

    public QualifiedIdentifier getName() {
      return (QualifiedIdentifier) children.get(0);
    }

    public List<Type> getArgs() {
      return (List<Type>) children.subList(1, children.size());
    }
  }

  public static final class ParenthesizedType extends Type {
    public ParenthesizedType(Type type, Span span) {
      super(span, List.of(type));
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitParenthesizedType(this);
    }

    public Type getType() {
      return (Type) children.get(0);
    }
  }

  public static final class NullableType extends Type {
    public NullableType(Type type, Span span) {
      super(span, List.of(type));
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitNullableType(this);
    }

    public Type getType() {
      return (Type) children.get(0);
    }
  }

  public static final class ConstrainedType extends Type {
    public ConstrainedType(List<Node> nodes, Span span) {
      super(span, nodes);
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitConstrainedType(this);
    }

    public Type getType() {
      return (Type) children.get(0);
    }

    public List<Expr> getExprs() {
      return (List<Expr>) children.subList(1, children.size());
    }
  }

  public static final class DefaultUnionType extends Type {
    public DefaultUnionType(Type type, Span span) {
      super(span, List.of(type));
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitDefaultUnionType(this);
    }

    public Type getType() {
      return (Type) children.get(0);
    }
  }

  public static final class UnionType extends Type {
    public UnionType(Type left, Type right, Span span) {
      super(span, List.of(left, right));
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitUnionType(this);
    }

    public Type getLeft() {
      return (Type) children.get(0);
    }

    public Type getRight() {
      return (Type) children.get(1);
    }
  }

  public static final class FunctionType extends Type {
    public FunctionType(List<Node> children, Span span) {
      super(span, children);
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitFunctionType(this);
    }

    public List<Type> getArgs() {
      return (List<Type>) children.subList(0, children.size() - 1);
    }

    public Type getRet() {
      return (Type) children.get(children.size() - 1);
    }
  }
}
