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

import org.pkl.core.parser.Parser
import org.pkl.core.util.IoUtils
import org.pkl.lsp.cst.CstBuilder
import org.pkl.lsp.cst.PklModule
import java.net.URI
import java.util.concurrent.CompletableFuture

class Builder {
  private var runningBuild: CompletableFuture<PklModule?> = CompletableFuture.supplyAsync(::noop)
  
  private val parser = Parser()
  private val cstBuilder = CstBuilder()
  
  fun requestBuild(file: URI) {
    runningBuild = CompletableFuture.supplyAsync { build(file) }
  }
  
  private fun build(file: URI): PklModule {
    try {
      val moduleCtx = parser.parseModule(IoUtils.readString(file.toURL()))
      return cstBuilder.visitModule(moduleCtx)
    } finally {
    }
  }
  
  private fun noop(): PklModule? {
    return null
  }
}
