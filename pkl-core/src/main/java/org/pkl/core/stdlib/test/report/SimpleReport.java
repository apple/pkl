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
package org.pkl.core.stdlib.test.report;

import java.io.IOException;
import java.io.Writer;
import java.util.stream.Collectors;
import org.pkl.core.runtime.TestResults;
import org.pkl.core.runtime.TestResults.TestSectionResults;
import org.pkl.core.runtime.TestResults.TestSectionResults.TestResult;
import org.pkl.core.util.StringUtils;

public final class SimpleReport implements TestReport {

  @Override
  public void report(TestResults results, Writer writer) throws IOException {
    var builder = new StringBuilder();

    builder.append("module ").append(results.moduleName).append("\n");

    reportResults(results.facts, builder);
    reportResults(results.examples, builder);

    builder.append(results.failed() ? "❌ " : "✅ ");

    var totalStatsLine =
        makeStatsLine("tests", results.totalTests(), results.totalFailures(), results.failed());
    builder.append(totalStatsLine);

    var totalAssertsStatsLine =
        makeStatsLine(
            "asserts", results.totalAsserts(), results.totalAssertsFailed(), results.failed());
    builder.append(", ").append(totalAssertsStatsLine);

    builder.append("\n\n");

    writer.append(builder);
  }

  private void reportResults(TestSectionResults section, StringBuilder builder) {
    if (!section.getResults().isEmpty()) {
      builder.append("  ").append(section.name).append("\n");

      StringUtils.joinToStringBuilder(
          builder, section.getResults(), "\n", res -> reportResult(res, builder));
      builder.append("\n");
    } else if (section.hasError()) {
      builder.append("  ").append(section.name).append("\n");
      var error = "Error:\n" + section.getError().getRendered();
      appendPadded(builder, error, "    ");
      builder.append("\n");
    }
  }

  private void reportResult(TestResult result, StringBuilder builder) {
    builder.append("    ");

    if (result.isExampleWritten()) {
      builder.append(result.name).append(" ✍️");
    } else {
      builder.append(result.isFailure() ? "❌ " : "✅ ").append(result.name);

      if (!result.isExample) {
        var statsLine =
            makeStatsLine(
                "asserts", result.totalAsserts(), result.getFailures().size(), result.isFailure());
      }

      if (result.isFailure()) {
        builder.append("\n");
        StringUtils.joinToStringBuilder(
            builder,
            result.getFailures(),
            "\n",
            failure -> appendPadded(builder, failure.getRendered(), "       "));
      }
    }
  }

  private static void appendPadded(StringBuilder builder, String lines, String padding) {
    StringUtils.joinToStringBuilder(
        builder,
        lines.lines().collect(Collectors.toList()),
        "\n",
        str -> builder.append(padding).append(str));
  }

  private String makeStatsLine(String kind, int total, int failed, boolean isFailed) {
    var passed = total - failed;
    var pct_passed = total > 0 ? 100.0 * passed / total : 0.0;

    String line = String.format("%.1f%% %s pass", pct_passed, kind);

    if (isFailed) {
      line += String.format(" [%d/%d failed]", failed, total);
    } else {
      line += String.format(" [%d passed]", passed);
    }

    return line;
  }
}
