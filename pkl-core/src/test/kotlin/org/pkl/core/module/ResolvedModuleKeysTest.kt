package org.pkl.core.module

import java.net.URI
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.createTempFile
import org.pkl.commons.writeString

class ResolvedModuleKeysTest {
  private val module = ModuleKeys.synthetic(URI("test:module"), "x = 1")

  @Test
  fun `path()`(@TempDir tempDir: Path) {
    val path = tempDir.createTempFile().writeString("x = 1")
    val resolvedUri = URI("test:resolved.uri")
    val resolved = ResolvedModuleKeys.file(module, resolvedUri, path)

    assertThat(resolved.original).isSameAs(module)
    assertThat(resolved.uri).isEqualTo(resolvedUri)
    assertThat(resolved.loadSource()).isEqualTo("x = 1")
  }

  @Test
  fun `url()`(@TempDir tempDir: Path) {
    val path = tempDir.createTempFile().writeString("x = 1")
    val resolvedUri = URI("test:resolved.uri")
    val resolved = ResolvedModuleKeys.url(
      module, resolvedUri, path.toUri().toURL()
    )

    assertThat(resolved.original).isSameAs(module)
    assertThat(resolved.uri).isEqualTo(resolvedUri)
    assertThat(resolved.loadSource()).isEqualTo("x = 1")
  }

  @Test
  fun `virtual()`() {
    val resolvedUri = URI("test:resolved.uri")
    val resolved = ResolvedModuleKeys.virtual(module, resolvedUri, "x = 1", false)

    assertThat(resolved.original).isSameAs(module)
    assertThat(resolved.uri).isEqualTo(resolvedUri)
    assertThat(resolved.loadSource()).isEqualTo("x = 1")
  }
}
