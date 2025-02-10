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

import java.util.List;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

@SuppressWarnings("ALL")
public abstract sealed class StringPart extends AbstractNode {

  public StringPart(Span span, @Nullable List<? extends @Nullable Node> children) {
    super(span, children);
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitStringPart(this);
  }

  public static final class StringConstantParts extends StringPart {
    public StringConstantParts(List<StringConstantPart> parts, Span span) {
      super(span, parts);
    }

    public List<StringConstantPart> getParts() {
      return (List<StringConstantPart>) children;
    }
  }

  public static final class StringInterpolation extends StringPart {
    public StringInterpolation(Expr expr, Span span) {
      super(span, List.of(expr));
    }

    public Expr getExpr() {
      return (Expr) children.get(0);
    }
  }
}
