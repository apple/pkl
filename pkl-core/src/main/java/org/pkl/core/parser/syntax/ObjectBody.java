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

import java.util.List;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;

public final class ObjectBody extends AbstractNode {
  private final int membersOffset;

  public ObjectBody(List<Node> nodes, int membersOffset, Span span) {
    super(span, nodes);
    this.membersOffset = membersOffset;
  }

  @Override
  public <T> T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitObjectBody(this);
  }

  @SuppressWarnings("unchecked")
  public List<Parameter> getParameters() {
    assert children != null;
    return (List<Parameter>) children.subList(0, membersOffset);
  }

  @SuppressWarnings("unchecked")
  public List<ObjectMember> getMembers() {
    assert children != null;
    return (List<ObjectMember>) children.subList(membersOffset, children.size());
  }
}
