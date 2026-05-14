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
package org.pkl.doc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE

class DocGeneratorTest {
  @Test
  @EnabledForJreRange(min = JRE.JAVA_21)
  fun `uses virtual thread executor on JDK 21+`() {
    // On older JDKs, we get a ThreadPoolExecutor.
    assertThat(DocGenerator.createDefaultExecutor().javaClass.canonicalName)
      .isEqualTo("java.util.concurrent.ThreadPerTaskExecutor")
  }
}
