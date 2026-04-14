/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import org.pkl.core.Version

class RuntimeDataTest {
  private val descendingVersionComparator =
    Comparator<String> { o1, o2 ->
        val version1 = Version.parse(o1)
        val version2 = Version.parse(o2)
        version1.compareTo(version2)
      }
      .reversed()

  @Test
  fun `overwrites the link if newer`() {
    val current =
      RuntimeData(
        knownUsages = setOf(RuntimeDataLink("foo.foo", "../../../foo/1.2.3/foo/index.html"))
      )
    val ref = ModuleRef(pkg = "bar", pkgUri = null, version = "1.2.3", module = "foo")
    val usages =
      setOf(
        ModuleRef(pkg = "foo", pkgUri = null, version = "1.2.4", module = "foo"),
        ModuleRef(pkg = "foo", pkgUri = null, version = "1.3.0", module = "foo"),
      )
    val result = current.addKnownUsages(ref, usages, { it.fullName }, descendingVersionComparator)
    assertThat(result)
      .isEqualTo(
        RuntimeData(
          knownUsages = setOf(RuntimeDataLink("foo.foo", "../../../foo/1.3.0/foo/index.html"))
        )
      )
  }

  @Test
  fun `does not overwrite if link is older`() {
    val current =
      RuntimeData(
        knownUsages = setOf(RuntimeDataLink("foo.foo", "../../../foo/1.3.0/foo/index.html"))
      )
    val ref = ModuleRef(pkg = "bar", pkgUri = null, version = "1.2.3", module = "foo")
    val usages = setOf(ModuleRef(pkg = "foo", pkgUri = null, version = "1.2.0", module = "foo"))

    val result = current.addKnownUsages(ref, usages, { it.fullName }, descendingVersionComparator)
    assertThat(result)
      .isEqualTo(
        RuntimeData(
          knownUsages = setOf(RuntimeDataLink("foo.foo", "../../../foo/1.3.0/foo/index.html"))
        )
      )
  }
}
