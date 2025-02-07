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

import java.util.ArrayList;
import java.util.List;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

@SuppressWarnings("DataFlowIssue")
public final class Module extends AbstractNode {
  public Module(List<Node> nodes, Span span) {
    super(span, nodes);
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitModule(this);
  }

  public @Nullable ModuleDecl getDecl() {
    return (ModuleDecl) children.get(0);
  }

  public List<Import> getImports() {
    if (children.size() < 2) return List.of();
    var res = new ArrayList<Import>();
    for (int i = 1; i < children.size(); i++) {
      var child = children.get(i);
      if (child instanceof Import imp) {
        res.add(imp);
      } else {
        // imports are sequential
        break;
      }
    }
    return res;
  }

  public List<Class> getClasses() {
    var res = new ArrayList<Class>();
    for (var child : children) {
      if (child instanceof Class clazz) {
        res.add(clazz);
      }
    }
    return res;
  }

  public List<TypeAlias> getTypeAliases() {
    var res = new ArrayList<TypeAlias>();
    for (var child : children) {
      if (child instanceof TypeAlias typeAlias) {
        res.add(typeAlias);
      }
    }
    return res;
  }

  public List<ClassProperty> getProperties() {
    var res = new ArrayList<ClassProperty>();
    for (var child : children) {
      if (child instanceof ClassProperty classProperty) {
        res.add(classProperty);
      }
    }
    return res;
  }

  public List<ClassMethod> getMethods() {
    var res = new ArrayList<ClassMethod>();
    for (var child : children) {
      if (child instanceof ClassMethod classMethod) {
        res.add(classMethod);
      }
    }
    return res;
  }
}
