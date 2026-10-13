/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.doc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UtilTest {
  @Test
  fun `getModulePath strips the package prefix`() {
    assertThat(getModulePath("pklgha.foo.bar", "pklgha.")).isEqualTo("foo/bar")
  }

  @Test
  fun `getModulePath leaves module names that do not start with the package prefix intact`() {
    assertThat(getModulePath("GitHubActionSwift", "pklgha.")).isEqualTo("GitHubActionSwift")
  }
}
