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
package org.pkl.core.resource

import java.nio.file.Path
import kotlin.io.path.outputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.core.Evaluator
import org.pkl.core.EvaluatorBuilder
import org.pkl.core.ModuleSource
import org.pkl.core.module.ModulePathResolver

class ResourceReadersEvaluatorTest {
  @Test
  fun `class path`() {
    val evaluator = Evaluator.preconfigured()

    val module =
      evaluator.evaluate(
        ModuleSource.text(
          """
        res1 = read("modulepath:/org/pkl/core/resource/resource.txt").text
        """
        )
      )

    assertThat(module.getProperty("res1")).isEqualTo("content")
  }

  @Test
  fun `module path`(@TempDir tempDir: Path) {
    val jarFile = tempDir.resolve("resource1.jar")
    jarFile.outputStream().use { outStream ->
      javaClass.getResourceAsStream("resource1.jar")!!.use { inStream ->
        inStream.copyTo(outStream)
      }
    }

    ModulePathResolver(listOf(jarFile)).use { resolver ->
      val reader = ResourceReaders.modulePath(resolver)

      val evaluator = EvaluatorBuilder.preconfigured().addResourceReader(reader).build()

      val module =
        evaluator.evaluate(
          ModuleSource.text(
            """
          res1 = read("modulepath:/dir1/resource1.txt").text
          """
          )
        )

      assertThat(module.getProperty("res1")).isEqualTo("content\n")
    }
  }
}
