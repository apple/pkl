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
package org.pkl.lsp

import java.io.IOException
import java.net.URI
import org.pkl.core.parser.Parser
import org.pkl.core.util.IoUtils
import org.pkl.lsp.ast.PklModule
import org.pkl.lsp.ast.PklModuleImpl

object Stdlib {

  fun baseModule(): PklModule = stdlibModules["base"]!!

  fun getModule(name: String): PklModule? = stdlibModules[name]

  fun allModules(): Map<String, PklModule> = stdlibModules

  private fun loadStdlib() {
    val parser = Parser()
    for (name in stdlibModuleNames) {
      loadStdlibModule(name, parser)
    }
  }

  private fun loadStdlibModule(name: String, parser: Parser) {
    val text = loadStdlibSource(name)
    val moduleCtx = parser.parseModule(text)
    val module = PklModuleImpl(moduleCtx, URI("pkl:$name"), StdlibFile(name))
    stdlibModules[name] = module
  }

  @Throws(IOException::class)
  private fun loadStdlibSource(module: String): String {
    return IoUtils.readClassPathResourceAsString(javaClass, "/org/pkl/core/stdlib/$module.pkl")
  }

  private val stdlibModules = mutableMapOf<String, PklModule>()

  private val stdlibModuleNames =
    listOf(
      "base",
      "Benchmark",
      "DocPackageInfo",
      "DocsiteInfo",
      "json",
      "jsonnet",
      "math",
      "platform",
      "Project",
      "protobuf",
      "reflect",
      "release",
      "semver",
      "settings",
      "shell",
      "test",
      "xml",
      "yaml"
    )

  init {
    loadStdlib()
  }
}
