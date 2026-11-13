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
package org.pkl.config.scala

import org.pkl.config.java.{Config, ConfigEvaluator}
import org.pkl.config.java.mapper.ConversionException
import org.pkl.config.scala.syntax.*
import org.pkl.core.ModuleSource
import org.scalatest.Outcome
import org.scalatest.funsuite.FixtureAnyFunSuite
import org.scalatest.prop.TableDrivenPropertyChecks.*

/**
 * Pinning the *full* contract that `Config.to[T]` should eventually honor.
 *
 * Table-driven: each row is `(description, pklSource, runner, expected)`. A second table covers
 * negative cases — Pkl values that should *not* convert to the requested target. Most rows fail
 * today because `to[T]` only carries a `ClassTag[T]` to `Config.as(Class)`, which erases generic
 * type arguments, union components, and singleton-literal types. The set is the reviewer-flagged
 * shapes (#1, #15) plus the broader supported-type-pair matrix; once we wire a full-`Type` reifier
 * behind `to[T]` (izumi `Tag` or a hand-rolled `ScalaType`), these flip green.
 */
class ConfigToSpec extends FixtureAnyFunSuite {

  type FixtureParam = ConfigEvaluator

  override def withFixture(test: OneArgTest): Outcome = {
    val evaluator = ConfigEvaluator.preconfigured().forScala()
    try withFixture(test.toNoArgTest(evaluator))
    finally evaluator.close()
  }

  private case class Case(desc: String, pklSource: String, run: Config => Any, expected: Any)
  private case class FailCase(desc: String, pklSource: String, run: Config => Any)

  private val cases: org.scalatest.prop.TableFor1[Case] = Table[Case](
    "case",
    // -------- scalar baselines (regression sentinels) --------
    Case("to[Int]", "value = 42", _.to[Int], 42),
    Case("to[String]", """value = "hello"""", _.to[String], "hello"),
    Case("to[Boolean]", "value = true", _.to[Boolean], true),

    // -------- generic collections (#1: `to[Seq[Int]]` example) --------
    Case(
      "to[Seq[Int]] on Listing<Int>",
      "value: Listing<Int> = new { 1; 2; 3 }",
      _.to[Seq[Int]],
      Seq(1, 2, 3)
    ),
    Case(
      "to[List[String]] on Listing<String>",
      """value: Listing<String> = new { "a"; "b" }""",
      _.to[List[String]],
      List("a", "b")
    ),
    Case(
      "to[Vector[Int]] on Listing<Int>",
      "value: Listing<Int> = new { 7; 8 }",
      _.to[Vector[Int]],
      Vector(7, 8)
    ),
    Case(
      "to[Set[Int]] on Listing<Int> deduplicated",
      "value: Listing<Int> = new { 1; 2; 2; 3 }",
      _.to[Set[Int]],
      Set(1, 2, 3)
    ),
    Case(
      "to[Map[String, Int]] on Mapping<String, Int>",
      """value: Mapping<String, Int> = new { ["a"] = 1; ["b"] = 2 }""",
      _.to[Map[String, Int]],
      Map("a" -> 1, "b" -> 2)
    ),

    // -------- nested generics --------
    Case(
      "to[Seq[Map[String, Int]]] roundtrips two levels of generic info",
      """value: Listing<Mapping<String, Int>> = new {
        |  new { ["a"] = 1 }
        |  new { ["b"] = 2 }
        |}""".stripMargin,
      _.to[Seq[Map[String, Int]]],
      Seq(Map("a" -> 1), Map("b" -> 2))
    ),
    Case(
      "to[Map[Int, Seq[String]]] roundtrips key+value generics",
      """value: Mapping<Int, Listing<String>> = new {
        |  [1] = new { "a"; "b" }
        |  [2] = new { "c" }
        |}""".stripMargin,
      _.to[Map[Int, Seq[String]]],
      Map(1 -> Seq("a", "b"), 2 -> Seq("c"))
    ),

    // -------- Option (Pkl nullability surfaces as PNull, not Option) --------
    Case(
      "to[Option[Int]] on present Int? value",
      "value: Int? = 5",
      _.to[Option[Int]],
      Some(5)
    ),
    Case(
      "to[Option[Int]] on null Int? value",
      "value: Int? = null",
      _.to[Option[Int]],
      None
    ),
    Case(
      "to[Option[Seq[Int]]] preserves the inner generic",
      "value: Listing<Int>? = new { 1; 2 }",
      _.to[Option[Seq[Int]]],
      Some(Seq(1, 2))
    ),

    // -------- tuples --------
    Case(
      "to[(Int, String)] on Pair<Int, String>",
      """value: Pair<Int, String> = Pair(7, "seven")""",
      _.to[(Int, String)],
      (7, "seven")
    ),

    // -------- Either (#15: full-Type carry needed for dispatch) --------
    Case(
      "to[Either[String, Int]] direct — String source picks Left",
      """value: String | Int = "hello"""",
      _.to[Either[String, Int]],
      Left("hello")
    ),
    Case(
      "to[Either[String, Int]] direct — Int source picks Right",
      "value: String | Int = 42",
      _.to[Either[String, Int]],
      Right(42)
    ),

    // -------- Scala 3 union types (#15) --------
    Case(
      "to[Int | Boolean] dispatches by Pkl source type — Int side",
      "value: Int | Boolean = 42",
      _.to[Int | Boolean],
      42
    ),
    Case(
      "to[Int | Boolean] dispatches by Pkl source type — Boolean side",
      "value: Int | Boolean = true",
      _.to[Int | Boolean],
      true
    ),

    // -------- singleton string-literal types (#15) --------
    Case(
      """to["hello"] succeeds when source is exactly "hello"""",
      """value: "hello" = "hello"""",
      _.to["hello"],
      "hello"
    ),
    Case(
      """to["A" | "B"] dispatches across literal-union members — A""",
      """value: "A" | "B" = "A"""",
      _.to["A" | "B"],
      "A"
    ),
    Case(
      """to["A" | "B"] dispatches across literal-union members — B""",
      """value: "A" | "B" = "B"""",
      _.to["A" | "B"],
      "B"
    )
  )

  private val failingCases: org.scalatest.prop.TableFor1[FailCase] = Table[FailCase](
    "case",
    FailCase(
      """to["hello"] errors when source is a different String""",
      """value: String = "world"""",
      _.to["hello"]
    ),
    FailCase(
      """to["A" | "B"] errors when source is "C"""",
      """value: String = "C"""",
      _.to["A" | "B"]
    ),
    FailCase(
      """to[Int | Boolean] errors on String source""",
      """value: String = "neither"""",
      _.to[Int | Boolean]
    )
  )

  test("type-pair coverage: positive cases") { evaluator =>
    forAll(cases) { c =>
      withClue(s"[${c.desc}] ") {
        val cfg = evaluator.evaluate(ModuleSource.text(c.pklSource))
        assert(c.run(cfg.get("value")) == c.expected)
      }
    }
  }

  test("type-pair coverage: negative cases (should raise ConversionException)") { evaluator =>
    forAll(failingCases) { c =>
      withClue(s"[${c.desc}] ") {
        val cfg = evaluator.evaluate(ModuleSource.text(c.pklSource))
        intercept[ConversionException](c.run(cfg.get("value")))
      }
    }
  }
}
