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
package org.pkl.commons.test

import java.nio.file.Path
import kotlin.io.path.*
import kotlin.streams.toList
import org.assertj.core.api.Assertions.fail
import org.pkl.commons.*
import org.pkl.commons.createParentDirectories

object FileTestUtils {
  val rootProjectDir: Path by lazy {
    val workingDir = currentWorkingDir
    workingDir.takeIf { it.resolve("settings.gradle.kts").exists() }
      ?: workingDir.parent.takeIf { it.resolve("settings.gradle.kts").exists() }
        ?: workingDir.parent.parent.takeIf { it.resolve("settings.gradle.kts").exists() }
        ?: throw AssertionError("Failed to locate root project directory.")
  }
  val selfSignedCertificate: Path by lazy {
    rootProjectDir.resolve("pkl-commons-test/build/keystore/localhost.pem")
  }
}

fun Path.listFilesRecursively(): List<Path> =
  walk(99).use { paths -> paths.filter { it.isRegularFile() || it.isSymbolicLink() }.toList() }

data class SnippetOutcome(val expectedOutFile: Path, val actual: String, val success: Boolean) {
  private val expectedErrFile =
    expectedOutFile.resolveSibling(expectedOutFile.toString().replaceAfterLast('.', "err"))

  private val expectedOutExists = expectedOutFile.exists()
  private val expectedErrExists = expectedErrFile.exists()
  private val overwrite
    get() = System.getenv().containsKey("OVERWRITE_SNIPPETS")

  private val expected by lazy {
    when {
      expectedOutExists && expectedErrExists ->
        fail("Test has both expected out and .err files: $displayName")
      expectedOutExists -> expectedOutFile.readString()
      expectedErrExists -> expectedErrFile.readString()
      else -> ""
    }
  }

  private val displayName by lazy {
    val path = expectedOutFile.toString()
    val baseDir = "src/test/files"
    val index = path.indexOf(baseDir)
    val endIndex = path.lastIndexOf('.')
    if (index == -1 || endIndex == -1) path else path.substring(index + baseDir.length, endIndex)
  }

  fun check() {
    when {
      success && !expectedOutExists && !expectedErrExists && actual.isBlank() -> return
      !success && expectedOutExists && !overwrite ->
        failWithDiff("Test was expected to succeed, but failed: $displayName")
      !success && expectedOutExists -> {
        expectedOutFile.deleteExisting()
        expectedErrFile.writeString(actual)
        fail("Wrote file $expectedErrFile for $displayName and deleted $expectedOutFile")
      }
      success && expectedErrExists && !overwrite ->
        failWithDiff("Test was expected to fail, but succeeded: $displayName")
      success && expectedErrExists -> {
        expectedErrFile.deleteExisting()
        expectedOutFile.writeString(actual)
        fail("Wrote file $expectedOutFile for $displayName and deleted $expectedErrFile")
      }
      !expectedOutExists && !expectedErrExists && actual.isNotBlank() -> {
        val file = if (success) expectedOutFile else expectedErrFile
        file.createParentDirectories().writeString(actual)
        failWithDiff("Created missing file $file for $displayName")
      }
      else -> {
        assert(success && expectedOutExists || !success && expectedErrExists)
        if (actual != expected) {
          if (overwrite) {
            val file = if (success) expectedOutFile else expectedErrFile
            file.writeString(actual)
            fail("Overwrote file $file for $displayName")
          } else {
            failWithDiff("Output was different from expected: $displayName")
          }
        }
      }
    }
  }

  private fun failWithDiff(message: String): Nothing =
    throw PklAssertionFailedError(message, expected, actual)
}
