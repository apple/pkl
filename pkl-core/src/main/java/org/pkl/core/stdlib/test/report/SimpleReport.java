/*
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
package org.pkl.core.stdlib.test.report;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.stream.Collectors;
import org.pkl.core.TestResults;
import org.pkl.core.TestResults.TestResult;
import org.pkl.core.TestResults.TestSectionResults;
import org.pkl.core.runtime.TextFormattingStringBuilder;
import org.pkl.core.runtime.TextFormattingStringBuilder.AnsiCode;
import org.pkl.core.util.ColorTheme;

public final class SimpleReport implements TestReport {

  private final boolean useColor;

  private static final String passingMark = "✔ ";

  private static final String failingMark = "✘ ";

  public SimpleReport(boolean useColor) {
    this.useColor = useColor;
  }

  @Override
  public void report(TestResults results, Writer writer) throws IOException {
    var builder = new TextFormattingStringBuilder(useColor);

    builder.append("module ").append(results.moduleName()).append("\n");

    if (results.error() != null) {
      var rendered = results.error().exception().getMessage();
      appendPadded(builder, rendered, "  ");
      builder.appendLine();
    } else {
      reportResults(results.facts(), builder);
      reportResults(results.examples(), builder);
    }

    writer.append(builder.toString());
  }

  public void summarize(List<TestResults> allTestResults, Writer writer) throws IOException {
    var totalTests = 0;
    var totalFailedTests = 0;
    var totalAsserts = 0;
    var totalFailedAsserts = 0;
    var isFailed = false;
    var isExampleWrittenFailure = true;
    for (var testResults : allTestResults) {
      if (!isFailed) {
        isFailed = testResults.failed();
      }
      if (testResults.failed()) {
        isExampleWrittenFailure = testResults.isExampleWrittenFailure() & isExampleWrittenFailure;
      }
      totalTests += testResults.totalTests();
      totalFailedTests += testResults.totalFailures();
      totalAsserts += testResults.totalAsserts();
      totalFailedAsserts += testResults.totalAssertsFailed();
    }
    var builder = new TextFormattingStringBuilder(useColor);
    if (isFailed && isExampleWrittenFailure) {
      builder.append(totalFailedTests).append(" examples written");
    } else {
      makeStatsLine(builder, "tests", totalTests, totalFailedTests, isFailed);
      builder.append(", ");
      makeStatsLine(builder, "asserts", totalAsserts, totalFailedAsserts, isFailed);
    }
    builder.appendLine();
    writer.append(builder.toString());
  }

  private void reportResults(TestSectionResults section, TextFormattingStringBuilder builder) {
    if (!section.results().isEmpty()) {
      builder.append("  ").append(section.name()).append("\n");
      builder.join(section.results(), "\n", res -> reportResult(res, builder));
      builder.append("\n");
    }
  }

  private void reportResult(TestResult result, TextFormattingStringBuilder builder) {
    builder.append("    ");

    if (result.isExampleWritten()) {
      builder.append("✍️ ").append(result.name());
    } else {
      if (result.isFailure()) {
        builder.append(ColorTheme.FAILING_TEST_MARK, failingMark);
      } else {
        builder.append(ColorTheme.PASSING_TEST_MARK, passingMark);
      }
      builder.append(ColorTheme.TEST_NAME, result.name());
      if (result.isFailure()) {
        var failurePadding = "       ";
        builder.append("\n");
        builder.join(
            result.failures(),
            "\n",
            failure -> appendPadded(builder, failure.message(), failurePadding));
        builder.join(
            result.errors(),
            "\n",
            error -> appendPadded(builder, error.exception().getMessage(), failurePadding));
      }
    }
  }

  private static void appendPadded(
      TextFormattingStringBuilder builder, String lines, String padding) {
    builder.join(
        lines.lines().collect(Collectors.toList()),
        "\n",
        str -> {
          if (!str.isEmpty()) builder.append(padding).append(str);
        });
  }

  private void makeStatsLine(
      TextFormattingStringBuilder sb, String kind, int total, int failed, boolean isFailed) {
    var passed = total - failed;
    var passRate = total > 0 ? 100.0 * passed / total : 0.0;

    var color = isFailed ? AnsiCode.RED : AnsiCode.GREEN;
    sb.append(
        color,
        () -> {
          sb.append(String.format("%.1f%%", passRate)).append(" ").append(kind).append(" pass");
        });

    if (isFailed) {
      sb.append(" [").append(failed).append('/').append(total).append(" failed]");
    } else {
      sb.append(" [").append(passed).append(" passed]");
    }
  }
}
