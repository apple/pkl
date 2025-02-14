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
package org.pkl.core.parser.ast;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.pkl.core.PklBugException;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

@SuppressWarnings({"unchecked", "DataFlowIssue"})
public abstract sealed class Expr extends AbstractNode {

  public Expr(Span span, @Nullable List<? extends @Nullable Node> children) {
    super(span, children);
  }

  public static final class ThisExpr extends Expr {
    public ThisExpr(Span span) {
      super(span, null);
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitThisExpr(this);
    }
  }

  public static final class OuterExpr extends Expr {
    public OuterExpr(Span span) {
      super(span, null);
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitOuterExpr(this);
    }
  }

  public static final class ModuleExpr extends Expr {
    public ModuleExpr(Span span) {
      super(span, null);
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitModuleExpr(this);
    }
  }

  public static final class NullLiteralExpr extends Expr {
    public NullLiteralExpr(Span span) {
      super(span, null);
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitNullLiteralExpr(this);
    }
  }

  public static final class BoolLiteralExpr extends Expr {
    private final boolean b;

    public BoolLiteralExpr(boolean b, Span span) {
      super(span, null);
      this.b = b;
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitBoolLiteralExpr(this);
    }

    public boolean isB() {
      return b;
    }
  }

  public static final class IntLiteralExpr extends Expr {
    private final String number;

    public IntLiteralExpr(String number, Span span) {
      super(span, null);
      this.number = number;
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitIntLiteralExpr(this);
    }

    public String getNumber() {
      return number;
    }
  }

  public static final class FloatLiteralExpr extends Expr {
    private final String number;

    public FloatLiteralExpr(String number, Span span) {
      super(span, null);
      this.number = number;
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitFloatLiteralExpr(this);
    }

    public String getNumber() {
      return number;
    }
  }

  public static final class SingleLineStringLiteralExpr extends Expr {
    private final Span startDelimiterSpan;
    private final Span endDelimiterSpan;

    public SingleLineStringLiteralExpr(
        List<StringPart> parts, Span startDelimiterSpan, Span endDelimiterSpan, Span span) {
      super(span, parts);
      this.startDelimiterSpan = startDelimiterSpan;
      this.endDelimiterSpan = endDelimiterSpan;
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitSingleLineStringLiteralExpr(this);
    }

    public List<StringPart> getParts() {
      assert children != null;
      return (List<StringPart>) children;
    }

    public Span getStartDelimiterSpan() {
      return startDelimiterSpan;
    }

    public Span getEndDelimiterSpan() {
      return endDelimiterSpan;
    }
  }

  public static final class MultiLineStringLiteralExpr extends Expr {
    private final Span startDelimiterSpan;
    private final Span endDelimiterSpan;

    public MultiLineStringLiteralExpr(
        List<StringPart> parts, Span startDelimiterSpan, Span endDelimiterSpan, Span span) {
      super(span, parts);
      this.startDelimiterSpan = startDelimiterSpan;
      this.endDelimiterSpan = endDelimiterSpan;
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitMultiLineStringLiteralExpr(this);
    }

    public List<StringPart> getParts() {
      return (List<StringPart>) children;
    }

    public Span getStartDelimiterSpan() {
      return startDelimiterSpan;
    }

    public Span getEndDelimiterSpan() {
      return endDelimiterSpan;
    }
  }

  public static final class ThrowExpr extends Expr {
    public ThrowExpr(Expr expr, Span span) {
      super(span, List.of(expr));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitThrowExpr(this);
    }

    public Expr getExpr() {
      return (Expr) children.get(0);
    }
  }

  public static final class TraceExpr extends Expr {
    public TraceExpr(Expr expr, Span span) {
      super(span, List.of(expr));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitTraceExpr(this);
    }

    public Expr getExpr() {
      return (Expr) children.get(0);
    }
  }

  public static final class ImportExpr extends Expr {
    private final boolean isGlob;

    public ImportExpr(StringConstant importStr, boolean isGlob, Span span) {
      super(span, List.of(importStr));
      this.isGlob = isGlob;
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitImportExpr(this);
    }

    public StringConstant getImportStr() {
      return (StringConstant) children.get(0);
    }

    public boolean isGlob() {
      return isGlob;
    }
  }

  public static final class ReadExpr extends Expr {
    private final ReadType readType;

    public ReadExpr(Expr expr, ReadType readType, Span span) {
      super(span, List.of(expr));
      this.readType = readType;
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitReadExpr(this);
    }

    public Expr getExpr() {
      return (Expr) children.get(0);
    }

    public ReadType getReadType() {
      return readType;
    }
  }

  public enum ReadType {
    READ,
    GLOB,
    NULL
  }

  public static final class UnqualifiedAccessExpr extends Expr {
    public UnqualifiedAccessExpr(
        Identifier identifier, @Nullable ArgumentList argumentList, Span span) {
      super(span, Arrays.asList(identifier, argumentList));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitUnqualifiedAccessExpr(this);
    }

    public Identifier getIdentifier() {
      return (Identifier) children.get(0);
    }

    public @Nullable ArgumentList getArgumentList() {
      return (ArgumentList) children.get(1);
    }
  }

  public static final class QualifiedAccessExpr extends Expr {
    private final boolean isNullable;

    public QualifiedAccessExpr(
        Expr expr,
        Identifier identifier,
        boolean isNullable,
        @Nullable ArgumentList argumentList,
        Span span) {
      super(span, Arrays.asList(expr, identifier, argumentList));
      this.isNullable = isNullable;
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitQualifiedAccessExpr(this);
    }

    public Expr getExpr() {
      return (Expr) children.get(0);
    }

    public Identifier getIdentifier() {
      return (Identifier) children.get(1);
    }

    public boolean isNullable() {
      return isNullable;
    }

    public @Nullable ArgumentList getArgumentList() {
      return (ArgumentList) children.get(2);
    }
  }

  public static final class SuperAccessExpr extends Expr {
    public SuperAccessExpr(Identifier identifier, @Nullable ArgumentList argumentList, Span span) {
      super(span, Arrays.asList(identifier, argumentList));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitSuperAccessExpr(this);
    }

    public Identifier getIdentifier() {
      return (Identifier) children.get(0);
    }

    public @Nullable ArgumentList getArgumentList() {
      return (ArgumentList) children.get(1);
    }
  }

  public static final class SuperSubscriptExpr extends Expr {
    public SuperSubscriptExpr(Expr arg, Span span) {
      super(span, List.of(arg));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitSuperSubscriptExpr(this);
    }

    public Expr getArg() {
      return (Expr) children.get(0);
    }
  }

  public static final class SubscriptExpr extends Expr {
    public SubscriptExpr(Expr expr, Expr arg, Span span) {
      super(span, List.of(expr, arg));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitSubscriptExpr(this);
    }

    public Expr getExpr() {
      return (Expr) children.get(0);
    }

    public Expr getArg() {
      return (Expr) children.get(1);
    }
  }

  public static final class IfExpr extends Expr {
    public IfExpr(Expr cond, Expr then, Expr els, Span span) {
      super(span, List.of(cond, then, els));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitIfExpr(this);
    }

    public Expr getCond() {
      return (Expr) children.get(0);
    }

    public Expr getThen() {
      return (Expr) children.get(1);
    }

    public Expr getEls() {
      return (Expr) children.get(2);
    }
  }

  public static final class LetExpr extends Expr {
    public LetExpr(Parameter parameter, Expr bindingExpr, Expr expr, Span span) {
      super(span, List.of(parameter, bindingExpr, expr));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitLetExpr(this);
    }

    public Parameter getParameter() {
      return (Parameter) children.get(0);
    }

    public Expr getBindingExpr() {
      return (Expr) children.get(1);
    }

    public Expr getExpr() {
      return (Expr) children.get(2);
    }
  }

  public static final class FunctionLiteralExpr extends Expr {
    public FunctionLiteralExpr(ParameterList parameterList, Expr expr, Span span) {
      super(span, List.of(parameterList, expr));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitFunctionLiteralExpr(this);
    }

    public ParameterList getParameterList() {
      return (ParameterList) children.get(0);
    }

    public Expr getExpr() {
      return (Expr) children.get(1);
    }
  }

  public static final class ParenthesizedExpr extends Expr {
    public ParenthesizedExpr(Expr expr, Span span) {
      super(span, List.of(expr));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitParenthesizedExpr(this);
    }

    public Expr getExpr() {
      return (Expr) children.get(0);
    }
  }

  public static final class NewExpr extends Expr {
    public NewExpr(@Nullable Type type, ObjectBody body, Span span) {
      super(span, Arrays.asList(type, body));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitNewExpr(this);
    }

    public @Nullable Type getType() {
      return (Type) children.get(0);
    }

    public ObjectBody getBody() {
      return (ObjectBody) children.get(1);
    }

    public Span newSpan() {
      return new Span(span.charIndex(), 3);
    }
  }

  public static final class AmendsExpr extends Expr {
    public AmendsExpr(Expr expr, ObjectBody body, Span span) {
      super(span, List.of(expr, body));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitAmendsExpr(this);
    }

    public Expr getExpr() {
      return (Expr) children.get(0);
    }

    public ObjectBody getBody() {
      return (ObjectBody) children.get(1);
    }
  }

  public static final class NonNullExpr extends Expr {
    public NonNullExpr(Expr expr, Span span) {
      super(span, List.of(expr));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitNonNullExpr(this);
    }

    public Expr getExpr() {
      return (Expr) children.get(0);
    }
  }

  public static final class UnaryMinusExpr extends Expr {
    public UnaryMinusExpr(Expr expr, Span span) {
      super(span, List.of(expr));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitUnaryMinusExpr(this);
    }

    public Expr getExpr() {
      return (Expr) children.get(0);
    }
  }

  public static final class LogicalNotExpr extends Expr {
    public LogicalNotExpr(Expr expr, Span span) {
      super(span, List.of(expr));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitLogicalNotExpr(this);
    }

    public Expr getExpr() {
      return (Expr) children.get(0);
    }
  }

  public static final class BinaryOperatorExpr extends Expr {
    private final Operator op;

    public BinaryOperatorExpr(Expr left, Expr right, Operator op, Span span) {
      super(span, List.of(left, right));
      this.op = op;
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitBinaryOperatorExpr(this);
    }

    public Expr getLeft() {
      return (Expr) children.get(0);
    }

    public Expr getRight() {
      return (Expr) children.get(1);
    }

    public Operator getOp() {
      return op;
    }

    @Override
    public String toString() {
      return "BinaryOp{children=" + children + ", op=" + op + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      BinaryOperatorExpr binaryOp = (BinaryOperatorExpr) o;
      return Objects.deepEquals(children, binaryOp.children)
          && op == binaryOp.op
          && Objects.equals(span, binaryOp.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(children, op, span);
    }
  }

  public static final class TypeCheckExpr extends Expr {
    public TypeCheckExpr(Expr expr, Type type, Span span) {
      super(span, List.of(expr, type));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitTypeCheckExpr(this);
    }

    public Expr getExpr() {
      return (Expr) children.get(0);
    }

    public Type getType() {
      return (Type) children.get(1);
    }
  }

  public static final class TypeCastExpr extends Expr {
    public TypeCastExpr(Expr expr, Type type, Span span) {
      super(span, List.of(expr, type));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitTypeCastExpr(this);
    }

    public Expr getExpr() {
      return (Expr) children.get(0);
    }

    public Type getType() {
      return (Type) children.get(1);
    }
  }

  /** This is a synthetic class only used at parse time. */
  public static final class OperatorExpr extends Expr {
    private final Operator op;

    public OperatorExpr(Operator op, Span span) {
      super(span, null);
      this.op = op;
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      // should never be called
      throw PklBugException.unreachableCode();
    }

    public Operator getOp() {
      return op;
    }

    @Override
    public String toString() {
      return "OperatorExpr{op=" + op + ", span=" + span + '}';
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
      return op == that.op && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(op, span);
    }
  }

  /** This is a synthetic class only used at parse time. */
  public static final class TypeExpr extends Expr {
    public TypeExpr(Type type) {
      super(type.span(), List.of(type));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      // should never be called
      throw PklBugException.unreachableCode();
    }

    public Type getType() {
      return (Type) children.get(0);
    }
  }
}
