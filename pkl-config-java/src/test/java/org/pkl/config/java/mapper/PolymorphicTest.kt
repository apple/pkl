package org.pkl.config.java.mapper

import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.ModuleSource
import com.example.Lib
import com.example.PolymorphicModuleTest
import com.example.PolymorphicModuleTest.Strudel
import com.example.PolymorphicModuleTest.TurkishDelight
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PolymorphicTest {
  @Test
  fun `deserializing polymorphic objects`() {
    val evaluator = ConfigEvaluator.preconfigured()
    val module = evaluator.evaluate(ModuleSource.modulePath("/codegenPkl/PolymorphicModuleTest.pkl")).`as`(PolymorphicModuleTest::class.java)
    assertThat(module.desserts[0]).isInstanceOf(Strudel::class.java)
    assertThat(module.desserts[1]).isInstanceOf(TurkishDelight::class.java)
    assertThat(module.planes[0]).isInstanceOf(Lib.Jet::class.java)
    assertThat(module.planes[1]).isInstanceOf(Lib.Propeller::class.java)
  }
}
