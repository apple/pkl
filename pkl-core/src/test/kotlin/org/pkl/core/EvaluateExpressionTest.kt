package org.pkl.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EvaluateExpressionTest {
  companion object {
    val evaluator by lazy { Evaluator.preconfigured() }

    @AfterAll
    @JvmStatic
    fun afterAll() {
      evaluator.close()
    }
  }

  private fun evaluate(program: String, expression: String): Any {
    return evaluator.evaluateExpression(ModuleSource.text(program), expression)
  }

  @Test
  fun `evaluate expression`() {
    val program = """
      res1 = 1
      res2 {
        res3 = 3
        res4 = 4
      }
    """.trimIndent()
    assertThat(evaluate(program, "res1")).isEqualTo(1L)
    val res2 = evaluate(program, "res2")
    assertThat(res2).isInstanceOf(PObject::class.java)
    res2 as PObject
    assertThat(res2.get("res3")).isEqualTo(3L)
    assertThat(res2.get("res4")).isEqualTo(4L)
  }

  @Test
  fun `evaluate subpath`() {
    val resp = evaluate("""
      foo {
        bar = 2
      }
    """.trimIndent(), "foo.bar")

    assertThat(resp).isEqualTo(2L)
  }

  @Test
  fun `evaluate output text`() {
    val result = evaluate("""
      foo {
        bar = 2
      }
      
      output {
        renderer = new YamlRenderer {}
      }
    """.trimIndent(), "output.text")

    assertThat(result).isEqualTo("""
      foo:
        bar: 2

    """.trimIndent())
  }

  @Test
  fun `evaluate let expression`() {
    val result = evaluate("foo = 1", "let (bar = 2) foo + bar")

    assertThat(result).isEqualTo(3L)
  }

  @Test
  fun `evaluate import expression`() {
    val result = evaluate(
      "",
      """import("pkl:release").current.documentation.homepage"""
    )

    assertThat(result as String).startsWith("https://pkl.apple.com/pkl/")
  }

  @Test
  fun `evaluate expression with invalid syntax`() {
    val error = assertThrows<PklException> { evaluate("foo = 1", "<>!!!") }

    assertThat(error).hasMessageContaining("Mismatched input")
    assertThat(error).hasMessageContaining("<>!!!")
  }

  @Test
  fun `evaluate non-expression`() {
    val error = assertThrows<PklException> { evaluate("bar = 2", "bar = 15") }

    assertThat(error).hasMessageContaining("Mismatched input")
    assertThat(error).hasMessageContaining("bar = 15")
  }

  @Test
  fun `evaluate semantically invalid expression`() {
    val error = assertThrows<PklException> { evaluate("foo = 1", "foo as String") }

    assertThat(error).hasMessageContaining("Expected value of type `String`, but got type `Int`")
  }
}
