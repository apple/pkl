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

import com.softwaremill.diffx.*
import com.softwaremill.diffx.scalatest.DiffShouldMatcher.*
import org.pkl.config.java.{ConfigEvaluator, ConfigEvaluatorBuilder}
import org.pkl.config.java.mapper.ConversionException
import org.pkl.config.scala.syntax.*
import org.pkl.core.{Duration => PDuration, ModuleSource}
import org.scalatest.Outcome
import org.scalatest.funsuite.FixtureAnyFunSuite

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Using
import scala.util.matching.Regex

class ScalaObjectMapperSpec extends FixtureAnyFunSuite {
  import ScalaObjectMapperSpec.*

  type FixtureParam = ConfigEvaluator

  override def withFixture(test: OneArgTest): Outcome = {
    val evaluator = ConfigEvaluator.preconfigured().forScala()
    try withFixture(test.toNoArgTest(evaluator))
    finally evaluator.close()
  }

  test("evaluate scala types") { evaluator =>
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
        |// Scala 3 enum (string-literal union)
        |simpleEnumViaString = "Bbb"
        |""".stripMargin
    }

    val result = evaluator.evaluate(ModuleSource.text(code)).to[ObjectMappingTestContainer]

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
      simpleEnumViaString = SimpleEnum.Bbb
    )
  }

  test("idiomatic Scala 3 enum routes through PStringToScalaEnum") { evaluator =>
    val code = {
      """
        |module M
        |color = "Bbb"
        |""".stripMargin
    }

    val result = evaluator.evaluate(ModuleSource.text(code)).to[EnumContainer]
    assert(result.color == SimpleEnum.Bbb)
  }

  test("unknown enum value raises ConversionException listing valid members in declaration order") {
    evaluator =>
      val code = {
        """
          |module M
          |color = "Purple"
          |""".stripMargin
      }

      val ex = intercept[ConversionException] {
        evaluator.evaluate(ModuleSource.text(code)).to[EnumContainer]
      }
      val msg = ex.getMessage
      assert(msg.contains("Purple"), s"expected input name in message, got: $msg")
      val aaaIdx = msg.indexOf("Aaa")
      val bbbIdx = msg.indexOf("Bbb")
      val cccIdx = msg.indexOf("Ccc")
      assert(
        aaaIdx >= 0 && bbbIdx > aaaIdx && cccIdx > bbbIdx,
        s"expected Aaa, Bbb, Ccc in declaration order, got: $msg"
      )
  }

  test("Java-compat Scala 3 enum (extends java.lang.Enum) routes through PStringToEnum") {
    evaluator =>
      val code = {
        """
          |module M
          |color = "Yyy"
          |""".stripMargin
      }

      val result = evaluator.evaluate(ModuleSource.text(code)).to[JavaCompatEnumContainer]
      assert(result.color == JavaCompatEnum.Yyy)
  }

  test("missing required property on case class raises ConversionException") { evaluator =>
    val code = {
      """
        |module M
        |name = "Alice"
        |""".stripMargin // 'age' is missing
    }

    val ex = intercept[ConversionException] {
      evaluator.evaluate(ModuleSource.text(code)).to[Person]
    }
    val msg = ex.getMessage
    assert(msg.contains("age"), s"expected missing property name in message, got: $msg")
  }

  test("type mismatch between Pkl value and case class field raises ConversionException") {
    evaluator =>
      val code = {
        """
          |module M
          |value = "not an int"
          |""".stripMargin
      }

      val ex = intercept[ConversionException] {
        evaluator.evaluate(ModuleSource.text(code)).to[IntContainer]
      }
      val msg = ex.getMessage
      assert(
        msg.toLowerCase.contains("cannot convert") ||
          msg.toLowerCase.contains("string") ||
          msg.toLowerCase.contains("int"),
        s"expected type-mismatch hint in message, got: $msg"
      )
  }

  test("pStringToScalaRegex converts Pkl String to Scala Regex") { evaluator =>
    val code = {
      """
        |module M
        |pattern = "^[0-9]+$"
        |""".stripMargin
    }

    val result = evaluator.evaluate(ModuleSource.text(code)).to[RegexContainer]
    assert(result.pattern.pattern.pattern() == "^[0-9]+$")
  }

  test("pRegexToScalaRegex converts Pkl Regex to Scala Regex") { evaluator =>
    val code = {
      """
        |module M
        |pattern: Regex = Regex("^[a-z]+$")
        |""".stripMargin
    }

    val result = evaluator.evaluate(ModuleSource.text(code)).to[RegexContainer]
    assert(result.pattern.pattern.pattern() == "^[a-z]+$")
  }

  test("forScala extension on ConfigEvaluatorBuilder wires Scala converters") { _ =>
    val code = {
      """
        |module M
        |name = "via-builder"
        |age = 7
        |""".stripMargin
    }

    Using.resource(ConfigEvaluatorBuilder.preconfigured().forScala().build()) { evaluator =>
      val result = evaluator.evaluate(ModuleSource.text(code)).to[Person]
      assert(result == Person("via-builder", 7))
    }
  }

  test(
    "inherited Java conversions: BigInteger, BigDecimal, URI, URL, Path, File, Char, Bytes, DataSize, Listing, Mapping"
  ) { evaluator =>
    val code = {
      """
        |module M
        |bigInt = 9007199254740993
        |bigDec = 1.5
        |uri = "https://example.com"
        |url = "https://example.com/path"
        |path = "/tmp/foo"
        |file = "/tmp/bar"
        |char = "A"
        |bytes = Bytes(72, 105)
        |dataSize: DataSize = 5.kb
        |listing: Listing<Int> = new { 1; 2; 3 }
        |mapping: Mapping<String, Int> = new { ["a"] = 1; ["b"] = 2 }
        |""".stripMargin
    }

    val result = evaluator.evaluate(ModuleSource.text(code)).to[InheritedTypesContainer]

    assert(result.bigInt == new java.math.BigInteger("9007199254740993"))
    assert(result.bigDec == java.math.BigDecimal.valueOf(1.5))
    assert(result.uri == new java.net.URI("https://example.com"))
    assert(result.url == new java.net.URI("https://example.com/path").toURL)
    assert(result.path == java.nio.file.Path.of("/tmp/foo"))
    assert(result.file == new java.io.File("/tmp/bar"))
    assert(result.char == java.lang.Character.valueOf('A'))
    assert(result.bytes.sameElements(Array[Byte](72, 105)))
    assert(result.dataSize == org.pkl.core.DataSize.ofKilobytes(5))
    assert(result.listing == Seq(1, 2, 3))
    assert(result.mapping == Map("a" -> 1, "b" -> 2))
  }
}

object ScalaObjectMapperSpec {

  case class TypedKey(value: Int)
  object TypedKey {
    implicit val diffx: Diff[TypedKey] = Diff.derived[TypedKey]
  }

  case class Person(name: String, age: Int)
  case class IntContainer(value: Int)
  case class RegexContainer(pattern: Regex)

  enum SimpleEnum {
    case Aaa, Bbb, Ccc
  }

  enum JavaCompatEnum extends java.lang.Enum[JavaCompatEnum] {
    case Xxx, Yyy, Zzz
  }

  case class EnumContainer(color: SimpleEnum)
  case class JavaCompatEnumContainer(color: JavaCompatEnum)

  case class InheritedTypesContainer(
      bigInt: java.math.BigInteger,
      bigDec: java.math.BigDecimal,
      uri: java.net.URI,
      url: java.net.URL,
      path: java.nio.file.Path,
      file: java.io.File,
      // boxed Character: Java's pStringToCharacter conversion targets java.lang.Character,
      // not the JVM primitive `char` that Scala's `Char` becomes in case-class signatures.
      char: java.lang.Character,
      bytes: Array[Byte],
      dataSize: org.pkl.core.DataSize,
      listing: Seq[Int],
      mapping: Map[String, Int]
  )

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
      // Scala 3 enum
      simpleEnumViaString: SimpleEnum
  )

  object ObjectMappingTestContainer {
    implicit def anyDiffx[T]: Diff[T] = Diff.useEquals[T]
    implicit val diffx: Diff[ObjectMappingTestContainer] = {
      Diff.derived[ObjectMappingTestContainer]
    }
  }
}
