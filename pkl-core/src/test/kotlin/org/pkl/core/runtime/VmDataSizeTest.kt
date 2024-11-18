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
package org.pkl.core.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.DataSizeUnit

class VmDataSizeTest {
  private val size1 = VmDataSize(0.3, DataSizeUnit.KILOBYTES)
  private val size2 = VmDataSize(300.0, DataSizeUnit.BYTES)
  private val size3 = VmDataSize(300.1, DataSizeUnit.BYTES)

  @Test
  fun `equals()`() {
    assertThat(size1).isEqualTo(size1)
    assertThat(size2).isEqualTo(size1)
    assertThat(size1).isEqualTo(size2)

    assertThat(size3).isNotEqualTo(size1)
    assertThat(size2).isNotEqualTo(size3)
  }

  @Test
  fun `hashCode()`() {
    assertThat(size1.hashCode()).isEqualTo(size1.hashCode())
    assertThat(size2.hashCode()).isEqualTo(size1.hashCode())
    assertThat(size1.hashCode()).isEqualTo(size2.hashCode())

    assertThat(size3.hashCode()).isNotEqualTo(size1.hashCode())
    assertThat(size2.hashCode()).isNotEqualTo(size3.hashCode())
  }
}
