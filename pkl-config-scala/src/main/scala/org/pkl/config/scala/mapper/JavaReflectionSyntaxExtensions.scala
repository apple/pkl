/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import org.pkl.config.java.mapper.Reflection

import java.lang.reflect.{GenericArrayType, ParameterizedType, Type}

/**
 * Provides aims to provide type-safe syntax extension to Java Reflection classes.
 */
private[mapper] object JavaReflectionSyntaxExtensions {

  /**
   * `ParameterizedType` syntax extension.
   */
  implicit class ParametrizedTypeSyntaxExtension(val x: Type) extends AnyVal {

    /**
     * Retrieves the first type parameter of a `ParameterizedType`.
     *
     * @return
     *   The first `Type` parameter.
     *
     * @example
     *   Usage:
     *   {{{
     * val parameterizedType: ParameterizedType = // obtain a ParameterizedType instance
     * val firstParamType = parameterizedType.params1
     *   }}}
     */
    def params1: Option[Type] = {
      val tpe = x match {
        case x: ParameterizedType     => Some(x.getActualTypeArguments.apply(0))
        case x: GenericArrayType      => Some(x.getGenericComponentType)
        case x: Class[?] if x.isArray => Some(x.componentType())
        case _                        => None
      }

      tpe map Reflection.normalize
    }

    /**
     * Retrieves the first two type parameters of a `ParameterizedType`.
     *
     * @return
     *   A tuple containing the first and second `Type` parameters.
     *
     * @example
     *   Usage:
     *   {{{
     * val parameterizedType: ParameterizedType = // obtain a ParameterizedType instance
     * val (firstParamType, secondParamType) = parameterizedType.params2
     *   }}}
     */
    def params2: Option[(Type, Type)] = x match {
      case x: ParameterizedType =>
        Some(
          (
            Reflection.normalize(x.getActualTypeArguments.apply(0)),
            Reflection.normalize(x.getActualTypeArguments.apply(1))
          )
        )
      case _ => None
    }
  }
}
