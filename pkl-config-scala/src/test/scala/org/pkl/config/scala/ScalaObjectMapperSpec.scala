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

import org.pkl.config.java.ConfigEvaluator
import org.pkl.config.scala.syntax._
import org.scalatest.funsuite.AnyFunSuite
import org.pkl.core.{ModuleSource, Duration => PDuration}

import java.time.{Instant, Duration => JDuration}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}
import com.softwaremill.diffx._
import com.softwaremill.diffx.scalatest.DiffShouldMatcher._
import org.pkl.config.scala.annotation.EnumOwner

import scala.annotation.nowarn
import scala.collection.mutable

@nowarn("cat=deprecation")
class ScalaObjectMapperSpec extends AnyFunSuite {
  import ScalaObjectMapperSpec._

  test("evaluate scala types") {

    val code =
      """
        |module ObjectMappingTestContainer
        |
        |class Foo {
        |  value: Int
        |}
        |
        |// Options
        |optionalVal1: String? = null
        |optionalVal2: String? = "some"
        |
        |// Instant
        |instant1 = 0
        |instant2 = "2024-10-31T02:25:26.036Z"
        |
        |// Vector
        |vector = List(1, 6, 9)
        |
        |// Seq
        |seq = List(9, 5, 36, 1)
        |mutableSeq = List("d", "a")
        |
        |// Buffer
        |mutableBuffer = List("hoo", "ray")
        |
        |// Queue
        |mutableQueue = Set("hoo", "ray")
        |
        |// Stack
        |mutableStack = Set("hoo", "ray")
        |
        |// Duration
        |pklDuration: Duration = 5.ms
        |scalaFiniteDuration: Duration = 5.ms
        |scalaDuration: Duration = 5000000.ns
        |
        |// Sets
        |stringSet: Set<String> = Set("in set")
        |intSet: Set<Int> = Set(1,2,4,8,16,32)
        |booleanSetSet: Set<Set<Boolean>> = Set(Set(false), Set(true), Set(true, false))
        |mutableSet = Set("aaa", "cc", "b")
        |
        |// Lists
        |stringList: List<String> = List("in list")
        |intList: List<Int> = List(1,2,3,5,7,11)
        |booleanListList: List<List<Boolean>> = List(List(false), List(true), List(true, false))
        |
        |// Streams
        |stream: List<String> = List("stream1", "stream2")
        |
        |// LazyList
        |lazyList: List<Int> = List(5, 4, 7, 1)
        |
        |// Maps
        |intStringMap: Map<Int, String> = Map(0, "in map")
        |booleanIntStringMapMap: Map<Boolean, Map<Int, String>> = Map(false, Map(0, "in map in map"))
        |booleanIntMapStringMap: Map<Map<Boolean, Int>, String> = Map(Map(true, 42), "in map with map keys")
        |
        |// Listings
        |stringSetListing: Listing<Set<String>> = new { Set("in set in listing") }
        |intListingListing: Listing<Listing<Int>> = new { new { 1337 } new { 100 } }
        |
        |// Mappings
        |intStringMapping: Mapping<Int, String> = new { [42] = "in map" }
        |stringStringSetMapping: Mapping<String, Set<String>> = new { ["key"] = Set("in set in map") }
        |
        |// Mutable Map
        |mutableMap = Map("foo", "bar")
        |
        |// Map & Mappings with structured keys
        |intSetListStringMap: Map<List<Set<Int>>, String> = Map(List(Set(27)), "in map with structured key")
        |typedStringMap: Map<Foo, String> = Map(
        |  new Foo { value = 1 }, "using typed objects",
        |  new Foo { value = 2 }, "also works")
        |dynamicStringMap: Map<Dynamic, String> = Map(
        |  new Dynamic { value = 42 }, "using Dynamics",
        |  new Dynamic { hello = "world" }, "also works")
        |
        |intListingStringMapping: Mapping<Listing<Int>, String> = new {
        |  [new Listing { 42 1337 }] = "structured key works"
        |}
        |intSetListStringMapping: Mapping<List<Set<Int>>, String> = new {
        |  [List(Set(27))] = "in mapping with structured key"
        |}
        |local intListing: Listing<Int> = new { 0 0 7 }
        |thisOneGoesToEleven: Mapping<List<Set<Int>>, Map<Listing<Int>, Mapping<Int, String>>> = new {
        |  [List(Set(0), Set(0), Set(7))] = Map(intListing, intStringMapping)
        |}
        |
        |simpleEnumViaString = "Bbb"
        |simpleEnumViaInt = 0
        |""".stripMargin

    val result = ConfigEvaluator
      .preconfigured()
      .forScala()
      .evaluate(ModuleSource.text(code))
      .to[ObjectMappingTestContainer]

    result shouldMatchTo ObjectMappingTestContainer(
      optionalVal1 = None,
      optionalVal2 = Some("some"),
      pklDuration = PDuration.ofMillis(5),
      scalaDuration = Duration(5, TimeUnit.MILLISECONDS),
      scalaFiniteDuration = FiniteDuration(5, TimeUnit.MILLISECONDS),
      instant1 = Instant.ofEpochMilli(0),
      instant2 = Instant.parse("2024-10-31T02:25:26.036Z"),
      stringSet = Set("in set"),
      intSet = Set(1, 2, 32, 16, 8, 4),
      booleanSetSet = Set(Set(false), Set(true), Set(true, false)),
      stringList = List("in list"),
      intList = List(1, 2, 3, 5, 7, 11),
      booleanListList = List(List(false), List(true), List(true, false)),
      vector = Vector(1, 6, 9),
      seq = Seq(9, 5, 36, 1),
      stream = Stream("stream1", "stream2"),
      lazyList = LazyList(5, 4, 7, 1),
      intStringMap = Map(0 -> "in map"),
      booleanIntStringMapMap = Map(false -> Map(0 -> "in map in map")),
      booleanIntMapStringMap = Map(Map(true -> 42) -> "in map with map keys"),
      intSetListStringMap = Map(List(Set(27)) -> "in map with structured key"),
      typedStringMap = Map(
        TypedKey(1) -> "using typed objects",
        TypedKey(2) -> "also works"
      ),
      dynamicStringMap = Map(
        Map("value" -> 42) -> "using Dynamics",
        Map("hello" -> "world") -> "also works"
      ),
      mutableMap = mutable.Map("foo" -> "bar"),
      mutableSet = mutable.Set("cc", "aaa", "b"),
      mutableSeq = mutable.Seq("d", "a"),
      mutableBuffer = mutable.Buffer("hoo", "ray"),
      mutableQueue = mutable.Queue("hoo", "ray"),
      mutableStack = mutable.Stack("hoo", "ray"),
      stringSetListing = List(Set("in set in listing")),
      intListingListing = List(List(1337), List(100)),
      intStringMapping = Map(42 -> "in map"),
      stringStringSetMapping = Map("key" -> Set("in set in map")),
      intListingStringMapping = Map(List(42, 1337) -> "structured key works"),
      intSetListStringMapping =
        Map(List(Set(27)) -> "in mapping with structured key"),
      thisOneGoesToEleven = Map(
        List(Set(0), Set(0), Set(7)) -> Map(
          List(0, 0, 7) -> Map(42 -> "in map")
        )
      ),
      simpleEnumViaString = SimpleEnum.Bbb,
      simpleEnumViaInt = SimpleEnum.Aaa
    )
  }
}

@nowarn("cat=deprecation")
object ScalaObjectMapperSpec {

  case class TypedKey(value: Int)
  object TypedKey {
    implicit val diffx: Diff[TypedKey] = Diff.derived[TypedKey]
  }

  object SimpleEnum extends Enumeration {
    @EnumOwner(classOf[SimpleEnum.type])
    case class V() extends Val(nextId)

    val Aaa = V()
    val Bbb = V()
    val Ccc = V()
  }

  case class ObjectMappingTestContainer(
      // Options
      optionalVal1: Option[String],
      optionalVal2: Option[String],
      // Duration
      pklDuration: PDuration,
      scalaFiniteDuration: FiniteDuration,
      scalaDuration: Duration,
      // Instant
      instant1: Instant,
      instant2: Instant,
      // Sets
      stringSet: Set[String],
      intSet: Set[Int],
      booleanSetSet: Set[Set[Boolean]],
      // Lists
      stringList: List[String],
      intList: List[Int],
      booleanListList: List[List[Boolean]],
      // Stream
      stream: Stream[String],
      // LazyList
      lazyList: LazyList[Int],
      // Vector
      vector: Vector[Int],
      // Seq
      seq: Seq[Int],
      // Maps
      intStringMap: Map[Int, String],
      booleanIntStringMapMap: Map[Boolean, Map[Int, String]],
      booleanIntMapStringMap: Map[Map[Boolean, Int], String],
      intSetListStringMap: Map[List[Set[Int]], String],
      typedStringMap: Map[TypedKey, String],
      dynamicStringMap: Map[Map[String, Any], String],
      // mutable.Map
      mutableMap: mutable.Map[String, String],
      // mutable.Set
      mutableSet: mutable.Set[String],
      // mutable.Seq
      mutableSeq: mutable.Seq[String],
      // mutable.Buffer
      mutableBuffer: mutable.Buffer[String],
      // mutable.Queue
      mutableQueue: mutable.Queue[String],
      // mutable.Stack
      mutableStack: mutable.Stack[String],
      // Listings
      stringSetListing: List[Set[String]],
      intListingListing: List[List[Int]],
      // Mapping
      intStringMapping: Map[Int, String],
      stringStringSetMapping: Map[String, Set[String]],
      // Map & Mapping with structured keys
      intListingStringMapping: Map[List[Int], String],
      intSetListStringMapping: Map[List[Set[Int]], String],
      thisOneGoesToEleven: Map[
        List[Set[Int]],
        Map[List[Int], Map[Int, String]]
      ],
      // enums
      simpleEnumViaString: SimpleEnum.V,
      simpleEnumViaInt: SimpleEnum.V
  )

  object ObjectMappingTestContainer {
    implicit def anyDiffx[T]: Diff[T] = Diff.useEquals[T]
    implicit val diffx: Diff[ObjectMappingTestContainer] =
      Diff.derived[ObjectMappingTestContainer]
  }
}
