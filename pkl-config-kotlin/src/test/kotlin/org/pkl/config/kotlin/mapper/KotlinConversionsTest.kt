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
package org.pkl.config.kotlin.mapper

import java.util.regex.Pattern
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.config.java.mapper.ConversionException
import org.pkl.config.java.mapper.ValueMapperBuilder

class KotlinConversionsTest {
  private val mapper = ValueMapperBuilder.unconfigured().build()

  @Test
  fun pStringToKotlinRegex() {
    val result = KotlinConversions.pStringToKotlinRegex.converter.convert("(?i)\\w*", mapper)
    assertThat(result.pattern).isEqualTo("(?i)\\w*")
    assertThat(result.options).isEqualTo(setOf(RegexOption.IGNORE_CASE))
  }

  @Test
  fun pRegexToKotlinRegex() {
    val result =
      KotlinConversions.pRegexToKotlinRegex.converter.convert(Pattern.compile("(?i)\\w*"), mapper)
    assertThat(result.pattern).isEqualTo("(?i)\\w*")
    assertThat(result.options).isEqualTo(setOf(RegexOption.IGNORE_CASE))
  }

  @Test
  fun pIntToULong() {
    assertThat(KotlinConversions.pIntToULong.converter.convert(0, mapper)).isEqualTo(0UL)

    assertThat(KotlinConversions.pIntToULong.converter.convert(Long.MAX_VALUE, mapper))
      .isEqualTo(Long.MAX_VALUE.toULong())

    assertThrows<ConversionException> {
      KotlinConversions.pIntToULong.converter.convert(-1, mapper)
    }
  }

  @Test
  fun pIntToUInt() {
    assertThat(KotlinConversions.pIntToUInt.converter.convert(0, mapper)).isEqualTo(0u)

    assertThat(KotlinConversions.pIntToUInt.converter.convert(UInt.MAX_VALUE.toLong(), mapper))
      .isEqualTo(UInt.MAX_VALUE)

    assertThrows<ConversionException> {
      KotlinConversions.pIntToUInt.converter.convert(UInt.MAX_VALUE.toLong() + 1, mapper)
    }

    assertThrows<ConversionException> { KotlinConversions.pIntToUInt.converter.convert(-1, mapper) }
  }

  @Test
  fun pIntToUShort() {
    assertThat(KotlinConversions.pIntToUShort.converter.convert(0, mapper)).isEqualTo(0.toUShort())

    assertThat(KotlinConversions.pIntToUShort.converter.convert(UShort.MAX_VALUE.toLong(), mapper))
      .isEqualTo(UShort.MAX_VALUE)

    assertThrows<ConversionException> {
      KotlinConversions.pIntToUShort.converter.convert(UShort.MAX_VALUE.toLong() + 1, mapper)
    }

    assertThrows<ConversionException> {
      KotlinConversions.pIntToUShort.converter.convert(-1, mapper)
    }
  }
}
