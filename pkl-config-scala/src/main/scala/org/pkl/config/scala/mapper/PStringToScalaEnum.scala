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
package org.pkl.config.scala.mapper

import org.pkl.config.java.mapper.{ConversionException, Converter, ConverterFactory, Reflection}
import org.pkl.core.PClassInfo
import org.pkl.core.util.CodeGeneratorUtils

import java.lang.reflect.Type as JType
import java.util.Optional
import scala.collection.immutable.VectorMap

/**
 * Converter from Pkl `String` to Scala 3 `enum` cases.
 *
 * Scala 3 enum cases implement `scala.reflect.Enum`. When the enum also extends `java.lang.Enum`
 * (the opt-in Java-compat form), `Class.isEnum` returns true and `pkl-config-java`'s
 * `PStringToEnum` already handles them; this factory returns `Optional.empty()` in that case so
 * the chain falls through to `PStringToEnum`. For the plain `enum Foo { case A, B }` form,
 * `isEnum` is false and this factory looks up the case via the companion module's synthetic
 * `values()` method.
 */
private[mapper] object PStringToScalaEnum extends ConverterFactory {

  override def create(sourceType: PClassInfo[?], targetType: JType): Optional[Converter[?, ?]] = {
    (sourceType, Reflection.toRawType(targetType)) match {
      case (PClassInfo.String, cls)
          if !cls.isEnum && classOf[scala.reflect.Enum].isAssignableFrom(cls) =>
        Optional.of(mkConverter(cls, readEnumValues(cls)))
      case _ =>
        Optional.empty()
    }
  }

  private def readEnumValues(cls: Class[?]): VectorMap[String, AnyRef] = {
    try {
      val moduleCls = Class.forName(cls.getName + "$", false, cls.getClassLoader)
      val module = moduleCls.getField("MODULE$").get(null)
      val values = moduleCls.getMethod("values").invoke(module).asInstanceOf[Array[?]]
      values.iterator
        .collect { case v: scala.reflect.Enum =>
          v.productPrefix -> v.asInstanceOf[AnyRef]
        }
        .to(VectorMap)
    } catch {
      case e: ReflectiveOperationException =>
        throw new ConversionException(
          s"Failed to introspect Scala 3 enum companion for `${cls.getTypeName}`.",
          e
        )
    }
  }

  private def mkConverter(
      cls: Class[?],
      byName: VectorMap[String, AnyRef]
  ): Converter[String, AnyRef] = { (value, _) =>
    {
      byName
        .get(value)
        .orElse(Option(CodeGeneratorUtils.toEnumConstantName(value)).flatMap(byName.get))
        .getOrElse {
          throw new ConversionException(
            s"Cannot convert String `$value` to Enum value of type `${cls.getTypeName}`. " +
              s"Expected one of: ${byName.keys.mkString(", ")}."
          )
        }
    }
  }
}
