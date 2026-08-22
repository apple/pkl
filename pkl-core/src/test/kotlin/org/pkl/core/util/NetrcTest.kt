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
package org.pkl.core.util

import java.util.Base64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NetrcTest {

  @Test
  fun `parse netrc file content`() {
    val content =
      """
      # Comment line
      machine github.com
        login octocat
        password secret_token_123

      machine my-artifactory.internal.net
        login user
        password my_token

      default
        login defaultuser
        password defaultpass
      """
        .trimIndent()

    val entries = Netrc.parse(content)
    assertThat(entries)
      .containsExactly(
        Netrc.Entry("github.com", false, "octocat", "secret_token_123", null),
        Netrc.Entry("my-artifactory.internal.net", false, "user", "my_token", null),
        Netrc.Entry("default", true, "defaultuser", "defaultpass", null),
      )

    val headersMap = Netrc.toHeadersMap(entries)
    val basicGithub =
      "Basic " + Base64.getEncoder().encodeToString("octocat:secret_token_123".toByteArray())
    val basicArtifactory =
      "Basic " + Base64.getEncoder().encodeToString("user:my_token".toByteArray())
    val basicDefault =
      "Basic " + Base64.getEncoder().encodeToString("defaultuser:defaultpass".toByteArray())

    assertThat(headersMap["http{,s}://github.com/**"])
      .isEqualTo(mapOf("Authorization" to listOf(basicGithub)))
    assertThat(headersMap["http{,s}://my-artifactory.internal.net/**"])
      .isEqualTo(mapOf("Authorization" to listOf(basicArtifactory)))
    assertThat(headersMap["**"]).isEqualTo(mapOf("Authorization" to listOf(basicDefault)))
  }

  @Test
  fun `parse quotes and comments`() {
    val content =
      """
      # Header comment
      machine example.com login "user with spaces" password "pass#with#hash" # trailing comment
      machine "quoted.machine.com" login "quoteduser" password "token_with_quotes"
      """
        .trimIndent()

    val entries = Netrc.parse(content)
    assertThat(entries)
      .containsExactly(
        Netrc.Entry("example.com", false, "user with spaces", "pass#with#hash", null),
        Netrc.Entry("quoted.machine.com", false, "quoteduser", "token_with_quotes", null),
      )

    val headersMap = Netrc.toHeadersMap(entries)
    val expectedBasic =
      "Basic " + Base64.getEncoder().encodeToString("user with spaces:pass#with#hash".toByteArray())
    val expectedQuoted =
      "Basic " + Base64.getEncoder().encodeToString("quoteduser:token_with_quotes".toByteArray())
    assertThat(headersMap["http{,s}://example.com/**"])
      .isEqualTo(mapOf("Authorization" to listOf(expectedBasic)))
    assertThat(headersMap["http{,s}://quoted.machine.com/**"])
      .isEqualTo(mapOf("Authorization" to listOf(expectedQuoted)))
  }

  @Test
  fun `skip macdef sections`() {
    val content =
      """
      macdef init
        echo hello
        echo world

      machine foo.com login user1 password pass1
      """
        .trimIndent()

    val entries = Netrc.parse(content)
    assertThat(entries).containsExactly(Netrc.Entry("foo.com", false, "user1", "pass1", null))
  }

  @Test
  fun `ignore duplicate machine entries after first in headers map`() {
    val content =
      """
      machine foo.com login firstUser password firstPass
      machine foo.com login secondUser password secondPass
      """
        .trimIndent()

    val entries = Netrc.parse(content)
    val headersMap = Netrc.toHeadersMap(entries)
    val expectedBasic =
      "Basic " + Base64.getEncoder().encodeToString("firstUser:firstPass".toByteArray())
    assertThat(headersMap["http{,s}://foo.com/**"])
      .isEqualTo(mapOf("Authorization" to listOf(expectedBasic)))
  }

  @Test
  fun `handle empty or comment-only content`() {
    assertThat(Netrc.parse("")).isEmpty()
    assertThat(Netrc.parse("# only comments\n# second line")).isEmpty()
    assertThat(Netrc.toHeadersMap(emptyList())).isEmpty()
  }
}
