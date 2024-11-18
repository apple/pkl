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
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.pkl.commons.cli.CliBaseOptions;
import org.pkl.core.evaluatorSettings.Color;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;
import org.pkl.gradle.utils.PluginUtils;

public abstract class BasePklTask extends DefaultTask {
  @Input
  public abstract ListProperty<String> getAllowedModules();

  @Input
  public abstract ListProperty<String> getAllowedResources();

  @Input
  public abstract MapProperty<String, String> getEnvironmentVariables();

  @Input
  public abstract MapProperty<String, String> getExternalProperties();

  @InputFiles
  public abstract ConfigurableFileCollection getModulePath();

  @Internal
  public abstract Property<Object> getSettingsModule();

  @Internal
  public Provider<Object> getParsedSettingsModule() {
    return getSettingsModule().map(PluginUtils::parseModuleNotation);
  }

  @InputFile
  @Optional
  public Provider<File> getSettingsModuleFile() {
    return getParsedSettingsModule()
        .map(
            it -> {
              if (it instanceof File file) {
                return file;
              }
              return null;
            });
  }

  @Input
  @Optional
  public Provider<URI> getSettingsModuleUri() {
    return getParsedSettingsModule()
        .map(
            it -> {
              if (it instanceof URI uri) {
                return uri;
              }
              return null;
            });
  }

  // Exposed as a task input via evalRootDirPath, because we only need to depend
  // on this directory's path and not on its contents.
  @Internal
  public abstract DirectoryProperty getEvalRootDir();

  @Input
  @Optional
  public Provider<String> getEvalRootDirPath() {
    return getEvalRootDir().map(it -> it.getAsFile().getAbsolutePath());
  }

  // This is not a task input because it doesn't affect task output but only performance.
  @Internal
  public abstract DirectoryProperty getModuleCacheDir();

  @Input
  @Optional
  public abstract Property<Boolean> getColor();

  @Input
  @Optional
  public abstract Property<Boolean> getNoCache();

  @Input
  @Optional
  public abstract Property<Duration> getEvalTimeout();

  @Input
  @Optional
  public abstract Property<Integer> getTestPort();

  @Input
  @Optional
  public abstract Property<URI> getHttpProxy();

  @Input
  @Optional
  public abstract ListProperty<String> getHttpNoProxy();

  @TaskAction
  public void runTask() {
    doRunTask();
  }

  protected abstract void doRunTask();

  @LateInit protected CliBaseOptions cachedOptions;

  // Must be called during task execution time only.
  @Internal
  protected CliBaseOptions getCliBaseOptions() {
    if (cachedOptions == null) {
      cachedOptions =
          new CliBaseOptions(
              getSourceModulesAsUris(),
              patternsFromStrings(getAllowedModules().get()),
              patternsFromStrings(getAllowedResources().get()),
              getEnvironmentVariables().get(),
              getExternalProperties().get(),
              parseModulePath(),
              getProject().getProjectDir().toPath(),
              mapAndGetOrNull(getEvalRootDirPath(), Paths::get),
              mapAndGetOrNull(getSettingsModule(), PluginUtils::parseModuleNotationToUri),
              null,
              getEvalTimeout().getOrNull(),
              mapAndGetOrNull(getModuleCacheDir(), it1 -> it1.getAsFile().toPath()),
              getColor().getOrElse(false) ? Color.ALWAYS : Color.NEVER,
              getNoCache().getOrElse(false),
              false,
              false,
              false,
              getTestPort().getOrElse(-1),
              Collections.emptyList(),
              getHttpProxy().getOrNull(),
              getHttpNoProxy().getOrElse(List.of()),
              Map.of(),
              Map.of());
    }
    return cachedOptions;
  }

  @Internal
  protected List<URI> getSourceModulesAsUris() {
    return Collections.emptyList();
  }

  @Inject
  protected abstract ObjectFactory getObjects();

  @Inject
  protected abstract ProviderFactory getProviders();

  protected List<Path> parseModulePath() {
    return getModulePath().getFiles().stream().map(File::toPath).collect(Collectors.toList());
  }

  protected List<Pattern> patternsFromStrings(List<String> patterns) {
    return patterns.stream().map(Pattern::compile).collect(Collectors.toList());
  }

  /**
   * Equivalent to {@code provider.map(it -> f.apply(it)).getOrNull()}.
   *
   * <p>This function is necessary because in some cases doing {@code
   * someProvider.map(...).getOrNull()} may trigger validation errors inside Gradle, when {@code
   * someProvider} is derived from a property.
   */
  protected <T, U> @Nullable U mapAndGetOrNull(Provider<T> provider, Function<T, U> f) {
    @Nullable T value = provider.getOrNull();
    return value == null ? null : f.apply(value);
  }
}
