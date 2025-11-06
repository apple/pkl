/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.config.scala.mapper

import org.pkl.config.java.mapper.{
  Converter,
  ConverterFactory,
  Reflection,
  ValueMapper
}
import org.pkl.config.scala.mapper.JavaReflectionSyntaxExtensions._
import org.pkl.core.PClassInfo

import java.lang.reflect.Type
import java.util.Optional
import scala.jdk.OptionConverters.RichOption
import scala.reflect.ClassTag

/** Provides infrastructure that helps define custom converter factories in a
  * somewhat concise way at the same time utilizing caching.
  */
private[mapper] object CachedConverterFactories {

  /** Function used in converters that essentially does a conversion logic.
    *
    * @tparam S
    *   source type
    * @tparam C
    *   cache. represented by `CachedSourceTypeInfo` for single-param generic
    *   types and `(CachedSourceTypeInfo, CachedSourceTypeInfo)` for two-param
    *   types.
    * @tparam T
    *   target type
    */
  private type ConversionFunction[S, C, T] = (S, C, ValueMapper) => T

  /** A converter for single-parameter types, caching conversion functions.
    *
    * @param conv
    *   A function that defines the conversion logic using the cached
    *   `CachedSourceTypeInfo`.
    */
  private final class Converter1[S, T](
      conv: ConversionFunction[S, CachedSourceTypeInfo, T]
  ) extends Converter[S, T] {
    private val s1 = new CachedSourceTypeInfo()
    override def convert(value: S, valueMapper: ValueMapper): T =
      conv.apply(value, s1, valueMapper)
  }

  /** A converter for two-parameter types (e.g., Tuple2 or Map), caching
    * conversion functions.
    *
    * @param conv
    *   A function that defines the conversion logic using two instances of
    *   `CachedSourceTypeInfo`.
    */
  private final class Converter2[S, T](
      conv: ConversionFunction[
        S,
        (CachedSourceTypeInfo, CachedSourceTypeInfo),
        T
      ]
  ) extends Converter[S, T] {
    private val s1 = new CachedSourceTypeInfo()
    private val s2 = new CachedSourceTypeInfo()
    override def convert(value: S, valueMapper: ValueMapper): T =
      conv.apply(value, (s1, s2), valueMapper)
  }

  /** A factory for creating converters based on parameterized types, supporting
    * generic conversion.
    *
    * @param acceptSourceType
    *   Predicate to determine if the source type is acceptable.
    * @param extractTypeParams
    *   Function to extract type parameters from the `ParameterizedType`.
    * @param newConverter
    *   Function to create a new converter based on extracted type parameters.
    */
  private final class ParametrizinglyTypedConverterFactory[T: ClassTag, TT](
      acceptSourceType: PClassInfo[_] => Boolean,
      extractTypeParams: Type => Option[TT],
      newConverter: TT => Converter[_, _]
  ) extends ConverterFactory {
    private val targetClassTag: ClassTag[T] = implicitly

    override def create(
        sourceType: PClassInfo[_],
        targetType: Type
    ): Optional[Converter[_, _]] = {
      if (acceptSourceType(sourceType)) {
        val targetClass = Reflection.toRawType(targetType)
        if (targetClassTag.runtimeClass.isAssignableFrom(targetClass)) {
          val typeParams = extractTypeParams(
            Reflection.getExactSupertype(targetType, targetClass)
          )
          typeParams.map(newConverter).toJava
        } else {
          Optional.empty()
        }
      } else {
        Optional.empty()
      }
    }
  }

  /** Factory method for single-parameter types such as `List` or `Option`,
    * using cached conversion.
    *
    * @param acceptSourceType
    *   Predicate to determine if the source type is acceptable.
    * @param conv
    *   Conversion function applied to the value and cache.
    */
  def forParametrizedType1[S, T: ClassTag](
      acceptSourceType: PClassInfo[_] => Boolean,
      conv: Type => ConversionFunction[
        S,
        CachedSourceTypeInfo,
        T
      ]
  ): ConverterFactory = new ParametrizinglyTypedConverterFactory[T, Type](
    acceptSourceType,
    _.params1,
    t1 => new Converter1(conv(t1))
  )

  /** Factory method for two-parameter types such as `Map` or `Tuple2`, using
    * cached conversion.
    *
    * @param acceptSourceType
    *   Predicate to determine if the source type is acceptable.
    * @param conv
    *   Conversion function applied to the value and cache.
    */
  def forParametrizedType2[S, T: ClassTag](
      acceptSourceType: PClassInfo[_] => Boolean,
      conv: (Type, Type) => ConversionFunction[
        S,
        (CachedSourceTypeInfo, CachedSourceTypeInfo),
        T
      ]
  ): ConverterFactory =
    new ParametrizinglyTypedConverterFactory[T, (Type, Type)](
      acceptSourceType,
      _.params2,
      { case (t1, t2) => new Converter2(conv(t1, t2)) }
    )
}
