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

import java.util.*
import java.util.regex.Pattern
import org.pkl.config.java.mapper.Conversion
import org.pkl.config.java.mapper.ConversionException
import org.pkl.core.PClassInfo

object KotlinConversions {
    val pIntToULong: Conversion<Long, ULong> =
        Conversion.of(PClassInfo.Int, ULong::class.java) { value, _ ->
            if (value < 0) {
                throw ConversionException(
                    "Cannot convert pkl.base#Int `$value` to kotlin.ULong " +
                        // use Long.MAX_VALUE instead of ULong.MAX_VALUE
                        "because it is outside range `0...${Long.MAX_VALUE}`"
                )
            }
            value.toULong()
        }

    val pIntToUInt: Conversion<Long, UInt> =
        Conversion.of(PClassInfo.Int, UInt::class.java) { value, _ ->
            val max = 0xFFFFFFFF // use literal instead of `UInt.MAX_VALUE.toLong()`
            if (value < 0 || value > max) {
                throw ConversionException(
                    "Cannot convert pkl.base#Int `$value` to kotlin.UInt " +
                        "because it is outside range `0...$max`"
                )
            }
            value.toUInt()
        }

    val pIntToUShort: Conversion<Long, UShort> =
        Conversion.of(PClassInfo.Int, UShort::class.java) { value, _ ->
            val max = 0xFFFF // use literal instead of `UShort.MAX_VALUE.toLong()`
            if (value < 0 || value > max) {
                throw ConversionException(
                    "Cannot convert pkl.base#Int `$value` to kotlin.UShort " +
                        "because it is outside range `0...$max`"
                )
            }
            value.toUShort()
        }

    val pIntToUByte: Conversion<Long, UByte> =
        Conversion.of(PClassInfo.Int, UByte::class.java) { value, _ ->
            val max = 0xFF // use literal instead of `UByte.MAX_VALUE.toLong()`
            if (value < 0 || value > max) {
                throw ConversionException(
                    "Cannot convert pkl.base#Int `$value` to kotlin.UByte " +
                        "because it is outside range `0...$max`"
                )
            }
            value.toUByte()
        }

    val pStringToKotlinRegex: Conversion<String, Regex> =
        Conversion.of(PClassInfo.String, Regex::class.java) { value, _ -> Regex(value) }

    val pRegexToKotlinRegex: Conversion<Pattern, Regex> =
        Conversion.of(PClassInfo.Regex, Regex::class.java) { value, _ -> value.toRegex() }

    val all: Collection<Conversion<*, *>> =
        Collections.unmodifiableList(
            listOf(
                pIntToULong,
                pIntToUInt,
                pIntToUShort,
                pIntToUByte,
                pStringToKotlinRegex,
                pRegexToKotlinRegex
            )
        )
}
