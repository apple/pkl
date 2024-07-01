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

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.toPath

class SecurityManagersTest {
  private val manager =
    SecurityManagers.standard(
      listOf(Pattern.compile("test:foo/bar")),
      listOf(Pattern.compile("env:FOO_BAR")),
      { uri -> if (uri.scheme == "one") 1 else if (uri.scheme == "two") 2 else 0 },
      null
    )

  @Test
  fun `checkResolveModule() - complete match`() {
    val e = catchThrowable { manager.checkResolveModule(URI("test:foo/bar")) }
    assertThat(e).doesNotThrowAnyException()
  }

  @Test
  fun `checkResolveModule() - partial match from start`() {
    val e = catchThrowable { manager.checkResolveModule(URI("test:foo/bar/baz")) }
    assertThat(e).doesNotThrowAnyException()
  }

  @Test
  fun `checkResolveModule() - partial match not from start`() {
    assertThrows<SecurityManagerException> { manager.checkResolveModule(URI("other:test:foo/bar")) }
  }

  @Test
  fun `checkResolveModule() - no match`() {
    assertThrows<SecurityManagerException> { manager.checkResolveModule(URI("other:uri")) }
  }

  @Test
  fun `checkResolveModule() - no match #2`() {
    assertThrows<SecurityManagerException> { manager.checkResolveModule(URI("test:foo/baz")) }
  }

  @Test
  fun `checkReadResource() - complete match`() {
    val e = catchThrowable { manager.checkReadResource(URI("env:FOO_BAR")) }
    assertThat(e).doesNotThrowAnyException()
  }

  @Test
  fun `checkReadResource() - partial match from start`() {
    val e = catchThrowable { manager.checkReadResource(URI("env:FOO_BAR_BAZ")) }
    assertThat(e).doesNotThrowAnyException()
  }

  @Test
  fun `checkReadResource() - partial match not from start`() {
    assertThrows<SecurityManagerException> { manager.checkReadResource(URI("other:env:FOO_BAR")) }
  }

  @Test
  fun `checkReadResource() - no match`() {
    assertThrows<SecurityManagerException> { manager.checkReadResource(URI("other:uri")) }
  }

  @Test
  fun `checkReadResource() - no match #2`() {
    assertThrows<SecurityManagerException> { manager.checkReadResource(URI("env:FOO_BAZ")) }
  }

  @Test
  fun `checkImportModule() - same trust level`() {
    val e = catchThrowable { manager.checkImportModule(URI("one:foo"), URI("one:bar")) }
    assertThat(e).doesNotThrowAnyException()
  }

  @Test
  fun `checkImportModule() - higher trust level`() {
    assertThrows<SecurityManagerException> {
      manager.checkImportModule(URI("one:foo"), URI("two:bar"))
    }
  }

  @Test
  fun `checkImportModule() - lower trust level`() {
    val e = catchThrowable { manager.checkImportModule(URI("two:foo"), URI("one:bar")) }
    assertThat(e).doesNotThrowAnyException()
  }

  @Test
  fun `default trust levels`() {
    val repl = URI("repl:foo")
    val localFile = URI("file:///some/path")
    val jarLocalFile = URI("jar:file:///some/path!/some/path")
    val namedModule = URI("modulepath:/some/path")
    val remoteFile = URI("file://apple.com/some.path")
    val jarRemoteFile = URI("jar:file://apple.com/some.path!/some/path")
    val other = URI("http://apple.com/some.path")
    val jarOther = URI("jar:http://apple.com/some.path!/some/path")
    val stdLib = URI("pkl:test")

    val levels = SecurityManagers.defaultTrustLevels
    assertThat(levels.apply(repl)).isEqualTo(40)
    assertThat(levels.apply(localFile)).isEqualTo(30)
    assertThat(levels.apply(jarLocalFile)).isEqualTo(30)
    assertThat(levels.apply(namedModule)).isEqualTo(20)
    assertThat(levels.apply(remoteFile)).isEqualTo(10)
    assertThat(levels.apply(jarRemoteFile)).isEqualTo(10)
    assertThat(levels.apply(other)).isEqualTo(10)
    assertThat(levels.apply(jarOther)).isEqualTo(10)
    assertThat(levels.apply(stdLib)).isEqualTo(0)
  }

  @Test
  fun `can resolve modules and resources under root dir - files do exist`(@TempDir tempDir: Path) {
    val rootDir = tempDir.resolve("root")
    Files.createDirectory(rootDir)

    val manager =
      SecurityManagers.standard(
        listOf(Pattern.compile("file")),
        listOf(Pattern.compile("file")),
        SecurityManagers.defaultTrustLevels,
        rootDir
      )

    val path = rootDir.resolve("baz.pkl")
    Files.createFile(path)
    manager.checkResolveModule(path.toUri())
    manager.checkReadResource(path.toUri())

    manager.checkResolveModule(rootDir.toUri().resolve("qux/../baz.pkl"))
    manager.checkReadResource(rootDir.toUri().resolve("qux/../baz.pkl"))
  }

  @Test
  fun `can resolve modules and resources under root dir - files don't exist`() {
    val rootDir = "/foo/bar".toPath()

    val manager =
      SecurityManagers.standard(
        listOf(Pattern.compile("file")),
        listOf(Pattern.compile("file")),
        SecurityManagers.defaultTrustLevels,
        rootDir
      )

    manager.checkResolveModule(Path.of("/foo/bar/baz.pkl").toUri())
    manager.checkReadResource(Path.of("/foo/bar/baz.pkl").toUri())

    manager.checkResolveModule(Path.of("/foo/bar/qux/../baz.pkl").toUri())
    manager.checkReadResource(Path.of("/foo/bar/qux/../baz.pkl").toUri())
  }

  @Test
  fun `cannot resolve modules and resources outside root dir - files do exist`(
    @TempDir tempDir: Path
  ) {
    val rootDir = tempDir.resolve("root")
    Files.createDirectory(rootDir)

    val manager =
      SecurityManagers.standard(
        listOf(Pattern.compile("file")),
        listOf(Pattern.compile("file")),
        SecurityManagers.defaultTrustLevels,
        rootDir
      )

    val path = rootDir.resolve("../baz.pkl")
    Files.createFile(path)
    assertThrows<SecurityManagerException> { manager.checkResolveModule(path.toUri()) }
    assertThrows<SecurityManagerException> { manager.checkReadResource(path.toUri()) }

    val symlink = rootDir.resolve("qux")
    Files.createSymbolicLink(symlink, tempDir)
    val path2 = symlink.resolve("baz2.pkl")
    Files.createFile(path2)
    assertThrows<SecurityManagerException> { manager.checkResolveModule(path2.toUri()) }
    assertThrows<SecurityManagerException> { manager.checkReadResource(path2.toUri()) }
  }

  @Test
  fun `cannot resolve modules and resources outside root dir - files don't exist`() {
    val rootDir = "/foo/bar".toPath()

    val manager =
      SecurityManagers.standard(
        listOf(Pattern.compile("file")),
        listOf(Pattern.compile("file")),
        SecurityManagers.defaultTrustLevels,
        rootDir
      )

    assertThrows<SecurityManagerException> {
      manager.checkResolveModule(Path.of("/foo/baz.pkl").toUri())
    }
    assertThrows<SecurityManagerException> {
      manager.checkReadResource(Path.of("/foo/baz.pkl").toUri())
    }

    assertThrows<SecurityManagerException> {
      manager.checkResolveModule(Path.of("/foo/bar/../baz.pkl").toUri())
    }
    assertThrows<SecurityManagerException> {
      manager.checkReadResource(Path.of("/foo/bar/../baz.pkl").toUri())
    }
  }
}
