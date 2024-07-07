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
package org.pkl.core

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.reflect.KClass
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.InputOutputTestEngine
import org.pkl.commons.test.PackageServer
import org.pkl.commons.test.PklExecutablePaths
import org.pkl.core.http.HttpClient
import org.pkl.core.project.Project
import org.pkl.core.util.IoUtils

abstract class AbstractLanguageSnippetTestsEngine : InputOutputTestEngine() {
  private val lineNumberRegex = Regex("(?m)^(( ║ )*)(\\d+) \\|")
  private val hiddenExtensionRegex = Regex(".*[.]([^.]*)[.]pkl")

  private val snippetsDir: Path =
    rootProjectDir.resolve("pkl-core/src/test/files/LanguageSnippetTests")

  protected val projectsDir: Path = snippetsDir.resolve("input/projects")

  private val expectedOutputDir: Path = snippetsDir.resolve("output")

  /**
   * Convenience for development; this selects which snippet test(s) to run. There is a
   * (non-language-snippet) test to make sure this is `""` before commit.
   */
  // language=regexp
  internal val selection: String = ""

  protected val packageServer: PackageServer = PackageServer()

  override val includedTests: List<Regex> = listOf(Regex(".*$selection\\.pkl"))

  override val excludedTests: List<Regex> = buildList {
    add(Regex(".*/native/.*"))
    if (IoUtils.isWindows()) {
      addAll(windowsExcludedTests)
    }
  }

  override val inputDir: Path = snippetsDir.resolve("input")

  override val isInputFile: (Path) -> Boolean = { it.isRegularFile() }

  protected tailrec fun Path.getProjectDir(): Path? =
    if (Files.exists(this.resolve("PklProject"))) this else parent?.getProjectDir()

  override fun expectedOutputFileFor(inputFile: Path): Path {
    val relativePath = IoUtils.relativize(inputFile, inputDir).toString()
    val stdoutPath =
      if (relativePath.matches(hiddenExtensionRegex)) relativePath.dropLast(4)
      else relativePath.dropLast(3) + "pcf"
    return expectedOutputDir.resolve(stdoutPath)
  }

  override fun beforeAll() {
    // disable SHA verification for packages
    IoUtils.setTestMode()
  }

  override fun afterAll() {
    packageServer.close()
  }

  private val replacement by lazy {
    if (snippetsDir.root.toString() != "/") "\$snippetsDir" else "/\$snippetsDir"
  }

  protected fun String.stripFilePaths(): String =
    replace(IoUtils.toNormalizedPathString(snippetsDir), replacement)

  protected fun String.stripLineNumbers() =
    replace(lineNumberRegex) { result ->
      // replace line number with equivalent number of 'x' characters to keep formatting intact
      (result.groups[1]!!.value) + "x".repeat(result.groups[3]!!.value.length) + " |"
    }

  protected fun String.stripWebsite() =
    replace(Release.current().documentation().homepage(), "https://\$pklWebsite/")

  // can't think of a better solution right now
  protected fun String.stripVersionCheckErrorMessage() =
    replace("Pkl version is ${Release.current().version()}", "Pkl version is xxx")

  protected fun String.stripStdlibLocationSha(): String {
    // Logic must be kept in-sync with `doc-package-info.pkl`.
    val commitIsh =
      if (Release.current().version().isNormal) Release.current().version()
      else Release.current().commitId()
    return replace(
      "https://github.com/apple/pkl/blob/${commitIsh}/stdlib/",
      "https://github.com/apple/pkl/blob/\$commitId/stdlib/"
    )
  }

  protected fun String.withUnixLineEndings(): String {
    return if (System.lineSeparator() == "\r\n") replace("\r\n", "\n") else this
  }
}

class LanguageSnippetTestsEngine : AbstractLanguageSnippetTestsEngine() {
  private fun evaluatorBuilder(): EvaluatorBuilder {
    return EvaluatorBuilder.preconfigured()
      .setLogger(Loggers.stdErr())
      .setStackFrameTransformer(StackFrameTransformers.empty)
      // used by some tests
      .setEnvironmentVariables(
        mapOf(
          "NAME1" to "value1",
          "NAME2" to "value2",
          "/foo/bar" to "foobar",
          "foo bar" to "foo bar",
          "file:///foo/bar" to "file:///foo/bar"
        )
      )
      .setExternalProperties(
        mapOf("name1" to "value1", "name2" to "value2", "/foo/bar" to "foobar")
      )
      .setModuleCacheDir(null)
      .setHttpClient(
        HttpClient.builder()
          .setTestPort(packageServer.port)
          .addCertificates(FileTestUtils.selfSignedCertificate)
          .buildLazily()
      )
  }

  override val testClass: KClass<*> = LanguageSnippetTests::class

  override fun generateOutputFor(inputFile: Path): kotlin.Pair<Boolean, String> {
    val logWriter = StringWriter()

    val (success, output) =
      try {
        val evaluator =
          evaluatorBuilder()
            .setLogger(Loggers.writer(PrintWriter(logWriter)))
            .apply {
              if (inputFile.startsWith(projectsDir)) {
                val projectDir = inputFile.getProjectDir() ?: return@apply
                val project =
                  Project.loadFromPath(
                    projectDir.resolve("PklProject"),
                    SecurityManagers.defaultManager,
                    null,
                    StackFrameTransformers.empty,
                    mapOf()
                  )
                securityManager = null
                applyFromProject(project)
              }
            }
            .build()
        evaluator.use { true to it.evaluateOutputText(ModuleSource.path(inputFile)) }
      } catch (e: PklBugException) {
        false to e.stackTraceToString()
      } catch (e: PklException) {
        false to e.message!!.stripLineNumbers().stripVersionCheckErrorMessage()
      }

    val stderr = logWriter.toString().withUnixLineEndings()

    return (success && stderr.isBlank()) to
      (output + stderr).stripFilePaths().stripWebsite().stripStdlibLocationSha()
  }
}

abstract class AbstractNativeLanguageSnippetTestsEngine : AbstractLanguageSnippetTestsEngine() {
  abstract val pklExecutablePath: Path

  override val excludedTests: List<Regex> =
    listOf(
      // exclude test that loads module from class path (there is no class path when using native
      // executable)
      // on the other hand, don't exclude /native/
      Regex(".*/import1b\\.pkl"),
    )

  /** Avoid running tests for native binaries when those native binaries have not been built. */
  override fun discover(
    discoveryRequest: EngineDiscoveryRequest,
    uniqueId: UniqueId
  ): TestDescriptor {
    if (!pklExecutablePath.exists()) {
      // return empty descriptor w/o children
      return EngineDescriptor(uniqueId, javaClass.simpleName)
    }

    return super.discover(discoveryRequest, uniqueId)
  }

  override fun generateOutputFor(inputFile: Path): kotlin.Pair<Boolean, String> {
    val args = buildList {
      add(pklExecutablePath.toString())
      add("eval")
      add("--no-cache")
      add("--colors=never")
      if (inputFile.startsWith(projectsDir)) {
        val projectDir = inputFile.getProjectDir()
        if (projectDir != null) {
          add("--project-dir")
          add(projectDir.toString())
        }
      } else {
        add("--no-project")
        add("--property")
        add("name1=value1")
        add("--property")
        add("name2=value2")
        add("--property")
        add("/foo/bar=foobar")
        add("--env-var")
        add("NAME1=value1")
        add("--env-var")
        add("NAME2=value2")
        add("--env-var")
        add("/foo/bar=foobar")
        add("--env-var")
        add("foo bar=foo bar")
        add("--env-var")
        add("file:///foo/bar=file:///foo/bar")
      }
      add("--settings")
      add("pkl:settings")
      add("--ca-certificates")
      add(FileTestUtils.selfSignedCertificate.toString())
      add("--test-mode")
      add("--test-port")
      add(packageServer.port.toString())
      add(inputFile.toString())
    }

    val builder = ProcessBuilder().command(args)

    val process = builder.start()
    return try {
      val (out, err) =
        listOf(process.inputStream, process.errorStream).map {
          it.reader().readText().withUnixLineEndings()
        }
      val success = process.waitFor() == 0 && err.isBlank()
      success to
        (out + err)
          .stripFilePaths()
          .stripLineNumbers()
          .stripWebsite()
          .stripVersionCheckErrorMessage()
          .stripStdlibLocationSha()
    } finally {
      process.destroy()
    }
  }
}

class MacAmd64LanguageSnippetTestsEngine : AbstractNativeLanguageSnippetTestsEngine() {
  override val pklExecutablePath: Path = PklExecutablePaths.macAmd64
  override val testClass: KClass<*> = MacLanguageSnippetTests::class
}

class MacAarch64LanguageSnippetTestsEngine : AbstractNativeLanguageSnippetTestsEngine() {
  override val pklExecutablePath: Path = PklExecutablePaths.macAarch64
  override val testClass: KClass<*> = MacLanguageSnippetTests::class
}

class LinuxAmd64LanguageSnippetTestsEngine : AbstractNativeLanguageSnippetTestsEngine() {
  override val pklExecutablePath: Path = PklExecutablePaths.linuxAmd64
  override val testClass: KClass<*> = LinuxLanguageSnippetTests::class
}

class LinuxAarch64LanguageSnippetTestsEngine : AbstractNativeLanguageSnippetTestsEngine() {
  override val pklExecutablePath: Path = PklExecutablePaths.linuxAarch64
  override val testClass: KClass<*> = LinuxLanguageSnippetTests::class
}

class AlpineLanguageSnippetTestsEngine : AbstractNativeLanguageSnippetTestsEngine() {
  override val pklExecutablePath: Path = PklExecutablePaths.alpineAmd64
  override val testClass: KClass<*> = AlpineLanguageSnippetTests::class
}

// error message contains different file path on Windows
private val windowsExcludedTests
  get() = listOf(Regex(".*missingProjectDeps/bug\\.pkl"))

class WindowsLanguageSnippetTestsEngine : AbstractNativeLanguageSnippetTestsEngine() {
  override val pklExecutablePath: Path = PklExecutablePaths.windowsAmd64
  override val testClass: KClass<*> = WindowsLanguageSnippetTests::class
  override val excludedTests: List<Regex>
    get() = super.excludedTests + windowsExcludedTests
}
