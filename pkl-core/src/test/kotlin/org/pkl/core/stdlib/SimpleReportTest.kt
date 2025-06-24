/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.io.StringWriter
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.TestResults
import org.pkl.core.TestResults.TestResult
import org.pkl.core.TestResults.TestSectionResults
import org.pkl.core.stdlib.test.report.SimpleReport

class SimpleReportTest {

  @Test
  fun `summarize method should generate correct output`() {
    var resultsBuilder = TestResults.Builder("module1", "module1")
    resultsBuilder.setFactsSection(
      TestSectionResults(
        TestResults.TestSectionName.FACTS,
        listOf(
          TestResult(
            "example1",
            321919,
            listOf(TestResults.Failure("Fact Failure", "failed")),
            emptyList(),
            false,
          )
        ),
      )
    )
    resultsBuilder.setExamplesSection(
      TestSectionResults(
        TestResults.TestSectionName.EXAMPLES,
        listOf(
          TestResult(
            "example1",
            432525,
            listOf(TestResults.Failure("Output Mismatch", "does not match")),
            emptyList(),
            false,
          )
        ),
      )
    )
    val testResults = listOf(resultsBuilder.build())

    val writer = StringWriter()
    val simpleReport = SimpleReport(false)
    simpleReport.summarize(testResults, writer)

    val expectedOutput =
      """
            0.0% tests pass [2/2 failed], 99.9% asserts pass [2/754444 failed]
            """
        .trimIndent()

    assertThat(writer.toString().trimIndent()).isEqualTo(expectedOutput)
  }
}
