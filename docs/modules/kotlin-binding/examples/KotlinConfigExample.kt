@file:Suppress("UNUSED_VARIABLE")

import org.pkl.config.java.ConfigEvaluator
import org.pkl.config.kotlin.forKotlin
import org.pkl.config.kotlin.to
import org.pkl.core.ModuleSource
import org.junit.jupiter.api.Test

// the pkl-jvm-examples repo has a similar example
class KotlinConfigExample {
  @Test
  fun usage() {
    // tag::usage[]
    val evaluator = ConfigEvaluator.preconfigured().forKotlin() // <1>
    val config = evaluator.use { // <2>
      it.evaluate(ModuleSource.text("""pigeon { age = 5; diet = new Listing { "Seeds" } }"""))
    }
    val pigeon = config["pigeon"] // <3>
    val age = pigeon["age"].to<Int>() // <4>
    val hobbies = pigeon["diet"].to<List<String>>() // <5>
    // end::usage[]
  }
  
  @Test
  fun nullable() {
    // tag::nullable[]
    val evaluator = ConfigEvaluator.preconfigured().forKotlin()
    val config = evaluator.use {
      it.evaluate(ModuleSource.text("name = null")) // <1>
    }
    val name = config["name"].to<String?>() // <2>
    // end::nullable[]
  }
}
