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
package org.pkl.core.packages

import java.net.URISyntaxException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PackageUriTest {
  @Test
  fun `rejects percent-encoded dot-dot path segments`() {
    val err =
      assertThrows<URISyntaxException> {
        PackageUri("package://attacker.com/%2e%2e/legit.example.com/legit@1.2.3")
      }
    assertThat(err).hasMessageContaining("..")
  }

  @Test
  fun `rejects literal dot-dot path segments`() {
    assertThrows<URISyntaxException> { PackageUri("package://attacker.com/../legit@1.2.3") }
  }

  @Test
  fun `rejects trailing dot-dot segment`() {
    assertThrows<URISyntaxException> { PackageUri("package://attacker.com/foo@1.2.3/%2e%2e") }
  }

  @Test
  fun `accepts a valid package URI`() {
    assertThatCode { PackageUri("package://example.com/my/package@1.0.0") }
      .doesNotThrowAnyException()
  }

  @Test
  fun `does not reject path segments that merely contain dots`() {
    assertThatCode { PackageUri("package://example.com/my..pkg/..foo/bar..@1.0.0") }
      .doesNotThrowAnyException()
  }
}
