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
package org.pkl.config.scala

import com.softwaremill.diffx._
import com.softwaremill.diffx.scalatest.DiffShouldMatcher._
import org.pkl.config.java.ConfigEvaluator
import org.pkl.config.java.mapper.ConversionException
import org.pkl.config.scala.syntax._
import org.pkl.core.{Duration => PDuration, ModuleSource}
import org.scalatest.funsuite.AnyFunSuite

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.matching.Regex

class ScalaObjectMapperSpec extends AnyFunSuite {
  import ScalaObjectMapperSpec._

  test("evaluate scala types") {

    val code = {
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
        |// Seq
        |seq = List(9, 5, 36, 1)
        |
        |// Duration
        |pklDuration: Duration = 5.ms
        |scalaFiniteDuration: Duration = 5.ms
        |scalaDuration: Duration = 5000000.ns
        |
        |// Maps
        |intStringMap: Map<Int, String> = Map(0, "in map")
        |booleanIntStringMapMap: Map<Boolean, Map<Int, String>> = Map(false, Map(0, "in map in map"))
        |typedStringMap: Map<Foo, String> = Map(
        |  new Foo { value = 1 }, "using typed objects",
        |  new Foo { value = 2 }, "also works")
        |
        |// Mappings
        |intStringMapping: Mapping<Int, String> = new { [42] = "in map" }
        |
        |// Listings → Seq
        |intListing: Listing<Int> = new { 42 1337 }
        |
        |simpleEnumViaString = "Bbb"
        |simpleEnum2ViaString = "Ccc"
        |""".stripMargin
    }

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
      seq = Seq(9, 5, 36, 1),
      intStringMap = Map(0 -> "in map"),
      booleanIntStringMapMap = Map(false -> Map(0 -> "in map in map")),
      typedStringMap = Map(
        TypedKey(1) -> "using typed objects",
        TypedKey(2) -> "also works"
      ),
      intStringMapping = Map(42 -> "in map"),
      intListing = Seq(42, 1337),
      simpleEnumViaString = SimpleEnum.Bbb,
      simpleEnum2ViaString = SimpleEnum2.Ccc
    )
  }

  test("unknown enum value raises ConversionException listing valid members") {
    val code = {
      """
        |module M
        |color = "Purple"
        |""".stripMargin
    }

    val ex = intercept[ConversionException] {
      ConfigEvaluator
        .preconfigured()
        .forScala()
        .evaluate(ModuleSource.text(code))
        .to[EnumContainer]
    }
    val msg = ex.getMessage
    assert(msg.contains("Purple"), s"expected input name in message, got: $msg")
    assert(
      msg.contains("Aaa") && msg.contains("Bbb") && msg.contains("Ccc"),
      s"expected candidate members in message, got: $msg"
    )
  }

  test("missing required property on case class raises ConversionException") {
    val code = {
      """
        |module M
        |name = "Alice"
        |""".stripMargin // 'age' is missing
    }

    val ex = intercept[ConversionException] {
      ConfigEvaluator
        .preconfigured()
        .forScala()
        .evaluate(ModuleSource.text(code))
        .to[Person]
    }
    val msg = ex.getMessage
    assert(msg.contains("age"), s"expected missing property name in message, got: $msg")
  }

  test("type mismatch between Pkl value and case class field raises ConversionException") {
    val code = {
      """
        |module M
        |value = "not an int"
        |""".stripMargin
    }

    val ex = intercept[ConversionException] {
      ConfigEvaluator
        .preconfigured()
        .forScala()
        .evaluate(ModuleSource.text(code))
        .to[IntContainer]
    }
    val msg = ex.getMessage
    assert(
      msg.toLowerCase.contains("cannot convert") ||
        msg.toLowerCase.contains("string") ||
        msg.toLowerCase.contains("int"),
      s"expected type-mismatch hint in message, got: $msg"
    )
  }

  test("enum property receiving non-String Pkl value raises ConversionException") {
    val code = {
      """
        |module M
        |color = 42
        |""".stripMargin
    }

    val ex = intercept[ConversionException] {
      ConfigEvaluator
        .preconfigured()
        .forScala()
        .evaluate(ModuleSource.text(code))
        .to[EnumContainer]
    }
    val msg = ex.getMessage
    assert(
      msg.contains("Expected String value"),
      s"expected explicit non-String hint in message, got: $msg"
    )
  }

  test("pStringToScalaRegex converts Pkl String to Scala Regex") {
    val code = {
      """
        |module M
        |pattern = "^[0-9]+$"
        |""".stripMargin
    }

    val result = ConfigEvaluator
      .preconfigured()
      .forScala()
      .evaluate(ModuleSource.text(code))
      .to[RegexContainer]
    assert(result.pattern.pattern.pattern() == "^[0-9]+$")
  }

  test("pRegexToScalaRegex converts Pkl Regex to Scala Regex") {
    val code = {
      """
        |module M
        |pattern: Regex = Regex("^[a-z]+$")
        |""".stripMargin
    }

    val result = ConfigEvaluator
      .preconfigured()
      .forScala()
      .evaluate(ModuleSource.text(code))
      .to[RegexContainer]
    assert(result.pattern.pattern.pattern() == "^[a-z]+$")
  }

  test("forScala extension on ConfigEvaluatorBuilder wires Scala converters") {
    import org.pkl.config.java.ConfigEvaluatorBuilder

    val code = {
      """
        |module M
        |name = "via-builder"
        |age = 7
        |""".stripMargin
    }

    val evaluator = ConfigEvaluatorBuilder.preconfigured().forScala().build()
    try {
      val result = evaluator.evaluate(ModuleSource.text(code)).to[Person]
      assert(result == Person("via-builder", 7))
    } finally {
      evaluator.close()
    }
  }
}

object ScalaObjectMapperSpec {

  case class TypedKey(value: Int)
  object TypedKey {
    implicit val diffx: Diff[TypedKey] = Diff.derived[TypedKey]
  }

  object SimpleEnum extends Enumeration {
    case class V() extends Val(nextId)

    val Aaa = V()
    val Bbb = V()
    val Ccc = V()
  }

  object SimpleEnum2 extends Enumeration {
    val Aaa, Bbb, Ccc = Value
  }

  case class EnumContainer(color: SimpleEnum2.Value)
  case class Person(name: String, age: Int)
  case class IntContainer(value: Int)
  case class RegexContainer(pattern: Regex)

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
      // Seq
      seq: Seq[Int],
      // Maps
      intStringMap: Map[Int, String],
      booleanIntStringMapMap: Map[Boolean, Map[Int, String]],
      typedStringMap: Map[TypedKey, String],
      // Mapping
      intStringMapping: Map[Int, String],
      // Listing → Seq
      intListing: Seq[Int],
      // Enums (nested-Val and plain-Value forms)
      simpleEnumViaString: SimpleEnum.V,
      simpleEnum2ViaString: SimpleEnum2.Value
  )

  object ObjectMappingTestContainer {
    implicit def anyDiffx[T]: Diff[T] = Diff.useEquals[T]
    implicit val diffx: Diff[ObjectMappingTestContainer] = {
      Diff.derived[ObjectMappingTestContainer]
    }
  }
}
