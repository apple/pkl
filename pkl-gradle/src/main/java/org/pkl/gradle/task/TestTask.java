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
package org.pkl.gradle.task;

import java.io.PrintWriter;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.pkl.cli.CliTestRunner;
import org.pkl.commons.cli.CliTestOptions;

public abstract class TestTask extends ModulesTask {
  @Optional
  @OutputDirectory
  public abstract DirectoryProperty getJunitReportsDir();

  @Input
  @Optional
  public abstract Property<Boolean> getJunitAggregateReports();

  @Input
  public abstract Property<String> getJunitAggregateSuiteName();

  @Input
  public abstract Property<Boolean> getOverwrite();

  public TestTask() {
    this.getJunitAggregateSuiteName().convention("pkl-tests");
  }

  @Override
  protected void doRunTask() {
    new CliTestRunner(
            getCliBaseOptions(),
            new CliTestOptions(
                mapAndGetOrNull(getJunitReportsDir(), it -> it.getAsFile().toPath()),
                getOverwrite().get(),
                getJunitAggregateReports().getOrElse(false),
                getJunitAggregateSuiteName().get()),
            new PrintWriter(System.out),
            new PrintWriter(System.err))
        .run();
  }
}
