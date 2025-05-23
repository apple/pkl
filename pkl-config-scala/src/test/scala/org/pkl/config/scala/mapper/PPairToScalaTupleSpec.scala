package org.pkl.config.scala.mapper

import org.pkl.config.java.mapper.{Types, ValueMapperBuilder}
import org.pkl.core.{Duration, Evaluator, PClassInfo, PObject}
import org.pkl.core.ModuleSource.modulePath
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers._
import org.pkl.config.scala.syntax._

import scala.jdk.CollectionConverters._

class PPairToScalaTupleSpec extends AnyFunSuite with BeforeAndAfterAll {
  import PPairToScalaTupleSpec._

  private val evaluator = Evaluator.preconfigured()

  private val module =
    evaluator.evaluate(modulePath("org/pkl/config/scala/mapper/PPairToScalaTuple.pkl"))

  private val mapper = ValueMapperBuilder.preconfigured().forScala().build()

  override def afterAll(): Unit = {
    evaluator.close()
  }

  test("Pair or scalar values") {
    val ex1 = module.getProperty("ex1")
    val mapped: (Int, Duration) =
      mapper.map(
        ex1,
        Types.parameterizedType(classOf[Tuple2[_, _]], classOf[Integer], classOf[Duration])
    )

    mapped shouldBe (1, Duration.ofSeconds(3))
  }

  test("Pair of PObject") {
    val ex2 = module.getProperty("ex2")
    val mapped: (PObject, PObject) =
      mapper.map(
        ex2,
        Types.parameterizedType(classOf[Tuple2[_, _]], classOf[PObject], classOf[PObject])
    )

    mapped._1.getProperties.asScala should contain only (
      "name" -> "pigeon",
      "age" -> 40L
    )

    mapped._2.getProperties.asScala should contain only (
      "name" -> "parrot",
      "age" -> 30L
    )
  }

  test("Pair of case class") {
    val ex2 = module.getProperty("ex2")
    val mapped: (Animal, Animal) =
      mapper.map(
        ex2,
        Types.parameterizedType(classOf[Tuple2[_, _]], classOf[Animal], classOf[Animal])
    )

    mapped._1 shouldBe Animal("pigeon", 40L)
    mapped._2 shouldBe Animal("parrot", 30L)
  }
}

object PPairToScalaTupleSpec {

  case class Animal(name: String, age: Long)
}