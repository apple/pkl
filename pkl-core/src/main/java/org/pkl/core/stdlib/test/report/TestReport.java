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

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.pkl.core.PklBugException;
import org.pkl.core.runtime.TestResults;
import org.pkl.core.util.StringBuilderWriter;

public interface TestReport {

  void report(TestResults results, Writer writer) throws IOException;

  default String report(TestResults results) {
    try {
      var builder = new StringBuilder();
      var writer = new StringBuilderWriter(builder);
      report(results, writer);
      return builder.toString();
    } catch (IOException e) {
      throw new PklBugException("Unexpected IO exception.", e);
    }
  }

  default void reportToPath(TestResults results, Path path) throws IOException {
    try (var writer = new FileWriter(path.toFile(), StandardCharsets.UTF_8)) {
      report(results, writer);
    }
  }
}
