/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util

import java.io.FileNotFoundException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.toPath
import org.pkl.core.SecurityManager
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.module.ModuleKeys
import org.pkl.core.runtime.ModuleResolver

class IoUtilsTest {
  object FakeSecurityManager : SecurityManager {
    override fun checkResolveModule(uri: URI) {}

    override fun checkImportModule(importingModule: URI, importedModule: URI) {}

    override fun checkReadResource(resource: URI) {}

    override fun checkResolveResource(resource: URI) {}
  }

  private val moduleResolver =
    ModuleResolver(
      listOf(
        ModuleKeyFactories.pkg,
        ModuleKeyFactories.file,
        ModuleKeyFactories.standardLibrary,
        ModuleKeyFactories.genericUrl,
      )
    )

  @Test
  fun `ensurePathEndsWithSlash() - relative URI`() {
    val uri = URI("/some/path")
    assertThat(IoUtils.ensurePathEndsWithSlash(uri)).isEqualTo(URI("/some/path/"))

    val uri2 = URI("/some/path/")
    assertThat(IoUtils.ensurePathEndsWithSlash(uri2)).isEqualTo(URI("/some/path/"))
  }

  @Test
  fun `ensurePathEndsWithSlash() - absolute URI`() {
    val uri = URI("https://apple.com/path")
    assertThat(IoUtils.ensurePathEndsWithSlash(uri)).isEqualTo(URI("https://apple.com/path/"))

    val uri2 = URI("https://apple.com/path/")
    assertThat(IoUtils.ensurePathEndsWithSlash(uri2)).isEqualTo(URI("https://apple.com/path/"))

    val uri3 = URI("https://user:pwd@apple.com:8080/path?foo=bar#frag")
    assertThat(IoUtils.ensurePathEndsWithSlash(uri3))
      .isEqualTo(URI("https://user:pwd@apple.com:8080/path/?foo=bar#frag"))

    val uri4 = URI("https://user:pwd@apple.com:8080/path/?foo=bar#frag")
    assertThat(IoUtils.ensurePathEndsWithSlash(uri4))
      .isEqualTo(URI("https://user:pwd@apple.com:8080/path/?foo=bar#frag"))
  }

  @Test
  fun `ensurePathEndsWithSlash() - opaque URI`() {
    val uri = URI("foo:some.thing")
    assertThat(IoUtils.ensurePathEndsWithSlash(uri)).isEqualTo(URI("foo:some.thing"))
  }

  @Test
  fun `resolving relative URI against triple-slash file URI results in triple-slash file URI`() {
    val resolved = IoUtils.resolve(URI("file:///foo/bar"), URI("baz"))
    assertThat(resolved).isEqualTo(URI("file:///foo/baz"))
  }

  @Test
  fun `resolving relative URI against single-slash file URI results in single-slash file URI`() {
    val resolved = IoUtils.resolve(URI("file:/foo/bar"), URI("baz"))
    assertThat(resolved).isEqualTo(URI("file:/foo/baz"))
  }

  @Test
  fun `resolving relative URI against triple-slash jar-file URI results in triple-slash jar-file URI`() {
    val resolved = IoUtils.resolve(URI("jar:file:///some/archive.zip!/foo/bar"), URI("baz"))
    assertThat(resolved).isEqualTo(URI("jar:file:///some/archive.zip!/foo/baz"))
  }

  @Test
  fun `resolving relative URI against single-slash jar-file URI results in single-slash jar-file URI`() {
    val resolved = IoUtils.resolve(URI("jar:file:/some/archive.zip!/foo/bar"), URI("baz"))
    assertThat(resolved).isEqualTo(URI("jar:file:/some/archive.zip!/foo/baz"))
  }

  @Test
  fun `resolve absolute URI against jar-file URI`() {
    val uri = URI("jar:file:///some/archive.zip!/foo/bar.pkl")
    assertThat(IoUtils.resolve(uri, URI("https://apple.com"))).isEqualTo(URI("https://apple.com"))
  }

  @Test
  fun `resolving other URIs works the same as java_net_URI_resolve()`() {
    val resolved = IoUtils.resolve(URI("https://apple.com/foo/bar"), URI("baz"))
    assertThat(resolved).isEqualTo(URI("https://apple.com/foo/baz"))

    val resolved2 = IoUtils.resolve(URI("test:opaque1"), URI("test:opaque2"))
    assertThat(resolved2).isEqualTo(URI("test:opaque2"))
  }

  @Test
  fun `relativize file URLs`() {
    assertThat(IoUtils.relativize(URI("file:///foo/bar/baz.pkl"), URI("file:///foo/bar/baz.pkl")))
      .isEqualTo(URI("baz.pkl"))

    assertThat(IoUtils.relativize(URI("file:///foo/bar/baz.pkl"), URI("file:///foo/bar/qux.pkl")))
      .isEqualTo(URI("baz.pkl"))

    assertThat(IoUtils.relativize(URI("file:///foo/bar/baz.pkl"), URI("file:///foo/bar/")))
      .isEqualTo(URI("baz.pkl"))

    assertThat(IoUtils.relativize(URI("file:///foo/bar/baz.pkl"), URI("file:///foo/bar")))
      .isEqualTo(URI("bar/baz.pkl"))

    // URI.relativize() returns an absolute URI here
    assertThat(IoUtils.relativize(URI("file:///foo/bar/baz.pkl"), URI("file:///foo/qux/")))
      .isEqualTo(URI("../bar/baz.pkl"))

    assertThat(IoUtils.relativize(URI("file:///foo/bar/baz.pkl"), URI("file:///foo/qux/qux2/")))
      .isEqualTo(URI("../../bar/baz.pkl"))

    assertThat(IoUtils.relativize(URI("file:///foo/bar/baz.pkl"), URI("file:///foo/qux/qux2")))
      .isEqualTo(URI("../bar/baz.pkl"))

    assertThat(IoUtils.relativize(URI("file:///foo/bar/baz.pkl"), URI("file:///qux/qux2/")))
      .isEqualTo(URI("../../foo/bar/baz.pkl"))

    assertThat(IoUtils.relativize(URI("file:///foo/bar/baz.pkl"), URI("https:///foo/bar/baz.pkl")))
      .isEqualTo(URI("file:///foo/bar/baz.pkl"))
  }

  @Test
  fun `relativize HTTP URLs`() {
    // perhaps URI("") would be a more precise result
    assertThat(
        IoUtils.relativize(URI("https://foo.com/bar/baz.pkl"), URI("https://foo.com/bar/baz.pkl"))
      )
      .isEqualTo(URI("baz.pkl"))

    assertThat(
        IoUtils.relativize(URI("https://foo.com/bar/baz.pkl"), URI("https://foo.com/bar/qux.pkl"))
      )
      .isEqualTo(URI("baz.pkl"))

    assertThat(IoUtils.relativize(URI("https://foo.com/bar/baz.pkl"), URI("https://foo.com/qux/")))
      .isEqualTo(URI("../bar/baz.pkl"))

    assertThat(
        IoUtils.relativize(
          URI("https://foo.com/bar/baz.pkl?query"),
          URI("https://foo.com/bar/qux.pkl"),
        )
      )
      .isEqualTo(URI("baz.pkl?query"))

    assertThat(
        IoUtils.relativize(
          URI("https://foo.com/bar/baz.pkl#fragment"),
          URI("https://foo.com/bar/qux.pkl"),
        )
      )
      .isEqualTo(URI("baz.pkl#fragment"))

    assertThat(
        IoUtils.relativize(
          URI("https://foo.com/bar/baz.pkl?query#fragment"),
          URI("https://foo.com/bar/qux.pkl"),
        )
      )
      .isEqualTo(URI("baz.pkl?query#fragment"))

    assertThat(
        IoUtils.relativize(
          URI("https://foo.com/bar/baz.pkl?query#fragment"),
          URI("https://foo.com/bar/qux.pkl?query2#fragment2"),
        )
      )
      .isEqualTo(URI("baz.pkl?query#fragment"))

    assertThat(
        IoUtils.relativize(URI("https://foo.com:80/bar/baz.pkl"), URI("https://foo.com:443/bar/"))
      )
      .isEqualTo(URI("https://foo.com:80/bar/baz.pkl"))

    assertThat(
        IoUtils.relativize(
          URI("https://foo.com:80/bar/baz.pkl"),
          URI("https://bar.com:80/bar/baz.pkl"),
        )
      )
      .isEqualTo(URI("https://foo.com:80/bar/baz.pkl"))

    assertThat(
        IoUtils.relativize(
          URI("https://foo:bar@foo.com:80/bar/baz.pkl"),
          URI("https://foo:baz@bar.com:80/bar/baz.pkl"),
        )
      )
      .isEqualTo(URI("https://foo:bar@foo.com:80/bar/baz.pkl"))

    assertThat(IoUtils.relativize(URI("https://foo/bar/baz.pkl"), URI("file://foo/bar/baz.pkl")))
      .isEqualTo(URI("https://foo/bar/baz.pkl"))
  }

  @Test
  fun `isWhitespace()`() {
    assertThat(IoUtils.isWhitespace("")).isTrue
    assertThat(IoUtils.isWhitespace("  \t ")).isTrue
    assertThat(IoUtils.isWhitespace("  a ")).isFalse
  }

  @Test
  fun `toPath()`() {
    val uri = URI("file:///foo/bar.txt")
    assertThat(IoUtils.toPath(uri)).isEqualTo(uri.toPath())

    assertThat(IoUtils.toPath(URI("https://apple.com"))).isNull()
    assertThat(IoUtils.toPath(URI("unknown://foo/bar"))).isNull()
  }

  @Test
  fun `toPath() only accepts absolute URIs`() {
    assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
      IoUtils.toPath(URI("foo/bar"))
    }
  }

  @Test
  fun getMaxLineLength() {
    assertThat(IoUtils.getMaxLineLength("abc")).isEqualTo(3)
    assertThat(IoUtils.getMaxLineLength("abc\n\nabcd\n\nab")).isEqualTo(4)
  }

  @Test
  fun capitalize() {
    assertThat(IoUtils.capitalize("abc")).isEqualTo("Abc")
    assertThat(IoUtils.capitalize("Abc")).isEqualTo("Abc")
    assertThat(IoUtils.capitalize("a&*")).isEqualTo("A&*")
    assertThat(IoUtils.capitalize("_&*")).isEqualTo("_&*")
    assertThat(IoUtils.capitalize("abc def")).isEqualTo("Abc def")
    assertThat(IoUtils.capitalize("Abc def")).isEqualTo("Abc def")
  }

  @Test
  fun inferModuleName() {
    val assertions =
      mapOf(
        "file:///foo.pkl" to "foo",
        "file:///foo/bar/baz.pkl" to "baz",
        "jar:file:///some/archive.zip!/foo.pkl" to "foo",
        "jar:file:///some/archive.zip!/foo/bar/baz.pkl" to "baz",
        "https://apple.com/foo.pkl" to "foo",
        "https://apple.com/foo/bar/baz.pkl" to "baz",
        "pkl:foo" to "foo",
        "pkl:foo.bar.baz" to "baz",
        "modulepath:/foo.pkl" to "foo",
        "modulepath:/foo/bar/baz.pkl" to "baz",
        "package://example.com/foo/bar@1.0.0#/baz.pkl" to "baz",
        "package://example.com/foo/bar@1.0.0#/baz/biz/qux.pkl" to "qux",
      )
    for ((uriStr, name) in assertions) {
      val uri = URI(uriStr)
      val moduleKey = moduleResolver.resolve(uri)
      assertThat(IoUtils.inferModuleName(moduleKey)).isEqualTo(name)
    }
  }

  @Test
  fun toUri() {
    assertThat(IoUtils.toUri("file://foo.pkl")).isEqualTo(URI("file://foo.pkl"))
    assertThat(IoUtils.toUri("foo.pkl")).isEqualTo(URI("foo.pkl"))
    assertThat(IoUtils.toUri("foo bar.pkl").rawPath).isEqualTo("foo%20bar.pkl")
    assertThrows<URISyntaxException> { IoUtils.toUri("file:foo bar.pkl") }
  }

  @Test
  fun `resolveUri - file hierarchy`(@TempDir tempDir: Path) {
    val file1 = tempDir.resolve("base1/base2/foo.pkl").createParentDirectories().createFile()
    val file2 =
      tempDir.resolve("base1/base2/dir1/dir2/foo.pkl").createParentDirectories().createFile()
    val file3 = tempDir.resolve("base1/dir2/foo.pkl").createParentDirectories().createFile()

    val uri = file2.toUri()
    val key = ModuleKeys.file(uri)

    assertThat(IoUtils.resolve(FakeSecurityManager, key, URI("..."))).isEqualTo(file1.toUri())
    assertThat(IoUtils.resolve(FakeSecurityManager, key, URI(".../foo.pkl")))
      .isEqualTo(file1.toUri())
    assertThat(IoUtils.resolve(FakeSecurityManager, key, URI(".../base2/foo.pkl")))
      .isEqualTo(file1.toUri())
    assertThat(IoUtils.resolve(FakeSecurityManager, key, URI(".../base1/base2/foo.pkl")))
      .isEqualTo(file1.toUri())
    assertThat(IoUtils.resolve(FakeSecurityManager, key, URI(".../dir2/foo.pkl")))
      .isEqualTo(file3.toUri())

    assertThrows<URISyntaxException> { IoUtils.resolve(FakeSecurityManager, key, URI(".../")) }

    assertThrows<URISyntaxException> { IoUtils.resolve(FakeSecurityManager, key, URI("...abc")) }

    assertThrows<FileNotFoundException> {
      IoUtils.resolve(FakeSecurityManager, key, URI(".../bar.pkl"))
    }

    assertThrows<FileNotFoundException> {
      IoUtils.resolve(FakeSecurityManager, key, URI(".../base2/bar.pkl"))
    }

    assertThrows<FileNotFoundException> {
      IoUtils.resolve(FakeSecurityManager, key, URI(".../foo/bar.pkl"))
    }
  }

  @Test
  fun `resolveUri - classpath hierarchy`() {
    val classLoader = this::class.java.classLoader
    val uri = URI("modulepath:/org/pkl/core/module/dir1/dir2/NamedModuleResolversTest.pkl")
    val key = ModuleKeys.classPath(uri, classLoader)
    assertThat(IoUtils.resolve(FakeSecurityManager, key, URI("...")))
      .isEqualTo(URI("modulepath:/org/pkl/core/module/NamedModuleResolversTest.pkl"))

    assertThat(IoUtils.resolve(FakeSecurityManager, key, URI(".../NamedModuleResolversTest.pkl")))
      .isEqualTo(URI("modulepath:/org/pkl/core/module/NamedModuleResolversTest.pkl"))

    assertThat(
        IoUtils.resolve(FakeSecurityManager, key, URI(".../module/NamedModuleResolversTest.pkl"))
      )
      .isEqualTo(URI("modulepath:/org/pkl/core/module/NamedModuleResolversTest.pkl"))

    assertThat(
        IoUtils.resolve(
          FakeSecurityManager,
          key,
          URI(".../core/module/NamedModuleResolversTest.pkl"),
        )
      )
      .isEqualTo(URI("modulepath:/org/pkl/core/module/NamedModuleResolversTest.pkl"))

    assertThat(
        IoUtils.resolve(
          FakeSecurityManager,
          key,
          URI(".../org/pkl/core/module/NamedModuleResolversTest.pkl"),
        )
      )
      .isEqualTo(URI("modulepath:/org/pkl/core/module/NamedModuleResolversTest.pkl"))

    val uri2 = URI("modulepath:/foo/bar/baz.pkl")
    val key2 = ModuleKeys.classPath(uri2, classLoader)
    assertThrows<FileNotFoundException> { IoUtils.resolve(FakeSecurityManager, key2, URI("...")) }
    assertThrows<FileNotFoundException> {
      IoUtils.resolve(FakeSecurityManager, key2, URI(".../other.pkl"))
    }
    assertThrows<FileNotFoundException> {
      IoUtils.resolve(FakeSecurityManager, key2, URI(".../dir1/dir2/NamedModuleResolversTest.pkl"))
    }

    assertThrows<URISyntaxException> { IoUtils.resolve(FakeSecurityManager, key2, URI(".../")) }
    assertThrows<URISyntaxException> {
      IoUtils.resolve(FakeSecurityManager, key2, URI("...NamedModuleResolversTest.pkl"))
    }
  }

  @Test
  fun `readBytes(URL) does not support HTTP URLs`() {
    assertThrows<IllegalArgumentException> { IoUtils.readBytes(URI("https://example.com")) }
    assertThrows<IllegalArgumentException> { IoUtils.readBytes(URI("http://example.com")) }
  }

  @Test
  fun `readString(URL) does not support HTTP URLs`() {
    assertThrows<IllegalArgumentException> {
      IoUtils.readString(URI("https://example.com").toURL())
    }
    assertThrows<IllegalArgumentException> { IoUtils.readString(URI("http://example.com").toURL()) }
  }

  @Test
  fun `toUrl() throws UnsupportedOperationException for file URIs with remote hostnames`() {
    val uri = URI("file://example.com/bar/baz.pkl")
    val exception = assertThrows<URISyntaxException> { IoUtils.validateFileUri(uri) }
    assertThat(exception.message).contains("File URIs may not include an authority.")
  }

  @Test
  fun `encodePath encodes characters reserved on windows`() {
    assertThat(IoUtils.encodePath("foo:bar")).isEqualTo("foo(3a)bar")
    assertThat(IoUtils.encodePath("<>:\"\\|?*")).isEqualTo("(3c)(3e)(3a)(22)(5c)(7c)(3f)(2a)")
    assertThat(IoUtils.encodePath("foo(3a)bar")).isEqualTo("foo((3a)bar")
    assertThat(IoUtils.encodePath("(")).isEqualTo("((")
    assertThat(IoUtils.encodePath("3a)")).isEqualTo("3a)")
    assertThat(IoUtils.encodePath("foo/bar/baz")).isEqualTo("foo/bar/baz")
  }

  @Test
  fun `normalizeUri removes dot segments from hierarchical URIs`() {
    assertThat(IoUtils.normalizeUri(URI("file:///foo/../baz.pkl"), true))
      .isEqualTo(URI("file:///baz.pkl"))
    assertThat(IoUtils.normalizeUri(URI("file:///foo/%2e./baz.pkl"), true))
      .isEqualTo(URI("file:///baz.pkl"))
    assertThat(IoUtils.normalizeUri(URI("file:///foo/%2e%2e/baz.pkl"), true))
      .isEqualTo(URI("file:///baz.pkl"))
    assertThat(IoUtils.normalizeUri(URI("https://example.com/a/../b?q=1"), true))
      .isEqualTo(URI("https://example.com/b?q=1"))
  }

  @Test
  fun `normalizeUri decodes 2e but not 2f for schemes other than file, jar and package`() {
    assertThat(IoUtils.normalizeUri(URI("https://example.com/a/%2e%2e/b"), true))
      .isEqualTo(URI("https://example.com/b"))
    assertThat(IoUtils.normalizeUri(URI("https://example.com/a/%2E./b"), true))
      .isEqualTo(URI("https://example.com/b"))
    assertThat(IoUtils.normalizeUri(URI("https://example.com/a/../b"), true))
      .isEqualTo(URI("https://example.com/b"))
    // `%2F` is distinct from `/`, and so is preserved
    assertThat(IoUtils.normalizeUri(URI("https://example.com/foo%2fbar/./baz"), true))
      .isEqualTo(URI("https://example.com/foo%2fbar/baz"))
    val uri = URI("https://example.com/a%2f..%2fb")
    assertThat(IoUtils.normalizeUri(uri, true)).isSameAs(uri)
  }

  @Test
  fun `normalizeUri decodes both 2e and 2f for file URIs`() {
    assertThat(IoUtils.normalizeUri(URI("file:///allowed/..%2fsecret/x"), true))
      .isEqualTo(URI("file:///secret/x"))
    assertThat(IoUtils.normalizeUri(URI("file:///allowed/%2E%2e%2Fsecret/x"), true))
      .isEqualTo(URI("file:///secret/x"))
  }

  @Test
  fun `normalizeUri decodes both 2e and 2f in package asset paths`() {
    assertThat(IoUtils.normalizeUri(URI("package://example.com/foo@1.0.0#/a%2f..%2fb.pkl"), true))
      .isEqualTo(URI("package://example.com/foo@1.0.0#/b.pkl"))
    assertThat(
        IoUtils.normalizeUri(URI("projectpackage://example.com/foo@1.0.0#/a/%2e%2e/b.pkl"), true)
      )
      .isEqualTo(URI("projectpackage://example.com/foo@1.0.0#/b.pkl"))
  }

  @Test
  fun `normalizeUri decodes 2f in the archive URI of jar file URIs`() {
    assertThat(IoUtils.normalizeUri(URI("jar:file:///allowed/..%2fsecret/x.zip!/a%2f..%2fb"), true))
      .isEqualTo(URI("jar:file:///secret/x.zip!/a%2f..%2fb"))
  }

  @Test
  fun `normalizeUri leaves non-hierarchical URIs unchanged`() {
    val uri = URI("foo:/bar/../baz")
    assertThat(IoUtils.normalizeUri(uri, false)).isSameAs(uri)
  }

  @Test
  fun `normalizeUri only normalizes fragment if URI is a package`() {
    assertThat(IoUtils.normalizeUri(URI("package://example.com/foo@1.0.0#/bar/../baz.pkl"), true))
      .isEqualTo(URI("package://example.com/foo@1.0.0#/baz.pkl"))
    assertThat(IoUtils.normalizeUri(URI("foo://example.com/bar#/bar/../baz.pkl"), true))
      .isEqualTo(URI("foo://example.com/bar#/bar/../baz.pkl"))
  }

  @Test
  fun `normalizeUri does not add a fragment`() {
    val uri = URI("https://example.com/foo.pkl")
    assertThat(IoUtils.normalizeUri(uri, true)).isEqualTo(uri)
    assertThat(IoUtils.normalizeUri(uri, true).toString()).doesNotContain("#")
  }

  @Test
  fun `normalizeUri returns the same URI if already normalized`() {
    val uri = URI("file:///foo/b%C3%A9/baz%20qux.pkl")
    assertThat(IoUtils.normalizeUri(uri, true)).isSameAs(uri)
  }

  @Test
  fun `normalizeUri only normalizes the archive URI of jar URIs`() {
    assertThat(IoUtils.normalizeUri(URI("jar:file:///foo/../bar.zip!/a/../b.pkl"), true))
      .isEqualTo(URI("jar:file:///bar.zip!/a/../b.pkl"))
    assertThat(IoUtils.normalizeUri(URI("jar:file:///foo/%2e%2e/bar.zip!/a/b.pkl"), true))
      .isEqualTo(URI("jar:file:///bar.zip!/a/b.pkl"))
    assertThat(IoUtils.normalizeUri(URI("JAR:file:///foo/../bar.zip!/b.pkl"), true))
      .isEqualTo(URI("JAR:file:///bar.zip!/b.pkl"))
    val uri = URI("jar:file:///foo/bar.zip!/a/../b.pkl")
    assertThat(IoUtils.normalizeUri(uri, true)).isSameAs(uri)
  }

  @Test
  fun `normalizeUri preserves encoding of everything except dot and slash segments`() {
    assertThat(IoUtils.normalizeUri(URI("file://example.com/a%2Fb/./c"), true))
      .isEqualTo(URI("file://example.com/a/b/c"))
    assertThat(IoUtils.normalizeUri(URI("file:///b%C3%A9/./x%20y"), true))
      .isEqualTo(URI("file:///b%C3%A9/x%20y"))
    assertThat(IoUtils.normalizeUri(URI("file://h/a/../b?x=%2e%2e#frag"), true))
      .isEqualTo(URI("file://h/b?x=%2e%2e#frag"))
  }

  @Test
  fun `normalizeUri handles unusual authorities`() {
    assertThat(IoUtils.normalizeUri(URI("file://user:p%40ss@example.com:8080/a/../b"), true))
      .isEqualTo(URI("file://user:p%40ss@example.com:8080/b"))
    assertThat(IoUtils.normalizeUri(URI("file://[::1]:8080/a/../b"), true))
      .isEqualTo(URI("file://[::1]:8080/b"))
  }

  @Test
  fun `normalizeUri does not turn a path into an authority`() {
    assertThat(IoUtils.normalizeUri(URI("file:////etc/../x"), true)).isEqualTo(URI("file:////x"))
    assertThat(IoUtils.normalizeUri(URI("file:/a/..//b"), true)).isEqualTo(URI("file:/.//b"))
  }

  @Test
  fun `normalizeUri handles trailing dot segments and unresolvable dot-dot segments`() {
    assertThat(IoUtils.normalizeUri(URI("file:///a/b/.."), true)).isEqualTo(URI("file:///a/"))
    assertThat(IoUtils.normalizeUri(URI("file:///a/."), true)).isEqualTo(URI("file:///a/"))
    assertThat(IoUtils.normalizeUri(URI("file:///a/../.."), true)).isEqualTo(URI("file:///../"))
    val uri = URI("file:///../x")
    assertThat(IoUtils.normalizeUri(uri, true)).isSameAs(uri)
  }

  @Test
  fun `normalizeUri is idempotent`() {
    for (str in
      listOf(
        "file:///a/../b/./c/%2e%2e/d",
        "file:/a/..//b",
        "file:////etc/../x",
        "https://example.com/a%2Fb/../c?q#f",
        "package://example.com/foo@1.0.0#/a/%2e%2e/../b.pkl",
        "jar:file:///a/../b.zip!/c/../d",
      )) {
      val once = IoUtils.normalizeUri(URI(str), true)
      assertThat(IoUtils.normalizeUri(once, true)).isEqualTo(once)
    }
  }

  @Test
  fun `normalizeRawPath removes dot segments`() {
    for (isWindows in listOf(false, true)) {
      for (decodeSlash in listOf(false, true)) {
        fun normalize(path: String) = IoUtils.normalizeRawPath(path, decodeSlash, isWindows)
        assertThat(normalize("/a/b/../c")).isEqualTo("/a/c")
        assertThat(normalize("/a/%2e%2E/b")).isEqualTo("/b")
        assertThat(normalize("/a/./b/.")).isEqualTo("/a/b/")
        assertThat(normalize("/a/b/..")).isEqualTo("/a/")
        assertThat(normalize("/../x")).isEqualTo("/../x")
        assertThat(normalize("/a/../..")).isEqualTo("/../")
        assertThat(normalize("//etc/../x")).isEqualTo("//x")
        assertThat(normalize("")).isEqualTo("")
        assertThat(normalize("/")).isEqualTo("/")
      }
    }
  }

  @Test
  fun `normalizeRawPath treats 2f as a path separator only if decodeSlash`() {
    for (isWindows in listOf(false, true)) {
      assertThat(IoUtils.normalizeRawPath("/a%2f..%2fb", true, isWindows)).isEqualTo("/b")
      assertThat(IoUtils.normalizeRawPath("/a%2F..%2Fb", true, isWindows)).isEqualTo("/b")
      assertThat(IoUtils.normalizeRawPath("/a%2fb", true, isWindows)).isEqualTo("/a/b")
      assertThat(IoUtils.normalizeRawPath("/a/.%2f./b", true, isWindows)).isEqualTo("/a/b")
      assertThat(IoUtils.normalizeRawPath("/%2f..%2fx", true, isWindows)).isEqualTo("/x")
      // `%2F` is distinct from `/` if not decodeSlash
      assertThat(IoUtils.normalizeRawPath("/a%2f..%2fb", false, isWindows)).isEqualTo("/a%2f..%2fb")
      assertThat(IoUtils.normalizeRawPath("/a%2fb/./c", false, isWindows)).isEqualTo("/a%2fb/c")
    }
  }

  @Test
  fun `normalizeRawPath does not decode 2f twice`() {
    assertThat(IoUtils.normalizeRawPath("/a%252f..%2fb", true, false)).isEqualTo("/a%252f../b")
    assertThat(IoUtils.normalizeRawPath("/a%255c..%5cb", true, true)).isEqualTo("/a%255c../b")
  }

  @Test
  fun `normalizeRawPath treats 5c as a path separator only on Windows`() {
    // on Windows, `\` is a path separator
    assertThat(IoUtils.normalizeRawPath("/a%5c..%5cb", true, true)).isEqualTo("/b")
    assertThat(IoUtils.normalizeRawPath("/a%5C..%5Cb", true, true)).isEqualTo("/b")
    assertThat(IoUtils.normalizeRawPath("/a%5cb", true, true)).isEqualTo("/a/b")
    assertThat(IoUtils.normalizeRawPath("/a/..%5c..%5cb", true, true)).isEqualTo("/../b")
    assertThat(IoUtils.normalizeRawPath("/a%5c.%2f..%2fb", true, true)).isEqualTo("/b")
    // on other platforms, `\` is a regular file name character
    assertThat(IoUtils.normalizeRawPath("/a%5c..%5cb", true, false)).isEqualTo("/a%5c..%5cb")
    assertThat(IoUtils.normalizeRawPath("/a%5C..%5Cb/./c", true, false)).isEqualTo("/a%5C..%5Cb/c")
  }

  @Test
  fun `normalizeRawPath does not treat 5c as a path separator if not decodeSlash`() {
    for (isWindows in listOf(false, true)) {
      assertThat(IoUtils.normalizeRawPath("/a%5c..%5cb", false, isWindows)).isEqualTo("/a%5c..%5cb")
    }
  }

  @Test
  fun `normalizeRawPath is idempotent`() {
    for (isWindows in listOf(false, true)) {
      for (decodeSlash in listOf(false, true)) {
        for (path in
          listOf("/a/../b", "/a%2f..%2fb", "/a%5c..%5cb", "/a/.%5c./b", "//x/../y", "/../../a")) {
          val once = IoUtils.normalizeRawPath(path, decodeSlash, isWindows)
          assertThat(IoUtils.normalizeRawPath(once, decodeSlash, isWindows)).isEqualTo(once)
        }
      }
    }
  }

  @Test
  fun `normalizeUri does not treat 5c as a path separator in other schemes`() {
    val uri = URI("https://example.com/allowed/..%5csecret/x")
    assertThat(IoUtils.normalizeUri(uri, true)).isSameAs(uri)
  }

  @Test
  fun `normalizeUri replaces the whole asset path of a package URI if it changed`() {
    // the normalized asset path must not be compared to the path of the package
    assertThat(IoUtils.normalizeUri(URI("package://example.com/x@1.0.0#/a/../x@1.0.0"), true))
      .isEqualTo(URI("package://example.com/x@1.0.0#/x@1.0.0"))
  }
}
