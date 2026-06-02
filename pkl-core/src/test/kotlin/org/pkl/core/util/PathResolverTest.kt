/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PathResolverTest {
  private val posix = PathResolvers.forPosix()
  private val windows = PathResolvers.forWindows()

  @Nested
  inner class PosixTests {
    @Test
    fun `simple relative path appended to file base`() {
      assertThat(posix.resolvePath(URI("file:///home/user/base.pkl"), "sibling.pkl"))
        .isEqualTo("/home/user/base.pkl/sibling.pkl")
    }

    @Test
    fun `relative path appended to directory base (trailing slash)`() {
      assertThat(posix.resolvePath(URI("file:///home/user/dir/"), "file.pkl"))
        .isEqualTo("/home/user/dir/file.pkl")
    }

    @Test
    fun `nested relative path`() {
      assertThat(posix.resolvePath(URI("file:///home/user/base.pkl"), "sub/dir/file.pkl"))
        .isEqualTo("/home/user/base.pkl/sub/dir/file.pkl")
    }

    @Test
    fun `absolute path overrides base`() {
      assertThat(posix.resolvePath(URI("file:///home/user/base.pkl"), "/absolute/path.pkl"))
        .isEqualTo("/absolute/path.pkl")
    }

    @Test
    fun `absolute path containing dot is normalized`() {
      assertThat(posix.resolvePath(URI("file:///home/user/base.pkl"), "/foo/./bar.pkl"))
        .isEqualTo("/foo/bar.pkl")
    }

    @Test
    fun `absolute path containing double-dot is normalized`() {
      assertThat(posix.resolvePath(URI("file:///home/user/base.pkl"), "/foo/../bar.pkl"))
        .isEqualTo("/bar.pkl")
    }

    @Test
    fun `single dot in relative path is elided`() {
      assertThat(posix.resolvePath(URI("file:///home/user/base.pkl"), "./sibling.pkl"))
        .isEqualTo("/home/user/base.pkl/sibling.pkl")
    }

    @Test
    fun `double-dot in relative path goes up one segment`() {
      assertThat(posix.resolvePath(URI("file:///home/user/base.pkl"), "../sibling.pkl"))
        .isEqualTo("/home/user/sibling.pkl")
    }

    @Test
    fun `two double-dots in relative path go up two segments`() {
      assertThat(posix.resolvePath(URI("file:///home/user/a/b.pkl"), "../../c.pkl"))
        .isEqualTo("/home/user/c.pkl")
    }

    @Test
    fun `mixed relative path with dot-dot`() {
      assertThat(posix.resolvePath(URI("file:///home/user/base.pkl"), "sub/dir/../../other.pkl"))
        .isEqualTo("/home/user/base.pkl/other.pkl")
    }

    @Test
    fun `double-dot beyond root clamps to root`() {
      assertThat(posix.resolvePath(URI("file:///file.pkl"), "../../root.pkl"))
        .isEqualTo("/root.pkl")
    }

    @Test
    fun `root base with relative path`() {
      assertThat(posix.resolvePath(URI("file:///"), "file.pkl")).isEqualTo("/file.pkl")
    }

    @Test
    fun `URI with percent-encoded path is decoded`() {
      // URI.getPath() decodes percent-encoding
      assertThat(posix.resolvePath(URI("file:///home/user%20name/base.pkl"), "file.pkl"))
        .isEqualTo("/home/user name/base.pkl/file.pkl")
    }
  }

  @Nested
  inner class WindowsTests {
    @Test
    fun `drive letter URI with simple relative path`() {
      assertThat(windows.resolvePath(URI("file:///C:/Users/user/base.pkl"), "relative.pkl"))
        .isEqualTo("""C:\Users\user\base.pkl\relative.pkl""")
    }

    @Test
    fun `drive letter URI with nested relative path`() {
      assertThat(windows.resolvePath(URI("file:///C:/Users/user/base.pkl"), "sub\\dir\\file.pkl"))
        .isEqualTo("""C:\Users\user\base.pkl\sub\dir\file.pkl""")
    }

    @Test
    fun `drive letter URI with forward-slash relative path is normalised to backslash`() {
      assertThat(windows.resolvePath(URI("file:///C:/Users/user/base.pkl"), "sub/dir/file.pkl"))
        .isEqualTo("""C:\Users\user\base.pkl\sub\dir\file.pkl""")
    }

    @Test
    fun `drive letter URI with directory base (trailing backslash)`() {
      assertThat(windows.resolvePath(URI("file:///C:/Users/dir/"), "file.pkl"))
        .isEqualTo("""C:\Users\dir\file.pkl""")
    }

    @Test
    fun `backslash dot in relative path is elided`() {
      assertThat(windows.resolvePath(URI("file:///C:/Users/user/base.pkl"), """..\sibling.pkl"""))
        .isEqualTo("""C:\Users\user\sibling.pkl""")
    }

    @Test
    fun `forward-slash dot-dot in relative path is normalised`() {
      assertThat(windows.resolvePath(URI("file:///C:/Users/user/base.pkl"), "../sibling.pkl"))
        .isEqualTo("""C:\Users\user\sibling.pkl""")
    }

    @Test
    fun `backslash single-dot in relative path is elided`() {
      assertThat(windows.resolvePath(URI("file:///C:/Users/user/base.pkl"), """.\\sibling.pkl"""))
        .isEqualTo("""C:\Users\user\base.pkl\sibling.pkl""")
    }

    @Test
    fun `two double-dots go up two segments`() {
      // join: C:\Users\user\a\b.pkl\..\..\c.pkl -> C:\Users\user\c.pkl
      assertThat(windows.resolvePath(URI("file:///C:/Users/user/a/b.pkl"), "..\\..\\c.pkl"))
        .isEqualTo("""C:\Users\user\c.pkl""")
    }

    @Test
    fun `double-dot beyond drive root clamps to root`() {
      assertThat(windows.resolvePath(URI("file:///C:/base.pkl"), "..\\..\\out.pkl"))
        .isEqualTo("""C:\out.pkl""")
    }

    @Test
    fun `absolute path on same drive overrides base`() {
      assertThat(windows.resolvePath(URI("file:///C:/Users/base.pkl"), """C:\other\path.pkl"""))
        .isEqualTo("""C:\other\path.pkl""")
    }

    @Test
    fun `absolute path on different drive overrides base`() {
      assertThat(windows.resolvePath(URI("file:///C:/Users/base.pkl"), """D:\other.pkl"""))
        .isEqualTo("""D:\other.pkl""")
    }

    @Test
    fun `absolute path with forward slashes is accepted`() {
      assertThat(windows.resolvePath(URI("file:///C:/Users/base.pkl"), "D:/other.pkl"))
        .isEqualTo("""D:\other.pkl""")
    }

    @Test
    fun `root-relative backslash path takes drive root from base`() {
      // \root.pkl is root-relative; drive letter is inherited from base
      assertThat(windows.resolvePath(URI("file:///C:/Users/base.pkl"), """\root.pkl"""))
        .isEqualTo("""C:\root.pkl""")
    }

    @Test
    fun `root-relative forward-slash path takes drive root from base`() {
      assertThat(windows.resolvePath(URI("file:///C:/Users/base.pkl"), "/root.pkl"))
        .isEqualTo("""C:\root.pkl""")
    }

    @Test
    fun `UNC URI with simple relative path`() {
      assertThat(windows.resolvePath(URI("file://server/share/base.pkl"), "relative.pkl"))
        .isEqualTo("""\\server\share\base.pkl\relative.pkl""")
    }

    @Test
    fun `UNC URI with double-dot goes up within share`() {
      assertThat(windows.resolvePath(URI("file://server/share/dir/base.pkl"), """..\sibling.pkl"""))
        .isEqualTo("""\\server\share\dir\sibling.pkl""")
    }

    @Test
    fun `UNC URI double-dot beyond share root clamps to share root`() {
      assertThat(windows.resolvePath(URI("file://server/share/base.pkl"), "..\\..\\.\\out.pkl"))
        .isEqualTo("""\\server\share\out.pkl""")
    }

    @Test
    fun `UNC URI with absolute UNC path overrides base`() {
      assertThat(
          windows.resolvePath(URI("file://server/share/base.pkl"), """\\other\share\file.pkl""")
        )
        .isEqualTo("""\\other\share\file.pkl""")
    }

    @Test
    fun `absolute path containing dot is normalized`() {
      assertThat(windows.resolvePath(URI("file:///C:/Users/base.pkl"), """C:\foo\.\bar.pkl"""))
        .isEqualTo("""C:\foo\bar.pkl""")
    }

    @Test
    fun `absolute path containing double-dot is normalized`() {
      assertThat(windows.resolvePath(URI("file:///C:/Users/base.pkl"), """C:\foo\..\bar.pkl"""))
        .isEqualTo("""C:\bar.pkl""")
    }

    @Test
    fun `file URI without drive letter`() {
      assertThat(windows.resolvePath(URI("file:///path/to/foo"), "bar"))
        .isEqualTo("""\path\to\foo\bar""")
    }
  }
}
