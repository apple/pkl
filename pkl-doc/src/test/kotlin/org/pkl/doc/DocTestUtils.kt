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
package org.pkl.doc

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.readBytes
import kotlin.io.path.readSymbolicLink
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.pkl.commons.readString
import org.pkl.commons.toPath
import org.pkl.core.util.IoUtils

object DocTestUtils {

  private val binaryFileExtensions = setOf("woff2", "png", "svg")

  fun testExpectedFile(expectedOutputDir: Path, actualOutputDir: Path, relativeFilePath: String) {
    val actualFile = actualOutputDir.resolve(relativeFilePath)
    assertThat(actualFile)
      .withFailMessage("Test bug: $actualFile should exist but does not.")
      .exists()

    // symlinks on Git and Windows is rather finnicky; they create shortcuts by default unless
    // a core Git option is set. Also, by default, symlinks require administrator privileges to run.
    // We'll just test that the symlink got created but skip verifying that it points to the right
    // place.
    if (actualFile.isSymbolicLink() && IoUtils.isWindows()) return
    val expectedFile = expectedOutputDir.resolve(relativeFilePath)
    if (expectedFile.exists()) {
      when {
        expectedFile.isSymbolicLink() -> {
          assertThat(actualFile).isSymbolicLink
          assertThat(expectedFile.readSymbolicLink().toString().toPath())
            .isEqualTo(actualFile.readSymbolicLink().toString().toPath())
        }
        expectedFile.extension in binaryFileExtensions ->
          assertThat(actualFile.readBytes()).isEqualTo(expectedFile.readBytes())
        else -> assertThat(actualFile.readString()).isEqualTo(expectedFile.readString())
      }
    } else {
      expectedFile.createParentDirectories()
      if (actualFile.isSymbolicLink()) {
        // needs special handling because `copyTo` can't copy symlinks between file systems
        val linkTarget = actualFile.readSymbolicLink()
        assertThat(linkTarget).isRelative
        Files.createSymbolicLink(expectedFile, linkTarget.toString().toPath())
      } else {
        actualFile.copyTo(expectedFile)
      }
      Assertions.fail("Created missing expected file `$relativeFilePath`.")
    }
  }
}
