/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.core.runtime.BaseModule

class ClassTypeTest {
  @Test
  fun `Class type checks preserve type argument`() {
    Evaluator.preconfigured().use { evaluator ->
      val output =
        evaluator.evaluateOutputText(
          ModuleSource.text(
            """
            open class A
            open class B
            class C extends A
            typealias AAlias = A
            typealias ClassOf<Type> = Class<Type>

            output {
              text =
                "\(C is Class<A>)\n" +
                "\(C is Class<B>)\n" +
                "\(A is Class<A>)\n" +
                "\(A is Class<B>)\n" +
                "\(C is Class<AAlias>)\n" +
                "\(C is ClassOf<A>)\n" +
                "\(C is ClassOf<B>)\n" +
                "\(List is Class<List>)"
            }
            """
              .trimIndent()
          )
        )

      assertThat(output).isEqualTo("true\nfalse\ntrue\nfalse\ntrue\ntrue\nfalse\ntrue")
    }
  }

  @Test
  fun `Class type annotation rejects a non-subclass`() {
    Evaluator.preconfigured().use { evaluator ->
      val exception =
        assertThrows<PklException> {
          evaluator.evaluate(
            ModuleSource.text(
              """
              open class A
              open class B
              value: Class<A> = B
              """
                .trimIndent()
            )
          )
        }

      assertThat(exception).hasMessageContaining("Expected value of type `Class`")
    }
  }

  @Test
  fun `Class type argument is preserved in exported schema`() {
    Evaluator.preconfigured().use { evaluator ->
      val schema =
        evaluator.evaluateSchema(
          ModuleSource.text(
            """
            class A
            value: Class<A> = A
            """
              .trimIndent()
          )
        )

      val classType = schema.moduleClass.properties.getValue("value").type as PType.Class
      assertThat(classType.pClass).isEqualTo(BaseModule.getClassClass().export())

      val typeArgument = classType.typeArguments.single() as PType.Class
      assertThat(typeArgument.pClass).isSameAs(schema.classes.getValue("A"))
    }
  }

  @Test
  fun `stdlib Class type argument can be a type variable`() {
    Evaluator.preconfigured().use { evaluator ->
      val output =
        evaluator.evaluateOutputText(
          ModuleSource.text(
            """
            output {
              text = List(1, "Pigeon").filterIsInstance(String).first
            }
            """
              .trimIndent()
          )
        )

      assertThat(output).isEqualTo("Pigeon")
    }
  }

  @Test
  fun `Class type arguments must be class types`() {
    listOf(
        "Class<A | B>",
        "Class<A?>",
        "Class<A(isSubclassOf(A))>",
        "Class<List<String>>",
        "Class<ListOfString>",
        "Class<ClassOf<List<String>>>",
        "Class<\"A\">",
        "Class<Int8>",
        "Class<module>",
        "Class<nothing>",
        "Class<unknown>",
      )
      .forEach { type ->
        Evaluator.preconfigured().use { evaluator ->
          val exception =
            assertThrows<PklException> {
              evaluator.evaluate(
                ModuleSource.text(
                  """
                  open class A
                  open class B
                  typealias ListOfString = List<String>
                  typealias ClassOf<Type> = Class<Type>
                  value: $type = A
                  """
                    .trimIndent()
                )
              )
            }

          assertThat(exception).hasMessageContaining("`Class` type arguments must be class types.")
        }
      }
  }
}
