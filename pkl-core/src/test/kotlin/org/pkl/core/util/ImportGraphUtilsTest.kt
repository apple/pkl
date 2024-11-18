/*
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
package org.pkl.core.util

import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.ImportGraph

class ImportGraphUtilsTest {
  @Test
  fun basic() {
    val barUri = URI("file:///bar.pkl")
    val fooUri = URI("file:///foo.pkl")
    val graph =
      ImportGraph(
        mapOf(
          fooUri to setOf(ImportGraph.Import(barUri)),
          barUri to setOf(ImportGraph.Import(fooUri))
        ),
        // resolved URIs is not important
        mapOf()
      )
    val cycles = ImportGraphUtils.findImportCycles(graph)
    assertThat(cycles).isEqualTo(listOf(listOf(fooUri, barUri)))
  }

  @Test
  fun `two cycles`() {
    val barUri = URI("file:///bar.pkl")
    val fooUri = URI("file:///foo.pkl")
    val bizUri = URI("file:///biz.pkl")
    val quxUri = URI("file:///qux.pkl")
    val graph =
      ImportGraph(
        mapOf(
          fooUri to setOf(ImportGraph.Import(barUri)),
          barUri to setOf(ImportGraph.Import(fooUri)),
          bizUri to setOf(ImportGraph.Import(quxUri)),
          quxUri to setOf(ImportGraph.Import(bizUri))
        ),
        // resolved URIs is not important
        mapOf()
      )
    val cycles = ImportGraphUtils.findImportCycles(graph)
    assertThat(cycles).isEqualTo(listOf(listOf(fooUri, barUri), listOf(bizUri, quxUri)))
  }

  @Test
  fun `no cycles`() {
    val barUri = URI("file:///bar.pkl")
    val fooUri = URI("file:///foo.pkl")
    val bizUri = URI("file:///biz.pkl")
    val quxUri = URI("file:///qux.pkl")
    val graph =
      ImportGraph(
        mapOf(
          barUri to setOf(ImportGraph.Import(fooUri)),
          fooUri to setOf(ImportGraph.Import(bizUri)),
          bizUri to setOf(ImportGraph.Import(quxUri)),
          quxUri to setOf()
        ),
        // resolved URIs is not important
        mapOf()
      )
    val cycles = ImportGraphUtils.findImportCycles(graph)
    assertThat(cycles).isEmpty()
  }

  @Test
  fun `self-import`() {
    val fooUri = URI("file:///foo.pkl")
    val graph =
      ImportGraph(
        mapOf(fooUri to setOf(ImportGraph.Import(fooUri))),
        // resolved URIs is not important
        mapOf()
      )
    val cycles = ImportGraphUtils.findImportCycles(graph)
    assertThat(cycles).isEqualTo(listOf(listOf(fooUri)))
  }
}
