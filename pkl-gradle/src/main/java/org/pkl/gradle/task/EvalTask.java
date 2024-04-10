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
import java.util.Collections;
import java.util.Set;
import javax.annotation.Nullable;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.OutputFiles;
import org.pkl.cli.CliEvaluator;
import org.pkl.cli.CliEvaluatorOptions;

public abstract class EvalTask extends ModulesTask {

  // not tracked because it might contain placeholders
  // required
  @Internal
  public abstract RegularFileProperty getOutputFile();

  // not tracked because it might contain placeholders
  // optional
  @Internal
  public abstract DirectoryProperty getMultipleFileOutputDir();

  @Input
  public abstract Property<String> getOutputFormat();

  @Input
  public abstract Property<String> getModuleOutputSeparator();

  @Input
  public abstract Property<String> getExpression();

  private final Provider<CliEvaluator> cliEvaluator =
      getProviders()
          .provider(
              () ->
                  new CliEvaluator(
                      new CliEvaluatorOptions(
                          getCliBaseOptions(),
                          getOutputFile().get().getAsFile().getAbsolutePath(),
                          getOutputFormat().get(),
                          getModuleOutputSeparator().get(),
                          mapAndGetOrNull(
                              getMultipleFileOutputDir(), it -> it.getAsFile().getAbsolutePath()),
                          getExpression().get())));

  @OutputFiles
  @Optional
  public FileCollection getEffectiveOutputFilePaths() {
    return getObjects()
        .fileCollection()
        .from(cliEvaluator.map(e -> nullToEmpty(e.getOutputFiles())));
  }

  @OutputDirectories
  @Optional
  public FileCollection getOutputDirectories() {
    return getObjects()
        .fileCollection()
        .from(cliEvaluator.map(e -> nullToEmpty(e.getOutputDirectories())));
  }

  private static <T> Set<T> nullToEmpty(@Nullable Set<T> set) {
    return set == null ? Collections.emptySet() : set;
  }

  @Override
  protected void doRunTask() {
    //noinspection ResultOfMethodCallIgnored
    getOutputs().getPreviousOutputFiles().forEach(File::delete);
    cliEvaluator.get().run();
  }
}
