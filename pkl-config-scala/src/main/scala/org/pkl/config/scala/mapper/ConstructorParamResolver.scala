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

import java.lang.reflect.{Constructor, Type as JType}
import scala.collection.concurrent.TrieMap

/**
 * Resolves a case class's primary-constructor parameters into [[Param]]s.
 *
 * Uses Java reflection for names + generic-aware erased types, and `scala.reflect.runtime.universe`
 * to recover path-dependent `Enumeration` information that Java reflection erases.
 *
 * The expensive part — scala-reflect mirror work — is cached per `Class`. Java-reflection type
 * resolution is cheap and varies with `targetType` (generic bindings), so it is recomputed per
 * call.
 */
private[mapper] object ConstructorParamResolver {
  import scala.reflect.runtime.universe as ru

  private val mirror: ru.Mirror = ru.runtimeMirror(getClass.getClassLoader)
  private val enumValueType: ru.Type = ru.typeOf[scala.Enumeration#Value]

  private val enumCache = TrieMap.empty[Class[?], Map[Int, Enumeration]]

  /**
   * Resolves the primary-constructor parameters of `ctor`. Returns `None` if the constructor has no
   * parameters, or any parameter name is unavailable at runtime.
   */
  def resolve(ctor: Constructor[?], targetType: JType): Option[Seq[Param]] = {
    val javaParams = ctor.getParameters
    if (javaParams.isEmpty || javaParams.exists(!_.isNamePresent)) None
    else {
      val clazz = ctor.getDeclaringClass
      val exactTypes = Reflection.getExactParameterTypes(ctor, targetType)
      val enumInfo = enumInfoFor(clazz)
      val params = javaParams.iterator.zipWithIndex.map { case (p, i) =>
        val jvmType = exactTypes(i)
        val tpe: Param.Type = enumInfo.get(i) match {
          case Some(enumeration) =>
            Param.Type.ScalaEnum(jvmType, enumeration.values.toList)
          case None => Param.Type.Jvm(jvmType)
        }
        Param(i, p.getName, tpe)
      }.toVector
      Some(params)
    }
  }

  private def enumInfoFor(clazz: Class[?]): Map[Int, Enumeration] = {
    enumCache.getOrElseUpdate(clazz, computeEnumInfo(clazz))
  }

  private def computeEnumInfo(clazz: Class[?]): Map[Int, Enumeration] = {
    ru.synchronized {
      try {
        val tpe = mirror.classSymbol(clazz).toType
        val ctorSym = tpe.decl(ru.termNames.CONSTRUCTOR)
        if (ctorSym == ru.NoSymbol || !ctorSym.isMethod) Map.empty
        else {
          ctorSym.asMethod.paramLists.headOption
            .map(
              _.iterator.zipWithIndex
                .flatMap { case (sym, idx) =>
                  resolveEnumeration(sym.typeSignature).map(idx -> _)
                }
                .toMap
            )
            .getOrElse(Map.empty)
        }
      } catch {
        case _: Throwable => Map.empty
      }
    }
  }

  private def resolveEnumeration(t: ru.Type): Option[Enumeration] = {
    val dealiased = t.dealias
    if (!(dealiased <:< enumValueType)) None
    else {
      dealiased match {
        case ru.TypeRef(pre, _, _) => extractModule(pre)
        case _                     => None
      }
    }
  }

  private def extractModule(pre: ru.Type): Option[Enumeration] = {
    val sym = pre.termSymbol
    if (sym == ru.NoSymbol || !sym.isModule) None
    else {
      try {
        mirror.reflectModule(sym.asModule).instance match {
          case e: Enumeration => Some(e)
          case _              => None
        }
      } catch {
        case _: Throwable => None
      }
    }
  }
}
