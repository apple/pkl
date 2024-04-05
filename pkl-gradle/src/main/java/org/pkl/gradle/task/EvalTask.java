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
package org.pkl.gradle.task;

import java.io.File;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.UntrackedTask;
import org.pkl.cli.CliEvaluator;
import org.pkl.cli.CliEvaluatorOptions;

@UntrackedTask(
    because =
        "Output file names accept placeholder values, and actual file names and directories can't be known ahead of time")
public abstract class EvalTask extends ModulesTask {
  @Internal
  public abstract RegularFileProperty getOutputFile();

  @Internal
  public abstract Property<String> getOutputFormat();

  @Internal
  public abstract Property<String> getModuleOutputSeparator();

  @Internal
  public abstract DirectoryProperty getMultipleFileOutputDir();

  @Internal
  public abstract Property<String> getExpression();

  @Override
  protected void doRunTask() {
    //noinspection ResultOfMethodCallIgnored
    getOutputs().getPreviousOutputFiles().forEach(File::delete);

    new CliEvaluator(
            new CliEvaluatorOptions(
                getCliBaseOptions(),
                getOutputFile().get().getAsFile().getAbsolutePath(),
                getOutputFormat().get(),
                getModuleOutputSeparator().get(),
                mapAndGetOrNull(getMultipleFileOutputDir(), it -> it.getAsFile().getAbsolutePath()),
                getExpression().get()))
        .run();
  }
}
