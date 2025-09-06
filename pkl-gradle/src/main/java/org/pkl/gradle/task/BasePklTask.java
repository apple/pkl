/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import org.gradle.api.Transformer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.jspecify.annotations.Nullable;
import org.pkl.commons.cli.CliBaseOptions;
import org.pkl.core.Pair;
import org.pkl.core.evaluatorSettings.Color;
import org.pkl.gradle.utils.PluginUtils;

@CacheableTask
public abstract class BasePklTask extends DefaultTask {
  private static final String TRUFFLE_USE_FALLBACK_RUNTIME_FLAG = "truffle.UseFallbackRuntime";

  private static final String POLYGLOT_WARN_INTERPRETER_ONLY_FLAG =
      "polyglot.engine.WarnInterpreterOnly";

  @Input
  public abstract ListProperty<String> getAllowedModules();

  @Input
  public abstract ListProperty<String> getAllowedResources();

  @Input
  public abstract MapProperty<String, String> getEnvironmentVariables();

  @Input
  public abstract MapProperty<String, String> getExternalProperties();

  @InputFiles
  @PathSensitive(PathSensitivity.ABSOLUTE)
  public abstract ConfigurableFileCollection getModulePath();

  @Internal
  public abstract Property<Object> getSettingsModule();

  @Internal
  public Provider<Object> getParsedSettingsModule() {
    return getSettingsModule().map(PluginUtils::parseModuleNotation);
  }

  @InputFile
  @Optional
  @PathSensitive(PathSensitivity.ABSOLUTE)
  public Provider<File> getSettingsModuleFile() {
    //noinspection RedundantCast
    return getParsedSettingsModule()
        // NullAway needs this redundant cast
        .map(
            (Transformer<@Nullable File, Object>)
                object -> object instanceof File file ? file : null);
  }

  @Input
  @Optional
  public Provider<URI> getSettingsModuleUri() {
    //noinspection RedundantCast
    return getParsedSettingsModule()
        // NullAway needs this redundant cast
        .map((Transformer<@Nullable URI, Object>) object -> object instanceof URI uri ? uri : null);
  }

  /**
   * The working directory for the task, used as the base for relative path resolution. This
   * replaces direct access to {@code project.getProjectDir()} at execution time to support the
   * Gradle configuration cache.
   */
  @Internal
  public abstract DirectoryProperty getWorkingDir();

  // Exposed as a task input via workingDirPath so that a change to the project directory
  // invalidates the task without tracking the directory's contents.
  @Input
  public Provider<String> getWorkingDirPath() {
    return getWorkingDir().map(it -> it.getAsFile().getAbsolutePath());
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

  @Input
  @Optional
  public abstract MapProperty<URI, URI> getHttpRewrites();

  @Input
  @Optional
  public abstract MapProperty<URI, List<Pair<String, String>>> getHttpHeaders();

  @Input
  @Optional
  public abstract Property<Boolean> getPowerAssertions();

  /**
   * There are issues with using native libraries in Gradle plugins. As a workaround for now, make
   * Truffle use an un-optimized runtime.
   *
   * @see <a
   *     href="https://discuss.gradle.org/t/loading-a-native-library-in-a-gradle-plugin/44854">https://discuss.gradle.org/t/loading-a-native-library-in-a-gradle-plugin/44854</a>
   * @see <a
   *     href="https://github.com/apple/pkl/issues/988">https://github.com/apple/pkl/issues/988</a>
   */
  // TODO: Remove this workaround when ugprading to Truffle 24.2+ (Truffle automatically falls back
  // in this scenario).
  protected void withFallbackTruffleRuntime(Runnable task) {
    System.setProperty(TRUFFLE_USE_FALLBACK_RUNTIME_FLAG, "true");
    System.setProperty(POLYGLOT_WARN_INTERPRETER_ONLY_FLAG, "false");
    task.run();
  }

  @TaskAction
  public void runTask() {
    withFallbackTruffleRuntime(this::doRunTask);
  }

  protected abstract void doRunTask();

  // Must be called during task execution time only.
  // Note: CliBaseOptions is intentionally not cached — caching would require holding a reference
  // across the configuration/execution boundary, which is incompatible with the Gradle
  // configuration cache. The cost of constructing this object per-invocation is negligible.
  @Internal
  protected CliBaseOptions getCliBaseOptions() {
    return new CliBaseOptions(
        getSourceModulesAsUris(),
        patternsFromStrings(getAllowedModules().get()),
        patternsFromStrings(getAllowedResources().get()),
        getEnvironmentVariables().get(),
        getExternalProperties().get(),
        parseModulePath(),
        getWorkingDir().get().getAsFile().toPath(),
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
        getHttpRewrites().getOrNull(),
        getHttpHeaders().getOrNull(),
        Map.of(),
        Map.of(),
        null,
        getPowerAssertions().getOrElse(false));
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
