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
package org.pkl.parser.syntax;

import java.util.List;
import java.util.stream.Collectors;
import org.pkl.parser.ParserVisitor;

public final class QualifiedIdentifier extends AbstractNode {
  public QualifiedIdentifier(List<Identifier> identifiers) {
    super(
        identifiers.get(0).span.endWith(identifiers.get(identifiers.size() - 1).span), identifiers);
  }

  @Override
  public <T> T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitQualifiedIdentifier(this);
  }

  @SuppressWarnings("unchecked")
  public List<Identifier> getIdentifiers() {
    assert children != null;
    return (List<Identifier>) children;
  }

  public String text() {
    return getIdentifiers().stream().map(Identifier::getValue).collect(Collectors.joining("."));
  }
}
