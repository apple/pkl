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

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.pkl.doc.CliDocGenerator;
import org.pkl.doc.CliDocGeneratorOptions;
import org.pkl.doc.DocGenerator.CurrentDirectoryMode;

public abstract class PkldocTask extends ModulesTask {
  @OutputDirectory
  public abstract DirectoryProperty getOutputDir();

  @Input
  public abstract Property<CurrentDirectoryMode> getCurrentDirectoryMode();

  @Override
  protected void doRunTask() {
    new CliDocGenerator(
            new CliDocGeneratorOptions(
                getCliBaseOptions(),
                getOutputDir().get().getAsFile().toPath(),
                false,
                getCurrentDirectoryMode().get()))
        .run();
  }
}
