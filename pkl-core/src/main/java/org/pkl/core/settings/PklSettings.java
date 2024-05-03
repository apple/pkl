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
package org.pkl.core.settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.pkl.core.*;
import org.pkl.core.httpsettings.PklHttpSettings;
import org.pkl.core.module.ModuleKeyFactories;
import org.pkl.core.resource.ResourceReaders;
import org.pkl.core.runtime.VmEvalException;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.util.IoUtils;

/**
 * Java representation of a Pkl settings file. A Pkl settings file is a Pkl module amending the
 * {@literal pkl.settings} standard library module. To load a settings file, use one of the static
 * {@code load} methods.
 */
// keep in sync with stdlib/settings.pkl
public record PklSettings(Editor editor, PklHttpSettings httpSettings) {
  private static final List<Pattern> ALLOWED_MODULES =
      List.of(Pattern.compile("pkl:"), Pattern.compile("file:"));

  private static final List<Pattern> ALLOWED_RESOURCES =
      List.of(Pattern.compile("env:"), Pattern.compile("file:"));

  /**
   * Loads the user settings file ({@literal ~/.pkl/settings.pkl}). If this file does not exist,
   * returns default settings defined by module {@literal pkl.settings}.
   */
  public static PklSettings loadFromPklHomeDir() throws VmEvalException {
    return loadFromPklHomeDir(IoUtils.getPklHomeDir());
  }

  /** For testing only. */
  static PklSettings loadFromPklHomeDir(Path pklHomeDir) throws VmEvalException {
    var path = pklHomeDir.resolve("settings.pkl");
    return Files.exists(path)
        ? load(ModuleSource.path(path))
        : new PklSettings(Editor.SYSTEM, PklHttpSettings.DEFAULT);
  }

  /** Loads a settings file from the given path. */
  public static PklSettings load(ModuleSource moduleSource) throws VmEvalException {
    try (var evaluator =
        EvaluatorBuilder.unconfigured()
            .setSecurityManager(
                SecurityManagers.standard(
                    ALLOWED_MODULES, ALLOWED_RESOURCES, SecurityManagers.defaultTrustLevels, null))
            .setStackFrameTransformer(StackFrameTransformers.defaultTransformer)
            .addModuleKeyFactory(ModuleKeyFactories.standardLibrary)
            .addModuleKeyFactory(ModuleKeyFactories.file)
            .addResourceReader(ResourceReaders.environmentVariable())
            .addEnvironmentVariables(System.getenv())
            .build()) {
      var module = evaluator.evaluate(moduleSource);
      return parseSettings(module, moduleSource);
    }
  }

  private static Editor parseEditor(PModule module, ModuleSource location) {
    // can't use object mapping in pkl-core, so map manually
    if (module.getPropertyOrNull("editor") instanceof PObject pObject
        && pObject.getPropertyOrNull("urlScheme") instanceof String str) {
      return new Editor(str);
    }
    throw new VmExceptionBuilder().evalError("invalidSettingsFile", location.getUri()).build();
  }

  private static PklSettings parseSettings(PModule module, ModuleSource location)
      throws VmEvalException {
    var httpSettings = module.getPropertyOrNull("http");
    if (httpSettings instanceof PObject http) {
      return new PklSettings(parseEditor(module, location), PklHttpSettings.parse(http, location));
    }
    throw new VmExceptionBuilder().evalError("invalidSettingsFile", location.getUri()).build();
  }

  /**
   * Returns the editor for viewing and editing Pkl files.
   *
   * <p>This method is deprecated, use {@link #editor()} instead.
   */
  @Deprecated(forRemoval = true)
  public Editor getEditor() {
    return editor;
  }

  @Override
  public String toString() {
    return "PklSettings{" + "editor=" + editor + '}';
  }

  /** An editor for viewing and editing Pkl files. */
  public record Editor(String urlScheme) {
    /** The editor associated with {@code file:} URLs ending in {@code .pkl}. */
    public static final Editor SYSTEM = new Editor("%{url}, line %{line}");

    /** The <a href="https://www.jetbrains.com/idea">IntelliJ IDEA</a> editor. */
    public static final Editor IDEA = new Editor("idea://open?file=%{path}&line=%{line}");

    /** The <a href="https://macromates.com">TextMate</a> editor. */
    public static final Editor TEXT_MATE =
        new Editor("txmt://open?url=%{url}&line=%{line}&column=%{column}");

    /** The <a href="https://www.sublimetext.com">Sublime Text</a> editor. */
    public static final Editor SUBLIME =
        new Editor("subl://open?url=%{url}&line=%{line}&column=%{column}");

    /** The <a href="https://atom.io">Atom</a> editor. */
    public static final Editor ATOM =
        new Editor("atom://open?url=%{url}&line=%{line}&column=%{column}");

    /** The <a href="https://code.visualstudio.com">Visual Studio Code</a> editor. */
    public static final Editor VS_CODE = new Editor("vscode://file/%{path}:%{line}:%{column}");

    /**
     * Returns the URL scheme for opening files in this editor. The following placeholders are
     * supported: {@code %{url}}, {@code %{path}}, {@code %{line}}, {@code %{column}}.
     *
     * <p>This method is deprecated; use {@link #urlScheme()} instead.
     */
    public String getUrlScheme() {
      return urlScheme;
    }

    @Override
    public String toString() {
      return "Editor{" + "urlScheme='" + urlScheme + '\'' + '}';
    }
  }
}
