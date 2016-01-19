package org.pkl.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClassInheritanceTest {
  private val evaluator = Evaluator.preconfigured()

  @Test
  fun `property override without type annotation is considered an object property definition`() {
    val module = evaluator.evaluateSchema(
      ModuleSource.text(
        """
        class Thing
        open class Base {
          hidden thing: Thing
        }
        class Derived extends Base {
          thing {}
        }
        """.trimIndent()
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
    val module = evaluator.evaluateSchema(
      ModuleSource.text(
        """
        class Thing
        open class Base {
          hidden thing: Thing
        }
        class Derived extends Base {
          thing: Thing = new {}
        }
        """.trimIndent()
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
