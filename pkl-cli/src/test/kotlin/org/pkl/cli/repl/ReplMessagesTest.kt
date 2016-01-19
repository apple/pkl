/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.cli.repl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.commons.toPath
import org.pkl.core.Loggers
import org.pkl.core.SecurityManagers
import org.pkl.core.StackFrameTransformers
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.repl.ReplRequest
import org.pkl.core.repl.ReplResponse
import org.pkl.core.repl.ReplServer

class ReplMessagesTest {
  private val server =
    ReplServer(
      SecurityManagers.defaultManager,
      Loggers.stdErr(),
      listOf(ModuleKeyFactories.standardLibrary),
      listOf(),
      mapOf(),
      mapOf(),
      null,
      null,
      null,
      "/".toPath(),
      StackFrameTransformers.defaultTransformer
    )

  @Test
  fun `run examples`() {
    val examples = ReplMessages.examples
    var startIndex = examples.indexOf("```")
    while (startIndex != -1) {
      val endIndex = examples.indexOf("```", startIndex + 3)
      assertThat(endIndex).isNotEqualTo(-1)
      val text =
        examples
          .substring(startIndex + 3, endIndex)
          .lines()
          .filterNot { it.contains(":force") }
          .joinToString("\n")
      val responses = server.handleRequest(ReplRequest.Eval("1", text, true, true))
      assertThat(responses.size).isBetween(1, 9)
      assertThat(responses).hasOnlyElementsOfType(ReplResponse.EvalSuccess::class.java)
      startIndex = examples.indexOf("```", endIndex + 3)
    }
  }
}
