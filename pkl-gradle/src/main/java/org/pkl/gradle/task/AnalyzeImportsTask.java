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

import java.io.File;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.pkl.cli.CliImportAnalyzer;
import org.pkl.cli.CliImportAnalyzerOptions;

public abstract class AnalyzeImportsTask extends ModulesTask {
  @OutputFile
  @Optional
  public abstract RegularFileProperty getOutputFile();

  @Input
  public abstract Property<String> getOutputFormat();

  private final Provider<CliImportAnalyzer> cliImportAnalyzerProvider =
      getProviders()
          .provider(
              () ->
                  new CliImportAnalyzer(
                      new CliImportAnalyzerOptions(
                          getCliBaseOptions(),
                          mapAndGetOrNull(getOutputFile(), it -> it.getAsFile().toPath()),
                          mapAndGetOrNull(getOutputFormat(), it -> it))));

  @Override
  protected void doRunTask() {
    //noinspection ResultOfMethodCallIgnored
    getOutputs().getPreviousOutputFiles().forEach(File::delete);
    cliImportAnalyzerProvider.get().run();
  }
}
