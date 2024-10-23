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
import java.util.stream.Collectors;
import org.pkl.core.runtime.TestResults;
import org.pkl.core.runtime.TestResults.Failure;
import org.pkl.core.runtime.TestResults.TestResult;
import org.pkl.core.util.StringUtils;

public final class SimpleReport implements TestReport {

  @Override
  public void report(TestResults results, Writer writer) throws IOException {
    var builder = new StringBuilder();
    builder.append("module ");
    builder.append(results.getModuleName());
    builder.append(" (").append(results.getDisplayUri()).append(")\n");
    StringUtils.joinToStringBuilder(
        builder, results.getResults(), "\n", res -> reportResult(res, builder));
    builder.append("\n");
    writer.append(builder);
  }

  private void reportResult(TestResult result, StringBuilder builder) {
    builder.append("  ").append(result.getName());
    if (result.isExampleWritten()) {
      builder.append(" ✍️");
    } else if (result.isSuccess()) {
      builder.append(" ✅");
    } else {
      builder.append(" ❌\n");
      StringUtils.joinToStringBuilder(
          builder, result.getFailures(), "\n", failure -> reportFailure(failure, builder));
      StringUtils.joinToStringBuilder(
          builder,
          result.getErrors(),
          "\n",
          error -> {
            builder.append("    Error:\n");
            appendPadded(builder, error.getException().getMessage(), "        ");
          });
    }
  }

  public static void reportFailure(Failure failure, StringBuilder builder) {
    appendPadded(builder, failure.getRendered(), "    ");
  }

  private static void appendPadded(StringBuilder builder, String lines, String padding) {
    StringUtils.joinToStringBuilder(
        builder,
        lines.lines().collect(Collectors.toList()),
        "\n",
        str -> builder.append(padding).append(str));
  }
}
