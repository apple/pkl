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
package org.pkl.core.stdlib

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.pkl.core.Evaluator
import org.pkl.core.ModuleSource
import org.pkl.core.PObject

class UrlModuleTest {
  companion object {
    private val evaluator by lazy { Evaluator.preconfigured() }

    @AfterAll
    @JvmStatic
    fun afterAll() {
      evaluator.close()
    }
  }

  @Test
  fun `a URL exports as an object whose properties are its components`() {
    val url =
      evaluator.evaluateExpression(
        // language=Pkl
        ModuleSource.text("""import "pkl:url""""),
        """new url.Parser {}.parse("https://user:pw@example.com:8080/a?q=1#f")""",
      ) as PObject

    assertThat(url.classInfo.qualifiedName).isEqualTo("pkl.url#Url")
    // the derived reads are external, so they are not exported
    assertThat(url.properties.keys)
      .containsExactlyInAnyOrder(
        "scheme",
        "username",
        "password",
        "host",
        "port",
        "path",
        "search",
        "fragment",
      )
    assertThat(url.getProperty("host")).isEqualTo("example.com")
    assertThat(url.getProperty("port")).isEqualTo(8080L)
  }
}
