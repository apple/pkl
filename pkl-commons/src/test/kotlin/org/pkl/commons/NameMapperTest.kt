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
package org.pkl.commons

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NameMapperTest {
  @Test
  fun `empty prefixes everything`() {
    val mapper = NameMapper(mapOf("" to "bar."))
    assertThat(mapper.map("foo.bar.Baz")).isEqualTo("bar.foo.bar" to "Baz")
    assertThat(mapper.map("Baz")).isEqualTo("bar" to "Baz")
  }

  @Test
  fun `longest prefix wins`() {
    val mapper = NameMapper(mapOf("bar." to "com.bar.", "bar.baz." to "foo.bar."))
    assertThat(mapper.map("bar.baz.Buzzy")).isEqualTo("foo.bar" to "Buzzy")
  }

  @Test
  fun `implicit uppercase classname`() {
    val mapper = NameMapper(mapOf("foo." to "bar."))
    assertThat(mapper.map("foo.bar.baz")).isEqualTo("bar.bar" to "Baz")
    assertThat(mapper.map("foo.bar")).isEqualTo("bar" to "Bar")
    assertThat(mapper.map("baz")).isEqualTo("" to "Baz")
    assertThat(mapper.map("baz")).isEqualTo("" to "Baz")
  }

  @Test
  fun `no implicit uppercased classname if explicitly renamed`() {
    val mapper =
      NameMapper(
        mapOf(
          "foo.bar" to "bar.bar",
          "foo.c" to "foo.z",
          "com.foo." to "x",
        )
      )
    assertThat(mapper.map("foo.bar")).isEqualTo("bar" to "bar")
    assertThat(mapper.map("foo.bar")).isEqualTo("bar" to "bar")
    assertThat(mapper.map("foo.cow")).isEqualTo("foo" to "zow")
    assertThat(mapper.map("com.foo.bar")).isEqualTo("" to "xbar")
  }
}
