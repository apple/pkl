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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.pkl.core.ModuleSource.text

class DeletionTest {
  companion object {
    private fun evaluate(input: String): PModule {
      return Evaluator.preconfigured().evaluate(text(input))
    }
  }

  @Test
  fun `literal deletion of properties`() {
    val actual =
      evaluate(
        """
        result = new Dynamic {
          deleteFoo = 1
          deleteBar = 2
          deleteBaz = 3
          deleteQux = 4
        } {
          deleteBar = delete
          deleteBaz = 42
          deleteQux = delete
        }
        asMap = result.toMap()
      """
          .trimIndent()
      )
    val result = actual.get("result") as PObject
    assertThat(result.get("deleteFoo")).isEqualTo(1L)
    assertThat(result.get("deleteBaz")).isEqualTo(42L)
    assertThat(result.hasProperty("deleteBar")).isFalse()
    assertThat(result.hasProperty("deleteQux")).isFalse()
    assertThat(actual.get("asMap")).isEqualTo(mapOf("deleteFoo" to 1L, "deleteBaz" to 42L))
  }

  @Test
  fun `literal deletion of entries`() {
    val actual =
      evaluate(
        """
        result = new Mapping {
          ["deleteFoo"] = 1
          ["deleteBar"] = 2
          ["deleteBaz"] = 3
          ["deleteQux"] = 4
        } {
          ["deleteBar"] = delete
          ["deleteBaz"] = 42
          ["deleteQux"] = delete
        }.toMap()
      """
          .trimIndent()
      )
    assertThat(actual.get("result")).isEqualTo(linkedMapOf("deleteFoo" to 1L, "deleteBaz" to 42L))
  }

  @Test
  fun `literal deletion of elements`() {
    val actual =
      evaluate(
        """
        result = new Dynamic {
          "foo"
          "bar"
          "baz"
        } {
          [1] = delete
        }.toList()
      """
          .trimIndent()
      )
    assertThat(actual.get("result")).isEqualTo(listOf("foo", "baz"))
  }

  @Test
  fun `member predicate deletion`() {
    val actual =
      evaluate(
        """
        source = new Dynamic {
          "one"
          "two"
          "three"
          "four"
          "five"
          ["foo"] = "six"
          ["bar"] = "seven"
          ["baz"] = "eight"
        } {
          [[contains("e")]] = delete
        }
        elements = source.toList()
        entries = source.toMap()
      """
          .trimIndent()
      )
    assertThat(actual.get("elements")).isEqualTo(listOf("two", "four"))
    assertThat(actual.get("entries")).isEqualTo(linkedMapOf("foo" to "six"))
  }

  @Test
  fun `deleted properties are no longer listed in errors`() {
    assertThatThrownBy {
        evaluate(
          """
            result = new Dynamic {
              foo = 1
              bar = 2
              baz = 3
            } {
              foo = delete
            }.qux
          """
            .trimIndent()
        )
      }
      .hasMessageNotContaining("foo")
  }

  @Test
  fun `element indices are correctly renamed`() {
    val actual =
      evaluate(
        """
      source = new Dynamic {
        "foo"
        "bar"
        "baz"
      } {
        [1] = delete
      }
      result = new Dynamic {
        for (i, v in source) {
          "\(i) -> \(v)"
        }
      }.toList()
    """
          .trimIndent()
      )
    assertThat(actual.get("result")).isEqualTo(listOf("0 -> foo", "1 -> baz"))
  }

  @Test
  fun `direct element access after multiple deletes`() {
    val actual =
      evaluate(
        """
      source = new Dynamic {
        "foo"   //  0  0  0
        "bar"   //  1  -  -
        "baz"   //  2  1  -
        "qux"   //  3  2  -  
        "quux"  //  4  3  1
        "corge" //  5  4  2
      } {
        [1] = delete
      } {
        [1] = delete
        [2] = delete
        [4] = "expected"
      }
      result = source[2]
    """
          .trimIndent()
      )
    assertThat(actual.get("result")).isEqualTo("expected")
  }
}
