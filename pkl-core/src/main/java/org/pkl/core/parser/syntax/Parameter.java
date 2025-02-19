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
package org.pkl.core.parser.syntax;

import java.util.Arrays;
import java.util.List;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

public abstract sealed class Parameter extends AbstractNode {

  public Parameter(Span span, @Nullable List<? extends @Nullable Node> children) {
    super(span, children);
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitParameter(this);
  }

  public static final class Underscore extends Parameter {
    public Underscore(Span span) {
      super(span, null);
    }
  }

  public static final class TypedIdentifier extends Parameter {
    public TypedIdentifier(
        Identifier identifier, @Nullable TypeAnnotation typeAnnotation, Span span) {
      super(span, Arrays.asList(identifier, typeAnnotation));
    }

    public Identifier getIdentifier() {
      assert children != null;
      return (Identifier) children.get(0);
    }

    public @Nullable TypeAnnotation getTypeAnnotation() {
      assert children != null;
      return (TypeAnnotation) children.get(1);
    }
  }
}
