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
  ConverterFactory,
  PObjectToDataObject,
  ValueMapper
}
import org.pkl.config.scala.mapper.JavaReflectionSyntaxExtensions.ParametrizedTypeSyntaxExtension
import org.pkl.core.util.CodeGeneratorUtils
import org.pkl.core.{PClassInfo, PNull, PObject, Pair}

import java.lang.reflect.{Constructor, Type}
import java.util.Optional
import scala.annotation.nowarn
import scala.collection.{immutable, mutable}
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.language.implicitConversions

/** Defines a set of PKL to Scala converter factories.
  */
object ScalaConverterFactories {

  private type Conv1[S, T] = Type => (S, CachedSourceTypeInfo, ValueMapper) => T

  private type Conv2[S, T] = (Type, Type) => (
      S,
      (CachedSourceTypeInfo, CachedSourceTypeInfo),
      ValueMapper
  ) => T

  val pObjectToCaseClass: ConverterFactory = new PObjectToDataObject {

    override def selectConstructor(
        clazz: Class[_]
    ): Optional[Constructor[_]] = {
      clazz.getDeclaredConstructors.headOption
        .filter(_ =>
          // case classes all implement Product
          clazz.getInterfaces
            .exists(i => classOf[scala.Product].isAssignableFrom(i))
        )
        .toJava
    }
  }

  val pAnyToOption: ConverterFactory =
    CachedConverterFactories.forParametrizedType1[Any, Option[_]](
      _ => true,
      t1 =>
        (value, s1, vm) => {
          value match {
            case _: PNull | null => None
            case v: Option[_]    => v.map(s1.updateAndGet(_, t1, vm))
            case v: Optional[_]  => v.toScala.map(s1.updateAndGet(_, t1, vm))
            case v               => Option(s1.updateAndGet(v, t1, vm))
          }
        }
    )

  val pPairToTuple: ConverterFactory =
    CachedConverterFactories.forParametrizedType2[Pair[_, _], (_, _)](
      PClassInfo.Pair,
      (t1, t2) =>
        (value, cc, vm) => {
          val (s1, s2) = cc
          val p1 = s1.updateAndGet(value.getFirst, t1, vm)
          val p2 = s2.updateAndGet(value.getSecond, t2, vm)
          (p1, p2)
        }
    )

  val pMapToMutableMapConv: Conv2[java.util.Map[_, _], mutable.Map[_, _]] =
    (t1, t2) =>
      (value, cc, vm) => {
        val (s1, s2) = cc
        value.asScala.map { case (k, v) =>
          (s1.updateAndGet(k, t1, vm), s2.updateAndGet(v, t2, vm))
        }
      }

  def pCollectionToMutableCollectionConv[T[_]](
      toSpecific: IterableOnce[_] => T[_]
  ): Conv1[java.util.Collection[_], T[_]] =
    t1 =>
      (value, cache, vm) =>
        toSpecific(value.asScala.map(x => cache.updateAndGet(x, t1, vm)))

  val pMapToImmutableMap: ConverterFactory = CachedConverterFactories
    .forParametrizedType2[java.util.Map[_, _], immutable.Map[_, _]](
      PClassInfo.Map,
      (t1, t2) =>
        (value, cc, vm) => pMapToMutableMapConv(t1, t2)(value, cc, vm).toMap
    )

  val pMapToMutableMap: ConverterFactory = CachedConverterFactories
    .forParametrizedType2[java.util.Map[_, _], mutable.Map[_, _]](
      PClassInfo.Map,
      pMapToMutableMapConv
    )

  val pObjectToImmutableMap: ConverterFactory =
    CachedConverterFactories.forParametrizedType2[PObject, immutable.Map[_, _]](
      x => x == PClassInfo.Object | x == PClassInfo.Dynamic,
      (t1, t2) =>
        (value, cc, vm) =>
          pMapToMutableMapConv(t1, t2)(value.getProperties, cc, vm).toMap
    )

  val pObjectToMutableMap: ConverterFactory =
    CachedConverterFactories.forParametrizedType2[PObject, mutable.Map[_, _]](
      x => x == PClassInfo.Object | x == PClassInfo.Dynamic,
      (t1, t2) =>
        (value, cc, vm) =>
          pMapToMutableMapConv(t1, t2)(value.getProperties, cc, vm)
    )

  //  val pCollectionToArray: ConverterFactory = CachedConverterFactories
  //    .forParametrizedType1[java.util.Collection[_], Array[_]](
  //      x => x == PClassInfo.Collection | x == PClassInfo.Set | x == PClassInfo.List,
  //      pCollectionToMutableCollectionConv[Array](_.iterator.toArray[Any])
  //    )

  val pCollectionToImmutableSet: ConverterFactory = CachedConverterFactories
    .forParametrizedType1[java.util.Collection[_], immutable.Set[_]](
      x =>
        x == PClassInfo.Collection | x == PClassInfo.Set | x == PClassInfo.List,
      pCollectionToMutableCollectionConv(_.iterator.toSet)
    )

  val pCollectionToMutableSet: ConverterFactory = CachedConverterFactories
    .forParametrizedType1[java.util.Collection[_], mutable.Set[_]](
      x =>
        x == PClassInfo.Collection | x == PClassInfo.Set | x == PClassInfo.List,
      pCollectionToMutableCollectionConv(mutable.Set.from)
    )

  val pCollectionToImmutableVector: ConverterFactory = CachedConverterFactories
    .forParametrizedType1[java.util.Collection[_], immutable.Vector[_]](
      x =>
        x == PClassInfo.Collection | x == PClassInfo.Set | x == PClassInfo.List,
      pCollectionToMutableCollectionConv(_.iterator.toVector)
    )

  val pCollectionToImmutableSeq: ConverterFactory = CachedConverterFactories
    .forParametrizedType1[java.util.Collection[_], immutable.Seq[_]](
      x =>
        x == PClassInfo.Collection | x == PClassInfo.Set | x == PClassInfo.List,
      pCollectionToMutableCollectionConv(_.iterator.toSeq)
    )

  val pCollectionToMutableSeq: ConverterFactory = CachedConverterFactories
    .forParametrizedType1[java.util.Collection[_], mutable.Seq[_]](
      x =>
        x == PClassInfo.Collection | x == PClassInfo.Set | x == PClassInfo.List,
      pCollectionToMutableCollectionConv(mutable.Seq.from)
    )

  val pCollectionToMutableBuffer: ConverterFactory = CachedConverterFactories
    .forParametrizedType1[java.util.Collection[_], mutable.Buffer[_]](
      x =>
        x == PClassInfo.Collection | x == PClassInfo.Set | x == PClassInfo.List,
      pCollectionToMutableCollectionConv(mutable.Buffer.from)
    )

  val pCollectionToImmutableList: ConverterFactory = CachedConverterFactories
    .forParametrizedType1[java.util.Collection[_], immutable.List[_]](
      x =>
        x == PClassInfo.Collection | x == PClassInfo.Set | x == PClassInfo.List,
      pCollectionToMutableCollectionConv(_.iterator.toList)
    )

  val pCollectionToMutableQueue: ConverterFactory = CachedConverterFactories
    .forParametrizedType1[java.util.Collection[_], mutable.Queue[_]](
      x =>
        x == PClassInfo.Collection | x == PClassInfo.Set | x == PClassInfo.List,
      pCollectionToMutableCollectionConv(mutable.Queue.from)
    )

  val pCollectionToMutableStack: ConverterFactory = CachedConverterFactories
    .forParametrizedType1[java.util.Collection[_], mutable.Stack[_]](
      x =>
        x == PClassInfo.Collection | x == PClassInfo.Set | x == PClassInfo.List,
      pCollectionToMutableCollectionConv(mutable.Stack.from)
    )

  @nowarn("cat=deprecation")
  val pCollectionToImmutableStream: ConverterFactory = CachedConverterFactories
    .forParametrizedType1[java.util.Collection[_], immutable.Stream[_]](
      x =>
        x == PClassInfo.Collection | x == PClassInfo.Set | x == PClassInfo.List,
      pCollectionToMutableCollectionConv(_.iterator.toStream)
    )

  val pCollectionToImmutableLazyList
      : ConverterFactory = CachedConverterFactories
    .forParametrizedType1[java.util.Collection[_], immutable.LazyList[_]](
      x =>
        x == PClassInfo.Collection | x == PClassInfo.Set | x == PClassInfo.List,
      pCollectionToMutableCollectionConv(_.iterator.to(LazyList))
    )

  // Do not shuffle converter factories within this list. Order matters.
  // As a general rule, try to keep more generic types lower and more specific higher
  val all: List[ConverterFactory] = List(
    pAnyToOption,
    pPairToTuple,
    pMapToImmutableMap,
    pCollectionToImmutableStream,
    pCollectionToImmutableSet,
    pCollectionToImmutableList,
    pCollectionToImmutableVector,
    pCollectionToImmutableLazyList,
    pCollectionToImmutableSeq,
    pObjectToImmutableMap,
    pMapToMutableMap,
    pObjectToMutableMap,
    pCollectionToMutableStack,
    pCollectionToMutableSet,
    pCollectionToMutableQueue,
    pCollectionToMutableBuffer,
    pCollectionToMutableSeq,
    pObjectToCaseClass,
    PStringOrIntToEnumeration
    //    pCollectionToArray,
  )

  private implicit def pClassInfoToPredicate(
      x: PClassInfo[_]
  ): PClassInfo[_] => Boolean = _ == x
}
