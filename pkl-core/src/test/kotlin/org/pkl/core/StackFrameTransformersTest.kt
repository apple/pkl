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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.pkl.commons.test.PackageServer
import org.pkl.core.http.HttpClient

class StackFrameTransformersTest {
  // TODO figure out how to test this; right now this fails because there is no VM context.
  @Test
  @Disabled
  fun replacePackageUriWithSourceCodeUrl() {
    PackageServer().use { server ->
      val httpClient = HttpClient.builder().setTestPort(server.port).build()
      EvaluatorBuilder.preconfigured().setHttpClient(httpClient).build().use {
        val frame =
          StackFrame("package://localhost:0/birds@0.5.0#/Bird.pkl", null, listOf(), 1, 1, 2, 2)
        val transformed = StackFrameTransformers.replacePackageUriWithSourceCodeUrl.apply(frame)
        assertThat(transformed.moduleUri)
          .isEqualTo("https://example.com/birds/v0.5.0/blob/Bird.pkl#L1-L2")
      }
    }
  }
}
