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
package org.pkl.config.java.mapper

import com.example.Lib
import com.example.PolymorphicModuleTest
import com.example.PolymorphicModuleTest.Strudel
import com.example.PolymorphicModuleTest.TurkishDelight
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.ModuleSource

class PolymorphicTest {
  @Test
  fun `deserializing polymorphic objects`() {
    val evaluator = ConfigEvaluator.preconfigured()
    val module =
      evaluator
        .evaluate(ModuleSource.modulePath("/codegenPkl/PolymorphicModuleTest.pkl"))
        .`as`(PolymorphicModuleTest::class.java)
    assertThat(module.desserts[0]).isInstanceOf(Strudel::class.java)
    assertThat(module.desserts[1]).isInstanceOf(TurkishDelight::class.java)
    assertThat(module.planes[0]).isInstanceOf(Lib.Jet::class.java)
    assertThat(module.planes[1]).isInstanceOf(Lib.Propeller::class.java)
  }
}
