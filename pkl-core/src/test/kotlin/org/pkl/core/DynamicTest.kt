package org.pkl.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class DynamicTest {
  private fun brokenInput(value: String): String = """
      class Person { name: String; age: Int }

      person: Person = new { name = 42; age = "Pigeon" } // oops

      output { value = $value }
    """.trimIndent()

  @Test
  fun `property access respects type`() {
    // Does not involve Dynamic, but is a baseline for the other cases.
    val evaluator = Evaluator.preconfigured()
    assertThrows<PklException> { 
      evaluator.evaluateOutputText(ModuleSource.text(brokenInput("person.name")))
    }
  }

  @Test
  fun `toDynamic respects type`() {
    val evaluator = Evaluator.preconfigured()
    assertThrows<PklException> {
      evaluator.evaluateOutputText(ModuleSource.text(brokenInput("person.toDynamic()")))
    }
  }

  @Test
  fun `amending a Dynamic loses type information`() {
    val evaluator = Evaluator.preconfigured()
    val amendingDynamic = "(person.toDynamic()) { name = false; age = 0.ms }"
    assertDoesNotThrow {
      evaluator.evaluateOutputText(ModuleSource.text(brokenInput(amendingDynamic)))
    }
  }
}
