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
package org.pkl.config.scala

import org.pkl.config.java.mapper.{ConversionException, ValueMapperBuilder}
import org.pkl.config.java.{Config, ConfigEvaluator, ConfigEvaluatorBuilder}
import org.pkl.config.scala.mapper.{ScalaConversions, ScalaConverterFactories}

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

/** Entry point for Scala-specific extensions to PKL configuration, enabling
  * type conversions and syntax improvements that align PKL's configuration
  * model with Scala types and structures.
  *
  * The `syntax` package object introduces two main extensions:
  *
  *   1. `forScala`: Enhances the PKL evaluation stack by adding Scala-specific
  *      type conversions and converter factories, making it possible to work
  *      seamlessly with Scala types.
  *   2. `Config.to`: Provides a type-safe `Config` conversion method.
  */
package object syntax {

  /** Extension for `ValueMapperBuilder`, enabling Scala-specific type
    * conversions and factories.
    *
    * Adds conversions from `ScalaConversions` and converter factories from
    * `ScalaConverterFactories` to the evaluation stack, allowing PKL to handle
    * Scala-native types effectively.
    *
    * @example
    *   Using `forScala` with a ValueMapperBuilder:
    *   {{{
    * val builder = new ValueMapperBuilder().forScala()
    * val evaluator = new ConfigEvaluatorBuilder().setValueMapperBuilder(builder).build
    *   }}}
    */
  implicit class ValueMapperBuilderSyntaxExtension(val x: ValueMapperBuilder)
      extends AnyVal {
    def forScala(): ValueMapperBuilder = {
      x.setConversions(
        (ScalaConversions.all ++ x.getConversions.asScala).asJava
      ).setConverterFactories(
        (ScalaConverterFactories.all ++ x.getConverterFactories.asScala).asJava
      )
    }
  }

  /** Extension for `ConfigEvaluatorBuilder`, enabling Scala-specific type
    * handling in the evaluator.
    *
    * This method sets up a `ConfigEvaluatorBuilder` with a `ValueMapperBuilder`
    * that has been extended with Scala conversions, enabling the evaluator to
    * process Scala-specific types in PKL configurations.
    *
    * @example
    *   Using `forScala` with a ConfigEvaluatorBuilder:
    *   {{{
    * val evaluatorBuilder = new ConfigEvaluatorBuilder().forScala()
    * val evaluator = evaluatorBuilder.build
    *   }}}
    */
  implicit class ConfigEvaluatorBuilderSyntaxExtension(
      val x: ConfigEvaluatorBuilder
  ) extends AnyVal {
    def forScala(): ConfigEvaluatorBuilder = {
      x.setValueMapperBuilder(x.getValueMapperBuilder.forScala())
    }
  }

  /** Extension for `ConfigEvaluator`, applying Scala-specific type conversions
    * to the evaluator.
    *
    * Builds a `ConfigEvaluator` with a Scala-aware `ValueMapper`, allowing for
    * seamless conversion of configuration values to Scala types.
    *
    * @example
    *   Using `forScala` with a ConfigEvaluator:
    *   {{{
    * val evaluator = new ConfigEvaluatorBuilder().build.forScala()
    *   }}}
    */
  implicit class ConfigEvaluatorSyntaxExtension(val x: ConfigEvaluator)
      extends AnyVal {
    def forScala(): ConfigEvaluator = {
      x.setValueMapper(x.getValueMapper.toBuilder.forScala().build)
    }
  }

  /** Extension for `Config`, adding a type-safe `to` method to retrieve values
    * as Scala types.
    *
    * The `to[T]` method provides an intuitive way to retrieve values from a PKL
    * `Config` as specific Scala types. If a `null` is returned or the retrieved
    * value does not match the target type, a `ConversionException` is thrown.
    * This encourages the use of `Option` for nullable values in configurations.
    *
    * @param ct
    *   Implicit `ClassTag` of the target type `T`.
    *
    * @throws ConversionException
    *   if the value is `null` or does not match the specified type `T`.
    *
    * @example
    *   Retrieving a value as an Option:
    *   {{{
    * val myPklConfig: Config = // load or build a PKL config
    * val config: MyScalaConfig = myPklConfig.to[MyCaseClass]
    *   }}}
    */
  implicit class ConfigSyntaxExtension(val x: Config) extends AnyVal {
    def to[T](implicit ct: ClassTag[T]): T = {
      val result = x.as[T](ct.runtimeClass)
      if (result == null || !result.isInstanceOf[T]) {
        throw new ConversionException(
          "Expected a non-null value but got `null`. " +
            "To allow optional values, use `Option`. e.g. `Option[String]`."
        )
      }
      result
    }
  }
}
