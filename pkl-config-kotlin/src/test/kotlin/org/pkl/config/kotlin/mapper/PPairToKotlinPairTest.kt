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

import kotlin.Pair
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.pkl.config.java.mapper.Types
import org.pkl.config.java.mapper.ValueMapperBuilder
import org.pkl.config.kotlin.forKotlin
import org.pkl.core.*
import org.pkl.core.ModuleSource.modulePath

class PPairToKotlinPairTest {
    companion object {
        private val evaluator = Evaluator.preconfigured()

        private val module =
            evaluator.evaluate(modulePath("org/pkl/config/kotlin/mapper/PPairToKotlinPairTest.pkl"))

        private val mapper = ValueMapperBuilder.preconfigured().forKotlin().build()

        @AfterAll
        @Suppress("unused")
        @JvmStatic
        fun afterAll() {
            evaluator.close()
        }
    }

    @Test
    fun ex1() {
        val ex1 = module.getProperty("ex1")
        val mapped: Pair<Int, Duration> =
            mapper.map(
                ex1,
                Types.parameterizedType(Pair::class.java, Integer::class.java, Duration::class.java)
            )
        assertThat(mapped).isEqualTo(Pair(1, Duration(3.0, DurationUnit.SECONDS)))
    }

    @Test
    fun ex2() {
        val ex2 = module.getProperty("ex2")
        val mapped: Pair<PObject, PObject> =
            mapper.map(
                ex2,
                Types.parameterizedType(Pair::class.java, PObject::class.java, PObject::class.java)
            )

        assertThat(mapped.first.properties).containsOnly(entry("name", "pigeon"), entry("age", 40L))

        assertThat(mapped.second.properties)
            .containsOnly(entry("name", "parrot"), entry("age", 30L))
    }
}
