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

import org.pkl.config.java.mapper.{Converter, ConverterFactory, ValueMapper}
import org.pkl.config.scala.mapper.JavaReflectionSyntaxExtensions.ParametrizedTypeSyntaxExtension
import org.pkl.core.PClassInfo
import org.pkl.core.util.CodeGeneratorUtils

import java.lang.reflect.Type
import java.util.Optional
import scala.jdk.OptionConverters._

private[mapper] object PStringOrIntToEnumeration extends ConverterFactory {

  override def create(
      sourceType: PClassInfo[_],
      targetType: Type
  ): Optional[Converter[_, _]] = {
    targetType.asCustomEnum.map { members =>
      (new Converter[Any, Any] {
        override def convert(value: Any, valueMapper: ValueMapper): Any = {
          val res = value match {
            case i: Long =>
              members.collectFirst {
                case value if value.id == i => value
              }
            case name: String =>
              members.collectFirst {
                case value if {
                      val n = value.toString;
                      n == name || CodeGeneratorUtils
                        .toEnumConstantName(n)
                        .equals(name)
                    } =>
                  value
              }
            case _ => None
          }

          res.orNull
        }
      }).asInstanceOf[Converter[_, _]]
    }.toJava
  }
}
