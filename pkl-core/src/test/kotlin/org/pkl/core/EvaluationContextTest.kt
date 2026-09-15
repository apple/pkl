/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.net.URI
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.io.path.writeText
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.core.resource.ResourceReader
import org.pkl.core.runtime.VmContext

class EvaluationContextTest {
  @Test
  fun `builder creates immutable contexts`() {
    val properties = mutableMapOf("request" to "one")
    val variables = mutableMapOf("REQUEST" to "one")
    val builder =
      EvaluationContext.builder()
        .addExternalProperties(properties)
        .addEnvironmentVariables(variables)
    val first = builder.build()

    properties["request"] = "changed"
    variables["REQUEST"] = "changed"
    builder.addExternalProperty("request", "two").addEnvironmentVariable("REQUEST", "two")

    assertThat(first.externalProperties()).containsExactlyEntriesOf(mapOf("request" to "one"))
    assertThat(first.environmentVariables()).containsExactlyEntriesOf(mapOf("REQUEST" to "one"))
    assertThat(builder.build().externalProperties()).containsEntry("request", "two")
    assertThat(builder.build().environmentVariables()).containsEntry("REQUEST", "two")

    assertThrows<UnsupportedOperationException> {
      first.externalProperties()["request"] = "mutated"
    }
    assertThrows<UnsupportedOperationException> {
      first.environmentVariables()["REQUEST"] = "mutated"
    }
  }

  @Test
  fun `evaluate with external properties and environment variables`() {
    val source =
      ModuleSource.create(
        URI("file:///evaluation-context.pkl"),
        """
        value = read("prop:request") + ":" + read("env:REQUEST") + ":" +
            read("prop:configured") + ":" + read("env:CONFIGURED")

        output {
          text = value
          bytes = value.encodeToBytes("UTF-8")
          value = outer.value
        }
        """
          .trimIndent(),
      )

    EvaluatorBuilder.preconfigured()
      .addExternalProperty("request", "default")
      .addExternalProperty("configured", "configured")
      .addEnvironmentVariable("REQUEST", "default")
      .addEnvironmentVariable("CONFIGURED", "configured")
      .build()
      .use { evaluator ->
        val defaultValue = "default:default:configured:configured"

        assertThat(evaluator.evaluateOutputText(source)).isEqualTo(defaultValue)

        assertThat(evaluator.evaluate(source, context("one")).getProperty("value"))
          .isEqualTo("one:one:configured:configured")
        assertThat(evaluator.evaluateOutputText(source, context("two")))
          .isEqualTo("two:two:configured:configured")
        assertThat(String(evaluator.evaluateOutputBytes(source, context("three"))))
          .isEqualTo("three:three:configured:configured")
        assertThat(evaluator.evaluateOutputValue(source, context("four")))
          .isEqualTo("four:four:configured:configured")

        for (expression in listOf("value", "output.text", "output.value")) {
          assertThat(evaluator.evaluateExpression(source, expression, context(expression)))
            .isEqualTo("$expression:$expression:configured:configured")
        }

        assertThat(evaluator.evaluateExpression(source, "output.bytes", context("bytes")))
          .isEqualTo("bytes:bytes:configured:configured".toByteArray())

        assertThat(evaluator.evaluateOutputText(source, EvaluationContext.builder().build()))
          .isEqualTo(defaultValue)
        assertThat(evaluator.evaluateOutputText(source)).isEqualTo(defaultValue)
      }
  }

  @Test
  fun `cache modules and resources per evaluation`(@TempDir tempDir: Path) {
    val child = tempDir.resolve("child.pkl")
    child.writeText("value = read(\"test:value\") + read(\"prop:request\")")
    val source =
      ModuleSource.create(
        tempDir.resolve("main.pkl").toUri(),
        """
        import "child.pkl"

        output {
          text = child.value + import("child.pkl").value + read("test:value")
        }
        """
          .trimIndent(),
      )

    var reads = 0
    val reader =
      object : ResourceReader {
        override fun getUriScheme() = "test"

        override fun hasHierarchicalUris() = false

        override fun isGlobbable() = false

        override fun read(uri: URI): Optional<Any> = Optional.of((++reads).toString())
      }

    EvaluatorBuilder.preconfigured()
      .apply {
        allowedResources.add(Pattern.compile("test:"))
        addResourceReader(reader)
        addExternalProperty("request", "default")
      }
      .build()
      .use { evaluator ->
        assertThat(evaluator.evaluateOutputText(source)).isEqualTo("1default1default1")

        val context = context("scoped")
        assertThat(evaluator.evaluateOutputText(source, context)).isEqualTo("2scoped2scoped2")
        assertThat(reads).isEqualTo(2)

        child.writeText("value = \"changed\" + read(\"prop:request\")")
        assertThat(evaluator.evaluateOutputText(source, context))
          .isEqualTo("changedscopedchangedscoped3")

        assertThat(evaluator.evaluateOutputText(source)).isEqualTo("1default1default1")
        assertThat(reads).isEqualTo(3)
      }
  }

  @Test
  fun `restore evaluator settings after failed evaluation`() {
    val source =
      ModuleSource.create(
        URI("file:///evaluation-context-failure.pkl"),
        """
        output {
          text = if (read("prop:request") == "bad") throw("failed") else read("env:REQUEST")
        }
        """
          .trimIndent(),
      )

    EvaluatorBuilder.preconfigured()
      .addExternalProperty("request", "default")
      .addEnvironmentVariable("REQUEST", "default")
      .build()
      .use { evaluator ->
        val error =
          assertThrows<PklException> { evaluator.evaluateOutputText(source, context("bad")) }
        assertThat(error).hasMessageContaining("failed")

        assertThat(evaluator.evaluateOutputText(source)).isEqualTo("default")
        assertThat(evaluator.evaluateOutputText(source, context("good"))).isEqualTo("good")
      }
  }

  @Test
  fun `share evaluation state across threads`() {
    val executor = Executors.newSingleThreadExecutor()

    try {
      val reader =
        object : ResourceReader {
          override fun getUriScheme() = "test"

          override fun hasHierarchicalUris() = false

          override fun isGlobbable() = false

          override fun read(uri: URI): Optional<Any> {
            val vmContext = VmContext.get(null)
            val modules = vmContext.moduleCache
            val resources = vmContext.resourceManager

            // Read the active evaluation settings and caches from another thread.
            val result =
              executor
                .submit(
                  Callable {
                    assertThat(vmContext.moduleCache).isSameAs(modules)
                    assertThat(vmContext.resourceManager).isSameAs(resources)
                    vmContext.externalProperties["request"] +
                      ":" +
                      vmContext.environmentVariables["REQUEST"]
                  }
                )
                .get(10, TimeUnit.SECONDS)

            return Optional.of(result)
          }
        }

      EvaluatorBuilder.preconfigured()
        .apply {
          allowedResources.add(Pattern.compile("test:"))
          addResourceReader(reader)
        }
        .build()
        .use { evaluator ->
          val source = ModuleSource.text("output { text = read(\"test:state\") }")

          assertThat(evaluator.evaluateOutputText(source, context("one"))).isEqualTo("one:one")
          assertThat(evaluator.evaluateOutputText(source, context("two"))).isEqualTo("two:two")
        }
    } finally {
      executor.shutdownNow()
    }
  }

  private fun context(value: String) =
    EvaluationContext.builder()
      .addExternalProperty("request", value)
      .addEnvironmentVariable("REQUEST", value)
      .build()
}
