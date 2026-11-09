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
package org.pkl.config.kotlin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.pkl.config.java.Config
import org.pkl.config.java.ConfigEvaluator
import org.pkl.config.java.ConfigEvaluatorBuilder
import org.pkl.config.java.mapper.ValueMapperBuilder
import org.pkl.core.ModuleSource.text

class ConfigEvaluatorExtensionsTest : AbstractConfigExtensionsTest() {
  companion object {
    private val evaluator = ConfigEvaluator.preconfigured().forKotlin()

    @AfterAll
    @JvmStatic
    fun afterAll() {
      evaluator.close()
    }
  }

  override fun loadConfig(text: String): Config = evaluator.evaluate(text(text))

  @Test
  fun `ConfigEvaluaterBuilder_forKotlin configures its valueMapperBuilder accordingly`() {
    val builderMapper = ConfigEvaluatorBuilder.preconfigured().forKotlin().valueMapperBuilder
    val referenceMapper = ValueMapperBuilder.preconfigured().forKotlin()

    assertThat(builderMapper.conversions.size).isEqualTo(referenceMapper.conversions.size)
    assertThat(builderMapper.converterFactories.size)
      .isEqualTo(referenceMapper.converterFactories.size)
  }
}
