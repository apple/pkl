package org.pkl.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * These tests don't assert Pkl's implementation correctness, but rather that no debugging settings
 * remain in the code.
 */
class RepositoryHygiene {
  @Test
  fun `no remaining language snippet test selection`() {
    assertThat(LanguageSnippetTestsEngine().selection).isEqualTo("")
  }
}
