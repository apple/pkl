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

import org.pkl.config.java.mapper.{Types, ValueMapper, ValueMapperBuilder}
import org.pkl.core.{Duration, Evaluator, PModule, PObject}
import org.pkl.core.ModuleSource.modulePath
import org.scalatest.Outcome
import org.scalatest.funsuite.FixtureAnyFunSuite
import org.scalatest.matchers.should.Matchers.*
import org.pkl.config.scala.syntax.*

import scala.jdk.CollectionConverters.*

class PPairToScalaTupleSpec extends FixtureAnyFunSuite {
  import PPairToScalaTupleSpec.*

  case class TestFixture(module: PModule, mapper: ValueMapper)

  type FixtureParam = TestFixture

  override def withFixture(test: OneArgTest): Outcome = {
    val evaluator = Evaluator.preconfigured()
    try {
      val module = evaluator.evaluate(
        modulePath("org/pkl/config/scala/mapper/PPairToScalaTuple.pkl")
      )
      val mapper = ValueMapperBuilder.preconfigured().forScala().build()
      withFixture(test.toNoArgTest(TestFixture(module, mapper)))
    } finally {
      evaluator.close()
    }
  }

  test("Pair or scalar values") { case TestFixture(module, mapper) =>
    val ex1 = module.getProperty("ex1")
    val mapped: (Int, Duration) = {
      mapper.map(
        ex1,
        Types.parameterizedType(
          classOf[Tuple2[?, ?]],
          classOf[Integer],
          classOf[Duration]
        )
      )
    }

    mapped shouldBe (1, Duration.ofSeconds(3))
  }

  test("Pair of PObject") { case TestFixture(module, mapper) =>
    val ex2 = module.getProperty("ex2")
    val mapped: (PObject, PObject) = {
      mapper.map(
        ex2,
        Types.parameterizedType(
          classOf[Tuple2[?, ?]],
          classOf[PObject],
          classOf[PObject]
        )
      )
    }

    mapped._1.getProperties.asScala should contain only (
      "name" -> "pigeon",
      "age" -> 40L
    )

    mapped._2.getProperties.asScala should contain only (
      "name" -> "parrot",
      "age" -> 30L
    )
  }

  test("Pair of case class") { case TestFixture(module, mapper) =>
    val ex2 = module.getProperty("ex2")
    val mapped: (Animal, Animal) = {
      mapper.map(
        ex2,
        Types.parameterizedType(
          classOf[Tuple2[?, ?]],
          classOf[Animal],
          classOf[Animal]
        )
      )
    }

    mapped._1 shouldBe Animal("pigeon", 40L)
    mapped._2 shouldBe Animal("parrot", 30L)
  }
}

object PPairToScalaTupleSpec {

  case class Animal(name: String, age: Long)
}
