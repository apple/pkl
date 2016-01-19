package org.pkl.core.resource

import org.pkl.core.Evaluator
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.core.EvaluatorBuilder
import org.pkl.core.ModuleSource
import org.pkl.core.module.ModulePathResolver
import kotlin.io.path.outputStream

class ResourceReadersEvaluatorTest {
  @Test
  fun `class path`() {
    val evaluator = Evaluator.preconfigured()

    val module = evaluator.evaluate(
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

      val evaluator = EvaluatorBuilder
        .preconfigured()
        .addResourceReader(reader)
        .build()

      val module = evaluator.evaluate(
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
