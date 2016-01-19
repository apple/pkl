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
package org.pkl.config.kotlin.mapper

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import kotlin.Any
import kotlin.Pair
import org.pkl.config.java.mapper.Converter
import org.pkl.config.java.mapper.ConverterFactory
import org.pkl.config.java.mapper.Reflection
import org.pkl.config.java.mapper.ValueMapper
import org.pkl.core.PClassInfo
import org.pkl.core.Pair as PPair

internal class PPairToKotlinPair : ConverterFactory {
  override fun create(sourceType: PClassInfo<*>, targetType: Type): Optional<Converter<*, *>> {
    if (sourceType !== PClassInfo.Pair) return Optional.empty()

    val targetClass = Reflection.toRawType(targetType)
    if (!Pair::class.java.isAssignableFrom(targetClass)) {
      return Optional.empty()
    }

    val pairType = Reflection.getExactSupertype(targetType, Pair::class.java) as ParameterizedType
    return Optional.of(
      ConverterImpl<Any, Any>(pairType.actualTypeArguments[0], pairType.actualTypeArguments[1])
    )
  }

  private class ConverterImpl<F, S>(
    private val firstTargetType: Type,
    private val secondTargetType: Type
  ) : Converter<PPair<Any, Any>, Pair<F, S>> {

    private var firstCachedType = PClassInfo.Unavailable
    private var firstCachedConverter: Converter<Any, F>? = null

    private var secondCachedType = PClassInfo.Unavailable
    private var secondCachedConverter: Converter<Any, S>? = null

    override fun convert(value: PPair<Any, Any>, valueMapper: ValueMapper): Pair<F, S> {
      val first = value.first
      if (!firstCachedType.isExactClassOf(first)) {
        firstCachedType = PClassInfo.forValue(first)
        firstCachedConverter = valueMapper.getConverter(firstCachedType, firstTargetType)
      }

      val second = value.second
      if (!secondCachedType.isExactClassOf(second)) {
        secondCachedType = PClassInfo.forValue(second)
        secondCachedConverter = valueMapper.getConverter(secondCachedType, secondTargetType)
      }

      return Pair(
        firstCachedConverter!!.convert(first, valueMapper),
        secondCachedConverter!!.convert(second, valueMapper)
      )
    }
  }
}
