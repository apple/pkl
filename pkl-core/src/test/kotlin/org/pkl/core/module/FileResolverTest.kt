/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.writeString
import org.pkl.core.EvaluatorBuilder
import org.pkl.core.ModuleSource

class FileResolverTest {
  @Test
  fun `includes symlink whose target is a regular file`(@TempDir tempDir: Path) {
    val listedDir = tempDir.resolve("listed").createDirectory()
    val otherDir = tempDir.resolve("other").createDirectory()
    listedDir.resolve("real.pkl").createFile()
    val target = otherDir.resolve("target.pkl").createFile()
    Files.createSymbolicLink(listedDir.resolve("linked.pkl"), target)

    val elements = FileResolver.listElements(listedDir)

    assertThat(elements)
      .containsExactlyInAnyOrder(PathElement("real.pkl", false), PathElement("linked.pkl", false))
  }

  @Test
  fun `includes symlink whose target is a directory`(@TempDir tempDir: Path) {
    val listedDir = tempDir.resolve("listed").createDirectory()
    val otherDir = tempDir.resolve("other").createDirectory()
    listedDir.resolve("real.pkl").createFile()
    Files.createSymbolicLink(listedDir.resolve("linked-dir"), otherDir)

    val elements = FileResolver.listElements(listedDir)

    assertThat(elements)
      .containsExactlyInAnyOrder(PathElement("real.pkl", false), PathElement("linked-dir", true))
  }

  @Test
  fun `includes broken symlink as non-directory entry`(@TempDir tempDir: Path) {
    tempDir.resolve("real.pkl").createFile()
    Files.createSymbolicLink(tempDir.resolve("broken.pkl"), tempDir.resolve("missing.pkl"))

    val elements = FileResolver.listElements(tempDir)

    assertThat(elements)
      .containsExactlyInAnyOrder(PathElement("real.pkl", false), PathElement("broken.pkl", false))
  }

  // The OS error message for symlink cycles is platform-specific.
  @Test
  @DisabledOnOs(OS.WINDOWS)
  fun `glob expansion surfaces symlink cycle`(@TempDir tempDir: Path) {
    val sources = tempDir.resolve("sources").createDirectory()
    sources.resolve("real.pkl").createFile()
    Files.createSymbolicLink(sources.resolve("loop"), sources)

    val main = tempDir.resolve("main.pkl")
    main.writeString("""result = import*("sources/**.pkl")""")

    EvaluatorBuilder.preconfigured().build().use { evaluator ->
      assertThatCode { evaluator.evaluateOutputText(ModuleSource.uri(main.toUri())) }
        .hasMessageContaining("Too many levels of symbolic links")
    }
  }
}
