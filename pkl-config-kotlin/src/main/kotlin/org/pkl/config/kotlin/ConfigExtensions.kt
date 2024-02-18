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
package org.pkl.config.kotlin

import org.pkl.config.java.Config
import org.pkl.config.java.ConfigEvaluator
import org.pkl.config.java.ConfigEvaluatorBuilder
import org.pkl.config.java.JavaType
import org.pkl.config.java.mapper.ConversionException
import org.pkl.config.java.mapper.ValueMapperBuilder
import org.pkl.config.kotlin.mapper.KotlinConversions
import org.pkl.config.kotlin.mapper.KotlinConverterFactories

/**
 * Converts this [Config] node to type [T] using the configured
 * [org.pkl.config.java.mapper.ValueMapper].
 *
 * To allow `null` values, specify a nullable type, for example `to<String?>()`.
 *
 * Kotlin code should prefer this method over [Config. as] for the following reasons:
 * * does not clash with Kotlin's `as` keyword
 * * throws [ConversionException] if conversion to non-nullable type returns `null`
 * * easier to use with parameterized types: `to<List<String>>()` vs.
 *   `as(JavaType.listOf(String::class.java))`
 */
inline fun <reified T> Config.to(): T {
  val javaType = object : JavaType<T>() {}
  val result = `as`<T>(javaType.type)

  @Suppress("SENSELESS_COMPARISON")
  if (result == null && null !is T) {
    throw ConversionException(
      "Expected a non-null value but got `null`. " +
        "To allow null values, convert to a nullable Kotlin type, for example `String?`."
    )
  }
  return result
}

/**
 * Configures this [ValueMapperBuilder] with conversions and converter factories for Kotlin types.
 */
fun ValueMapperBuilder.forKotlin(): ValueMapperBuilder =
  addConversions(KotlinConversions.all).addConverterFactories(KotlinConverterFactories.all)

/**
 * Configures this [ConfigEvaluatorBuilder] with conversions and converter factories for Kotlin
 * types.
 */
fun ConfigEvaluatorBuilder.forKotlin(): ConfigEvaluatorBuilder =
  setValueMapperBuilder(valueMapperBuilder.forKotlin())

fun ConfigEvaluator.forKotlin(): ConfigEvaluator =
  setValueMapper(valueMapper.toBuilder().forKotlin().build())
