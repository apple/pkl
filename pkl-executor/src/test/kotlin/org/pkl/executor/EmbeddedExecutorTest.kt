package org.pkl.executor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.FilteringClassLoader
import org.pkl.commons.test.PackageServer
import org.pkl.commons.toPath
import org.pkl.core.Release
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class EmbeddedExecutorTest {
  /**
   * A combination of ExecutorOptions version, pkl-executor version, 
   * and Pkl distribution version that parameterized tests should be run against.
   */
  data class ExecutionContext(
    val executor: Executor,
    val options: (ExecutorOptions) -> ExecutorOptions,
    val name: String
  ) {
    override fun toString(): String = name
  }
  
  companion object {
    @JvmStatic
    private val allExecutionContexts: List<ExecutionContext> by lazy {
      listOf(
        ExecutionContext(executor1_1.value, ::convertToOptions1, "Options1, Executor1, Distribution1"),

        // This context has a pkl-executor version that is lower than the distribution version.
        // It can be enabled once there is a distribution that includes pkl-executor.
        //ExecutionContext(executor1_2.value, ::convertToOptions1, "Options1, Executor1, Distribution2"),

        ExecutionContext(executor2_1.value, ::convertToOptions1, "Options1, Executor2, Distribution1"),
        ExecutionContext(executor2_1.value, ::convertToOptions2, "Options2, Executor2, Distribution1"),

        ExecutionContext(executor2_2.value, ::convertToOptions1, "Options1, Executor2, Distribution2"),
        ExecutionContext(executor2_2.value, ::convertToOptions2, "Options2, Executor2, Distribution2")
      )
    }

    private val currentExecutor: Executor by lazy { executor2_2.value }
    
    // A pkl-executor library that supports ExecutorSpiOptions up to v1
    // and a Pkl distribution that supports ExecutorSpiOptions up to v1.
    private val executor1_1: Lazy<Executor> = lazy {
      EmbeddedExecutor(listOf(pklDistribution1), pklExecutorClassLoader1)
    }

    // A pkl-executor library that supports ExecutorSpiOptions up to v1
    // and a Pkl distribution that supports ExecutorSpiOptions up to v2.
    private val executor1_2: Lazy<Executor> = lazy {
      EmbeddedExecutor(listOf(pklDistribution2), pklExecutorClassLoader1)
    }

    // A pkl-executor library that supports ExecutorSpiOptions up to v2
    // and a Pkl distribution that supports ExecutorSpiOptions up to v1.
    private val executor2_1: Lazy<Executor> = lazy {
      EmbeddedExecutor(listOf(pklDistribution1), pklExecutorClassLoader2)
    }

    // A pkl-executor library that supports ExecutorSpiOptions up to v2
    // and a Pkl distribution that supports ExecutorSpiOptions up to v.
    private val executor2_2: Lazy<Executor> = lazy {
      EmbeddedExecutor(listOf(pklDistribution2), pklExecutorClassLoader2)
    }
    
    private val allExecutors by lazy { 
      listOf(executor1_1, executor1_2, executor2_1, executor2_2)
    }

    // a pkl-executor class loader that supports ExecutorSpiOptions up to v1
    private val pklExecutorClassLoader1: ClassLoader by lazy {
      FilteringClassLoader(pklExecutorClassLoader2) { className ->
        !className.endsWith("ExecutorSpiOptions2")
      }
    }

    // a pkl-executor class loader that supports ExecutorSpiOptions up to v2
    private val pklExecutorClassLoader2: ClassLoader by lazy {
      EmbeddedExecutor::class.java.classLoader
    }
    
    @AfterAll
    @JvmStatic
    fun afterAll() {
      for (executor in allExecutors) {
        if (executor.isInitialized()) executor.value.close()
      }
    }
    
    // a Pkl distribution that supports ExecutorSpiOptions up to v1
    private val pklDistribution1: Path by lazy {
      val path = System.getProperty("pklDistribution025")?.toPath() ?:
        // can get rid of this path by switching to IntelliJ's Gradle test runner
        System.getProperty("user.home").toPath()
          .resolve(".gradle/caches/modules-2/files-2.1/org.pkl-lang/pkl-config-java-all/" +
            "0.25.0/e9451dda554f1659e49ff5bdd30accd26be7bf0f/pkl-config-java-all-0.25.0.jar")
      path.apply {
          if (!exists()) throw AssertionError("Missing test fixture. " +
            "To fix this problem, run `./gradlew :pkl-executor:prepareTest`.")
        }
    }

    // a Pkl distribution that supports ExecutorSpiOptions up to v2
    private val pklDistribution2: Path by lazy {
      val path = System.getProperty("pklDistributionCurrent")?.toPath() ?:
        // can get rid of this path by switching to IntelliJ's Gradle test runner
        FileTestUtils.rootProjectDir
          .resolve("pkl-config-java/build/libs/pkl-config-java-all-" +
            "${Release.current().version().withBuild(null).toString().replaceFirst("dev", "SNAPSHOT")}.jar")
      path.apply {
        if (!exists()) throw AssertionError("Missing test fixture. " +
          "To fix this problem, run `./gradlew :pkl-executor:prepareTest`.")
      }
    }

    private fun convertToOptions2(options: ExecutorOptions): ExecutorOptions2 =
      if (options is ExecutorOptions2) options else ExecutorOptions2(
        options.allowedModules,
        options.allowedResources,
        options.environmentVariables,
        options.externalProperties,
        options.modulePath,
        options.rootDir,
        options.timeout,
        options.outputFormat,
        options.moduleCacheDir,
        options.projectDir,
        listOf(),
        listOf()
      )

    private fun convertToOptions1(options: ExecutorOptions): ExecutorOptions =
      if (options.javaClass == ExecutorOptions::class.java) options else ExecutorOptions(
        options.allowedModules,
        options.allowedResources,
        options.environmentVariables,
        options.externalProperties,
        options.modulePath,
        options.rootDir,
        options.timeout,
        options.outputFormat,
        options.moduleCacheDir,
        options.projectDir
      )
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

  @ParameterizedTest
  @MethodSource("getAllExecutionContexts")
  fun `evaluate a module that is missing a ModuleInfo annotation`(context: ExecutionContext, @TempDir tempDir: Path) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile.toFile().writeText(
      """
      module test

      x = 1
    """.trimIndent()
    )

    val e = assertThrows<ExecutorException> {
      context.executor.evaluatePath(
          pklFile,
          context.options(ExecutorOptions2(
            listOf("file:"),
            listOf("prop:"),
            mapOf(),
            mapOf(),
            listOf(),
            tempDir,
            null,
            null,
            null,
            null,
            listOf(),
            listOf()
          )
        ))
    }

    assertThat(e.message)
      .contains("Pkl module `test.pkl` does not state which Pkl version it requires.")
  }

  @ParameterizedTest
  @MethodSource("getAllExecutionContexts")
  fun `evaluate a module that requests an incompatible Pkl version`(context: ExecutionContext, @TempDir tempDir: Path) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile.toFile().writeText(
      """
      @ModuleInfo { minPklVersion = "99.99.99" }
      module test

      x = 1
    """.trimIndent()
    )

    val e = assertThrows<ExecutorException> {
      context.executor.evaluatePath(
          pklFile,
          context.options(ExecutorOptions2(
            listOf("file:"),
            listOf("prop:"),
            mapOf(),
            mapOf(),
            listOf(),
            tempDir,
            null,
            null,
            null,
            null,
            listOf(),
            listOf()
          ))
        )
    }

    assertThat(e.message)
      .contains("Pkl version `99.99.99` requested by module `test.pkl` is not supported.")
  }

  @ParameterizedTest
  @MethodSource("getAllExecutionContexts")
  fun `evaluate a module that reads environment variables and external properties`(context: ExecutionContext, @TempDir tempDir: Path) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile.toFile().writeText(
      """
      @ModuleInfo { minPklVersion = "0.11.0" }
      module test

      x = read("env:ENV_VAR")
      y = read("prop:property")
    """.trimIndent()
    )

    val result = context.executor.evaluatePath(
        pklFile,
        context.options(ExecutorOptions2(
          listOf("file:"),
          // should `prop:pkl.outputFormat` be allowed automatically?
          listOf("prop:", "env:"),
          mapOf("ENV_VAR" to "ENV_VAR"),
          mapOf("property" to "property"),
          listOf(),
          null,
          null,
          null,
          null,
          null,
          listOf(),
          listOf()
        ))
      )

    assertThat(result.trim()).isEqualTo(
      """
      x = "ENV_VAR"
      y = "property"
    """.trimIndent().trim()
    )
  }

  @ParameterizedTest
  @MethodSource("getAllExecutionContexts")
  fun `evaluate a module that depends on another module`(context: ExecutionContext, @TempDir tempDir: Path) {
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

    val result = context.executor.evaluatePath(
        pklFile,
        context.options(ExecutorOptions2(
          listOf("file:"),
          listOf("prop:"),
          mapOf(),
          mapOf(),
          listOf(),
          null,
          null,
          null,
          null,
          null,
          listOf(),
          listOf()
        )
      )
    )

    assertThat(result.trim()).isEqualTo(
      """
      foo {
        bar = 42
      }
    """.trimIndent().trim()
    )
  }

  @ParameterizedTest
  @MethodSource("getAllExecutionContexts")
  fun `evaluate a module whose evaluation fails`(context: ExecutionContext, @TempDir tempDir: Path) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile.toFile().writeText(
      """
      @ModuleInfo { minPklVersion = "0.11.0" }
      module test

      foo = throw("ouch")
    """.trimIndent()
    )

    val e = assertThrows<ExecutorException> {
      context.executor.evaluatePath(
          pklFile,
          context.options(ExecutorOptions2(
            listOf("file:"),
            listOf("prop:"),
            mapOf(),
            mapOf(),
            listOf(),
            tempDir,
            null,
            null,
            null,
            null,
            listOf(),
            listOf()
          )
        ))
    }

    assertThat(e.message)
      .contains("ouch")
      // ensure module file paths are relativized
      .contains("at test#foo (test.pkl)")
      .doesNotContain(tempDir.toString())
  }

  @ParameterizedTest
  @MethodSource("getAllExecutionContexts")
  fun `time out a module`(context: ExecutionContext, @TempDir tempDir: Path) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile.toFile().writeText(
      """
      @ModuleInfo { minPklVersion = "0.11.0" }
      module test

      x = fib(100)

      function fib(n) = if (n < 2) n else fib(n - 1) + fib(n - 2)
    """.trimIndent()
    )

    val e = assertThrows<ExecutorException> {
      context.executor.evaluatePath(
          pklFile,
          context.options(ExecutorOptions2(
            listOf("file:"),
            listOf("prop:"),
            mapOf(),
            mapOf(),
            listOf(),
            tempDir,
            Duration.ofSeconds(1),
            null,
            null,
            null,
            listOf(),
            listOf()
          ))
        )
    }

    assertThat(e.message)
      .contains("Evaluation timed out after 1 second(s).")
  }

  @Test
  fun `evaluate a module that loads a package`(@TempDir tempDir: Path) {
    val cacheDir = tempDir.resolve("cache")
    val pklFile = tempDir.resolve("test.pkl")
    pklFile.toFile().writeText(
      """
      @ModuleInfo { minPklVersion = "0.24.0" }
      module MyModule

      import "package://localhost:12110/birds@0.5.0#/Bird.pkl"

      chirpy = new Bird { name = "Chirpy"; favoriteFruit { name = "Orange" } }
    """.trimIndent()
    )
    PackageServer.ensureStarted()
    val result = currentExecutor.evaluatePath(pklFile,
        ExecutorOptions2(
          listOf("file:", "package:", "https:"),
          listOf("prop:", "package:", "https:"),
          mapOf(),
          mapOf(),
          listOf(),
          null,
          null,
          null,
          cacheDir,
          null,
          listOf(FileTestUtils.selfSignedCertificate),
          listOf())
      )
    assertThat(result.trim()).isEqualTo("""
      chirpy {
        name = "Chirpy"
        favoriteFruit {
          name = "Orange"
        }
      }
    """.trimIndent())
    
    // verify that cache was populated
    assertThat(cacheDir.toFile().list()).isNotEmpty()
  }

  @ParameterizedTest
  @MethodSource("getAllExecutionContexts")
  fun `evaluate a project dependency`(context: ExecutionContext, @TempDir tempDir: Path) {
    val cacheDir = tempDir.resolve("packages")
    PackageServer.populateCacheDir(cacheDir)
    val projectDir = tempDir.resolve("project/")
    projectDir.createDirectories()
    projectDir.resolve("PklProject").toFile().writeText("""
      amends "pkl:Project"
      
      dependencies {
        ["birds"] { uri = "package://localhost:12110/birds@0.5.0" }
      }
    """.trimIndent())
    val dollar = '$'
    projectDir.resolve("PklProject.deps.json").toFile().writeText("""
      {
        "schemaVersion": 1,
        "resolvedDependencies": {
          "package://localhost:12110/birds@0": {
            "type": "remote",
            "uri": "projectpackage://localhost:12110/birds@0.5.0",
            "checksums": {
              "sha256": "${dollar}skipChecksumVerification"
            }
          },
          "package://localhost:12110/fruit@1": {
            "type": "remote",
            "uri": "projectpackage://localhost:12110/fruit@1.0.5",
            "checksums": {
              "sha256": "${dollar}skipChecksumVerification"
            }
          }
        }
      }
    """.trimIndent())
    val pklFile = projectDir.resolve("test.pkl")
    pklFile.toFile().writeText(
      """
        @ModuleInfo { minPklVersion = "0.24.0" }
        module myModule
        
        import "@birds/catalog/Swallow.pkl"
        
        result = Swallow
      """.trimIndent()
    )
    val result = context.executor.evaluatePath(pklFile,
        context.options(ExecutorOptions2(
          listOf("file:", "package:", "projectpackage:", "https:"),
          listOf("prop:", "package:", "projectpackage:", "https:"),
          mapOf(),
          mapOf(),
          listOf(),
          null,
          null,
          null,
          cacheDir,
          projectDir,
          listOf(),
          listOf())
      ))
    assertThat(result).isEqualTo("""
      result {
        name = "Swallow"
        favoriteFruit {
          name = "Apple"
        }
      }
      
    """.trimIndent())
  }
}
