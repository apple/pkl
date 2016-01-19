package org.pkl.core.stdlib

import org.pkl.core.Evaluator
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.pkl.core.ModuleSource

class ReflectModuleTest {
  private val evaluator = Evaluator.preconfigured()

  @ValueSource(
    strings = [
      "pkl:base", "pkl:json", "pkl:jsonnet", "pkl:math", "pkl:protobuf", "pkl:reflect",
      "pkl:settings", "pkl:shell", "pkl:test", "pkl:xml", "pkl:yaml"
    ]
  )
  @ParameterizedTest(name = "can reflect on {0} module")
  fun `can reflect on stdlib module`(moduleName: String) {
    //language=Pkl
    evaluator.evaluate(
      ModuleSource.text(
        """
        import "pkl:reflect"
        // use toString() because default values of some class properties
        // (e.g., Listing.default) cannot be exported or rendered as Pcf
        output {
          text = reflect.Module(import("$moduleName")).toString()
        }
        """.trimIndent()
      )
    )
  }
}
