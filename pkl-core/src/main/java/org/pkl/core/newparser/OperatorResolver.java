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
import org.pkl.core.newparser.cst.Expr;
import org.pkl.core.newparser.cst.Expr.OperatorExpr;
import org.pkl.core.newparser.cst.Expr.TypeExpr;
import org.pkl.core.newparser.cst.Operator;
import org.pkl.core.util.Nullable;

class OperatorResolver {
  private OperatorResolver() {}

  private enum Associativity {
    LEFT,
    RIGHT
  }

  public static int getPrecedence(Operator op) {
    return switch (op) {
      case NULL_COALESCE -> 0;
      case PIPE -> 1;
      case OR -> 2;
      case AND -> 3;
      case EQ_EQ, NOT_EQ -> 4;
      case IS, AS -> 5;
      case LT, LTE, GT, GTE -> 6;
      case PLUS, MINUS -> 7;
      case MULT, DIV, INT_DIV, MOD -> 8;
      case POW -> 9;
      case DOT, QDOT -> 10;
    };
  }

  private static Associativity getAssociativity(Operator op) {
    return switch (op) {
      case POW, NULL_COALESCE -> Associativity.RIGHT;
      default -> Associativity.LEFT;
    };
  }

  private static @Nullable Operator getHighestPrecedence(List<Expr> exprs, int min) {
    var highest = -1;
    Operator op = null;
    for (var expr : exprs) {
      if (expr instanceof OperatorExpr o) {
        var precedence = getPrecedence(o.op());
        if (precedence > highest && precedence >= min) {
          highest = precedence;
          op = o.op();
        }
      }
    }
    return op;
  }

  private static int index(List<Expr> exprs, Associativity associativity, Operator op) {
    if (associativity == Associativity.LEFT) {
      for (var i = 0; i < exprs.size(); i++) {
        if (exprs.get(i) instanceof OperatorExpr operator && operator.op() == op) {
          return i;
        }
      }
    } else {
      for (var i = exprs.size() - 1; i >= 0; i--) {
        if (exprs.get(i) instanceof OperatorExpr operator && operator.op() == op) {
          return i;
        }
      }
    }
    return -1;
  }

  private static List<Expr> resolveOperator(
      List<Expr> exprs, Associativity associativity, Operator op) {
    var res = new ArrayList<>(exprs);

    var i = index(res, associativity, op);
    var left = res.get(i - 1);
    var right = res.get(i + 1);
    var span = left.getSpan().endWith(right.getSpan());
    var binOp =
        switch (op) {
          case IS -> new Expr.TypeCheck(left, ((TypeExpr) right).type(), span);
          case AS -> new Expr.TypeCast(left, ((TypeExpr) right).type(), span);
          default -> new Expr.BinaryOp(left, right, op, span);
        };
    res.remove(i - 1);
    res.remove(i - 1);
    res.remove(i - 1);
    res.add(i - 1, binOp);
    return res;
  }

  /**
   * Resolve all operators based on their precedence and associativity. This requires that the list
   * has a valid form: `expr` `op` `expr` ...
   */
  public static Expr resolveOperators(List<Expr> exprs) {
    if (exprs.size() == 1) return exprs.get(0);

    var res = resolveOperatorsHigherThan(exprs, 0);
    if (res.size() > 1) {
      throw new ParserError(
          "Malformed expression",
          exprs.get(0).getSpan().endWith(exprs.get(exprs.size() - 1).getSpan()));
    }

    return res.get(0);
  }

  public static List<Expr> resolveOperatorsHigherThan(List<Expr> exprs, int minPrecedence) {
    var res = exprs;
    var highest = getHighestPrecedence(res, minPrecedence);
    while (highest != null) {
      var associativity = getAssociativity(highest);
      res = resolveOperator(res, associativity, highest);
      highest = getHighestPrecedence(res, minPrecedence);
    }

    return res;
  }
}
