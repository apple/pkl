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
package org.pkl.executor

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.FilteringClassLoader
import org.pkl.commons.test.PackageServer
import org.pkl.commons.toPath
import org.pkl.core.Release

class EmbeddedExecutorTest {
  /**
   * An executor that uses a particular combination of ExecutorSpiOptions version, pkl-executor
   * version, and Pkl distribution version.
   */
  class TestExecutor(
    private val executor: Executor,
    private val spiOptionsVersion: Int,
    private val name: String,
  ) {
    fun evaluatePath(modulePath: Path, optionSpec: ExecutorOptions.Builder.() -> Unit): String {
      val options =
        ExecutorOptions.builder().apply(optionSpec).spiOptionsVersion(spiOptionsVersion).build()
      return executor.evaluatePath(modulePath, options)
    }

    override fun toString(): String = name
  }

  companion object {
    @JvmStatic
    private val allTestExecutors: List<TestExecutor> by lazy {
      listOf(
        TestExecutor(executor1_1.value, 1, "SpiOptions1, Executor1, Distribution1"),

        // This context has a pkl-executor version that is lower than the distribution version.
        TestExecutor(executor1_2.value, 1, "SpiOptions1, Executor1, Distribution2"),
        TestExecutor(executor2_1.value, 1, "SpiOptions1, Executor2, Distribution1"),
        TestExecutor(executor2_1.value, 3, "SpiOptions3, Executor2, Distribution1"),
        TestExecutor(executor2_2.value, 1, "SpiOptions1, Executor2, Distribution2"),
        TestExecutor(executor2_2.value, 3, "SpiOptions3, Executor2, Distribution2"),
      )
    }

    private val currentExecutor: TestExecutor by lazy {
      TestExecutor(executor2_2.value, -1, "currentExecutor")
    }

    // A pkl-executor library that supports ExecutorSpiOptions up to v1
    // and a Pkl distribution that supports ExecutorSpiOptions up to v1.
    private val executor1_1: Lazy<Executor> = lazy {
      EmbeddedExecutor(listOf(pklDistribution1), pklExecutorClassLoader1)
    }

    // A pkl-executor library that supports ExecutorSpiOptions up to v1
    // and a Pkl distribution that supports ExecutorSpiOptions up to v3.
    private val executor1_2: Lazy<Executor> = lazy {
      EmbeddedExecutor(listOf(pklDistribution2), pklExecutorClassLoader1)
    }

    // A pkl-executor library that supports ExecutorSpiOptions up to v3
    // and a Pkl distribution that supports ExecutorSpiOptions up to v1.
    private val executor2_1: Lazy<Executor> = lazy {
      EmbeddedExecutor(listOf(pklDistribution1), pklExecutorClassLoader2)
    }

    // A pkl-executor library that supports ExecutorSpiOptions up to v3
    // and a Pkl distribution that supports ExecutorSpiOptions up to v3.
    private val executor2_2: Lazy<Executor> = lazy {
      EmbeddedExecutor(listOf(pklDistribution2), pklExecutorClassLoader2)
    }

    private val allExecutors by lazy { listOf(executor1_1, executor1_2, executor2_1, executor2_2) }

    // a pkl-executor class loader that supports ExecutorSpiOptions up to v1
    private val pklExecutorClassLoader1: ClassLoader by lazy {
      FilteringClassLoader(pklExecutorClassLoader2) { className ->
        !className.matches(Regex(".*ExecutorSpiOptions\\d+$"))
      }
    }

    // a pkl-executor class loader that supports ExecutorSpiOptions up to v3
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
      FileTestUtils.rootProjectDir
        .resolve("pkl-executor/build/pklHistoricalDistributions/pkl-config-java-all-0.25.0.jar")
        .apply { if (!exists()) missingTestFixture() }
    }

    // a Pkl distribution that supports ExecutorSpiOptions up to v3
    private val pklDistribution2: Path by lazy {
      FileTestUtils.rootProjectDir
        .resolve(
          "pkl-config-java/build/libs/pkl-config-java-all-" +
            "${Release.current().version().withBuild(null).toString().replaceFirst("dev", "SNAPSHOT")}.jar"
        )
        .apply { if (!exists()) missingTestFixture() }
    }

    private fun missingTestFixture(): Nothing =
      throw AssertionError(
        "Missing test fixture. " + "To fix this problem, run `./gradlew :pkl-executor:prepareTest`."
      )
  }

  @Test
  fun extractMinPklVersion() {
    assertThat(
        EmbeddedExecutor.extractMinPklVersion(
          """
      @ModuleInfo { minPklVersion = "1.2.3" }
    """
            .trimIndent()
        )
      )
      .isEqualTo(Version.parse("1.2.3"))

    assertThat(
        EmbeddedExecutor.extractMinPklVersion(
          """
      @ModuleInfo{minPklVersion="1.2.3"}
    """
            .trimIndent()
        )
      )
      .isEqualTo(Version.parse("1.2.3"))

    assertThat(
        EmbeddedExecutor.extractMinPklVersion(
          """
      @ModuleInfo   {   minPklVersion   =   "1.2.3"   }
    """
            .trimIndent()
        )
      )
      .isEqualTo(Version.parse("1.2.3"))

    assertThat(
        EmbeddedExecutor.extractMinPklVersion(
          """
      @ModuleInfo {
          minPklVersion = "1.2.3"
      }
    """
            .trimIndent()
        )
      )
      .isEqualTo(Version.parse("1.2.3"))

    assertThat(
        EmbeddedExecutor.extractMinPklVersion(
          """
      @ModuleInfo {
          author = "foo@bar.apple.com"
          minPklVersion = "1.2.3"
      }
    """
            .trimIndent()
        )
      )
      .isEqualTo(Version.parse("1.2.3"))

    assertThat(
        EmbeddedExecutor.extractMinPklVersion(
          """
      @ModuleInfo {
          minPklVersion = "1.2.3"
          author = "foo@bar.apple.com"
      }
    """
            .trimIndent()
        )
      )
      .isEqualTo(Version.parse("1.2.3"))
  }

  @Test
  fun `create embedded executor with non-existing Pkl distribution`() {
    val e =
      assertThrows<IllegalArgumentException> {
        Executors.embedded(listOf("/non/existing".toPath()))
      }

    val sep = File.separatorChar
    assertThat(e.message).contains("Cannot find Jar file").contains("${sep}non${sep}existing")
  }

  @Test
  fun `create embedded executor with invalid Pkl distribution that is not a Jar file`(
    @TempDir tempDir: Path
  ) {
    val file = Files.createFile(tempDir.resolve("pkl.jar"))
    val e = assertThrows<IllegalArgumentException> { Executors.embedded(listOf(file)) }

    assertThat(e.message).contains("Cannot find service").contains("pkl.jar")
  }

  @ParameterizedTest
  @MethodSource("getAllTestExecutors")
  fun `evaluate a module that is missing a ModuleInfo annotation`(
    executor: TestExecutor,
    @TempDir tempDir: Path,
  ) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile
      .toFile()
      .writeText(
        """
      module test

      x = 1
    """
          .trimIndent()
      )

    val e =
      assertThrows<ExecutorException> {
        executor.evaluatePath(pklFile) {
          allowedModules("file:")
          allowedResources("prop:")
          rootDir(tempDir)
        }
      }

    assertThat(e.message)
      .contains("Pkl module `test.pkl` does not state which Pkl version it requires.")
  }

  @ParameterizedTest
  @MethodSource("getAllTestExecutors")
  fun `evaluate a module that requests an incompatible Pkl version`(
    executor: TestExecutor,
    @TempDir tempDir: Path,
  ) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile
      .toFile()
      .writeText(
        """
      @ModuleInfo { minPklVersion = "99.99.99" }
      module test

      x = 1
    """
          .trimIndent()
      )

    val e =
      assertThrows<ExecutorException> {
        executor.evaluatePath(pklFile) {
          allowedModules("file:")
          allowedResources("prop:")
          rootDir(tempDir)
        }
      }

    assertThat(e.message)
      .contains("Pkl version `99.99.99` requested by module `test.pkl` is not supported.")
  }

  @ParameterizedTest
  @MethodSource("getAllTestExecutors")
  fun `evaluate a module that reads environment variables and external properties`(
    executor: TestExecutor,
    @TempDir tempDir: Path,
  ) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile
      .toFile()
      .writeText(
        """
      @ModuleInfo { minPklVersion = "0.11.0" }
      module test

      x = read("env:ENV_VAR")
      y = read("prop:property")
    """
          .trimIndent()
      )

    val result =
      executor.evaluatePath(pklFile) {
        allowedModules("file:")
        // should `prop:pkl.outputFormat` be allowed automatically?
        allowedResources("prop:", "env:")
        environmentVariables(mapOf("ENV_VAR" to "ENV_VAR"))
        externalProperties(mapOf("property" to "property"))
      }

    assertThat(result.trim())
      .isEqualTo(
        """
      x = "ENV_VAR"
      y = "property"
    """
          .trimIndent()
          .trim()
      )
  }

  @ParameterizedTest
  @MethodSource("getAllTestExecutors")
  fun `evaluate a module that depends on another module`(
    executor: TestExecutor,
    @TempDir tempDir: Path,
  ) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile
      .toFile()
      .writeText(
        """
      @ModuleInfo { minPklVersion = "0.11.0" }
      amends "template.pkl"

      foo {
        bar = 42
      }
    """
          .trimIndent()
      )

    val templateFile = tempDir.resolve("template.pkl")
    templateFile
      .toFile()
      .writeText(
        """
      foo: Foo

      class Foo {
        bar: Int
      }
    """
          .trimIndent()
      )

    val result =
      executor.evaluatePath(pklFile) {
        allowedModules("file:")
        allowedResources("prop:")
      }

    assertThat(result.trim())
      .isEqualTo(
        """
      foo {
        bar = 42
      }
    """
          .trimIndent()
          .trim()
      )
  }

  @ParameterizedTest
  @MethodSource("getAllTestExecutors")
  fun `evaluate a module whose evaluation fails`(executor: TestExecutor, @TempDir tempDir: Path) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile
      .toFile()
      .writeText(
        """
      @ModuleInfo { minPklVersion = "0.11.0" }
      module test

      foo = throw("ouch")
    """
          .trimIndent()
      )

    val e =
      assertThrows<ExecutorException> {
        executor.evaluatePath(pklFile) {
          allowedModules("file:")
          allowedResources("prop:")
          rootDir(tempDir)
        }
      }

    assertThat(e.message)
      .contains("ouch")
      // ensure module file paths are relativized
      .contains("at test#foo (test.pkl)")
      .doesNotContain(tempDir.toString())
  }

  @ParameterizedTest
  @MethodSource("getAllTestExecutors")
  fun `evaluate a module whose project evaluation fails`(
    executor: TestExecutor,
    @TempDir tempDir: Path,
  ) {
    // the toRealPath is important here or the failure reason can change
    // this happens on macOS where /tmp is a symlink to /private/tmp
    // it's related to how SecurityManagers.Standard handles canonicalizing paths that don't exist
    val rootDir = tempDir.toRealPath()

    val innerDir = rootDir.resolve("inner").createDirectories()
    innerDir
      .resolve("PklProject")
      .toFile()
      .writeText(
        """
      amends "pkl:Project"
      dependencies {
        ["myDep"] = import("../nonexistent/PklProject")
      }
    """
          .trimIndent()
      )

    val pklFile = innerDir.resolve("test.pkl")
    pklFile
      .toFile()
      .writeText(
        """
      @ModuleInfo { minPklVersion = "0.11.0" }
      module test

      foo = "bar"
    """
          .trimIndent()
      )

    val e =
      assertThrows<ExecutorException> {
        executor.evaluatePath(pklFile) {
          allowedModules("file:", "pkl:")
          allowedResources("prop:")
          projectDir(innerDir)
          rootDir(rootDir)
        }
      }

    assertThat(e.message).contains("Cannot find module").contains("/nonexistent/PklProject")

    // ensure module file paths are relativized
    // legacy distribution does not handle these errors with the correct stack frame transformer
    // only assert on this for newer distributions
    if (!executor.toString().contains("Distribution1")) {
      assertThat(e.message).doesNotContain(innerDir.toString())
    }
  }

  @ParameterizedTest
  @MethodSource("getAllTestExecutors")
  fun `time out a module`(executor: TestExecutor, @TempDir tempDir: Path) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile
      .toFile()
      .writeText(
        """
      @ModuleInfo { minPklVersion = "0.11.0" }
      module test

      x = fib(100)

      function fib(n) = if (n < 2) n else fib(n - 1) + fib(n - 2)
    """
          .trimIndent()
      )

    val e =
      assertThrows<ExecutorException> {
        executor.evaluatePath(pklFile) {
          allowedModules("file:")
          allowedResources("prop:")
          rootDir(tempDir)
          timeout(Duration.ofSeconds(1))
        }
      }

    assertThat(e.message).contains("Evaluation timed out after 1 second(s).")
  }

  @Test
  fun `evaluate a module that loads a package`(@TempDir tempDir: Path) {
    val cacheDir = tempDir.resolve("cache")
    val pklFile = tempDir.resolve("test.pkl")
    pklFile
      .toFile()
      .writeText(
        """
      @ModuleInfo { minPklVersion = "0.24.0" }
      module MyModule

      import "package://localhost:0/birds@0.5.0#/Bird.pkl"

      chirpy = new Bird { name = "Chirpy"; favoriteFruit { name = "Orange" } }
    """
          .trimIndent()
      )
    val result =
      PackageServer().use { server ->
        currentExecutor.evaluatePath(pklFile) {
          allowedModules("file:", "package:", "https:")
          allowedResources("prop:", "package:", "https:")
          moduleCacheDir(cacheDir)
          certificateFiles(FileTestUtils.selfSignedCertificate)
          testPort(server.port)
        }
      }
    assertThat(result.trim())
      .isEqualTo(
        """
      chirpy {
        name = "Chirpy"
        favoriteFruit {
          name = "Orange"
        }
      }
    """
          .trimIndent()
      )

    // verify that cache was populated
    assertThat(cacheDir.toFile().list()).isNotEmpty()
  }

  @Test
  fun `http rewrites option`(@TempDir tempDir: Path) {
    val pklFile = tempDir.resolve("test.pkl")
    pklFile.writeText(
      """
      @ModuleInfo { minPklVersion = "0.29.0" }
      result = import("https://example.com/foo.pkl")
    """
        .trimIndent()
    )
    assertThatCode {
        currentExecutor.evaluatePath(pklFile) {
          allowedModules("file:", "https:")
          allowedResources("prop:")
          httpRewrites(mapOf(URI("https://example.com/") to URI("https://example.example/")))
        }
      }
      .hasMessageContaining(
        "Error connecting to host `example.example`. (request was rewritten: https://example.com/foo.pkl -> https://example.example/foo.pkl)"
      )
  }

  @ParameterizedTest
  @MethodSource("getAllTestExecutors")
  @DisabledOnOs(OS.WINDOWS, disabledReason = "Can't populate legacy cache dir on Windows")
  fun `evaluate a project dependency`(executor: TestExecutor, @TempDir tempDir: Path) {
    val cacheDir = tempDir.resolve("packages")
    PackageServer.populateCacheDir(cacheDir)
    PackageServer.populateLegacyCacheDir(cacheDir)
    val projectDir = tempDir.resolve("project/")
    projectDir.createDirectories()
    projectDir
      .resolve("PklProject")
      .toFile()
      .writeText(
        """
      amends "pkl:Project"
      
      dependencies {
        ["birds"] { uri = "package://localhost:0/birds@0.5.0" }
      }
    """
          .trimIndent()
      )
    val dollar = '$'
    projectDir
      .resolve("PklProject.deps.json")
      .toFile()
      .writeText(
        """
      {
        "schemaVersion": 1,
        "resolvedDependencies": {
          "package://localhost:0/birds@0": {
            "type": "remote",
            "uri": "projectpackage://localhost:0/birds@0.5.0",
            "checksums": {
              "sha256": "${dollar}skipChecksumVerification"
            }
          },
          "package://localhost:0/fruit@1": {
            "type": "remote",
            "uri": "projectpackage://localhost:0/fruit@1.0.5",
            "checksums": {
              "sha256": "${dollar}skipChecksumVerification"
            }
          }
        }
      }
    """
          .trimIndent()
      )
    val pklFile = projectDir.resolve("test.pkl")
    pklFile
      .toFile()
      .writeText(
        """
        @ModuleInfo { minPklVersion = "0.24.0" }
        module myModule
        
        import "@birds/catalog/Swallow.pkl"
        
        result = Swallow
      """
          .trimIndent()
      )
    val result =
      executor.evaluatePath(pklFile) {
        allowedModules("file:", "package:", "projectpackage:", "https:", "pkl:")
        allowedResources("file:", "prop:", "package:", "projectpackage:", "https:")
        moduleCacheDir(cacheDir)
        projectDir(projectDir)
      }
    assertThat(result)
      .isEqualTo(
        """
      result {
        name = "Swallow"
        favoriteFruit {
          name = "Apple"
        }
      }
      
    """
          .trimIndent()
      )
  }
}
