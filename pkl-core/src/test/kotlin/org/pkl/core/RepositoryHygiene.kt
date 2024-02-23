package org.pkl.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.walk
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

/**
 * These tests don't assert Pkl's implementation correctness, but rather that no debugging settings
 * remain in the code.
 */
class RepositoryHygiene {
  @Test
  fun `no remaining language snippet test selection`() {
    assertThat(LanguageSnippetTestsEngine().selection).isEqualTo("")
  }

  @Test
  fun `no output files exists for language snippets without an input`() {
    val input = snippetsFolder.resolve("input")
    val inputs = input.walk().filter {
      it.extension == "pkl"
    }.map {
      val path = input.relativize(it).toString()
      inputRegex.replace(path, "$1$2")
    }.collect(Collectors.toSet())

    val output = snippetsFolder.resolve("output")
    output.walk().filter { it.isRegularFile() }.forEach {
      val out = output.relativize(it).toString()
      checkOutputHasInput(inputs, out)
    }
  }

  private fun checkOutputHasInput(inputs: Set<String>, output: String) {
    val fileTocheck = outputRegex.replace(output, "$1.pkl")
    assertThat(inputs).contains(fileTocheck)
  }

  private val snippetsFolder: Path by lazy {
    FileTestUtils.rootProjectDir.resolve("pkl-core/src/test/files/LanguageSnippetTests")
  }

  companion object {
    private val inputRegex = Regex("(.*)[.][^.]*([.]pkl)")
    private val outputRegex = Regex("(.*)[.][^.]+$")
  }
}
