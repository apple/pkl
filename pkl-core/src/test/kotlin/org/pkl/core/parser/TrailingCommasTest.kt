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
package org.pkl.core.parser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.Evaluator
import org.pkl.parser.Parser

// tests type argument and parameter parsing with trailing commas that cannot be tested with
// snippets because these constructs are currently only allowed in the stdlib
class TrailingCommasTest {
  private val evaluator = Evaluator.preconfigured()

  @Test
  fun `class type parameter lists parse correctly`() {
    val module =
      Parser()
        .parseModule(
          """
        class Foo<
          Key,
          Value,
        >
        
        class Bar<
          Key,
          Value,
        > {
          baz: Key
          buzz: Value
        }
      """
            .trimIndent()
        )

    val fooClass = module.classes.find { it.name.value == "Foo" }
    assertThat(fooClass).isNotNull
    assertThat(fooClass!!.typeParameterList?.parameters?.first()?.identifier?.value)
      .isEqualTo("Key")
    assertThat(fooClass.typeParameterList?.parameters?.last()?.identifier?.value).isEqualTo("Value")

    val barClass = module.classes.find { it.name.value == "Bar" }
    assertThat(barClass).isNotNull
    assertThat(barClass!!.typeParameterList?.parameters?.first()?.identifier?.value)
      .isEqualTo("Key")
    assertThat(barClass.typeParameterList?.parameters?.last()?.identifier?.value).isEqualTo("Value")
  }

  @Test
  fun `method type parameter lists parse correctly`() {
    val module =
      Parser()
        .parseModule(
          """
        function foo<
          A,
          B,
        >(a: A, b: B,): Value? = "\(a):\(b)"
      """
            .trimIndent()
        )

    val fooMethod = module.methods.find { it.name.value == "foo" }
    assertThat(fooMethod).isNotNull
    assertThat(fooMethod!!.typeParameterList?.parameters?.first()?.identifier?.value).isEqualTo("A")
    assertThat(fooMethod.typeParameterList?.parameters?.last()?.identifier?.value).isEqualTo("B")
  }
}
