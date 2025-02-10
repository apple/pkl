/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.parser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpanTest {

  @Test
  fun `endWith test`() {
    var span1 = Span(10, 20)
    var span2 = Span(20, 20)
    assertThat(span1.endWith(span2)).isEqualTo(Span(10, 30))

    span1 = Span(10, 20)
    span2 = Span(0, 40)
    assertThat(span1.endWith(span2)).isEqualTo(Span(10, 30))

    span1 = Span(10, 30)
    span2 = Span(20, 20)
    assertThat(span1.endWith(span2)).isEqualTo(Span(10, 30))

    span1 = Span(10, 30)
    span2 = Span(20, 5)
    assertThat(span1.endWith(span2)).isEqualTo(Span(10, 15))
  }
}
