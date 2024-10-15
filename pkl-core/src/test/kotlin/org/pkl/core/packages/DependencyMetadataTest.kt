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
package org.pkl.core.packages

import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.PClassInfo
import org.pkl.core.PObject
import org.pkl.core.Version

class DependencyMetadataTest {
  private val dependencyMetadata =
    DependencyMetadata(
      "my-proj-name",
      PackageUri("package://example.com/my-proj-name@0.10.0"),
      Version.parse("0.10.0"),
      URI("https://example.com/foo/bar@0.5.3.zip"),
      Checksums("abc123"),
      mapOf(
        "foo" to
          Dependency.RemoteDependency(
            PackageUri("package://example.com/foo@0.5.3"),
            Checksums("abc123")
          ),
      ),
      "https://example.com/my/source/0.5.3/blob%{path}#L%{line}-L%{endLine}",
      URI("https://example.com/my/source"),
      URI("https://example.com/my/docs"),
      "MIT",
      "The MIT License, you know it",
      listOf("birdy@bird.com"),
      URI("https://example.com/issues"),
      "Some package description",
      listOf(
        PObject(PClassInfo.Unlisted, mapOf()),
        PObject(PClassInfo.Deprecated, mapOf("since" to "0.26.1", "message" to "don't use"))
      ),
    )

  private val dependencyMetadataStr =
    """
     {
       "name": "my-proj-name",
       "packageUri": "package://example.com/my-proj-name@0.10.0",
       "version": "0.10.0",
       "packageZipUrl": "https://example.com/foo/bar@0.5.3.zip",
       "packageZipChecksums": {
         "sha256": "abc123"
       },
       "dependencies": {
         "foo": {
           "uri": "package://example.com/foo@0.5.3",
           "checksums": {
             "sha256": "abc123"
           }
         }
       },
       "sourceCodeUrlScheme": "https://example.com/my/source/0.5.3/blob%{path}#L%{line}-L%{endLine}",
       "sourceCode": "https://example.com/my/source",
       "documentation": "https://example.com/my/docs",
       "license": "MIT",
       "licenseText": "The MIT License, you know it",
       "authors": [
         "birdy@bird.com"
       ],
       "issueTracker": "https://example.com/issues",
       "description": "Some package description",
       "annotations": [
         {
           "moduleName": "pkl.base",
           "class": "Unlisted",
           "moduleUri": "pkl:base",
           "properties": {}
         },
         {
           "moduleName": "pkl.base",
           "class": "Deprecated",
           "moduleUri": "pkl:base",
           "properties": {
             "since": "0.26.1",
             "message": "don't use"
           }
         }
       ]
     }
    """
      .trimIndent()

  @Test
  fun parse() {
    val parsed = DependencyMetadata.parse(dependencyMetadataStr)
    assertThat(parsed).isEqualTo(dependencyMetadata)
  }

  @Test
  fun writeTo() {
    val str =
      ByteArrayOutputStream()
        .apply { dependencyMetadata.writeTo(this) }
        .toString(StandardCharsets.UTF_8)
    assertThat(str).isEqualTo(dependencyMetadataStr)
  }
}
