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
import com.diffplug.spotless.FormatterFunc
import com.diffplug.spotless.FormatterStep
import java.io.File
import java.io.Serial
import java.io.Serializable

/**
 * A Spotless [FormatterStep] that suppresses formatting changes where the only difference between
 * the formatted output and the file's content in the upstream base ref is the license header year.
 *
 * Avoids an issue where, in the process of working on the codebase:
 * 1. A file is modified.
 * 2. Spotless formats the file, and also updates the copyright year.
 * 3. The original modification is reverted.
 * 4. Spotless formats the file again, but now the copyright year is the updated year.
 */
class RevertYearOnlyChangesStep(private val repoRoot: File, private val ratchetFrom: String) :
  Serializable {
  companion object {
    @Serial private const val serialVersionUID: Long = 1L
  }

  fun create(): FormatterStep =
    FormatterStep.createLazy(
      "revertYearOnlyChanges",
      { this },
      { RevertYearOnlyChangesFunc(repoRoot, ratchetFrom) },
    )
}

class RevertYearOnlyChangesFunc(private val repoRoot: File, private val ratchetFrom: String) :
  FormatterFunc.NeedsFile, Serializable {
  companion object {
    @Serial private const val serialVersionUID: Long = 1L

    // Matches "Copyright © 2024" or "Copyright © 2024-2025"
    private val YEAR_REGEX = Regex("""(Copyright © )\d{4}(-\d{4})?""")
  }

  override fun applyWithFile(unix: String, file: File): String {
    val relativePath = repoRoot.toPath().relativize(file.toPath()).toString()
    val upstreamContent = gitShow(ratchetFrom, relativePath) ?: return unix
    val normalizedRaw = YEAR_REGEX.replace(unix, "\$1YEAR")
    val normalizedUpstream = YEAR_REGEX.replace(upstreamContent, "\$1YEAR")
    return if (normalizedRaw == normalizedUpstream) {
      // Only the year changed — return the upstream content
      upstreamContent
    } else {
      unix
    }
  }

  private fun gitShow(ref: String, path: String): String? {
    val process =
      ProcessBuilder("git", "show", "$ref:$path")
        .directory(repoRoot)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
    return if (process.waitFor() == 0) output.replace("\r\n", "\n") else null
  }
}
