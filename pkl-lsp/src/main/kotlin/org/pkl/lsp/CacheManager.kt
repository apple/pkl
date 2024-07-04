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

import java.net.URI
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import org.pkl.lsp.ast.PklModule

/** Manages all Pkl files that are not local to the file system: http(s), packages. */
object CacheManager {
  val pklDir = Path.of(System.getProperty("user.home")).resolve(".pkl")
  val pklCacheDir = pklDir.resolve(".cache")

  val lspCacheDir = Files.createTempDirectory("pkl-lsp-cache")

  private fun findHttpPath(uri: URI): Path? {
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    val encoded = URLEncoder.encode(uri.toString(), Charsets.UTF_8)
    return lspCacheDir.resolve(encoded)
  }

  fun findHttpContent(uri: URI): String? {
    val path = findHttpPath(uri) ?: return null
    return if (path.exists() && path.isRegularFile() && path.isReadable()) {
      // uri is cached
      path.readText()
    } else {
      val content = uri.toURL().readText()
      path.writeText(content)
      content
    }
  }

  fun findHttpModule(uri: URI): PklModule? {
    return findHttpContent(uri)?.let { contents ->
      Builder.fileToModule(contents, uri, HttpsFile(uri))
    }
  }
}
