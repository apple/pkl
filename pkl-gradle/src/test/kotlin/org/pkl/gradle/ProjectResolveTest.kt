/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.gradle

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProjectResolveTest : AbstractTest() {
  @Test
  fun basic() {
    writeBuildFile()
    writeProjectContent()
    runTask("resolveMyProj")
    assertThat(testProjectDir.resolve("proj1/PklProject.deps.json"))
      .hasContent(
        """
      {
        "schemaVersion": 1,
        "resolvedDependencies": {}
      }
    """
          .trimIndent()
      )
  }

  private fun writeBuildFile(additionalContents: String = "") {
    writeFile(
      "build.gradle",
      """
      plugins {
        id "org.pkl-lang"
      }

      pkl {
        project {
          resolvers {
            resolveMyProj {
              projectDirectories.from(file("proj1"))
              settingsModule = "pkl:settings"
              $additionalContents
            }
          }
        }
      }
    """,
    )
  }

  private fun writeProjectContent() {
    writeFile(
      "proj1/PklProject",
      """
      amends "pkl:Project"
    """
        .trimIndent(),
    )
  }
}
