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
package org.pkl.parser.syntax;

public enum Operator {
  NULL_COALESCE(1, false),
  PIPE(2, true),
  OR(3, true),
  AND(4, true),
  EQ_EQ(5, true),
  NOT_EQ(5, true),
  IS(6, true),
  AS(6, true),
  LT(7, true),
  GT(7, true),
  LTE(7, true),
  GTE(7, true),
  PLUS(8, true),
  MINUS(8, true),
  MULT(9, true),
  DIV(9, true),
  INT_DIV(9, true),
  MOD(9, true),
  POW(10, false),
  NON_NULL(16, true),
  SUBSCRIPT(18, true),
  DOT(20, true),
  QDOT(20, true);

  private final int prec;
  private final boolean isLeftAssoc;

  Operator(int prec, boolean isLeftAssoc) {
    this.prec = prec;
    this.isLeftAssoc = isLeftAssoc;
  }

  public int getPrec() {
    return prec;
  }

  public boolean isLeftAssoc() {
    return isLeftAssoc;
  }
}
