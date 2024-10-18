/*
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
package org.pkl.core.module

import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.createParentDirectories
import org.pkl.commons.toPath
import org.pkl.commons.writeString
import org.pkl.core.SecurityManagers
import org.pkl.core.externalreader.*

class ModuleKeyFactoriesTest {
  @Test
  fun `standard library`() {
    val factory = ModuleKeyFactories.standardLibrary
    assertThat(factory.create(URI("other:test"))).isNotPresent

    val module = factory.create(URI("pkl:test"))
    assertThat(module).isPresent
  }

  @Test
  fun file() {
    val factory = ModuleKeyFactories.file
    assertThat(factory.create(URI("other:test"))).isNotPresent

    val module = factory.create(URI("file:///some/file"))
    assertThat(module).isPresent
  }

  @Test
  fun `generic url`() {
    val factory = ModuleKeyFactories.genericUrl
    assertThat(factory.create(URI("other:text"))).isNotPresent

    val module = factory.create(URI("file:///some/file"))
    assertThat(module).isPresent
  }

  @Test
  fun `class path`() {
    val factory = ModuleKeyFactories.classPath(this::class.java.classLoader)
    assertThat(factory.create(URI("other:text"))).isNotPresent

    val module = factory.create(URI("modulepath:/foo/bar.pkl"))
    assertThat(module).isPresent
  }

  @Test
  fun `module path - basics`() {
    val factory = ModuleKeyFactories.modulePath(ModulePathResolver(listOf("/path".toPath())))
    assertThat(factory.create(URI("other:text"))).isNotPresent

    val module = factory.create(URI("modulepath:/foo/bar.pkl"))
    assertThat(module).isPresent
  }

  @Test
  fun `module path - directories`(@TempDir tempDir: Path) {
    val dir1 = tempDir.resolve("foo/bar").createDirectories()
    val dir2 = tempDir.resolve("foo2/bar2").createDirectories()
    val filePath = dir2.resolve("baz/mymodule.pkl").createParentDirectories().writeString("x = 1")

    val factory = ModuleKeyFactories.modulePath(ModulePathResolver(listOf(dir1, dir2)))
    val module = factory.create(URI("modulepath:/baz/mymodule.pkl"))
    assertThat(module).isPresent

    val resolved = module.get().resolve(SecurityManagers.defaultManager)
    assertThat(resolved.original).isSameAs(module.get())
    assertThat(resolved.uri).isEqualTo(filePath.toRealPath().toUri())
    assertThat(resolved.loadSource().trim()).isEqualTo("x = 1")
  }

  @Test
  fun `module path - jar files`(@TempDir tempDir: Path) {
    val jarFile = tempDir.resolve("test.jar")
    jarFile.outputStream().use { outStream ->
      javaClass.getResourceAsStream("test.jar")!!.use { inStream -> inStream.copyTo(outStream) }
    }

    val factory = ModuleKeyFactories.modulePath(ModulePathResolver(listOf(jarFile)))
    val module = factory.create(URI("modulepath:/dir1/module1.pkl"))
    assertThat(module).isPresent

    val resolved = module.get().resolve(SecurityManagers.defaultManager)
    assertThat(resolved.original).isSameAs(module.get())
    assertThat(resolved.uri.toString()).startsWith("jar:file:")
    assertThat(resolved.uri.toString()).endsWith("/test.jar!/dir1/module1.pkl")
    assertThat(resolved.loadSource().trim()).isEqualTo("x = 1")

    // Assert that creating a modulekey from the resolved URI is still a ModulePath
    val module2 = factory.create(resolved.uri)
    assertThat(module2).isPresent
    assertThat(module2.get()).hasSameClassAs(module.get())
  }

  @Test
  fun `module path via service provider`() {
    val factories = ModuleKeyFactories.fromServiceProviders()
    assertThat(factories).hasSize(1)

    val factory = factories.single()

    val module = factory.create(URI("test:foo"))
    assertThat(module).isPresent
    assertThat(module.get().uri.scheme).isEqualTo("modulepath")

    val module2 = factory.create(URI("other"))
    assertThat(module2).isNotPresent
  }

  @Test
  fun external() {
    val extReader = TestExternalModuleReader()
    val (proc, runtime) =
      TestExternalReaderProcess.initializeTestHarness(listOf(extReader), emptyList())

    val factory = ModuleKeyFactories.external(extReader.scheme, proc)

    val module = factory.create(URI("test:foo"))
    assertThat(module).isPresent
    assertThat(module.get().uri.scheme).isEqualTo("test")

    val module2 = factory.create(URI("other"))
    assertThat(module2).isNotPresent

    proc.close()
    runtime.close()
  }
}
