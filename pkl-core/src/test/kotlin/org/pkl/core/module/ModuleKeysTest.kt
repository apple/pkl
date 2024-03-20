package org.pkl.core.module

import org.pkl.commons.createParentDirectories
import org.pkl.commons.toPath
import org.pkl.commons.writeString
import org.pkl.core.SecurityManagers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.FileNotFoundException
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path
import kotlin.io.path.createFile

class ModuleKeysTest {
  private val securityManager = SecurityManagers.defaultManager

  @Test
  fun synthetic() {
    val key = ModuleKeys.synthetic(URI("repl:some"), "age = 40")

    assertThat(key.uri).isEqualTo(URI("repl:some"))
    assertThat(key.isCached).isFalse
    assertThat(ModuleKeys.isStdLibModule(key)).isFalse
    assertThat(ModuleKeys.isBaseModule(key)).isFalse

    assertThat(key.resolve(securityManager).uri).isEqualTo(URI("repl:some"))
    assertThat(key.resolve(securityManager).loadSource()).contains("age = 40")
  }

  @Test
  fun `standard library`() {
    val key = ModuleKeys.standardLibrary(URI("pkl:test"))

    assertThat(key.uri).isEqualTo(URI("pkl:test"))
    assertThat(key.isCached).isTrue
    assertThat(ModuleKeys.isStdLibModule(key)).isTrue
    assertThat(ModuleKeys.isBaseModule(key)).isFalse

    assertThat(key.resolve(securityManager).uri).isEqualTo(URI("pkl:test"))
    assertThat(key.resolve(securityManager).loadSource()).contains("module pkl.test")
  }

  @Test
  fun `standard library - wrong scheme`() {
    assertThrows<IllegalArgumentException> {
      ModuleKeys.standardLibrary(URI("other:base"))
    }
  }

  @Test
  fun `file`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("foo.pkl").createFile()
    file.writeString("age = 40")

    val uri = file.toUri()
    val key = ModuleKeys.file(uri, file.toAbsolutePath())

    assertThat(key.uri).isEqualTo(uri)
    assertThat(key.isCached).isTrue
    assertThat(ModuleKeys.isStdLibModule(key)).isFalse
    assertThat(ModuleKeys.isBaseModule(key)).isFalse

    val resolvedKey = key.resolve(securityManager)
    val resolvedUri = uri.toPath().toRealPath().toUri()

    assertThat(resolvedKey.uri).isEqualTo(resolvedUri)
    assertThat(resolvedKey.loadSource()).isEqualTo("age = 40")
  }

  @Test
  fun `class path`() {
    val uri = URI("modulepath:/org/pkl/core/module/NamedModuleResolversTest.pkl")
    val key = ModuleKeys.classPath(uri, ModuleKeysTest::class.java.classLoader)

    assertThat(key.uri).isEqualTo(uri)
    assertThat(key.isCached).isTrue
    assertThat(ModuleKeys.isStdLibModule(key)).isFalse
    assertThat(ModuleKeys.isBaseModule(key)).isFalse

    val resolved = key.resolve(SecurityManagers.defaultManager)
    assertThat(resolved.original).isSameAs(key)
    assertThat(resolved.uri.scheme).isEqualTo("file")
    assertThat(resolved.uri.path).endsWith("/org/pkl/core/module/NamedModuleResolversTest.pkl")
    assertThat(resolved.loadSource().trim()).isEqualTo("x = 1")
  }

  @Test
  fun `class path - wrong scheme`() {
    assertThrows<IllegalArgumentException> {
      ModuleKeys.classPath(URI("other:base"), ModuleKeysTest::class.java.classLoader)
    }
  }

  @Test
  fun `class path - module not found`() {
    val key = ModuleKeys.classPath(URI("modulepath:/non/existing"), ModuleKeysTest::class.java.classLoader)

    assertThrows<FileNotFoundException> {
      key.resolve(SecurityManagers.defaultManager)
    }
  }

  @Test
  fun `class path - missing leading slash`() {
    val e = assertThrows<IllegalArgumentException> {
      ModuleKeys.classPath(URI("modulepath:foo/bar.pkl"), ModuleKeysTest::class.java.classLoader)
    }
    assertThat(e).hasMessageContaining("`/`")
  }

  @Test
  fun `module path`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("foo/bar.pkl").createParentDirectories()
    file.writeString("age = 40")

    val uri = URI("modulepath:/foo/bar.pkl")
    val key = ModuleKeys.modulePath(uri, ModulePathResolver(listOf(tempDir)))

    assertThat(key.uri).isEqualTo(uri)
    assertThat(key.isCached).isTrue
    assertThat(ModuleKeys.isStdLibModule(key)).isFalse
    assertThat(ModuleKeys.isBaseModule(key)).isFalse

    val resolvedKey = key.resolve(securityManager)

    assertThat(resolvedKey.uri.scheme)
      .isEqualTo("file")
    assertThat(resolvedKey.loadSource())
      .isEqualTo("age = 40")
  }

  @Test
  fun `module path - wrong scheme`() {
    assertThrows<IllegalArgumentException> {
      ModuleKeys.modulePath(URI("other:base"), ModulePathResolver(listOf()))
    }
  }

  @Test
  fun `module path - module not found`() {
    val key = ModuleKeys.modulePath(URI("modulepath:/non/existing"), ModulePathResolver(listOf()))

    assertThrows<FileNotFoundException> {
      key.resolve(SecurityManagers.defaultManager)
    }
  }

  @Test
  fun `module path - missing leading slash`() {
    val e = assertThrows<IllegalArgumentException> {
      ModuleKeys.modulePath(URI("modulepath:foo/bar.pkl"), ModulePathResolver(listOf()))
    }
    assertThat(e).hasMessageContaining("`/`")
  }

  @Test
  fun `package - no version`() {
    val e = assertThrows<URISyntaxException> {
      ModuleKeys.pkg(URI("package://localhost:0/birds#/Bird.pkl"))
    }
    assertThat(e).hasMessageContaining("A package URI must have its path suffixed by its version")
  }

  @Test
  fun `package - invalid semver`() {
    val e = assertThrows<URISyntaxException> {
      ModuleKeys.pkg(URI("package://localhost:0/birds@notAVersion#/Bird.pkl"))
    }
    assertThat(e).hasMessageContaining("`notAVersion` could not be parsed")
  }

  @Test
  fun `package - missing leading slash`() {
    val e = assertThrows<URISyntaxException> {
      ModuleKeys.pkg(URI("package:invalid"))
    }
    assertThat(e).hasMessageContaining("Module URI `package:invalid` is missing a `/` after `package:`")
  }

  @Test
  fun `package - missing authority`() {
    val e = assertThrows<URISyntaxException> {
      ModuleKeys.pkg(URI("package:/not/a/valid/path"))
    }
    assertThat(e).hasMessageContaining("Package URIs must have an authority component")

    val e2 = assertThrows<URISyntaxException> {
      ModuleKeys.pkg(URI("package:///not/a/valid/path"))
    }
    assertThat(e2).hasMessageContaining("Package URIs must have an authority component")
  }

  @Test
  fun `package - missing path`() {
    val e = assertThrows<URISyntaxException> {
      ModuleKeys.pkg(URI("package://example.com"))
    }
    assertThat(e).hasMessageContaining("Package URIs must have a path component")
  }

  @Test
  fun `generic URL`() {
    val uri = URI("https://apple.com/some/foo.pkl")
    val key = ModuleKeys.genericUrl(uri)

    assertThat(key.uri).isEqualTo(uri)
    assertThat(key.isCached).isTrue

    assertThat(ModuleKeys.isStdLibModule(key)).isFalse
    assertThat(ModuleKeys.isBaseModule(key)).isFalse
  }

  @Test
  fun `generic URL - resolve`(@TempDir tempDir: Path) {
    // use file: URL because it's easiest to test (but normally handled by ModuleKeys.file())

    val file = tempDir.resolve("foo.pkl").createFile()
    file.writeString("age = 40")

    val uri = file.toUri()
    val key = ModuleKeys.genericUrl(uri)

    val resolvedKey = key.resolve(securityManager)
    assertThat(resolvedKey.uri).isEqualTo(uri)
    assertThat(resolvedKey.loadSource()).isEqualTo("age = 40")
  }

  @Test
  fun `generic URL with unknown scheme`() {
    val uri = URI("repl:foo")
    val key = ModuleKeys.genericUrl(uri)

    val e = assertThrows<MalformedURLException> {
      key.resolve(securityManager)
    }

    assertThat(e)
      .hasMessage("unknown protocol: repl")
  }
}
