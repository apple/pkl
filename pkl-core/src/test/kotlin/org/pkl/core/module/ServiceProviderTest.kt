package org.pkl.core.module

import org.pkl.core.Evaluator
import org.pkl.core.ModuleSource
import org.pkl.core.PClassInfo
import org.pkl.core.PModule
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.net.URI

class ServiceProviderTest {
  @Test
  fun `load module through service provider`() {
    val module = Evaluator
      .preconfigured()
      .evaluate(ModuleSource.uri(URI("test:foo")))

    val uri = URI("modulepath:/org/pkl/core/module/testFactoryTest.pkl")
    Assertions.assertThat(module).isEqualTo(
      PModule(
        uri,
        "testFactoryTest",
        PClassInfo.forModuleClass("testFactoryTest", uri),
        mapOf("name" to "Pigeon", "age" to 40L)
      )
    )
  }
}
