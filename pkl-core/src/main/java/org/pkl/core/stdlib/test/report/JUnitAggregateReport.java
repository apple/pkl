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
package org.pkl.core.stdlib.test.report;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.pkl.core.PklBugException;
import org.pkl.core.TestResults;
import org.pkl.core.runtime.VmDynamic;

public final class JUnitAggregateReport extends TestReport {

  @Override
  public void report(TestResults results, Writer writer) throws IOException {
    throw new PklBugException("One file report not supported for aggregate reporter.", new IOException());
  }

  public void report(String name, List<TestResults> results, Writer writer) throws IOException {
    var reporter = new JUnitReport();

    var totalTests = results.stream().collect(Collectors.summingLong(r -> r.totalTests()));
    var totalFailures = results.stream().collect(Collectors.summingLong(r -> r.totalFailures()));

    var attrs =
        reporter.buildAttributes(
            "name", name,
            "tests", totalTests,
            "failures", totalFailures);

    var tests = results.stream().map(r -> reporter.buildSuite(r)).collect(Collectors.toCollection(ArrayList::new));

    var suite =
        reporter.buildXmlElement("testsuites", attrs, tests.toArray(new VmDynamic[0]));

    writer.append(JUnitReport.renderXML("    ", "1.0", suite));
  }

  public void reportToPath(String name, List<TestResults> results, Path path) throws IOException {
    try (var writer = new FileWriter(path.toFile(), StandardCharsets.UTF_8)) {
      report(name, results, writer);
    }
  }
}
