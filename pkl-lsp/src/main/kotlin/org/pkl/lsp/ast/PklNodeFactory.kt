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

import java.io.File
import java.net.URI
import org.pkl.core.parser.Parser
import org.pkl.lsp.FsFile

object PklNodeFactory {

  @Suppress("MemberVisibilityCanBePrivate")
  fun createModule(text: String): PklModule {
    return PklModuleImpl(parser.parseModule(text), URI("fake:module"), FsFile(File(".")))
  }

  fun createTypeParameter(name: String): PklTypeParameter {
    val module = createModule("class X<$name>")
    return module.classes[0].classHeader.typeParameterList!!.typeParameters[0]
  }

  private val parser: Parser = Parser()
}
