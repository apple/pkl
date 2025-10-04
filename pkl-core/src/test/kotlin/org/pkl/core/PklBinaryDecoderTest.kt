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
    assertThat((decoded.getProperty("regex") as Pattern).toString()).isEqualTo("foo.*")
    assertThat(decoded.getProperty("bytes") as ByteArray).isEqualTo(byteArrayOf(0x01, 0x02, 0x03))
  }
}
