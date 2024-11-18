/*
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.module

import java.net.URI
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.pkl.core.Evaluator
import org.pkl.core.ModuleSource
import org.pkl.core.PClassInfo
import org.pkl.core.PModule

class ServiceProviderTest {
  @Test
  fun `load module through service provider`() {
    val module = Evaluator.preconfigured().evaluate(ModuleSource.uri(URI("test:foo")))

    val uri = URI("modulepath:/org/pkl/core/module/testFactoryTest.pkl")
    Assertions.assertThat(module)
      .isEqualTo(
        PModule(
          uri,
          "testFactoryTest",
          PClassInfo.forModuleClass("testFactoryTest", uri),
          mapOf("name" to "Pigeon", "age" to 40L)
        )
      )
  }
}
