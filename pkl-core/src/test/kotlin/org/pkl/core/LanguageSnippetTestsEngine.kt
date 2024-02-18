package org.pkl.core

import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.pkl.commons.test.InputOutputTestEngine
import org.pkl.commons.test.PackageServer
import org.pkl.core.project.Project
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.reflect.KClass

abstract class AbstractLanguageSnippetTestsEngine : InputOutputTestEngine() {
  private val lineNumberRegex = Regex("(?m)^(( ║ )*)(\\d+) \\|")
  private val hiddenExtensionRegex = Regex(".*[.]([^.]*)[.]pkl")

  private val snippetsDir: Path =
    rootProjectDir.resolve("pkl-core/src/test/files/LanguageSnippetTests")

  protected val projectsDir: Path = snippetsDir.resolve("input/projects")

  private val expectedOutputDir: Path = snippetsDir.resolve("output")

  /**
   * Convenience for development; this selects which snippet test(s) to run.
   * There is a (non-language-snippet) test to make sure this is `""` before commit.
   */
  //language=regexp
  internal val selection: String = ""

  override val includedTests: List<Regex> = listOf(Regex(".*$selection\\.pkl"))

  override val excludedTests: List<Regex> = listOf(Regex(".*/native/.*"))

  override val inputDir: Path = snippetsDir.resolve("input")

  override val isInputFile: (Path) -> Boolean = { it.isRegularFile() }

  protected val cacheDir: Path by lazy {
    rootProjectDir.resolve("pkl-core/build/packages-cache")
      .also { PackageServer.populateCacheDir(it) }
  }

  protected tailrec fun Path.getProjectDir(): Path? =
    if (Files.exists(this.resolve("PklProject"))) this
    else parent?.getProjectDir()

  override fun expectedOutputFileFor(inputFile: Path): Path {
    val relativePath = inputDir.relativize(inputFile).toString()
    val stdoutPath =
      if (relativePath.matches(hiddenExtensionRegex)) relativePath.dropLast(4)
      else relativePath.dropLast(3) + "pcf"
    return expectedOutputDir.resolve(stdoutPath)
  }

  protected fun String.stripFilePaths() = replace(snippetsDir.toString(), "/\$snippetsDir")

  protected fun String.stripLineNumbers() = replace(lineNumberRegex) { result ->
    // replace line number with equivalent number of 'x' characters to keep formatting intact
    (result.groups[1]!!.value) + "x".repeat(result.groups[3]!!.value.length) + " |"
  }

  protected fun String.stripWebsite() = replace(Release.current().documentation().homepage(), "https://\$pklWebsite/")

  // can't think of a better solution right now
  protected fun String.stripVersionCheckErrorMessage() =
    replace("Pkl version is ${Release.current().version()}", "Pkl version is xxx")
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
      .setExternalProperties(mapOf(
        "name1" to "value1",
        "name2" to "value2",
        "/foo/bar" to "foobar"
      ))
      .setModuleCacheDir(cacheDir)
  }

  override val testClass: KClass<*> = LanguageSnippetTests::class

  override fun generateOutputFor(inputFile: Path): kotlin.Pair<Boolean, String> {
    val logWriter = StringWriter()

    val (success, output) = try {
      val evaluator = evaluatorBuilder()
        .setLogger(Loggers.writer(PrintWriter(logWriter)))
        .apply {
          if (inputFile.startsWith(projectsDir)) {
            val projectDir = inputFile.getProjectDir() ?: return@apply
            val project = Project.loadFromPath(
              projectDir.resolve("PklProject"),
              SecurityManagers.defaultManager,
              Duration.ofSeconds(30.0).toJavaDuration(),
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
      false to e.message!!
        .stripLineNumbers()
        .stripVersionCheckErrorMessage()
    }

    val stderr = logWriter.toString()

    return (success && stderr.isBlank()) to (output + stderr).stripFilePaths().stripWebsite()
  }
}

abstract class AbstractNativeLanguageSnippetTestsEngine : AbstractLanguageSnippetTestsEngine() {
  abstract val pklExecutablePath: Path

  override val excludedTests: List<Regex> = listOf(
    // exclude test that loads module from class path (there is no class path when using native executable)
    // on the other hand, don't exclude /native/
    Regex(".*/import1b\\.pkl"),
  )

  /**
   * Avoid running tests for native binaries when those native binaries have not been built.
   */
  override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
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
      add("--cache-dir")
      add(cacheDir.toString())
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
      add("--test-mode")
      add(inputFile.toString())
    }

    val builder = ProcessBuilder()
      .command(args)

    val process = builder.start()
    return try {
      val (out, err) = listOf(process.inputStream, process.errorStream)
        .map { it.reader().readText() }
      val success = process.waitFor() == 0 && err.isBlank()
      success to (out + err)
        .stripFilePaths()
        .stripLineNumbers()
        .stripWebsite()
        .stripVersionCheckErrorMessage()
    } finally {
      process.destroy()
    }
  }
}

class MacAmd64LanguageSnippetTestsEngine : AbstractNativeLanguageSnippetTestsEngine() {
  override val pklExecutablePath: Path = rootProjectDir.resolve("pkl-cli/build/executable/pkl-macos-amd64")
  override val testClass: KClass<*> = MacLanguageSnippetTests::class
}

class MacAarch64LanguageSnippetTestsEngine : AbstractNativeLanguageSnippetTestsEngine() {
  override val pklExecutablePath: Path = rootProjectDir.resolve("pkl-cli/build/executable/pkl-macos-aarch64")
  override val testClass: KClass<*> = MacLanguageSnippetTests::class
}

class LinuxAmd64LanguageSnippetTestsEngine : AbstractNativeLanguageSnippetTestsEngine() {
  override val pklExecutablePath: Path = rootProjectDir.resolve("pkl-cli/build/executable/pkl-linux-amd64")
  override val testClass: KClass<*> = LinuxLanguageSnippetTests::class
}

class LinuxAarch64LanguageSnippetTestsEngine : AbstractNativeLanguageSnippetTestsEngine() {
  override val pklExecutablePath: Path = rootProjectDir.resolve("pkl-cli/build/executable/pkl-linux-aarch64")
  override val testClass: KClass<*> = LinuxLanguageSnippetTests::class
}

class AlpineLanguageSnippetTestsEngine : AbstractNativeLanguageSnippetTestsEngine() {
  override val pklExecutablePath: Path = rootProjectDir.resolve("pkl-cli/build/executable/pkl-alpine-linux-amd64")
  override val testClass: KClass<*> = AlpineLanguageSnippetTests::class
}
