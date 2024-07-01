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
package org.pkl.core.stdlib

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.core.runtime.Identifier
import org.pkl.core.runtime.VmException
import org.pkl.core.runtime.VmValueConverter.*

class PathSpecParserTest {
  private val parser = PathSpecParser()

  @Test
  fun `parse valid path specs`() {
    assertThat(parser.parse("")).isEqualTo(arrayOf(TOP_LEVEL_VALUE))

    assertThat(parser.parse("^")).isEqualTo(arrayOf(TOP_LEVEL_VALUE))

    assertThat(parser.parse("property")).isEqualTo(arrayOf(Identifier.get("property")))

    assertThat(parser.parse("^property"))
      .isEqualTo(arrayOf(Identifier.get("property"), TOP_LEVEL_VALUE))

    assertThat(parser.parse("prop1.prop2.prop3"))
      .isEqualTo(arrayOf(Identifier.get("prop3"), Identifier.get("prop2"), Identifier.get("prop1")))

    assertThat(parser.parse("^prop1.prop2.prop3"))
      .isEqualTo(
        arrayOf(
          Identifier.get("prop3"),
          Identifier.get("prop2"),
          Identifier.get("prop1"),
          TOP_LEVEL_VALUE
        )
      )

    assertThat(parser.parse("[key]")).isEqualTo(arrayOf("key"))

    assertThat(parser.parse("^[key]")).isEqualTo(arrayOf("key", TOP_LEVEL_VALUE))

    assertThat(parser.parse("[key1][key2][key3]")).isEqualTo(arrayOf("key3", "key2", "key1"))

    assertThat(parser.parse("^[key1][key2][key3]"))
      .isEqualTo(arrayOf("key3", "key2", "key1", TOP_LEVEL_VALUE))

    assertThat(parser.parse("*")).isEqualTo(arrayOf(WILDCARD_PROPERTY))

    assertThat(parser.parse("^*")).isEqualTo(arrayOf(WILDCARD_PROPERTY, TOP_LEVEL_VALUE))

    assertThat(parser.parse("[*]")).isEqualTo(arrayOf(WILDCARD_ELEMENT))

    assertThat(parser.parse("^[*]")).isEqualTo(arrayOf(WILDCARD_ELEMENT, TOP_LEVEL_VALUE))

    assertThat(parser.parse("*.*.*"))
      .isEqualTo(arrayOf(WILDCARD_PROPERTY, WILDCARD_PROPERTY, WILDCARD_PROPERTY))

    assertThat(parser.parse("^*.*.*"))
      .isEqualTo(arrayOf(WILDCARD_PROPERTY, WILDCARD_PROPERTY, WILDCARD_PROPERTY, TOP_LEVEL_VALUE))

    assertThat(parser.parse("[*][*][*]"))
      .isEqualTo(arrayOf(WILDCARD_ELEMENT, WILDCARD_ELEMENT, WILDCARD_ELEMENT))

    assertThat(parser.parse("^[*][*][*]"))
      .isEqualTo(arrayOf(WILDCARD_ELEMENT, WILDCARD_ELEMENT, WILDCARD_ELEMENT, TOP_LEVEL_VALUE))

    assertThat(parser.parse("[*].*[*]"))
      .isEqualTo(arrayOf(WILDCARD_ELEMENT, WILDCARD_PROPERTY, WILDCARD_ELEMENT))

    assertThat(parser.parse("^[*].*[*]"))
      .isEqualTo(arrayOf(WILDCARD_ELEMENT, WILDCARD_PROPERTY, WILDCARD_ELEMENT, TOP_LEVEL_VALUE))

    assertThat(parser.parse("*[*].*"))
      .isEqualTo(arrayOf(WILDCARD_PROPERTY, WILDCARD_ELEMENT, WILDCARD_PROPERTY))

    assertThat(parser.parse("^*[*].*"))
      .isEqualTo(arrayOf(WILDCARD_PROPERTY, WILDCARD_ELEMENT, WILDCARD_PROPERTY, TOP_LEVEL_VALUE))

    assertThat(parser.parse("prop1[key1].*[key2].prop2[*]"))
      .isEqualTo(
        arrayOf(
          WILDCARD_ELEMENT,
          Identifier.get("prop2"),
          "key2",
          WILDCARD_PROPERTY,
          "key1",
          Identifier.get("prop1")
        )
      )

    assertThat(parser.parse("^prop1[key1].*[key2].prop2[*]"))
      .isEqualTo(
        arrayOf(
          WILDCARD_ELEMENT,
          Identifier.get("prop2"),
          "key2",
          WILDCARD_PROPERTY,
          "key1",
          Identifier.get("prop1"),
          TOP_LEVEL_VALUE
        )
      )
  }

  // TODO: how to handle whitespace?
  // TODO: support quoted identifiers?
  @Test
  fun `parse invalid path specs`() {
    assertThrows<VmException> { parser.parse("^^") }

    assertThrows<VmException> { parser.parse("property.") }

    assertThrows<VmException> { parser.parse("property^") }

    assertThrows<VmException> { parser.parse(".property") }

    assertThrows<VmException> { parser.parse("prop1..prop2") }

    assertThrows<VmException> { parser.parse("[key") }

    assertThrows<VmException> { parser.parse("key]") }

    assertThrows<VmException> { parser.parse("[[key]]") }

    assertThrows<VmException> { parser.parse("property.[key]") }

    assertThrows<VmException> { parser.parse("[key1].[key2]") }

    assertThrows<VmException> { parser.parse("[key1] [key2]") }

    assertThrows<VmException> { parser.parse("**") }

    assertThrows<VmException> { parser.parse("[**]") }

    assertThrows<VmException> { parser.parse("[*") }
  }
}
