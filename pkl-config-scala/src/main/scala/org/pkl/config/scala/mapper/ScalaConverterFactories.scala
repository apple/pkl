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

import org.pkl.config.java.mapper.{ConverterFactory, ValueMapper}
import org.pkl.core.{PClassInfo, PNull, Pair}

import java.lang.reflect.Type
import java.util.Optional
import scala.collection.immutable
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.language.implicitConversions

/**
 * Default set of PKL → Scala converter factories.
 *
 * The factories here mirror the types that Pkl codegen produces in every language; users who
 * hand-author Scala classes with other collection shapes (Set, Vector, mutable collections, etc.)
 * can register their own converter factories.
 */
object ScalaConverterFactories {

  private type Conv1[S, T] = Type => (S, CachedSourceTypeInfo, ValueMapper) => T

  private type Conv2[S, T] = (Type, Type) => (
      S,
      (CachedSourceTypeInfo, CachedSourceTypeInfo),
      ValueMapper
  ) => T

  val pObjectToCaseClass: ConverterFactory = ScalaPObjectToCaseClass

  val pAnyToOption: ConverterFactory = {
    CachedConverterFactories.forParametrizedType1[Any, Option[?]](
      _ => true,
      t1 => { (value, s1, vm) =>
        {
          value match {
            case _: PNull | null => None
            case v: Option[_]    => v.map(s1.updateAndGet(_, t1, vm))
            case v: Optional[_]  => v.toScala.map(s1.updateAndGet(_, t1, vm))
            case v               => Option(s1.updateAndGet(v, t1, vm))
          }
        }
      }
    )
  }

  val pPairToTuple: ConverterFactory = {
    CachedConverterFactories.forParametrizedType2[Pair[?, ?], (?, ?)](
      PClassInfo.Pair,
      (t1, t2) => { (value, cc, vm) =>
        {
          val (s1, s2) = cc
          val p1 = s1.updateAndGet(value.getFirst, t1, vm)
          val p2 = s2.updateAndGet(value.getSecond, t2, vm)
          (p1, p2)
        }
      }
    )
  }

  val pMapToImmutableMap: ConverterFactory = CachedConverterFactories
    .forParametrizedType2[java.util.Map[?, ?], immutable.Map[?, ?]](
      PClassInfo.Map,
      (t1, t2) => { (value, cc, vm) =>
        {
          val (s1, s2) = cc
          value.asScala.map { case (k, v) =>
            (s1.updateAndGet(k, t1, vm), s2.updateAndGet(v, t2, vm))
          }.toMap
        }
      }
    )

  val pCollectionToImmutableSeq: ConverterFactory = CachedConverterFactories
    .forParametrizedType1[java.util.Collection[?], immutable.Seq[?]](
      x => x == PClassInfo.Collection || x == PClassInfo.Set || x == PClassInfo.List,
      t1 => (value, cache, vm) => value.asScala.iterator.map(cache.updateAndGet(_, t1, vm)).toSeq
    )

  // Do not shuffle converter factories within this list. Order matters.
  // As a general rule, try to keep more generic types lower and more specific higher
  val all: List[ConverterFactory] = List(
    pAnyToOption,
    pPairToTuple,
    pMapToImmutableMap,
    pCollectionToImmutableSeq,
    pObjectToCaseClass
  )

  private implicit def pClassInfoToPredicate(
      x: PClassInfo[?]
  ): PClassInfo[?] => Boolean = _ == x
}
