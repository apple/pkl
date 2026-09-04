/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.stdlib.url

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class PunycodeTest {
  companion object {
    /** RFC 3492 §7.1's sample strings, as (name, code points, Punycode) triples. */
    @JvmStatic
    fun samples(): List<Arguments> =
      listOf(
        Arguments.of(
          "(A) Arabic (Egyptian)",
          "0644 064A 0647 0645 0627 0628 062A 0643 0644 0645 0648 0634 0639 0631 0628 064A 061F",
          "egbpdaj6bu4bxfgehfvwxn",
        ),
        Arguments.of(
          "(B) Chinese (simplified)",
          "4ED6 4EEC 4E3A 4EC0 4E48 4E0D 8BF4 4E2D 6587",
          "ihqwcrb4cv8a8dqg056pqjye",
        ),
        Arguments.of(
          "(C) Chinese (traditional)",
          "4ED6 5011 7232 4EC0 9EBD 4E0D 8AAA 4E2D 6587",
          "ihqwctvzc91f659drss3x8bo0yb",
        ),
        Arguments.of(
          "(D) Czech",
          "0050 0072 006F 010D 0070 0072 006F 0073 0074 011B 006E 0065 006D 006C 0075 0076 00ED " +
            "010D 0065 0073 006B 0079",
          "Proprostnemluvesky-uyb24dma41a",
        ),
        Arguments.of(
          "(E) Hebrew",
          "05DC 05DE 05D4 05D4 05DD 05E4 05E9 05D5 05D8 05DC 05D0 05DE 05D3 05D1 05E8 05D9 05DD " +
            "05E2 05D1 05E8 05D9 05EA",
          "4dbcagdahymbxekheh6e0a7fei0b",
        ),
        Arguments.of(
          "(F) Hindi (Devanagari)",
          "092F 0939 0932 094B 0917 0939 093F 0928 094D 0926 0940 0915 094D 092F 094B 0902 0928 " +
            "0939 0940 0902 092C 094B 0932 0938 0915 0924 0947 0939 0948 0902",
          "i1baa7eci9glrd9b2ae1bj0hfcgg6iyaf8o0a1dig0cd",
        ),
        Arguments.of(
          "(G) Japanese (kanji and hiragana)",
          "306A 305C 307F 3093 306A 65E5 672C 8A9E 3092 8A71 3057 3066 304F 308C 306A 3044 306E " +
            "304B",
          "n8jok5ay5dzabd5bym9f0cm5685rrjetr6pdxa",
        ),
        Arguments.of(
          "(H) Korean (Hangul syllables)",
          "C138 ACC4 C758 BAA8 B4E0 C0AC B78C B4E4 C774 D55C AD6D C5B4 B97C C774 D574 D55C B2E4 " +
            "BA74 C5BC B9C8 B098 C88B C744 AE4C",
          "989aomsvi5e83db1d2a355cv1e0vak1dwrv93d5xbh15a0dt30a5jpsd879ccm6fea98c",
        ),
        // §7.1 prints this one as `b1abfaaepdrnnbgefbaDotcwatmq2g4l`: the capital `D` is the
        // optional mixed-case annotation, which an encoder is not required to produce and which
        // `decode` ignores. See `decode ignores the mixed-case annotation`.
        Arguments.of(
          "(I) Russian (Cyrillic)",
          "043F 043E 0447 0435 043C 0443 0436 0435 043E 043D 0438 043D 0435 0433 043E 0432 043E " +
            "0440 044F 0442 043F 043E 0440 0443 0441 0441 043A 0438",
          "b1abfaaepdrnnbgefbadotcwatmq2g4l",
        ),
        Arguments.of(
          "(J) Spanish",
          "0050 006F 0072 0071 0075 00E9 006E 006F 0070 0075 0065 0064 0065 006E 0073 0069 006D " +
            "0070 006C 0065 006D 0065 006E 0074 0065 0068 0061 0062 006C 0061 0072 0065 006E 0045 " +
            "0073 0070 0061 00F1 006F 006C",
          "PorqunopuedensimplementehablarenEspaol-fmd56a",
        ),
        Arguments.of(
          "(K) Vietnamese",
          "0054 1EA1 0069 0073 0061 006F 0068 1ECD 006B 0068 00F4 006E 0067 0074 0068 1EC3 0063 " +
            "0068 1EC9 006E 00F3 0069 0074 0069 1EBF 006E 0067 0056 0069 1EC7 0074",
          "TisaohkhngthchnitingVit-kjcr8268qyxafd2f1b9g",
        ),
        Arguments.of(
          "(L) 3<nen>B<gumi><kinpachi><sensei>",
          "0033 5E74 0042 7D44 91D1 516B 5148 751F",
          "3B-ww4c5e180e575a65lsy2b",
        ),
        Arguments.of(
          "(M) <amuro><namie>-with-SUPER-MONKEYS",
          "5B89 5BA4 5948 7F8E 6075 002D 0077 0069 0074 0068 002D 0053 0055 0050 0045 0052 002D " +
            "004D 004F 004E 004B 0045 0059 0053",
          "-with-SUPER-MONKEYS-pc58ag80a8qai00g7n9n",
        ),
        Arguments.of(
          "(N) Hello-Another-Way-<sorezore><no><basho>",
          "0048 0065 006C 006C 006F 002D 0041 006E 006F 0074 0068 0065 0072 002D 0057 0061 0079 " +
            "002D 305D 308C 305E 308C 306E 5834 6240",
          "Hello-Another-Way--fc4qua05auwb3674vfr0b",
        ),
        Arguments.of(
          "(O) <hitotsu><yane><no><shita>2",
          "3072 3068 3064 5C4B 6839 306E 4E0B 0032",
          "2-u9tlzr9756bt3uc0v",
        ),
        Arguments.of(
          "(P) Maji<de>Koi<suru>5<byou><mae>",
          "004D 0061 006A 0069 3067 004B 006F 0069 3059 308B 0035 79D2 524D",
          "MajiKoi5-783gue6qz075azm5e",
        ),
        Arguments.of(
          "(Q) <pafii>de<runba>",
          "30D1 30D5 30A3 30FC 0064 0065 30EB 30F3 30D0",
          "de-jg4avhby1noc0d",
        ),
        Arguments.of(
          "(R) <sono><supiido><de>",
          "305D 306E 30B9 30D4 30FC 30C9 3067",
          "d9juau41awczczp",
        ),
        Arguments.of(
          "(S) -> \$1.00 <-",
          "002D 003E 0020 0024 0031 002E 0030 0030 0020 003C 002D",
          "-> \$1.00 <--",
        ),
      )

    private fun codePoints(spec: String): String {
      val result = StringBuilder()
      spec.split(" ").forEach { result.appendCodePoint(it.toInt(16)) }
      return result.toString()
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("samples")
  fun `encodes RFC 3492's sample strings`(name: String, spec: String, encoded: String) {
    assertThat(Punycode.encode(codePoints(spec))).isEqualTo(encoded)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("samples")
  fun `decodes RFC 3492's sample strings`(name: String, spec: String, encoded: String) {
    assertThat(Punycode.decode(encoded)).isEqualTo(codePoints(spec))
  }

  @ParameterizedTest
  @ValueSource(strings = ["münchen", "faß", "你好你好", "☃", "é", "ドメイン名例", "abc", "𝐀𝐋", "aé1你-b"])
  fun `round-trips`(input: String) {
    val encoded = Punycode.encode(input)
    assertThat(encoded).isNotNull()
    assertThat(Punycode.decode(encoded!!)).isEqualTo(input)
  }

  @Test
  fun `encodes the labels IDNA needs`() {
    // the `xn--` prefix is the caller's business, so these are the ACE labels minus their prefix
    assertThat(Punycode.encode("münchen")).isEqualTo("mnchen-3ya")
    assertThat(Punycode.encode("faß")).isEqualTo("fa-hia")
    assertThat(Punycode.encode("你好你好")).isEqualTo("6qqa088eba")
    assertThat(Punycode.encode("☃")).isEqualTo("n3h")
    assertThat(Punycode.encode("é")).isEqualTo("9ca")
  }

  @Test
  fun `appends the delimiter only when there are basic code points`() {
    assertThat(Punycode.encode("münchen")).startsWith("mnchen-")
    assertThat(Punycode.encode("你好你好")).doesNotContain("-")
    assertThat(Punycode.encode("")).isEqualTo("")
  }

  @Test
  fun `decode is case-insensitive`() {
    val expected =
      codePoints(
        "043F 043E 0447 0435 043C 0443 0436 0435 043E 043D 0438 043D 0435 0433 043E 0432 043E " +
          "0440 044F 0442 043F 043E 0440 0443 0441 0441 043A 0438"
      )
    assertThat(Punycode.decode("b1abfaaepdrnnbgefbadotcwatmq2g4l")).isEqualTo(expected)
    assertThat(Punycode.decode("B1ABFAAEPDRNNBGEFBADOTCWATMQ2G4L")).isEqualTo(expected)
    assertThat(Punycode.decode("b1ABfaaEPDrnnbgefBAdotcWATmq2g4l")).isEqualTo(expected)
  }

  @Test
  fun `decode rejects empty output`() {
    assertThat(Punycode.decode("")).isNull()
    assertThat(Punycode.decode("-")).isNull()
  }

  @Test
  fun `decode rejects a code point that is not a Punycode digit`() {
    assertThat(Punycode.decode("a!")).isNull()
    assertThat(Punycode.decode("mnchen-3y%")).isNull()
  }

  @Test
  fun `decode rejects a non-basic code point in the literal portion`() {
    assertThat(Punycode.decode("ü-a")).isNull()
  }

  @Test
  fun `decode rejects a truncated delta`() {
    assertThat(Punycode.decode("99")).isNull()
    assertThat(Punycode.decode("9".repeat(20))).isNull()
  }

  @Test
  fun `decode rejects a code point beyond the Unicode range`() {
    assertThat(Punycode.decode("zz99z")).isNull()
  }

  @Test
  fun `decode rejects arithmetic overflow`() {
    assertThat(Punycode.decode("zz99999z")).isNull()
  }

  @Test
  fun `decode rejects a surrogate code point`() {
    assertThat(Punycode.decode("ib9b")).isNull()
  }
}
