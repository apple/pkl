package org.pkl.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.commons.toPath
import org.pkl.core.http.HttpClient
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.repl.ReplRequest
import org.pkl.core.repl.ReplResponse
import org.pkl.core.repl.ReplServer
import org.pkl.core.resource.ResourceReaders

class ReplServerTest {
  private val server = ReplServer(
    SecurityManagers.defaultManager,
    HttpClient.dummyClient(),
    Loggers.stdErr(),
    listOf(
      ModuleKeyFactories.standardLibrary,
      ModuleKeyFactories.classPath(this::class.java.classLoader),
      ModuleKeyFactories.file
    ),
    listOf(
      ResourceReaders.environmentVariable(),
      ResourceReaders.externalProperty()
    ),
    mapOf("NAME1" to "value1", "NAME2" to "value2"),
    mapOf("name1" to "value1", "name2" to "value2"),
    null,
    null,
    null,
    "/".toPath(),
    StackFrameTransformers.defaultTransformer
  )

  @Test
  fun `complete members of local property`() {
    server.handleRequest(
      ReplRequest.Eval("id", "local foo = new { bar = 10 }", false, false)
    )
    val responses = server.handleRequest(
      ReplRequest.Completion("id", "foo")
    )

    assertThat(responses.size).isEqualTo(1)

    val response = responses[0]
    assertThat(response).isInstanceOf(ReplResponse.Completion::class.java)

    val completionResponse = response as ReplResponse.Completion
    assertThat(completionResponse.members.toSortedSet())
      .isEqualTo(
        sortedSetOf(
          "default", "bar", "toList()", "toMap()", "getProperty(",
          "getPropertyOrNull(", "hasProperty(", "ifNonNull(",
          "length()", "getClass()", "toString()", "toTyped("
        )
      )
  }

  @Test
  fun `complete members of module import`() {
    server.handleRequest(ReplRequest.Eval("id", "import \"pkl:test\"", false, false))
    val responses = server.handleRequest(
      ReplRequest.Completion("id", "test")
    )

    assertThat(responses.size).isEqualTo(1)

    val response = responses[0]
    assertThat(response).isInstanceOf(ReplResponse.Completion::class.java)

    val completionResponse = response as ReplResponse.Completion
    assertThat(completionResponse.members).apply {
      contains("relativePathTo(") // member of base.pkl
      contains("catch(") // member of test.pkl
    }
  }

  @Test
  fun `complete members of 'this' expression`() {
    val responses1 = server.handleRequest(
      ReplRequest.Eval("id", "x = 1; function f() = 3", false, false)
    )
    assertThat(responses1.size).isEqualTo(0)

    val responses2 = server.handleRequest(
      ReplRequest.Completion("id", "this")
    )
    assertThat(responses2.size).isEqualTo(1)

    val response = responses2[0]
    assertThat(response).isInstanceOf(ReplResponse.Completion::class.java)

    val completionResponse = response as ReplResponse.Completion
    assertThat(completionResponse.members.toSortedSet())
      .isEqualTo(
        sortedSetOf(
          "output", "toDynamic()", "toMap()", "f()", "x", "ifNonNull(", "getClass()",
          "getProperty(", "getPropertyOrNull(", "hasProperty(", "relativePathTo(", "toString()"
        )
      )
  }

  @Test
  fun `complete members of empty expression`() {
    server.handleRequest(ReplRequest.Eval("id", "x = 1", false, false))
    val responses = server.handleRequest(ReplRequest.Completion("id", ""))

    assertThat(responses.size).isEqualTo(1)

    val response = responses[0]
    assertThat(response).isInstanceOf(ReplResponse.Completion::class.java)

    val completionResponse = response as ReplResponse.Completion
    assertThat(completionResponse.members).apply {
      contains("x")
      contains("Any") // pkl.base class
      contains("Regex(") // pkl.base function
      contains("Infinity") // pkl.base property
    }
  }

  @Test
  fun `read environment variable`() {
    val result = makeEvalRequest("""read("env:NAME1")""")
    assertThat(result).isEqualTo("\"value1\"")
  }

  @Test
  fun `read external property`() {
    val result = makeEvalRequest("""read("prop:name1")""")
    assertThat(result).isEqualTo("\"value1\"")
  }

  @Test
  fun `replace untyped property with typed property`() {
    val result = makeEvalRequest("timeout = 5.ms; timeout")
    assertThat(result).isEqualTo("5.ms")

    val result2 = makeEvalRequest("timeout = 10; timeout")
    assertThat(result2).isEqualTo("10")

    val result3 = makeEvalRequest("timeout: Duration = 8.ms; timeout")
    assertThat(result3).isEqualTo("8.ms")

    val result4 = makeFailingEvalRequest("timeout = 12; timeout")
    assertThat(result4).contains("Expected value of type `Duration`, but got type `Int`.")
  }

  @Test
  fun `replace untyped method with typed method`() {
    val result = makeEvalRequest("function greet(name) = \"Hello, \\(name)!\"; greet(\"Pigeon\") ")
    assertThat(result).isEqualTo("\"Hello, Pigeon!\"")

    val result2 = makeEvalRequest("""greet(42)""")
    assertThat(result2).isEqualTo("\"Hello, 42!\"")

    val result3 = makeEvalRequest("function greet(name: String): String = \"Hello, \\(name)!\"; greet(\"Pigeon\") ")
    assertThat(result3).isEqualTo("\"Hello, Pigeon!\"")

    val result4 = makeFailingEvalRequest("""greet(44)""")
    assertThat(result4).contains("Expected value of type `String`, but got type `Int`.")
  }

  private fun makeEvalRequest(text: String): String {
    val responses =
      server.handleRequest(ReplRequest.Eval("id", text, false, false))

    assertThat(responses).hasSize(1)
    val response = responses[0]
    assertThat(response).isInstanceOf(ReplResponse.EvalSuccess::class.java)

    val successResponse = response as ReplResponse.EvalSuccess
    return successResponse.result
  }

  private fun makeFailingEvalRequest(text: String): String {
    val responses =
      server.handleRequest(ReplRequest.Eval("id", text, false, false))

    assertThat(responses).hasSize(1)
    val response = responses[0]
    assertThat(response).isInstanceOf(ReplResponse.EvalError::class.java)

    val errorResponse = response as ReplResponse.EvalError
    return errorResponse.message
  }
}
