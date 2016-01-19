package org.pkl.executor

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.toPath
import org.pkl.commons.walk

class EmbeddedExecutorTest {
  private val pklDistribution by lazy {
    val libsDir = FileTestUtils.rootProjectDir.resolve("pkl-config-java/build/libs")
    if (!Files.isDirectory(libsDir)) {
      throw AssertionError(
        "JAR `pkl-config-java-all` does not exist. Run `./gradlew :pkl-config-java:build` to create it."
      )
    }
    libsDir.walk()
      .filter { path ->
        path.toString().let {
          it.contains("-all") &&
              it.endsWith(".jar") &&
              !it.contains("-sources") &&
              !it.contains("-javadoc")
        }
      }
      .findFirst()
      .orElseThrow {
        AssertionError(
          "JAR `pkl-config-java-all` does not exist. Run `./gradlew :pkl-config-java:build` to create it."
        )
      }
  }

  @Test
  fun extractMinPklVersion() {
    assertThat(
            EmbeddedExecutor.extractMinPklVersion(
                    """
      @ModuleInfo { minPklVersion = "1.2.3" }
    """.trimIndent()
            )
    ).isEqualTo(Version.parse("1.2.3"))

    assertThat(
            EmbeddedExecutor.extractMinPklVersion(
                    """
      @ModuleInfo{minPklVersion="1.2.3"}
    """.trimIndent()
            )
    ).isEqualTo(Version.parse("1.2.3"))

    assertThat(
            EmbeddedExecutor.extractMinPklVersion(
                    """
      @ModuleInfo   {   minPklVersion   =   "1.2.3"   }
    """.trimIndent()
            )
    ).isEqualTo(Version.parse("1.2.3"))

    assertThat(
            EmbeddedExecutor.extractMinPklVersion(
                    """
      @ModuleInfo {
          minPklVersion = "1.2.3"
      }
    """.trimIndent()
            )
    ).isEqualTo(Version.parse("1.2.3"))

    assertThat(
            EmbeddedExecutor.extractMinPklVersion(
                    """
      @ModuleInfo {
          author = "foo@bar.apple.com"
          minPklVersion = "1.2.3"
      }
    """.trimIndent()
            )
    ).isEqualTo(Version.parse("1.2.3"))

    assertThat(
            EmbeddedExecutor.extractMinPklVersion(
                    """
      @ModuleInfo {
          minPklVersion = "1.2.3"
          author = "foo@bar.apple.com"
      }
    """.trimIndent()
            )
    ).isEqualTo(Version.parse("1.2.3"))
  }

  @Test
  fun `create embedded executor with non-existing Pkl distribution`() {
    val e = assertThrows<IllegalArgumentException> {
        Executors.embedded(listOf("/non/existing".toPath()))
    }

    assertThat(e.message)
      .contains("Cannot find Jar file")
      .contains("/non/existing")
  }

  @Test
  fun `create embedded executor with invalid Pkl distribution that is not a Jar file`(@TempDir tempDir: Path) {
    val file = Files.createFile(tempDir.resolve("pkl.jar"))
    val e = assertThrows<IllegalArgumentException> {
        Executors.embedded(listOf(file))
    }

    assertThat(e.message)
      .contains("Cannot find service")
      .contains("pkl.jar")
  }

  @Test
  fun `evaluate a module that is missing a ModuleInfo annotation`(@TempDir tempDir: Path) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile.toFile().writeText(
      """
      module test

      x = 1
    """.trimIndent()
    )

    val executor = Executors.embedded(listOf(pklDistribution))
    val e = assertThrows<ExecutorException> {
      executor.use {
        it.evaluatePath(
          pklFile,
          ExecutorOptions(
            listOf("file:"),
            listOf("prop:"),
            mapOf(),
            mapOf(),
            listOf(),
            tempDir,
            null,
            null,
            null
          )
        )
      }
    }

    assertThat(e.message)
      .contains("Pkl module `test.pkl` does not state which Pkl version it requires.")
  }

  @Test
  fun `evaluate a module that requests an incompatible Pkl version`(@TempDir tempDir: Path) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile.toFile().writeText(
      """
      @ModuleInfo { minPklVersion = "99.99.99" }
      module test

      x = 1
    """.trimIndent()
    )

    val executor = Executors.embedded(listOf(pklDistribution))
    val e = assertThrows<ExecutorException> {
      executor.use {
        it.evaluatePath(
          pklFile,
          ExecutorOptions(
            listOf("file:"),
            listOf("prop:"),
            mapOf(),
            mapOf(),
            listOf(),
            tempDir,
            null,
            null,
            null
          )
        )
      }
    }

    assertThat(e.message)
      .contains("Pkl version `99.99.99` requested by module `test.pkl` is not supported.")
  }

  @Test
  fun `evaluate a module that reads environment variables and external properties`(@TempDir tempDir: Path) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile.toFile().writeText(
      """
      @ModuleInfo { minPklVersion = "0.11.0" }
      module test

      x = read("env:ENV_VAR")
      y = read("prop:property")
    """.trimIndent()
    )

    val executor = Executors.embedded(listOf(pklDistribution))
    val result = executor.use {
      it.evaluatePath(
        pklFile,
        ExecutorOptions(
          listOf("file:"),
          // should `prop:pkl.outputFormat` be allowed automatically?
          listOf("prop:", "env:"),
          mapOf("ENV_VAR" to "ENV_VAR"),
          mapOf("property" to "property"),
          listOf(),
          null,
          null,
          null,
          null
        )
      )
    }

    assertThat(result.trim()).isEqualTo(
      """
      x = "ENV_VAR"
      y = "property"
    """.trimIndent().trim()
    )
  }

  @Test
  fun `evaluate a module that depends on another module`(@TempDir tempDir: Path) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile.toFile().writeText(
      """
      @ModuleInfo { minPklVersion = "0.11.0" }
      amends "template.pkl"

      foo {
        bar = 42
      }
    """.trimIndent()
    )

    val templateFile = tempDir.resolve("template.pkl")
    templateFile.toFile().writeText(
      """
      foo: Foo

      class Foo {
        bar: Int
      }
    """.trimIndent()
    )

    val executor = Executors.embedded(listOf(pklDistribution))
    val result = executor.use {
      it.evaluatePath(
        pklFile,
        ExecutorOptions(
          listOf("file:"),
          listOf("prop:"),
          mapOf(),
          mapOf(),
          listOf(),
          null,
          null,
          null,
          null
        )
      )
    }

    assertThat(result.trim()).isEqualTo(
      """
      foo {
        bar = 42
      }
    """.trimIndent().trim()
    )
  }

  @Test
  fun `evaluate a module whose evaluation fails`(@TempDir tempDir: Path) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile.toFile().writeText(
      """
      @ModuleInfo { minPklVersion = "0.11.0" }
      module test

      foo = throw("ouch")
    """.trimIndent()
    )

    val executor = Executors.embedded(listOf(pklDistribution))
    val e = assertThrows<ExecutorException> {
      executor.use {
        it.evaluatePath(
          pklFile,
          ExecutorOptions(
            listOf("file:"),
            listOf("prop:"),
            mapOf(),
            mapOf(),
            listOf(),
            tempDir,
            null,
            null,
            null
          )
        )
      }
    }

    assertThat(e.message)
      .contains("ouch")
      // ensure module file paths are relativized
      .contains("at test#foo (test.pkl)")
      .doesNotContain(tempDir.toString())
  }

  @Test
  fun `time out a module`(@TempDir tempDir: Path) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile.toFile().writeText(
      """
      @ModuleInfo { minPklVersion = "0.11.0" }
      module test

      x = fib(100)

      function fib(n) = if (n < 2) n else fib(n - 1) + fib(n - 2)
    """.trimIndent()
    )

    val executor = Executors.embedded(listOf(pklDistribution))
    val e = assertThrows<ExecutorException> {
      executor.use {
        it.evaluatePath(
          pklFile,
          ExecutorOptions(
            listOf("file:"),
            listOf("prop:"),
            mapOf(),
            mapOf(),
            listOf(),
            tempDir,
            Duration.ofSeconds(1),
            null,
            null
          )
        )
      }
    }

    assertThat(e.message)
      .contains("Evaluation timed out after 1 second(s).")
  }

  // As of 0.16, only Pkl Hub modules are cached.
  // Because this test doesn't import a Pkl Hub module, it doesn't really test
  // that the `moduleCacheDir` option takes effect.
  @Test
  fun `evaluate a module with enabled module cache`(@TempDir tempDir: Path) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile.toFile().writeText(
      """
      @ModuleInfo { minPklVersion = "0.16.0" }
      module test

      x = 42
    """.trimIndent()
    )

    val executor = Executors.embedded(listOf(pklDistribution))
    val result = executor.use {
      it.evaluatePath(
        pklFile,
        ExecutorOptions(
          listOf("file:"),
          listOf("prop:"),
          mapOf(),
          mapOf(),
          listOf(),
          null,
          null,
          null,
          ExecutorOptions.defaultModuleCacheDir()
        )
      )
    }

    assertThat(result.trim()).isEqualTo(
      """
      x = 42
    """.trimIndent().trim()
    )
  }
}
