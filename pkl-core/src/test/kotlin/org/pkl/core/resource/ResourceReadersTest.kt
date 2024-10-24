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
package org.pkl.core.resource

import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path
import kotlin.io.path.outputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.core.module.ModulePathResolver

class ResourceReadersTest {
  @Test
  fun `class path - present resource`() {
    val reader = ResourceReaders.classPath(this::class.java.classLoader)
    val resource = reader.read(URI("modulepath:/org/pkl/core/resource/resource.txt"))

    assertThat(resource).isPresent
    assertThat(resource.get()).isInstanceOf(Resource::class.java)
    assertThat((resource.get() as Resource).text).isEqualTo("content")
    assertThat((resource.get() as Resource).base64).isEqualTo("Y29udGVudA==")
  }

  @Test
  fun `class path - absent resource`() {
    val reader = ResourceReaders.classPath(this::class.java.classLoader)
    val resource = reader.read(URI("modulepath:/non/existing"))

    assertThat(resource).isNotPresent
  }

  @Test
  fun `class path - missing leading slash`() {
    val reader = ResourceReaders.classPath(this::class.java.classLoader)

    assertThrows<URISyntaxException> {
      reader.read(URI("modulepath:org/pkl/core/resource/resource.txt"))
    }
  }

  @Test
  fun `module path - present resource`(@TempDir tempDir: Path) {
    val jarFile = tempDir.resolve("resource1.jar")
    jarFile.outputStream().use { outStream ->
      javaClass.getResourceAsStream("resource1.jar")!!.use { inStream ->
        inStream.copyTo(outStream)
      }
    }
    val zipFile = tempDir.resolve("resource2.zip")
    zipFile.outputStream().use { outStream ->
      javaClass.getResourceAsStream("resource2.zip")!!.use { inStream ->
        inStream.copyTo(outStream)
      }
    }

    ModulePathResolver(listOf(jarFile, zipFile)).use { resolver ->
      val reader = ResourceReaders.modulePath(resolver)

      val resource1 = reader.read(URI("modulepath:/dir1/resource1.txt"))
      assertThat(resource1).isPresent
      assertThat(resource1.get()).isInstanceOf(Resource::class.java)
      assertThat((resource1.get() as Resource).text).isEqualTo("content\n")

      val resource2 = reader.read(URI("modulepath:/dir2/subdir2/resource2.txt"))
      assertThat(resource2).isPresent
      assertThat(resource2.get()).isInstanceOf(Resource::class.java)
      assertThat((resource2.get() as Resource).text).isEqualTo("content\n")
      assertThat((resource2.get() as Resource).base64).isEqualTo("Y29udGVudAo=")
    }
  }

  @Test
  fun `module path - absent resource`() {
    val reader = ResourceReaders.modulePath(ModulePathResolver(listOf()))
    val resource = reader.read(URI("modulepath:/non/existing"))

    assertThat(resource).isNotPresent
  }

  @Test
  fun `module path - missing leading slash`() {
    val reader = ResourceReaders.modulePath(ModulePathResolver(listOf()))

    assertThrows<URISyntaxException> { reader.read(URI("modulepath:non/existing")) }
  }

  @Test
  fun `module path - missing jar is ignored`(@TempDir tempDir: Path) {
    val missingJarFile = tempDir.resolve("missing.jar")

    val jarFile = tempDir.resolve("resource1.jar")
    jarFile.outputStream().use { outStream ->
      javaClass.getResourceAsStream("resource1.jar")!!.use { inStream ->
        inStream.copyTo(outStream)
      }
    }

    ModulePathResolver(listOf(missingJarFile, jarFile)).use { resolver ->
      val reader = ResourceReaders.modulePath(resolver)

      val resource1 = reader.read(URI("modulepath:/dir1/resource1.txt"))
      assertThat(resource1).isPresent
      assertThat(resource1.get()).isInstanceOf(Resource::class.java)
      assertThat((resource1.get() as Resource).text).isEqualTo("content\n")
      assertThat((resource1.get() as Resource).base64).isEqualTo("Y29udGVudAo=")
    }
  }

  @Test
  fun `via service provider`() {
    val readers = ResourceReaders.fromServiceProviders()
    assertThat(readers).hasSize(1)

    val reader = readers.single()
    val resource = reader.read(URI("test:foo"))

    assertThat(resource).contains("success")
  }
}
