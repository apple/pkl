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
package org.pkl.core

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.core.ModuleSource.text

class EvaluateMultipleFileOutputTest {

  private val evaluator = Evaluator.preconfigured()

  @Test
  fun `output files`() {
    val program =
      """
      output {
        files {
          ["foo.yml"] {
            text = "foo: foo text"
          }
          ["bar.yml"] {
            text = "bar: bar text"
          }
          ["bar/biz.yml"] {
            text = "biz: bar biz"
          }
          ["bar/../bark.yml"] {
            text = "bark: bark bark"
          }
        }
      }
    """
        .trimIndent()
    val output = evaluator.evaluateOutputFiles(text(program))
    assertThat(output.keys).isEqualTo(setOf("foo.yml", "bar.yml", "bar/biz.yml", "bar/../bark.yml"))
    assertThat(output["foo.yml"]?.text).isEqualTo("foo: foo text")
    assertThat(output["bar.yml"]?.text).isEqualTo("bar: bar text")
    assertThat(output["bar/biz.yml"]?.text).isEqualTo("biz: bar biz")
    assertThat(output["bar/../bark.yml"]?.text).isEqualTo("bark: bark bark")
  }

  @Test
  fun `using a renderer`() {
    val evaluator = Evaluator.preconfigured()
    val program =
      """
      output {
        files {
          ["foo.json"] {
            value = new {
              foo = "fooey"
              bar = "barrey"
            }
            renderer = new JsonRenderer {}
          }
        }
      }
    """
        .trimIndent()
    val output = evaluator.evaluateOutputFiles(text(program))
    assertThat(output["foo.json"]?.text)
      .isEqualTo(
        """
      {
        "foo": "fooey",
        "bar": "barrey"
      }
      
    """
          .trimIndent()
      )
  }

  @Test
  fun `reading files after the evaluator is closed`() {
    val evaluator = Evaluator.preconfigured()
    val program =
      """
      output {
        files {
          ["foo.json"] {
            value = new {
              foo = "fooey"
              bar = "barrey"
            }
            renderer = new JsonRenderer {}
          }
        }
      }
    """
        .trimIndent()
    val output = evaluator.evaluateOutputFiles(text(program))
    evaluator.close()
    assertThrows<PklException> { output["foo.json"]!!.text }
  }
}
