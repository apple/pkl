/*
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
package org.pkl.core.module

import java.net.URI
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.createTempFile
import org.pkl.commons.writeString

class ResolvedModuleKeysTest {
  private val module = ModuleKeys.synthetic(URI("test:module"), "x = 1")

  @Test
  fun `path()`(@TempDir tempDir: Path) {
    val path = tempDir.createTempFile().writeString("x = 1")
    val resolvedUri = URI("test:resolved.uri")
    val resolved = ResolvedModuleKeys.file(module, resolvedUri, path)

    assertThat(resolved.original).isSameAs(module)
    assertThat(resolved.uri).isEqualTo(resolvedUri)
    assertThat(resolved.loadSource()).isEqualTo("x = 1")
  }

  @Test
  fun `url()`(@TempDir tempDir: Path) {
    val path = tempDir.createTempFile().writeString("x = 1")
    val resolvedUri = URI("test:resolved.uri")
    val resolved = ResolvedModuleKeys.url(module, resolvedUri, path.toUri().toURL())

    assertThat(resolved.original).isSameAs(module)
    assertThat(resolved.uri).isEqualTo(resolvedUri)
    assertThat(resolved.loadSource()).isEqualTo("x = 1")
  }

  @Test
  fun `virtual()`() {
    val resolvedUri = URI("test:resolved.uri")
    val resolved = ResolvedModuleKeys.virtual(module, resolvedUri, "x = 1", false)

    assertThat(resolved.original).isSameAs(module)
    assertThat(resolved.uri).isEqualTo(resolvedUri)
    assertThat(resolved.loadSource()).isEqualTo("x = 1")
  }
}
