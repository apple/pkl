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
package org.pkl.gradle.task;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.UntrackedTask;
import org.pkl.cli.CliProjectPackager;
import org.pkl.commons.cli.CliTestOptions;

@UntrackedTask(because = "Output names are known only after execution")
public abstract class ProjectPackageTask extends BasePklTask {
  @InputFiles
  public abstract ConfigurableFileCollection getProjectDirectories();

  @Internal
  public abstract DirectoryProperty getOutputPath();

  @Optional
  @OutputDirectory
  public abstract DirectoryProperty getJunitReportsDir();

  @Input
  public abstract Property<Boolean> getOverwrite();

  @Input
  @Optional
  public abstract Property<Boolean> getSkipPublishCheck();

  @Override
  protected void doRunTask() {
    var projectDirectories =
        getProjectDirectories().getFiles().stream()
            .map(it -> Path.of(it.getAbsolutePath()))
            .collect(Collectors.toList());
    if (projectDirectories.isEmpty()) {
      throw new InvalidUserDataException("No project directories specified.");
    }
    new CliProjectPackager(
            getCliBaseOptions(),
            projectDirectories,
            new CliTestOptions(
                mapAndGetOrNull(getJunitReportsDir(), it -> it.getAsFile().toPath()),
                getOverwrite().get()),
            getOutputPath().get().getAsFile().getAbsolutePath(),
            getSkipPublishCheck().getOrElse(false),
            new PrintWriter(System.out),
            new PrintWriter(System.err))
        .run();
  }
}
