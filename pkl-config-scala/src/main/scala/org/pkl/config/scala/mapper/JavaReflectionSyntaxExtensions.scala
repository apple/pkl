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

import org.pkl.config.java.mapper.Reflection
import org.pkl.config.scala.annotation.EnumOwner

import java.lang.reflect.{GenericArrayType, ParameterizedType, Type}

/** Provides aims to provide type-safe syntax extension to Java Reflection
  * classes.
  */
private[mapper] object JavaReflectionSyntaxExtensions {

  /** `ParameterizedType` syntax extension.
    */
  implicit class ParametrizedTypeSyntaxExtension(val x: Type) extends AnyVal {

    /** Retrieves the first type parameter of a `ParameterizedType`.
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
        case x: Class[_] if x.isArray => Some(x.componentType())
        case _                        => None
      }

      tpe map Reflection.normalize
    }

    /** Retrieves the first two type parameters of a `ParameterizedType`.
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

    /** Attempts to recover the full list of enumeration values from a given
      * runtime class.
      *
      * This method is designed to work with Scala 2 `Enumeration` values that
      * were defined using a custom subclass of `Enumeration.Val`, annotated
      * with `@EnumOwner`, where the annotation holds a reference to the
      * singleton `Enumeration` object.
      *
      * The method checks whether the provided `Type` is a subclass of
      * `Enumeration#Value`, and if so, attempts to locate the `@EnumOwner`
      * annotation on its class. If present, it uses reflection to access the
      * singleton `Enumeration` instance and returns its list of values.
      *
      * @example
      *   {{{
      * object SimpleEnum extends Enumeration {
      *
      *   @EnumOwner(classOf[SimpleEnum.type])
      *   case class V() extends Val(nextId)
      *
      *   val Aaa = V()
      *   val Bbb = V()
      *   val Ccc = V()
      * }
      *   }}}
      *
      * @return
      *   Some(list of `Enumeration#Value`) if the enumeration can be resolved,
      *   None otherwise
      */
    def asCustomEnum: Option[List[Enumeration#Value]] = {

      def derive(enumClass: Class[_]): Option[List[Enumeration#Value]] = {
        try {
          val f = enumClass.getDeclaredField("MODULE$")
          f.setAccessible(true)

          val enumInstance = f.get(null).asInstanceOf[Enumeration]
          Some(enumInstance.values.toList)
        } catch {
          case _: Throwable => None
        }
      }

      x match {
        case x: Class[_] if classOf[Enumeration#Value].isAssignableFrom(x) =>
          for {
            anno <- Option(x.getAnnotation(classOf[EnumOwner]))
            enum <- derive(anno.value())
          } yield enum

        case _ =>
          None
      }
    }
  }
}
