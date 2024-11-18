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
package org.pkl.core.truffle

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.Evaluator
import org.pkl.core.ModuleSource

// Verifies that (long, long) specialization is chosen for (long, long) inputs
// even if (double, double) specialization is currently active.
// This didn't work correctly for the pkl.math nodes exercised by this test
// before we removed the @ImplicitCast from long and double.
// (Strangely enough, it did work for nodes such as AdditionNode,
// which Christian Humer couldn't reproduce with a standalone example.
// According to him, @ImplicitCast doesn't provide such a guarantee
// and shouldn't be used if it's needed as in our case.)
class LongVsDoubleSpecializationTest {
  private val evaluator = Evaluator.preconfigured()

  @Test
  fun addition() {
    val result =
      evaluator.evaluate(
        ModuleSource.text("""
        x1 = Pair(1.0 + 2.0, 1 + 2).second
        """)
      )

    assertThat(result.properties["x1"]).isEqualTo(3L)
  }

  @Test
  fun subtraction() {
    val result =
      evaluator.evaluate(
        ModuleSource.text("""
        x1 = Pair(1.0 - 2.0, 1 - 2).second
        """)
      )

    assertThat(result.properties["x1"]).isEqualTo(-1L)
  }

  @Test
  fun multiplication() {
    val result =
      evaluator.evaluate(
        ModuleSource.text("""
        x1 = Pair(1.0 * 2.0, 1 * 2).second
        """)
      )

    assertThat(result.properties["x1"]).isEqualTo(2L)
  }

  @Test
  fun exponentiation() {
    val result =
      evaluator.evaluate(
        ModuleSource.text(
          """
        import "pkl:math"
  
        x1 = Pair(2.0 ** 2.0, 2 ** 2).second
        """
        )
      )

    assertThat(result.properties["x1"]).isEqualTo(4L)
  }

  @Test
  fun math_min() {
    val result =
      evaluator.evaluate(
        ModuleSource.text(
          """
        import "pkl:math"
  
        x1 = Pair(math.min(1.0, 2.0), math.min(1, 2)).second
        """
        )
      )

    assertThat(result.properties["x1"]).isEqualTo(1L)
  }

  @Test
  fun math_max() {
    val result =
      evaluator.evaluate(
        ModuleSource.text(
          """
        import "pkl:math"
  
        x1 = Pair(math.max(1.0, 2.0), math.max(1, 2)).second
        """
        )
      )

    assertThat(result.properties["x1"]).isEqualTo(2L)
  }
}
