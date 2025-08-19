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

import java.io.File;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.pkl.codegen.java.CliJavaCodeGenerator;
import org.pkl.codegen.java.CliJavaCodeGeneratorOptions;

public abstract class JavaCodeGenTask extends CodeGenTask {
  @Input
  public abstract Property<Boolean> getGenerateGetters();

  @Input
  public abstract Property<Boolean> getGenerateJavadoc();

  @Input
  @Optional
  public abstract Property<String> getParamsAnnotation();

  @Input
  @Optional
  public abstract Property<String> getNonNullAnnotation();

  @Override
  protected void doRunTask() {
    //noinspection ResultOfMethodCallIgnored
    getOutputs().getPreviousOutputFiles().forEach(File::delete);

    new CliJavaCodeGenerator(
            new CliJavaCodeGeneratorOptions(
                getCliBaseOptions(),
                getProject().file(getOutputDir()).toPath(),
                getIndent().get(),
                getAddGeneratedAnnotation().get(),
                getGenerateGetters().get(),
                getGenerateJavadoc().get(),
                getGenerateSpringBootConfig().get(),
                getParamsAnnotation().getOrNull(),
                getNonNullAnnotation().getOrNull(),
                getImplementSerializable().get(),
                getRenames().get()))
        .run();
  }
}
