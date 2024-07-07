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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.pkl.commons.cli.CliBaseOptions;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;

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
    return getSettingsModule().map(this::parseModuleNotation);
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
              mapAndGetOrNull(getSettingsModule(), this::parseModuleNotationToUri),
              null,
              getEvalTimeout().getOrNull(),
              mapAndGetOrNull(getModuleCacheDir(), it1 -> it1.getAsFile().toPath()),
              getNoCache().getOrElse(false),
              false,
              false,
              false,
              getTestPort().getOrElse(-1),
              Collections.emptyList(),
              getHttpProxy().getOrNull(),
              getHttpNoProxy().getOrElse(List.of()),
              "auto");
    }
    return cachedOptions;
  }

  @Internal
  protected List<URI> getSourceModulesAsUris() {
    return Collections.emptyList();
  }

  protected List<Path> parseModulePath() {
    return getModulePath().getFiles().stream().map(File::toPath).collect(Collectors.toList());
  }

  /**
   * Parses the specified source module notation into a "parsed" notation which is then used for
   * input path tracking and as an argument for the CLI API.
   *
   * <p>This method accepts the following input types:
   *
   * <ul>
   *   <li>{@link URI} - used as is.
   *   <li>{@link File} - used as is.
   *   <li>{@link Path} - converted to a {@link File}. This conversion may fail because not all
   *       {@link Path}s point to the local file system.
   *   <li>{@link URL} - converted to a {@link URI}. This conversion may fail because {@link URL}
   *       allows for URLs which are not compliant URIs.
   *   <li>{@link CharSequence} - first, converted to a string. If this string is "URI-like" (see
   *       {@link IoUtils#isUriLike(String)}), then we attempt to parse it as a {@link URI}, which
   *       may fail. Otherwise, we attempt to parse it as a {@link Path}, which is then converted to
   *       a {@link File} (both of these operations may fail).
   *   <li>{@link FileSystemLocation} - converted to a {@link File} via the {@link
   *       FileSystemLocation#getAsFile()} method.
   * </ul>
   *
   * In case the returned value is determined to be a {@link URI}, then this URI is first checked
   * for whether its scheme is {@code file}, like {@code file:///example/path}. In such case, this
   * method returns a {@link File} corresponding to the file path in the URI. Otherwise, a {@link
   * URI} instance is returned.
   *
   * @throws InvalidUserDataException In case the input is none of the types described above, or
   *     when the underlying value cannot be parsed correctly.
   */
  protected Object parseModuleNotation(Object notation) {
    if (notation instanceof URI uri) {
      if ("file".equals(uri.getScheme())) {
        return new File(uri.getPath());
      }
      return uri;
    } else if (notation instanceof File) {
      return notation;
    } else if (notation instanceof Path path) {
      try {
        return path.toFile();
      } catch (UnsupportedOperationException e) {
        throw new InvalidUserDataException("Failed to parse Pkl module file path: " + notation, e);
      }
    } else if (notation instanceof URL url) {
      try {
        return parseModuleNotation(url.toURI());
      } catch (URISyntaxException e) {
        throw new InvalidUserDataException("Failed to parse Pkl module URI: " + notation, e);
      }
    } else if (notation instanceof CharSequence) {
      var s = notation.toString();
      if (IoUtils.isUriLike(s)) {
        try {
          return parseModuleNotation(IoUtils.toUri(s));
        } catch (URISyntaxException e) {
          throw new InvalidUserDataException("Failed to parse Pkl module URI: " + s, e);
        }
      } else {
        try {
          return Paths.get(s).toFile();
        } catch (InvalidPathException | UnsupportedOperationException e) {
          throw new InvalidUserDataException("Failed to parse Pkl module file path: " + s, e);
        }
      }
    } else if (notation instanceof FileSystemLocation location) {
      return location.getAsFile();
    } else {
      throw new InvalidUserDataException(
          "Unsupported value of type "
              + notation.getClass()
              + " used as a module path: "
              + notation);
    }
  }

  protected URI parseModuleNotationToUri(Object m) {
    var parsed1 = parseModuleNotation(m);
    return parsedModuleNotationToUri(parsed1);
  }

  /**
   * Converts either a file or a URI to a URI. We convert a file to a URI via the {@link
   * IoUtils#createUri(String)} because other ways of conversion can make relative paths into
   * absolute URIs, which may break module loading.
   */
  private URI parsedModuleNotationToUri(Object notation) {
    if (notation instanceof File file) {
      return IoUtils.createUri(file.getPath());
    } else if (notation instanceof URI uri) {
      return uri;
    }
    throw new IllegalArgumentException("Invalid parsed module notation: " + notation);
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
