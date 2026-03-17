/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.util.Arrays;
import org.pkl.parser.ParserVisitor;
import org.pkl.parser.Span;
import org.pkl.parser.util.Nullable;

public final class ImportDeconstruction extends AbstractNode {

  public ImportDeconstruction(Identifier name, @Nullable Identifier alias, Span span) {
    super(span, Arrays.asList(name, alias));
  }

  public Identifier getName() {
    assert children != null;
    var name = (Identifier) children.get(0);
    assert name != null;
    return name;
  }

  public @Nullable Identifier getAlias() {
    assert children != null;
    return (Identifier) children.get(1);
  }

  @Override
  public <T> T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitImportDeconstruction(this);
  }
}
