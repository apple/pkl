/**
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
package org.pkl.core.ast.builder

import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.core.SecurityManagers
import org.pkl.core.StackFrameTransformers
import org.pkl.core.module.ModuleKeys
import org.pkl.core.runtime.VmException

class ImportsAndReadsParserTest {
  @Test
  fun parse() {
    val moduleText =
      """
      amends "foo.pkl"

      import "bar.pkl"
      import "bazzy/buz.pkl"

      res1 = import("qux.pkl")
      res2 = import*("qux/*.pkl")

      class MyClass {
        res3 {
          res4 {
            res5 = read("/some/dir/chown.txt")
            res6 = read?("/some/dir/chowner.txt")
            res7 = read*("/some/dir/*.txt")
          }
        }
      }
    """
        .trimIndent()
    val moduleKey = ModuleKeys.synthetic(URI("repl:text"), moduleText)
    val imports =
      ImportsAndReadsParser.parse(moduleKey, moduleKey.resolve(SecurityManagers.defaultManager))
    assertThat(imports?.map { it.stringValue })
      .hasSameElementsAs(
        listOf(
          "foo.pkl",
          "bar.pkl",
          "bazzy/buz.pkl",
          "qux.pkl",
          "qux/*.pkl",
          "/some/dir/chown.txt",
          "/some/dir/chowner.txt",
          "/some/dir/*.txt"
        )
      )
  }

  @Test
  fun `invalid syntax`() {
    val moduleText =
      """
        not valid Pkl syntax
      """
        .trimIndent()
    val moduleKey = ModuleKeys.synthetic(URI("repl:text"), moduleText)
    val err =
      assertThrows<VmException> {
        ImportsAndReadsParser.parse(moduleKey, moduleKey.resolve(SecurityManagers.defaultManager))
      }
    assertThat(err.toPklException(StackFrameTransformers.defaultTransformer))
      .hasMessage(
        """
          –– Pkl Error ––
          Mismatched input: `<EOF>`. Expected one of: `{`, `=`, `:`

          1 | not valid Pkl syntax
                                  ^
          at text (repl:text)

        """
          .trimIndent()
      )
  }
}
