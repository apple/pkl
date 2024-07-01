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
import org.junit.jupiter.api.Test

class ClassInheritanceTest {
  private val evaluator = Evaluator.preconfigured()

  @Test
  fun `property override without type annotation is considered an object property definition`() {
    val module =
      evaluator.evaluateSchema(
        ModuleSource.text(
          """
        class Thing
        open class Base {
          hidden thing: Thing
        }
        class Derived extends Base {
          thing {}
        }
        """
            .trimIndent()
        )
      )

    val derivedClass = module.classes["Derived"]!!
    assertThat(derivedClass.properties["thing"]).isNull()
    val thingProperty = derivedClass.allProperties["thing"]
    assertThat(thingProperty).isNotNull
    assertThat(thingProperty!!.isHidden).isTrue
    assertThat(thingProperty.type).isInstanceOf(PType.Class::class.java)
    assertThat((thingProperty.type as PType.Class).pClass).isSameAs(module.classes["Thing"])
  }

  @Test
  fun `property override with type annotation is considered a class property definition`() {
    val module =
      evaluator.evaluateSchema(
        ModuleSource.text(
          """
        class Thing
        open class Base {
          hidden thing: Thing
        }
        class Derived extends Base {
          thing: Thing = new {}
        }
        """
            .trimIndent()
        )
      )

    val derivedClass = module.classes["Derived"]!!
    val thingProperty = derivedClass.properties["thing"]
    assertThat(thingProperty).isNotNull
    assertThat(thingProperty!!.isHidden).isFalse
    assertThat(thingProperty.type).isInstanceOf(PType.Class::class.java)
    assertThat((thingProperty.type as PType.Class).pClass).isSameAs(module.classes["Thing"])
  }
}
