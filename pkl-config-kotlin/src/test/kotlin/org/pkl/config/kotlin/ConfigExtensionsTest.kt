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
package org.pkl.config.kotlin

import kotlin.test.Ignore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.config.java.ConfigEvaluator
import org.pkl.config.java.ConfigEvaluatorBuilder
import org.pkl.config.java.mapper.ConversionException
import org.pkl.config.kotlin.ConfigExtensionsTest.Hobby.READING
import org.pkl.config.kotlin.ConfigExtensionsTest.Hobby.SWIMMING
import org.pkl.core.ModuleSource.text

class ConfigExtensionsTest {
  private val evaluator = ConfigEvaluator.preconfigured().forKotlin()

  @Test
  fun `convert to kotlin classes`() {
    val config =
      evaluator.evaluate(
        text(
          """
        pigeon {
          name = "pigeon"
          age = 30
          hobbies = List("swimming", "reading")
          address {
            street = "Fuzzy St."
          }
        }
        """
        )
      )

    val address = config["pigeon"]["address"].to<Address<String>>()
    assertThat(address.street).isEqualTo("Fuzzy St.")

    val pigeon = config["pigeon"].to<Person<String>>()
    assertThat(pigeon).isNotNull
    assertThat(pigeon.name).isEqualTo("pigeon")
    assertThat(pigeon.age).isEqualTo(30)
    assertThat(pigeon.hobbies).isEqualTo(setOf(READING, SWIMMING))
    assertThat(pigeon.address.street).isEqualTo("Fuzzy St.")
  }

  @Test
  fun `convert to kotlin class with nullable property`() {
    // cover ConfigEvaluatorBuilder.preconfigured()
    val evaluator = ConfigEvaluatorBuilder.preconfigured().forKotlin().build()

    val config = evaluator.evaluate(text("pigeon { address = null }"))

    val pigeon = config["pigeon"].to<Person2>()
    assertThat(pigeon.address).isNull()
  }

  @Test
  fun `convert to kotlin class with covariant collection property type`() {
    val config =
      evaluator.evaluate(
        text(
          """pigeon { addresses = List(new Dynamic { street = "Fuzzy St." }, new Dynamic { street = "Other St." }) }"""
        )
      )

    config["pigeon"].to<Person3>()
  }

  @Test
  @Ignore("sgammon: Broken with Kotlin upgrade")
  fun `convert to nullable type`() {
    val config =
      evaluator.evaluate(text("""pigeon { address1 { street = "Fuzzy St." }; address2 = null }"""))

    val address1 = config["pigeon"]["address1"].to<Address<String>?>()
    assertThat(address1).isEqualTo(Address(street = "Fuzzy St."))

    val address2 = config["pigeon"]["address2"].to<Address<String>?>()
    assertThat(address2).isNull()

    val e = assertThrows<ConversionException> { config["pigeon"]["address2"].to<Address<String>>() }
    assertThat(e)
      .hasMessage(
        "Expected a non-null value but got `null`. " +
          "To allow null values, convert to a nullable Kotlin type, for example `String?`."
      )
  }

  @Test
  fun `convert to kotlin class that has defaults for constructor args`() {
    val config =
      evaluator.evaluate(
        text(
          """
        pigeon {
          name = "Pigeon"
          age = 42
          hobbies = List()
        }
        """
        )
      )

    val pigeon = config["pigeon"].to<PersonWithDefaults>()
    assertThat(pigeon.name).isEqualTo("Pigeon")
    assertThat(pigeon.age).isEqualTo(42)
    assertThat(pigeon.hobbies).isEqualTo(listOf<String>())
  }

  // check that java converter factory still kicks in
  @Test
  fun `convert to java class with multiple constructors`() {
    val config =
      evaluator.evaluate(
        text(
          """
        pigeon {
          name = "Pigeon"
          age = 42
          hobbies = List()
        }
        """
        )
      )

    val pigeon = config["pigeon"].to<JavaPerson>()
    assertThat(pigeon.name).isEqualTo("Pigeon")
    assertThat(pigeon.age).isEqualTo(42)
    assertThat(pigeon.hobbies).isEqualTo(listOf<String>())
  }

  @Test
  fun `convert list to parameterized list`() {
    val config =
      evaluator.evaluate(
        text(
          """friends = List(new Dynamic { name = "lilly"}, new Dynamic {name = "bob"}, new Dynamic {name = "susan"})"""
        )
      )

    val friends = config["friends"].to<List<SimplePerson>>()
    assertThat(friends)
      .isEqualTo(listOf(SimplePerson("lilly"), SimplePerson("bob"), SimplePerson("susan")))
  }

  @Test
  fun `convert map to parameterized map`() {
    val config =
      evaluator.evaluate(
        text(
          """friends = Map("l", new Dynamic { name = "lilly"}, "b", new Dynamic { name = "bob"}, "s", new Dynamic { name = "susan"})"""
        )
      )

    val friends = config["friends"].to<Map<String, SimplePerson>>()
    assertThat(friends)
      .isEqualTo(
        mapOf(
          "l" to SimplePerson("lilly"),
          "b" to SimplePerson("bob"),
          "s" to SimplePerson("susan")
        )
      )
  }

  @Test
  fun `convert container to parameterized map`() {
    val config =
      evaluator.evaluate(
        text("""friends {l { name = "lilly"}; b { name = "bob"}; s { name = "susan"}}""")
      )

    val friends = config["friends"].to<Map<String, SimplePerson>>()
    assertThat(friends)
      .isEqualTo(
        mapOf(
          "l" to SimplePerson("lilly"),
          "b" to SimplePerson("bob"),
          "s" to SimplePerson("susan")
        )
      )
  }

  @Test
  fun `convert enum with mangled names`() {
    val values = MangledNameEnum.values().map { "\"$it\"" }
    val config =
      evaluator.evaluate(
        text(
          """
      typealias MangledNameEnum = ${values.joinToString(" | ")}
      allEnumValues: Set<MangledNameEnum> = Set(${values.joinToString(", ")})
      """
            .trimIndent()
        )
      )
    val allEnumValues = config["allEnumValues"].to<Set<MangledNameEnum>>()
    assertThat(allEnumValues).isEqualTo(MangledNameEnum.values().toSet())
  }

  data class SimplePerson(val name: String)

  class Person<T>(val name: String, val age: Int, val hobbies: Set<Hobby>, val address: Address<T>)

  enum class Hobby {
    SWIMMING,
    @Suppress("unused") SURFING,
    READING
  }

  data class Address<out T>(val street: T)

  class Person2(val address: Address<String>?)

  class Person3(@Suppress("unused") val addresses: List<OpenAddress>)

  open class OpenAddress(val street: String) {
    override fun equals(other: Any?): Boolean {
      return other is OpenAddress && street == other.street
    }

    override fun hashCode(): Int {
      return street.hashCode()
    }
  }

  class PersonWithDefaults(
    val name: String = "Pigeon",
    val age: Int = 42,
    val hobbies: List<String>
  )

  @Suppress("NonAsciiCharacters", "EnumEntryName")
  enum class MangledNameEnum(val value: String) {
    FROM_CAMEL_CASE("fromCamelCase"),
    HYPHENATED_NAME("hyphenated-name"),
    EN_QUAD_EM_SPACE_IDEOGRAPHIC_SPACE_("EnQuad\\u2000EmSpace\\u2003IdeographicSpace\\u3000"),
    ᾊ_ᾨ("ᾊ\u0ABFᾨ"),
    _42_FROM_INVALID_START("42-from-invalid-start"),
    __EMOJI__("❎Emoji✅✅"),
    ÀŒÜ("àœü"),
    日本_つくば("日本-つくば")
  }
}
