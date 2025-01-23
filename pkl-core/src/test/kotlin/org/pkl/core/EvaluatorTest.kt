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
package org.pkl.core

import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.regex.Pattern
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.createTempFile
import org.pkl.commons.test.PackageServer
import org.pkl.commons.toPath
import org.pkl.commons.writeString
import org.pkl.core.ModuleSource.*
import org.pkl.core.module.ModuleKey
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.module.ModuleKeyFactory
import org.pkl.core.module.ResolvedModuleKey
import org.pkl.core.project.Project
import org.pkl.core.util.IoUtils

class EvaluatorTest {
  companion object {
    private val evaluator = Evaluator.preconfigured()

    @Suppress("ConstPropertyName") private const val sourceText = "name = \"pigeon\"; age = 10 + 20"

    private object CustomModuleKeyFactory : ModuleKeyFactory {
      override fun create(uri: URI): Optional<ModuleKey> {
        return if (uri.scheme == "custom") Optional.of(CustomModuleKey(uri))
        else Optional.empty<ModuleKey>()
      }
    }

    private class CustomModuleKey(private val uri: URI) : ModuleKey, ResolvedModuleKey {
      override fun hasHierarchicalUris(): Boolean = true

      override fun isGlobbable(): Boolean = false

      override fun getOriginal(): ModuleKey = this

      override fun getUri(): URI = uri

      override fun loadSource(): String =
        javaClass.classLoader.getResourceAsStream(uri.path.drop(1))!!.use {
          it.readAllBytes().toString(StandardCharsets.UTF_8)
        }

      override fun resolve(securityManager: SecurityManager): ResolvedModuleKey = this
    }

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
    val e = assertThrows<PklException> { evaluator.evaluate(text("import \"foo.bar\"")) }
    assertThat(e).hasMessageContaining("Module `repl:text` cannot have a relative import URI.")
  }

  @Test
  fun `evaluate named module`() {
    val module = evaluator.evaluate(modulePath("org/pkl/core/EvaluatorTest.pkl"))
    checkModule(module)
  }

  @Test
  fun `evaluate non-existing named module`() {
    val e = assertThrows<PklException> { evaluator.evaluate(modulePath("non/existing.pkl")) }
    assertThat(e).hasMessageContaining("Cannot find module `modulepath:/non/existing.pkl`.")
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
    val file = File("/non/existing")
    val e = assertThrows<PklException> { evaluator.evaluate(file(file)) }
    assertThat(e).hasMessageContaining("Cannot find module `${file.toPath().toUri()}`.")
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
    val path = "/non/existing".toPath()
    val e = assertThrows<PklException> { evaluator.evaluate(path(path)) }
    assertThat(e).hasMessageContaining("Cannot find module `${path.toUri()}`.")
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
      val e = assertThrows<PklException> { evaluator.evaluate(path(file)) }
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
    val e =
      assertThrows<PklException> { evaluator.evaluate(uri(URI("https://localhost/non/existing"))) }
    assertThat(e).hasMessageContaining("I/O error loading module `https://localhost/non/existing`.")
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
    val e = assertThrows<PklException> { evaluator.evaluate(uri(moduleUri)) }
    assertThat(e).hasMessageContaining("Cannot find module `$moduleUri`.")
  }

  @Test
  fun `evaluate jar URI with non-existing archive path`(@TempDir tempDir: Path) {
    val zipFile = createModulesZip(tempDir)
    val moduleUri = URI("jar:${zipFile.toUri()}!/non/existing")
    val e = assertThrows<PklException> { evaluator.evaluate(uri(moduleUri)) }
    assertThat(e).hasMessageContaining("Cannot find module `$moduleUri`.")
  }

  @Test
  fun `evaluate module with relative URI`() {
    val e = assertThrows<PklException> { evaluator.evaluate(create(URI("foo.bar"), "")) }

    assertThat(e).hasMessageContaining("Cannot evaluate relative module URI `foo.bar`.")
  }

  @Test
  fun `evaluating a broken module multiple times results in the same error every time`() {
    val e1 =
      assertThrows<PklException> {
        evaluator.evaluate(modulePath("org/pkl/core/brokenModule1.pkl"))
      }
    val e2 =
      assertThrows<PklException> {
        evaluator.evaluate(modulePath("org/pkl/core/brokenModule1.pkl"))
      }
    assertThat(e2.message).isEqualTo(e1.message)

    val e3 =
      assertThrows<PklException> {
        evaluator.evaluate(modulePath("org/pkl/core/brokenModule2.pkl"))
      }
    val e4 =
      assertThrows<PklException> {
        evaluator.evaluate(modulePath("org/pkl/core/brokenModule2.pkl"))
      }
    assertThat(e4.message).isEqualTo(e3.message)
  }

  @Test
  fun `evaluation timeout`() {
    val evaluator =
      EvaluatorBuilder.preconfigured().setTimeout(java.time.Duration.ofMillis(100)).build()
    val e =
      assertThrows<PklException> {
        evaluator.evaluate(
          text(
            """
        function fib(n) = if (n < 2) 0 else fib(n - 1) + fib(n - 2)
        x = fib(100)
      """
              .trimIndent()
          )
        )
      }
    assertThat(e.message).contains("timed out")
  }

  @Test
  fun `stack overflow`() {
    val evaluator = Evaluator.preconfigured()
    val e =
      assertThrows<PklException> {
        evaluator.evaluate(
          text(
            """
        a = b
        b = c
        c = a
      """
              .trimIndent()
          )
        )
      }
    assertThat(e.message).contains("A stack overflow occurred.")
  }

  @Test
  fun `cannot import module located outside root dir`(@TempDir tempDir: Path) {
    val evaluator =
      EvaluatorBuilder.preconfigured()
        .setSecurityManager(
          SecurityManagers.standard(
            SecurityManagers.defaultAllowedModules,
            SecurityManagers.defaultAllowedResources,
            SecurityManagers.defaultTrustLevels,
            tempDir,
          )
        )
        .build()

    val module = tempDir.resolve("test.pkl")
    module.writeString(
      """
      amends "/non/existing.pkl"
    """
        .trimIndent()
    )

    val e = assertThrows<PklException> { evaluator.evaluate(path(module)) }
    assertThat(e.message).contains("Refusing to load module `file:///non/existing.pkl`")
  }

  @Test
  fun `multiple-file output`() {
    val evaluator = Evaluator.preconfigured()
    val program =
      """
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
    """
        .trimIndent()
    val output = evaluator.evaluateOutputFiles(text(program))
    assertThat(output.keys).isEqualTo(setOf("foo.yml", "bar.yml", "bar/biz.yml", "bar/../bark.yml"))
    assertThat(output["foo.yml"]?.text).isEqualTo("foo: foo text")
    assertThat(output["bar.yml"]?.text).isEqualTo("bar: bar text")
    assertThat(output["bar/biz.yml"]?.text).isEqualTo("biz: bar biz")
    assertThat(output["bar/../bark.yml"]?.text).isEqualTo("bark: bark bark")
  }

  @Test
  fun `project set from modulepath`(@TempDir cacheDir: Path) {
    PackageServer.populateCacheDir(cacheDir)
    val evaluatorBuilder = EvaluatorBuilder.preconfigured().setModuleCacheDir(cacheDir)
    val project = Project.load(modulePath("/org/pkl/core/project/project5/PklProject"))
    val result =
      evaluatorBuilder.setProjectDependencies(project.dependencies).build().use { evaluator ->
        evaluator.evaluateOutputText(modulePath("/org/pkl/core/project/project5/main.pkl"))
      }
    assertThat(result)
      .isEqualTo(
        """
      prop1 {
        name = "Apple"
      }
      prop2 {
        res = 1
      }

    """
          .trimIndent()
      )
  }

  @Test
  fun `project set from custom ModuleKeyFactory`(@TempDir cacheDir: Path) {
    PackageServer.populateCacheDir(cacheDir)
    val evaluatorBuilder =
      with(EvaluatorBuilder.preconfigured()) {
        setAllowedModules(SecurityManagers.defaultAllowedModules + Pattern.compile("custom:"))
        setAllowedResources(SecurityManagers.defaultAllowedResources + Pattern.compile("custom:"))
        setModuleCacheDir(cacheDir)
        setModuleKeyFactories(
          listOf(
            CustomModuleKeyFactory,
            ModuleKeyFactories.standardLibrary,
            ModuleKeyFactories.pkg,
            ModuleKeyFactories.projectpackage,
            ModuleKeyFactories.file,
          )
        )
      }
    val project =
      evaluatorBuilder.build().use {
        Project.load(it, uri("custom:/org/pkl/core/project/project5/PklProject"))
      }

    val evaluator = evaluatorBuilder.setProjectDependencies(project.dependencies).build()
    val output =
      evaluator.use { it.evaluateOutputText(uri("custom:/org/pkl/core/project/project5/main.pkl")) }
    assertThat(output)
      .isEqualTo(
        """
        prop1 {
          name = "Apple"
        }
        prop2 {
          res = 1
        }

        """
          .trimIndent()
      )
  }

  @Test
  fun `project base path set to non-hierarchical scheme`() {
    class FooBarModuleKey(val moduleUri: URI) : ModuleKey, ResolvedModuleKey {
      override fun hasHierarchicalUris(): Boolean = false

      override fun isGlobbable(): Boolean = false

      override fun getOriginal(): ModuleKey = this

      override fun getUri(): URI = moduleUri

      override fun loadSource(): String =
        if (uri.schemeSpecificPart.endsWith("PklProject")) {
          """
          amends "pkl:Project"
          """
            .trimIndent()
        } else
          """
          birds = import("@birds/catalog/Ostrich.pkl")
        """
            .trimIndent()

      override fun resolve(securityManager: SecurityManager): ResolvedModuleKey {
        return this
      }
    }

    val fooBayModuleKeyFactory = ModuleKeyFactory { uri ->
      if (uri.scheme == "foobar") Optional.of(FooBarModuleKey(uri)) else Optional.empty()
    }

    val evaluatorBuilder =
      with(EvaluatorBuilder.preconfigured()) {
        setAllowedModules(SecurityManagers.defaultAllowedModules + Pattern.compile("foobar:"))
        setAllowedResources(SecurityManagers.defaultAllowedResources + Pattern.compile("foobar:"))
        setModuleKeyFactories(
          listOf(
            fooBayModuleKeyFactory,
            ModuleKeyFactories.standardLibrary,
            ModuleKeyFactories.pkg,
            ModuleKeyFactories.projectpackage,
            ModuleKeyFactories.file,
          )
        )
      }

    val project = evaluatorBuilder.build().use { Project.load(it, uri("foobar:foo/PklProject")) }
    val evaluator = evaluatorBuilder.setProjectDependencies(project.dependencies).build()
    assertThatCode { evaluator.use { it.evaluateOutputText(uri("foobar:baz")) } }
      .hasMessageContaining(
        "Cannot import dependency because project URI `foobar:foo/PklProject` does not have a hierarchical path."
      )
  }

  @Test
  fun `cannot glob import in local dependency from modulepath`(@TempDir cacheDir: Path) {
    PackageServer.populateCacheDir(cacheDir)
    val evaluatorBuilder = EvaluatorBuilder.preconfigured().setModuleCacheDir(cacheDir)
    val project = Project.load(modulePath("/org/pkl/core/project/project6/PklProject"))
    evaluatorBuilder.setProjectDependencies(project.dependencies).build().use { evaluator ->
      assertThatCode {
          evaluator.evaluateOutputText(
            modulePath("/org/pkl/core/project/project6/globWithinDependency.pkl")
          )
        }
        .hasMessageContaining(
          """
        Cannot resolve import in local dependency because scheme `modulepath` is not globbable.
        
        1 | res = import*("*.pkl")
                  ^^^^^^^^^^^^^^^^
      """
            .trimIndent()
        )
      assertThatCode {
          evaluator.evaluateOutputText(
            modulePath("/org/pkl/core/project/project6/globIntoDependency.pkl")
          )
        }
        .hasMessageContaining(
          """
        –– Pkl Error ––
        Cannot resolve import in local dependency because scheme `modulepath` is not globbable.
        
        1 | import* "@project7/*.pkl" as proj7Files
            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
      """
            .trimIndent()
        )
    }
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

    module1.writeText(
      """
      // verify that relative import is resolved correctly
      import "../baz/module2.pkl"
      
      name = module2.name
      age = module2.age
    """
        .trimIndent()
    )

    module2.writeText(sourceText)

    val zipFile = tempDir.resolve("modules.zip")
    IoUtils.zipDirectory(modulesDir, zipFile)
    assertThat(zipFile).exists()
    return zipFile
  }
}
