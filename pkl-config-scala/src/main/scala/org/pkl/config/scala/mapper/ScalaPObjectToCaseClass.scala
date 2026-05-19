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

import org.pkl.config.java.mapper.{
  ConversionException,
  Converter,
  ConverterFactory,
  Reflection,
  ValueMapper
}
import org.pkl.core.util.CodeGeneratorUtils
import org.pkl.core.{Composite, PClassInfo, PObject}

import java.lang.invoke.{MethodHandle, MethodHandles}
import java.lang.reflect.Type as JType
import java.util.Optional
import scala.collection.concurrent.TrieMap
import scala.jdk.OptionConverters._

/**
 * Scala-aware replacement for `org.pkl.config.java.mapper.PObjectToDataObject`.
 *
 * Accepts only case classes. Uses [[ConstructorParamResolver]] to describe each ctor parameter —
 * including path-dependent `Enumeration#Value` information that Java reflection erases. Enum
 * parameters are converted directly to `Enumeration#Value` without going through the generic
 * `ValueMapper.getConverter` lookup (which can't see past the erased `scala.Enumeration$Value`).
 */
private[mapper] object ScalaPObjectToCaseClass extends ConverterFactory {
  private val lookup = MethodHandles.lookup()

  override def create(
      sourceType: PClassInfo[_],
      targetType: JType
  ): Optional[Converter[_, _]] = {
    if (sourceType != PClassInfo.Module && sourceType.getJavaClass != classOf[PObject]) {
      Optional.empty()
    } else {
      val rawClass = Reflection.toRawType(targetType)
      if (!classOf[scala.Product].isAssignableFrom(rawClass)) Optional.empty()
      else {
        val result: Option[Converter[_, _]] = for {
          ctor <- rawClass.getDeclaredConstructors.headOption
          params <- ConstructorParamResolver.resolve(ctor, targetType)
        } yield {
          val handle = {
            try lookup.unreflectConstructor(ctor)
            catch {
              case e: IllegalAccessException =>
                throw new ConversionException(s"Error accessing constructor `$ctor`.", e)
            }
          }
          new ScalaCaseClassConverter(targetType, handle, params)
        }
        result.toJava
      }
    }
  }

  /**
   * Matches a Pkl `String` against an `Enumeration`'s members, tolerating the
   * `CodeGeneratorUtils.toEnumConstantName` transformation that Pkl codegen applies. Throws
   * `ConversionException` with the full candidate list if no member matches.
   */
  private def matchEnumMember(
      value: String,
      members: List[Enumeration#Value]
  ): Enumeration#Value = members
    .find { v =>
      val n = v.toString
      n == value || CodeGeneratorUtils.toEnumConstantName(n) == value
    }
    .getOrElse(
      throw new ConversionException(
        s"Cannot convert String `$value` to Enumeration value. " +
          s"Expected one of: ${members.map(_.toString).mkString(", ")}."
      )
    )

  private final class ScalaCaseClassConverter(
      targetType: JType,
      ctorHandle: MethodHandle,
      parameters: Seq[Param]
  ) extends Converter[Composite, AnyRef] {

    private val perParamCache: TrieMap[String, CachedSourceTypeInfo] = TrieMap.empty

    override def convert(value: Composite, vm: ValueMapper): AnyRef = {
      val args = parameters.map { convertParam(_, value, vm) }
      try ctorHandle.invokeWithArguments(args: _*).asInstanceOf[AnyRef]
      catch {
        case t: Throwable =>
          throw new ConversionException(s"Error invoking constructor `$ctorHandle`.", t)
      }
    }

    private def convertParam(p: Param, value: Composite, vm: ValueMapper): Object = {
      val properties = value.getProperties
      val property = Option(properties.get(p.name)).getOrElse {
        throw new ConversionException(
          "Cannot convert Pkl object to Java object." +
            s"\nPkl type             : ${value.getClassInfo}" +
            s"\nJava type            : ${targetType.getTypeName}" +
            s"\nMissing Pkl property : ${p.name}" +
            s"\nActual Pkl properties: ${properties.keySet}"
        )
      }
      try {
        p.tpe match {
          case Param.Type.Jvm(jvmType) =>
            perParamCache
              .getOrElseUpdate(p.name, new CachedSourceTypeInfo())
              .updateAndGet(property, jvmType, vm)
              .asInstanceOf[Object]
          case Param.Type.ScalaEnum(_, members) =>
            // No per-param cache here: unlike the Param.Type.Jvm branch (which memoizes
            // the expensive `vm.getConverter` dispatch), enum lookup is a direct scan
            // over the already-resolved member list — there's no upstream call to cache.
            property match {
              case s: String => matchEnumMember(s, members).asInstanceOf[Object]
              case _         =>
                throw new ConversionException(
                  s"Expected String value for Enumeration property `${p.name}`, " +
                    s"got `${property.getClass.getName}`."
                )
            }
        }
      } catch {
        case e: ConversionException =>
          throw new ConversionException(
            s"Error converting property `${p.name}` in Pkl object of type " +
              s"`${value.getClassInfo}` to equally named constructor parameter in " +
              s"Java class `${Reflection.toRawType(targetType).getTypeName}`: " +
              e.getMessage,
            e.getCause
          )
      }
    }
  }
}
