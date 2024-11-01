/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// ArrayBuilder is designed for Java; it is less nice to use from Kotlin
class ArrayBuilderTest {
  private data class Person(val name: String)

  private val builder = ArrayBuilder.of<Person?> { arrayOfNulls<Person>(it) }
  private val alice = Person("Alice")

  @Test
  fun `add zero elements`() {
    val elementCount = 0
    assertThat(builder.isEmpty).isTrue()
    assertThat(builder.length()).isEqualTo(elementCount)
    assertThat(builder.lastIndex()).isEqualTo(elementCount - 1)

    val array = builder.toOversizedArray<Person>()
    assertThat(array.javaClass.componentType).isEqualTo(Person::class.java)
    assertThat(array).isEmpty()

    val array2 = builder.toArray<Person>()
    assertThat(array2.javaClass.componentType).isEqualTo(Person::class.java)
    assertThat(array2).isEmpty()
  }

  @Test
  fun `add fewer elements than initial array length`() {
    val elementCount = ArrayBuilder.INITIAL_LENGTH - 1
    repeat(elementCount) { builder.add(alice) }

    assertThat(builder.isEmpty).isFalse()
    assertThat(builder.length()).isEqualTo(elementCount)
    assertThat(builder.lastIndex()).isEqualTo(elementCount - 1)

    val array = builder.toOversizedArray<Person>()
    assertThat(array.javaClass.componentType).isEqualTo(Person::class.java)
    assertThat(array).hasSize(ArrayBuilder.INITIAL_LENGTH)
    for (i in 0 until elementCount) {
      assertThat(array[i]).isSameAs(alice)
    }
    for (i in elementCount..array.lastIndex) {
      assertThat(array[i]).isNull()
    }

    val array2 = builder.toArray<Person>()
    assertThat(array2.javaClass.componentType).isEqualTo(Person::class.java)
    assertThat(array2).hasSize(elementCount)
  }

  @Test
  fun `add same number of elements as initial array length`() {
    val elementCount = ArrayBuilder.INITIAL_LENGTH
    repeat(elementCount) { builder.add(alice) }

    assertThat(builder.isEmpty).isFalse()
    assertThat(builder.length()).isEqualTo(elementCount)
    assertThat(builder.lastIndex()).isEqualTo(elementCount - 1)

    val array = builder.toOversizedArray<Person>()
    assertThat(array.javaClass.componentType).isEqualTo(Person::class.java)
    assertThat(array).hasSize(elementCount)
    array.forEach { assertThat(it).isSameAs(alice) }
    assertThat(builder.toArray<Person>()).isSameAs(array)
  }

  @Test
  fun `add more elements than initial array length`() {
    val elementCount = ArrayBuilder.INITIAL_LENGTH + 1
    repeat(elementCount) { builder.add(alice) }

    assertThat(builder.isEmpty).isFalse()
    assertThat(builder.length()).isEqualTo(elementCount)
    assertThat(builder.lastIndex()).isEqualTo(elementCount - 1)

    val array = builder.toOversizedArray<Person>()
    assertThat(array.javaClass.componentType).isEqualTo(Person::class.java)
    assertThat(array).hasSize(ArrayBuilder.INITIAL_LENGTH * 2)
    for (i in 0 until elementCount) {
      assertThat(array[i]).isSameAs(alice)
    }
    for (i in elementCount..array.lastIndex) {
      assertThat(array[i]).isNull()
    }

    val array2 = builder.toArray<Person>()
    assertThat(array2.javaClass.componentType).isEqualTo(Person::class.java)
    assertThat(array2).hasSize(elementCount)
    array2.forEach { assertThat(it).isSameAs(alice) }
  }
}
