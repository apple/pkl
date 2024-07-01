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
package org.pkl.core

import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PObjectTest {
  private val testUri = URI("repl:test")
  private val classInfo = PClassInfo.get("test", "Person", testUri)
  private val properties = mapOf("name" to "Pigeon", "age" to 42)
  private val pigeon = PObject(classInfo, properties)

  @Test
  fun `getPClassInfo()`() {
    assertThat(pigeon.classInfo).isSameAs(classInfo)
  }

  @Test
  fun `getProperties()`() {
    assertThat(pigeon.properties).isSameAs(properties)
  }

  @Test
  fun `getProperty()`() {
    assertThat(pigeon.getProperty("name")).isEqualTo("Pigeon")
    assertThat(pigeon.getProperty("age")).isEqualTo(42)
  }

  @Test
  fun `get unknown property`() {
    val e = assertThrows<NoSuchPropertyException> { pigeon.getProperty("other") }

    assertThat(e)
      .hasMessage(
        "Object of type `test#Person` does not have a property " +
          "named `other`. Available properties: [name, age]"
      )
  }

  @Test
  fun `hasProperty()`() {
    assertThat(pigeon.hasProperty("name")).isTrue
    assertThat(pigeon.hasProperty("age")).isTrue
    assertThat(pigeon.hasProperty("other")).isFalse
  }

  @Test
  fun `accept()`() {
    var objectVisited = false
    var moduleVisited = false

    val visitor =
      object : ValueVisitor {
        override fun visitObject(value: PObject) {
          objectVisited = true
        }

        override fun visitModule(value: PModule) {
          moduleVisited = true
        }
      }

    pigeon.accept(visitor)

    assertThat(objectVisited).isTrue
    assertThat(moduleVisited).isFalse
  }

  @Test
  fun `equals() and hashCode()`() {
    assertThat(pigeon).isEqualTo(pigeon)
    assertThat(pigeon.hashCode()).isEqualTo(pigeon.hashCode())

    val pigeon2 = PObject(PClassInfo.get("test", "Person", URI("repl:test")), HashMap(properties))

    assertThat(pigeon2).isEqualTo(pigeon)
    assertThat(pigeon2.hashCode()).isEqualTo(pigeon.hashCode())
  }

  @Test
  fun `non-equal - different type`() {
    val pigeon2 = PObject(PClassInfo.get("test", "Other", URI("repl:Other")), properties)

    assertThat(pigeon2).isNotEqualTo(pigeon)
    assertThat(pigeon2.hashCode()).isNotEqualTo(pigeon.hashCode())
  }

  @Test
  fun `non-equal - different property value`() {
    val pigeon2 =
      PObject(
        PClassInfo.get("test", "Person", URI("repl:test")),
        mapOf("name" to "Pigeon", "age" to 21)
      )

    assertThat(pigeon2).isNotEqualTo(pigeon)
    assertThat(pigeon2.hashCode()).isNotEqualTo(pigeon.hashCode())
  }

  @Test
  fun `non-equal - missing property`() {
    val pigeon2 =
      PObject(PClassInfo.get("test", "Person", URI("repl:test")), mapOf("name" to "Pigeon"))

    assertThat(pigeon2).isNotEqualTo(pigeon)
    assertThat(pigeon2.hashCode()).isNotEqualTo(pigeon.hashCode())
  }

  @Test
  fun `non-equal - extra property`() {
    val pigeon2 =
      PObject(
        PClassInfo.get("test", "Person", URI("repl:test")),
        mapOf("name" to "Pigeon", "age" to 42, "other" to true)
      )

    assertThat(pigeon2).isNotEqualTo(pigeon)
    assertThat(pigeon2.hashCode()).isNotEqualTo(pigeon.hashCode())
  }

  @Test
  fun `toString()`() {
    assertThat(pigeon.toString()).isEqualTo("test#Person { name = Pigeon; age = 42 }")
  }
}
