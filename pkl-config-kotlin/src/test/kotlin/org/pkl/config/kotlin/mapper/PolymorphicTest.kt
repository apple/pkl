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
package org.pkl.config.kotlin.mapper

import com.example.PolymorphicLib
import com.example.PolymorphicModuleTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.config.java.ConfigEvaluatorBuilder
import org.pkl.config.kotlin.forKotlin
import org.pkl.config.kotlin.to
import org.pkl.core.ModuleSource.modulePath

class PolymorphicTest {
    @Test
    fun `deserializing polymorphic objects`() {
        val evaluator = ConfigEvaluatorBuilder.preconfigured().forKotlin().build()
        val config = evaluator.evaluate(modulePath("/codegenPkl/PolymorphicModuleTest.pkl"))
        val module = config.to<PolymorphicModuleTest>()
        assertThat(module.desserts[0]).isInstanceOf(PolymorphicModuleTest.Strudel::class.java)
        assertThat(module.desserts[1])
            .isInstanceOf(PolymorphicModuleTest.TurkishDelight::class.java)
        assertThat(module.planes[0]).isInstanceOf(PolymorphicLib.Jet::class.java)
        assertThat(module.planes[1]).isInstanceOf(PolymorphicLib.Propeller::class.java)
    }
}
