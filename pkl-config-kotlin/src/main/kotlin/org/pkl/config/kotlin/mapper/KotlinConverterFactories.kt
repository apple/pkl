/*
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

import java.lang.reflect.Constructor
import java.util.*
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.kotlinFunction
import org.pkl.config.java.mapper.ConverterFactories
import org.pkl.config.java.mapper.ConverterFactory
import org.pkl.config.java.mapper.Named
import org.pkl.config.java.mapper.PObjectToDataObject

/** [ConverterFactory]s for use with Kotlin. */
object KotlinConverterFactories {
  /**
   * Variation of [ConverterFactories.pObjectToDataObject] for Kotlin objects. Uses the primary
   * constructor of the Kotlin target class. Constructor parameters do *not* need to be annotated
   * with [Named]. Supports both regular Kotlin classes and Kotlin data classes.
   */
  val pObjectToDataObject: ConverterFactory =
    object : PObjectToDataObject() {
      override fun selectConstructor(clazz: Class<*>): Optional<Constructor<*>> =
        Optional.ofNullable(clazz.kotlin.primaryConstructor?.javaConstructor)

      override fun getParameterNames(constructor: Constructor<*>): Optional<List<String>> {
        val params = constructor.kotlinFunction?.parameters
        val paramNames = params?.mapNotNull { it.name }
        return Optional.ofNullable(paramNames?.takeIf { it.size == params.size })
      }
    }

  val pPairToKotlinPair: ConverterFactory = PPairToKotlinPair()

  val all: Collection<ConverterFactory> =
    Collections.unmodifiableList(listOf(pObjectToDataObject, pPairToKotlinPair))
}
