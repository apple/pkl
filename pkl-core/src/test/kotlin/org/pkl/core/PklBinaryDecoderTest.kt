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
package org.pkl.core

import java.net.URI
import java.util.regex.Pattern
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.core.util.pklbinary.PklBinaryCode

class PklBinaryDecoderTest {
  @Test
  fun `decoding succeeds`() {
    val moduleUri = URI.create("file:///\$snippetsDir/input/api/encoding1.msgpack.yaml.pkl")
    // based on pkl-core/src/test/files/LanguageSnippetTests/input/api/encoding1.msgpack.yaml.pkl
    // but Class, TypeAlias, and IntSeq values are nil'd
    // and any module URIs are normalized to use $snippetsDir instead of an absoulute path
    val inputStream = javaClass.getResourceAsStream("pklBinaryDecoderTest.msgpack")
    assertThat(inputStream).isNotNull
    val decoded = PklBinaryDecoder.decode(inputStream!!.readAllBytes())
    val expected =
      PObject(
        PClassInfo.get("encoding1", "Foo", moduleUri),
        mapOf(
          "dynamic" to PObject(PClassInfo.Dynamic, mapOf("hello" to "world", "0" to "hello world")),
          "string" to "foo",
          "map" to mapOf("foo" to "bar"),
          "mapping" to mapOf("foo" to "bar"),
          "list" to listOf("foo", "bar"),
          "listing" to listOf("foo", 0L),
          "set" to setOf("foo", "bar"),
          "duration" to Duration(123.0, DurationUnit.HOURS),
          "dataSize" to DataSize(123.0, DataSizeUnit.GIBIBYTES),
          "pair" to Pair("foo", "bar"),
          "intSeq" to PNull.getInstance(), // IntSeq is not exportable
          "regex" to
            (decoded as PObject).getProperty("regex"), // asserted below == Pattern.compile("foo.*")
          "func" to PNull.getInstance(), // Function is not encodable or decodable
          "bytes" to
            decoded.getProperty("bytes"), // asserted below == byteArrayOf(0x01, 0x02, 0x03)
          "moduleClass" to PNull.getInstance(), // PClass is not decodable
          "baseModuleClass" to PNull.getInstance(), // PClass is not decodable
          "encodingModuleClass" to PNull.getInstance(), // PClass is not decodable
          "stdlibClass" to PNull.getInstance(), // PClass is not decodable
          "someClass" to PNull.getInstance(), // PClass is not decodable
          "stdlibTypealias" to PNull.getInstance(), // TypeAlias is not decodable
          "someTypealias" to PNull.getInstance(), // TypeAlias is not decodable
          "something" to
            PObject(
              PClassInfo.get("pkl.base", "PcfRenderer", PClassInfo.pklBaseUri),
              mapOf(
                "converters" to emptyMap<Object, Object>(),
                "extension" to "pcf",
                "indent" to "  ",
                "omitNullProperties" to false,
                "useCustomStringDelimiters" to false,
              ),
            ),
        ),
      )

    assertThat(decoded).isEqualTo(expected)
    assertThat((decoded.getProperty("regex") as Pattern).pattern()).isEqualTo("foo.*")
    assertThat(decoded.getProperty("bytes") as ByteArray).isEqualTo(byteArrayOf(0x01, 0x02, 0x03))
  }

  private val strFoo =
    byteArrayOf(0xA3.toByte(), 'f'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte())
  private val doubleZero =
    byteArrayOf(0xcb.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

  fun assertExceptionCauseMessageContains(bytes: ByteArray, msg: String) {
    val exc = assertThrows<RuntimeException> { PklBinaryDecoder.decode(bytes) }
    assertThat(exc.cause).hasMessageContaining(msg)
  }

  @Test
  fun `decoding of unsupported pkl types fails`() {
    assertExceptionCauseMessageContains(
      byteArrayOf(0x94.toByte(), PklBinaryCode.INTSEQ.code, 0x00, 0x04, 0x02),
      "Cannot decode IntSeq value",
    )
    assertExceptionCauseMessageContains(
      byteArrayOf(0x93.toByte(), PklBinaryCode.CLASS.code) + strFoo + strFoo,
      "Cannot decode Class value",
    )
    assertExceptionCauseMessageContains(
      byteArrayOf(0x93.toByte(), PklBinaryCode.TYPEALIAS.code) + strFoo + strFoo,
      "Cannot decode TypeAlias value",
    )
    assertExceptionCauseMessageContains(
      byteArrayOf(0x91.toByte(), PklBinaryCode.FUNCTION.code),
      "Cannot decode Function value",
    )
  }

  @Test
  fun `decode unexpected eof`() {
    // missing List element
    assertExceptionCauseMessageContains(
      byteArrayOf(0x92.toByte(), PklBinaryCode.LIST.code, 0x91.toByte()),
      "Unexpected EOF",
    )
    // missing struct slot
    assertExceptionCauseMessageContains(
      byteArrayOf(0x92.toByte(), PklBinaryCode.LIST.code),
      "Unexpected EOF",
    )
  }

  @Test
  fun `decode unexpected msgpack values`() {
    assertExceptionCauseMessageContains(
      byteArrayOf(0xc4.toByte(), 0x00),
      "Unexpected msgpack bin value",
    )
    assertExceptionCauseMessageContains(byteArrayOf(0x80.toByte()), "Unexpected msgpack map value")
    assertExceptionCauseMessageContains(
      byteArrayOf(0xd4.toByte(), 0x00, 0x00),
      "Unexpected msgpack ext value",
    )
  }

  @Test
  fun `decode invalid non-primitives`() {
    // empty struct array
    assertExceptionCauseMessageContains(
      byteArrayOf(0x90.toByte()),
      "Unexpected empty object array value",
    )
    // unrecognized code
    assertExceptionCauseMessageContains(
      byteArrayOf(0x93.toByte(), 0xff.toByte()),
      "Unrecognized code 0xff",
    )
    // unrecognized object code (member code)
    assertExceptionCauseMessageContains(
      byteArrayOf(0x93.toByte(), PklBinaryCode.ELEMENT.code, 0x00, 0x00),
      "Unrecognized object code ELEMENT",
    )
  }

  @Test
  fun `decode invalid object`() {
    // too few slots
    assertExceptionCauseMessageContains(
      byteArrayOf(0x91.toByte(), PklBinaryCode.OBJECT.code),
      "Expected OBJECT structure to have at least 4 slots, found 1",
    )
    // blank class
    assertExceptionCauseMessageContains(
      byteArrayOf(0x94.toByte(), PklBinaryCode.OBJECT.code, 0xa0.toByte()) + strFoo,
      "Unexpected blank object class name",
    )
    // blank module uri
    assertExceptionCauseMessageContains(
      byteArrayOf(0x94.toByte(), PklBinaryCode.OBJECT.code) + strFoo + byteArrayOf(0xa0.toByte()),
      "Unexpected blank object module URI",
    )
    // unexpected member struct length
    assertExceptionCauseMessageContains(
      byteArrayOf(0x94.toByte(), PklBinaryCode.OBJECT.code) +
        strFoo +
        strFoo +
        byteArrayOf(0x91.toByte(), 0x90.toByte()),
      "Expected 3 fields in object member, found 0",
    )
    // unrecognized code in a member
    assertExceptionCauseMessageContains(
      byteArrayOf(0x94.toByte(), PklBinaryCode.OBJECT.code) +
        strFoo +
        strFoo +
        byteArrayOf(0x91.toByte(), 0x93.toByte(), 0xff.toByte()),
      "Unrecognized code 0xff",
    )
    // unrecognized member code (object code)
    assertExceptionCauseMessageContains(
      byteArrayOf(0x94.toByte(), PklBinaryCode.OBJECT.code) +
        strFoo +
        strFoo +
        byteArrayOf(0x91.toByte(), 0x93.toByte(), PklBinaryCode.OBJECT.code),
      "Unrecognized member code OBJECT",
    )
  }

  @Test
  fun `decode invalid units`() {
    assertExceptionCauseMessageContains(
      byteArrayOf(0x93.toByte(), PklBinaryCode.DURATION.code) + doubleZero + strFoo,
      "Invalid Duration unit `foo`",
    )
    assertExceptionCauseMessageContains(
      byteArrayOf(0x93.toByte(), PklBinaryCode.DATASIZE.code) + doubleZero + strFoo,
      "Invalid DataSize unit `foo`",
    )
  }

  @Test
  fun `decode invalid types`() {
    assertExceptionCauseMessageContains(
      byteArrayOf(0x93.toByte(), PklBinaryCode.CLASS.code, 0xa0.toByte()) + strFoo,
      "Unexpected blank class name",
    )
    assertExceptionCauseMessageContains(
      byteArrayOf(0x93.toByte(), PklBinaryCode.CLASS.code) + strFoo + byteArrayOf(0xa0.toByte()),
      "Unexpected blank class module URI",
    )
    assertExceptionCauseMessageContains(
      byteArrayOf(0x93.toByte(), PklBinaryCode.TYPEALIAS.code, 0xa0.toByte()) + strFoo,
      "Unexpected blank typealias name",
    )
    assertExceptionCauseMessageContains(
      byteArrayOf(0x93.toByte(), PklBinaryCode.TYPEALIAS.code) +
        strFoo +
        byteArrayOf(0xa0.toByte()),
      "Unexpected blank typealias module URI",
    )
  }
}
