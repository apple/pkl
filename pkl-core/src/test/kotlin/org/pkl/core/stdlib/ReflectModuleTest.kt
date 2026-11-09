/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.pkl.core.Evaluator
import org.pkl.core.ModuleSource

class ReflectModuleTest {
  private val evaluator = Evaluator.preconfigured()

  @ValueSource(
    strings =
      [
        "pkl:base",
        "pkl:json",
        "pkl:jsonnet",
        "pkl:math",
        "pkl:protobuf",
        "pkl:reflect",
        "pkl:settings",
        "pkl:shell",
        "pkl:test",
        "pkl:xml",
        "pkl:yaml",
      ]
  )
  @ParameterizedTest(name = "can reflect on {0} module")
  fun `can reflect on stdlib module`(moduleName: String) {
    // language=Pkl
    evaluator.evaluate(
      ModuleSource.text(
        """
        import "pkl:reflect"
        // use toString() because default values of some class properties
        // (e.g., Listing.default) cannot be exported or rendered as Pcf
        output {
          text = reflect.Module(import("$moduleName")).toString()
        }
        """
          .trimIndent()
      )
    )
  }
}
