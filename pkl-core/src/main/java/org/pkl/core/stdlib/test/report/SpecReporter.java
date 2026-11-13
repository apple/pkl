/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import org.pkl.core.TestResults;
import org.pkl.core.TestResults.TestSectionResults;
import org.pkl.core.util.AnsiStringBuilder;
import org.pkl.core.util.StringUtils;

public final class SpecReporter extends BaseReporter {

  public SpecReporter(boolean useColor) {
    super(useColor);
  }

  @Override
  public void report(TestResults results, Writer writer) throws IOException {
    var builder = new AnsiStringBuilder(useColor);

    builder.append("module ").append(results.moduleName()).append('\n');

    if (results.error() != null) {
      var rendered = results.error().exception().getMessage();
      appendPadded(builder, rendered, "  ");
      builder.append('\n');
    } else {
      reportResults(results.facts(), builder);
      reportResults(results.examples(), builder);
    }

    writer.append(builder.toString());
  }

  private void reportResults(TestSectionResults section, AnsiStringBuilder builder) {
    if (!section.results().isEmpty()) {
      builder.append("  ").append(section.name()).append('\n');
      StringUtils.joinToStringBuilder(
          builder, section.results(), "\n", res -> reportResult(res, builder));
      builder.append('\n');
    }
  }
}
