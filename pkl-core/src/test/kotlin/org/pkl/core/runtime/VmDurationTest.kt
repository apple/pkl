/**
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
import org.pkl.core.DurationUnit

class VmDurationTest {
  private val duration1 = VmDuration(0.3, DurationUnit.SECONDS)
  private val duration2 = VmDuration(300.0, DurationUnit.MILLIS)
  private val duration3 = VmDuration(300.1, DurationUnit.MILLIS)

  @Test
  fun `equals()`() {
    assertThat(duration1).isEqualTo(duration1)
    assertThat(duration2).isEqualTo(duration1)
    assertThat(duration1).isEqualTo(duration2)

    assertThat(duration3).isNotEqualTo(duration1)
    assertThat(duration2).isNotEqualTo(duration3)
  }

  @Test
  fun `hashCode()`() {
    assertThat(duration1.hashCode()).isEqualTo(duration1.hashCode())
    assertThat(duration2.hashCode()).isEqualTo(duration1.hashCode())
    assertThat(duration1.hashCode()).isEqualTo(duration2.hashCode())

    assertThat(duration3.hashCode()).isNotEqualTo(duration1.hashCode())
    assertThat(duration2.hashCode()).isNotEqualTo(duration3.hashCode())
  }
}
