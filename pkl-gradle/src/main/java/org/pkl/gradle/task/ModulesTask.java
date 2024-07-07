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
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.pkl.commons.cli.CliBaseOptions;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Pair;

public abstract class ModulesTask extends BasePklTask {
  // We expose the contents of this property as task inputs via the sourceModuleFiles
  // and sourceModuleUris properties. We cannot use two separate properties because
  // the order of source modules matters for the CLI API invocation, so it must be
  // a single collection.
  @Internal
  public abstract ListProperty<Object> getSourceModules();

  @InputFiles
  public abstract ConfigurableFileCollection getTransitiveModules();

  private final Map<List<Object>, Pair<List<File>, List<URI>>> parsedSourceModulesCache =
      new HashMap<>();

  // Used for input tracking purposes only.
  @Internal
  public Provider<Pair<List<File>, List<URI>>> getParsedSourceModules() {
    return getSourceModules()
        .map(it -> parsedSourceModulesCache.computeIfAbsent(it, this::splitFilesAndUris));
  }

  // We use @InputFiles and FileCollection here to ensure that file contents are tracked.
  @InputFiles
  public FileCollection getSourceModuleFiles() {
    return getProject().files(getParsedSourceModules().map(it -> it.first));
  }

  // We use @Input and just a list value because we can only track the URIs themselves
  // but not their contents.
  @Input
  public Provider<List<URI>> getSourceModuleUris() {
    return getParsedSourceModules().map(it -> it.second);
  }

  /**
   * Returns the sourceModules property as a list of URIs.
   *
   * <p>This method ensures that the order of source modules in the sourceModules property is
   * preserved all the way to the CLI API invocation.
   */
  @Internal
  @Override
  protected List<URI> getSourceModulesAsUris() {
    return getSourceModules().get().stream()
        .map(this::parseModuleNotationToUri)
        .collect(Collectors.toList());
  }

  // Exposed as a task input via getProjectDirPath, because we only need to depend
  // on this directory's path and not on its contents.
  @Internal
  public abstract DirectoryProperty getProjectDir();

  @Input
  @Optional
  public Provider<String> getProjectDirPath() {
    return getProjectDir().map(it -> it.getAsFile().getAbsolutePath());
  }

  @Input
  @Optional
  public abstract Property<Boolean> getOmitProjectSettings();

  @Input
  @Optional
  public abstract Property<Boolean> getNoProject();

  /**
   * A source module can be either a file or a URI. Files can be tracked, so this method splits a
   * collection of module notations (which can be strings, URIs, URLs, Files or Paths) into a list
   * of files (for content-based tracking) and URIs (for simple value-based tracking). These lists
   * are then exposed as separate read-only properties to make Gradle track them as proper inputs.
   */
  private Pair<List<File>, List<URI>> splitFilesAndUris(List<Object> modules) {
    var files = new ArrayList<File>();
    var uris = new ArrayList<URI>();
    for (var m : modules) {
      var parsed = parseModuleNotation(m);
      if (parsed instanceof File file) {
        files.add(file);
      } else if (parsed instanceof URI uri) {
        uris.add(uri);
      }
    }
    return Pair.of(files, uris);
  }

  /**
   * Converts either a file or a URI to a URI. We convert a file to a URI via the {@link
   * IoUtils#createUri(String)} because other ways of conversion can make relative paths into
   * absolute URIs, which may break module loading.
   */
  private URI parsedModuleNotationToUri(Object notation) {
    if (notation instanceof File file) {
      return IoUtils.createUri(IoUtils.toNormalizedPathString(file.toPath()));
    } else if (notation instanceof URI uri) {
      return uri;
    }
    throw new IllegalArgumentException("Invalid parsed module notation: " + notation);
  }

  protected URI parseModuleNotationToUri(Object m) {
    var parsed1 = parseModuleNotation(m);
    return parsedModuleNotationToUri(parsed1);
  }

  @TaskAction
  @Override
  public void runTask() {
    if (getCliBaseOptions().getNormalizedSourceModules().isEmpty()) {
      throw new InvalidUserDataException("No source modules specified.");
    }
    doRunTask();
  }

  @Internal
  @Override
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
              mapAndGetOrNull(getSettingsModule(), this::parseModuleNotationToUri),
              getProjectDir().isPresent() ? getProjectDir().get().getAsFile().toPath() : null,
              getEvalTimeout().getOrNull(),
              mapAndGetOrNull(getModuleCacheDir(), it1 -> it1.getAsFile().toPath()),
              getNoCache().getOrElse(false),
              getOmitProjectSettings().getOrElse(false),
              getNoProject().getOrElse(false),
              false,
              getTestPort().getOrElse(-1),
              Collections.emptyList(),
              null,
              List.of(),
              "auto");
    }
    return cachedOptions;
  }
}
