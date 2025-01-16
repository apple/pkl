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
package org.pkl.core.newparser.cst;

import java.util.List;
import org.pkl.core.newparser.Span;
import org.pkl.core.util.Nullable;

public sealed interface ClassEntry extends ModuleEntry {

  record ClassProperty(
      @Nullable DocComment docComment,
      List<Annotation> annotations,
      List<Modifier> modifiers,
      Ident name,
      Type type,
      Span span)
      implements ClassEntry {}

  record ClassPropertyExpr(
      @Nullable DocComment docComment,
      List<Annotation> annotations,
      List<Modifier> modifiers,
      Ident name,
      @Nullable Type type,
      Expr expr,
      Span span)
      implements ClassEntry {}

  record ClassPropertyBody(
      @Nullable DocComment docComment,
      List<Annotation> annotations,
      List<Modifier> modifiers,
      Ident name,
      List<ObjectBody> bodyList,
      Span span)
      implements ClassEntry {}

  record ClassMethod(
      @Nullable DocComment docComment,
      List<Annotation> annotations,
      List<Modifier> modifiers,
      Ident name,
      List<TypeParameter> typePars,
      List<Parameter> args,
      @Nullable Type returnType,
      @Nullable Expr expr,
      Span span)
      implements ClassEntry {}

  default Span getSpan() {
    if (this instanceof ClassProperty x) return x.span;
    if (this instanceof ClassPropertyExpr x) return x.span;
    if (this instanceof ClassPropertyBody x) return x.span;
    if (this instanceof ClassMethod x) return x.span;
    throw new RuntimeException("unknown class entry " + this);
  }
}
