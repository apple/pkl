/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.lsp.ast

import org.pkl.core.parser.antlr.PklParser.QualifiedIdentifierContext
import org.pkl.core.parser.antlr.PklParser.StringConstantContext
import org.pkl.lsp.PklVisitor

class PklQualifiedIdentifierImpl(
  override val parent: Node,
  override val ctx: QualifiedIdentifierContext
) : AbstractNode(parent, ctx), PklQualifiedIdentifier {
  override val identifiers: List<Terminal> by lazy { terminals }
  override val fullName: String by lazy { text }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitQualifiedIdentifier(this)
  }
}

class PklStringConstantImpl(override val parent: Node, override val ctx: StringConstantContext) :
  AbstractNode(parent, ctx), PklStringConstant {
  override val value: String by lazy { text }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitStringConstant(this)
  }
}
