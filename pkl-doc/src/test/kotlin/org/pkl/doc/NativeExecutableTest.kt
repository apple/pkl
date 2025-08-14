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
package org.pkl.doc

import org.junit.jupiter.api.condition.DisabledIfSystemProperty
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.pkl.commons.test.Executables

// need both annotations for this to work (see https://stackoverflow.com/a/63252081)
@EnabledIfSystemProperty(named = "org.pkl.doc.NativeExecutableTest", matches = "true")
@DisabledIfSystemProperty(named = "org.pkl.doc.NativeExecutableTest", matches = "(?!true)")
class NativeExecutableTest {
  companion object {
    val helper = DocGeneratorTestHelper()

    @JvmStatic
    private fun generateDocs(): List<String> {
      return helper.generateDocsWithCli(Executables.pkldoc.firstExistingNative)
    }
  }

  @ParameterizedTest()
  @MethodSource("generateDocs")
  fun test(relativePath: String) {
    DocTestUtils.testExpectedFile(helper.expectedOutputDir, helper.actualOutputDir, relativePath)
  }
}
