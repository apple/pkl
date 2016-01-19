package org.pkl.core

import org.pkl.commons.createParentDirectories
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.createTempFile
import org.pkl.commons.toPath
import org.pkl.commons.writeString
import org.pkl.core.ModuleSource.*
import org.pkl.core.util.IoUtils
import org.junit.jupiter.api.AfterAll
import java.nio.file.FileSystems
import kotlin.io.path.writeText

class EvaluatorTest {
  companion object {
    private val evaluator = Evaluator.preconfigured()

    private const val sourceText = "name = \"pigeon\"; age = 10 + 20"

    @AfterAll
    @JvmStatic
    fun afterAll() {
      evaluator.close()
    }
  }

  @Test
  fun `evaluate text`() {
    val module = evaluator.evaluate(text(sourceText))
    checkModule(module)
  }

  @Test
  fun `evaluate text with relative import`() {
    val e = assertThrows<PklException> {
      evaluator.evaluate(text("import \"foo.bar\""))
    }
    assertThat(e)
      .hasMessageContaining("Module `repl:text` cannot have a relative import URI.")
  }

  @Test
  fun `evaluate named module`() {
    val module = evaluator.evaluate(
      modulePath("org/pkl/core/EvaluatorTest.pkl")
    )
    checkModule(module)
  }

  @Test
  fun `evaluate non-existing named module`() {
    val e = assertThrows<PklException> {
      evaluator.evaluate(modulePath("non/existing.pkl"))
    }
    assertThat(e)
      .hasMessageContaining("Cannot find module `modulepath:/non/existing.pkl`.")
  }

  @Test
  fun `evaluate file`(@TempDir tempDir: Path) {
    val file = tempDir.createTempFile()
    Files.writeString(file, sourceText)

    val module = evaluator.evaluate(path(file))
    checkModule(module)
  }

  @Test
  fun `evaluate non-existing file`() {
    val e = assertThrows<PklException> {
      evaluator.evaluate(file(File("/non/existing")))
    }
    assertThat(e)
      .hasMessageContaining("Cannot find module `file:///non/existing`.")
  }

  @Test
  fun `evaluate path`(@TempDir tempDir: Path) {
    val path = tempDir.createTempFile()
    Files.writeString(path, sourceText)

    val module = evaluator.evaluate(path(path))
    checkModule(module)
  }

  @Test
  fun `evaluate non-existing path`() {
    val e = assertThrows<PklException> {
      evaluator.evaluate(path("/non/existing".toPath()))
    }
    assertThat(e)
      .hasMessageContaining("Cannot find module `file:///non/existing`.")
  }
  
  @Test
  fun `evaluate zip file system path`(@TempDir tempDir: Path) {
    val zipFile = createModulesZip(tempDir)
    // cast required to compile on JDK 14+ (which adds new overload)
    FileSystems.newFileSystem(zipFile, null as ClassLoader?).use { zipFs ->
      val file = zipFs.getPath("foo/bar/module1.pkl")
      val module = evaluator.evaluate(path(file))
      checkModule(module)
    }
  }

  @Test
  fun `evaluate non-existing zip file system path`(@TempDir tempDir: Path) {
    val zipFile = createModulesZip(tempDir)
    // cast required to compile on JDK 14+ (which adds new overload)
    FileSystems.newFileSystem(zipFile, null as ClassLoader?).use { zipFs ->
      val file = zipFs.getPath("non/existing")
      val e = assertThrows<PklException> {
        evaluator.evaluate(path(file))
      }
      assertThat(e)
        .hasMessageContaining("Cannot find module `jar:file:")
        .hasMessageContaining("non/existing")
    }
  }

  @Test
  fun `evaluate URI`(@TempDir tempDir: Path) {
    val path = tempDir.createTempFile()
    Files.writeString(path, sourceText)

    val module = evaluator.evaluate(uri(path.toUri()))
    checkModule(module)
  }

  @Test
  fun `evaluate non-existing URI`() {
    val e = assertThrows<PklException> {
      evaluator.evaluate(uri(URI("https://localhost/non/existing")))
    }
    assertThat(e)
      .hasMessageContaining("I/O error loading module `https://localhost/non/existing`.")
  }

  @Test
  fun `evaluate jar URI`(@TempDir tempDir: Path) {
    val zipFile = createModulesZip(tempDir)
    val module = evaluator.evaluate(uri(URI("jar:${zipFile.toUri()}!/foo/bar/module1.pkl")))
    checkModule(module)
  }

  @Test
  fun `evaluate jar URI with non-existing archive`() {
    val moduleUri = URI("jar:file:///non/existing!/bar.pkl")
    val e = assertThrows<PklException> {
      evaluator.evaluate(uri(moduleUri))
    }
    assertThat(e)
      .hasMessageContaining("Cannot find module `$moduleUri`.")
  }

  @Test
  fun `evaluate jar URI with non-existing archive path`(@TempDir tempDir: Path) {
    val zipFile = createModulesZip(tempDir)
    val moduleUri = URI("jar:${zipFile.toUri()}!/non/existing")
    val e = assertThrows<PklException> {
      evaluator.evaluate(uri(moduleUri))
    }
    assertThat(e)
      .hasMessageContaining("Cannot find module `$moduleUri`.")
  }

  @Test
  fun `evaluate module with relative URI`() {
    val e = assertThrows<PklException> {
      evaluator.evaluate(create(URI("foo.bar"), ""))
    }

    assertThat(e)
      .hasMessageContaining("Cannot evaluate relative module URI `foo.bar`.")
  }

  @Test
  fun `evaluating a broken module multiple times results in the same error every time`() {
    val e1 = assertThrows<PklException> {
      evaluator.evaluate(modulePath("org/pkl/core/brokenModule1.pkl"))
    }
    val e2 = assertThrows<PklException> {
      evaluator.evaluate(modulePath("org/pkl/core/brokenModule1.pkl"))
    }
    assertThat(e2.message).isEqualTo(e1.message)

    val e3 = assertThrows<PklException> {
      evaluator.evaluate(modulePath("org/pkl/core/brokenModule2.pkl"))
    }
    val e4 = assertThrows<PklException> {
      evaluator.evaluate(modulePath("org/pkl/core/brokenModule2.pkl"))
    }
    assertThat(e4.message).isEqualTo(e3.message)
  }

  @Test
  fun `evaluation timeout`() {
    val evaluator = EvaluatorBuilder.preconfigured()
      .setTimeout(java.time.Duration.ofMillis(100))
      .build()
    val e = assertThrows<PklException> {
      evaluator.evaluate(text(
        """
        function fib(n) = if (n < 2) 0 else fib(n - 1) + fib(n - 2)
        x = fib(100)
      """.trimIndent()
      ))
    }
    assertThat(e.message).contains("timed out")
  }

  @Test
  fun `stack overflow`() {
    val evaluator = Evaluator.preconfigured()
    val e = assertThrows<PklException> {
      evaluator.evaluate(text("""
        a = b
        b = c
        c = a
      """.trimIndent()))
    }
    assertThat(e.message).contains("A stack overflow occurred.")
  }

  @Test
  fun `cannot import module located outside root dir`(@TempDir tempDir: Path) {
    val evaluator = EvaluatorBuilder.preconfigured()
      .setSecurityManager(
        SecurityManagers.standard(
          SecurityManagers.defaultAllowedModules,
          SecurityManagers.defaultAllowedResources,
          SecurityManagers.defaultTrustLevels,
          tempDir
        )
      )
      .build()

    val module = tempDir.resolve("test.pkl")
    module.writeString(
      """
      amends "/non/existing.pkl"
    """.trimIndent()
    )

    val e = assertThrows<PklException> {
      evaluator.evaluate(path(module))
    }
    assertThat(e.message).contains("Refusing to load module `file:///non/existing.pkl`")
  }

  @Test
  fun `multiple-file output`() {
    val evaluator = Evaluator.preconfigured()
    val program = """
      output {
        files {
          ["foo.yml"] {
            text = "foo: foo text"
          }
          ["bar.yml"] {
            text = "bar: bar text"
          }
          ["bar/biz.yml"] {
            text = "biz: bar biz"
          }
          ["bar/../bark.yml"] {
            text = "bark: bark bark"
          }
        }
      }
    """.trimIndent()
    val output = evaluator.evaluateOutputFiles(ModuleSource.text(program))
    assertThat(output.keys).isEqualTo(setOf(
      "foo.yml",
      "bar.yml",
      "bar/biz.yml",
      "bar/../bark.yml"
    ))
    assertThat(output["foo.yml"]?.text).isEqualTo("foo: foo text")
    assertThat(output["bar.yml"]?.text).isEqualTo("bar: bar text")
    assertThat(output["bar/biz.yml"]?.text).isEqualTo("biz: bar biz")
    assertThat(output["bar/../bark.yml"]?.text).isEqualTo("bark: bark bark")
  }

  private fun checkModule(module: PModule) {
    assertThat(module.properties.size).isEqualTo(2)
    assertThat(module.getProperty("name")).isEqualTo("pigeon")
    assertThat(module.getProperty("age")).isEqualTo(30L)
  }

  private fun createModulesZip(tempDir: Path): Path {
    val modulesDir = tempDir.resolve("modules")
    val module1 = modulesDir.resolve("foo/bar/module1.pkl").createParentDirectories()
    val module2 = modulesDir.resolve("foo/baz/module2.pkl").createParentDirectories()

    module1.writeText("""
      // verify that relative import is resolved correctly
      import "../baz/module2.pkl"
      
      name = module2.name
      age = module2.age
    """.trimIndent())

    module2.writeText(sourceText)

    val zipFile = tempDir.resolve("modules.zip")
    IoUtils.zipDirectory(modulesDir, zipFile)
    assertThat(zipFile).exists()
    return zipFile
  }
}
