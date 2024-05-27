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
package org.pkl.core.evaluatorSettings;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.pkl.core.Duration;
import org.pkl.core.PNull;
import org.pkl.core.PObject;
import org.pkl.core.PklBugException;
import org.pkl.core.PklException;
import org.pkl.core.Value;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.Nullable;

/** Java version of {@code pkl.EvaluatorSettings}. */
public record PklEvaluatorSettings(
    @Nullable Map<String, String> externalProperties,
    @Nullable Map<String, String> env,
    @Nullable List<Pattern> allowedModules,
    @Nullable List<Pattern> allowedResources,
    @Nullable Boolean noCache,
    @Nullable Path moduleCacheDir,
    @Nullable List<Path> modulePath,
    @Nullable Duration timeout,
    @Nullable Path rootDir,
    @Nullable Http http) {

  /** Initializes a {@link PklEvaluatorSettings} from a raw object representation. */
  @SuppressWarnings("unchecked")
  public static PklEvaluatorSettings parse(
      Value input, Function<? super String, Path> pathNormalizer) {
    if (!(input instanceof PObject pSettings)) {
      throw PklBugException.unreachableCode();
    }

    var moduleCacheDirStr = (String) pSettings.get("moduleCacheDir");
    var moduleCacheDir = moduleCacheDirStr == null ? null : pathNormalizer.apply(moduleCacheDirStr);

    var allowedModulesStrs = (List<String>) pSettings.get("allowedModules");
    var allowedModules =
        allowedModulesStrs == null
            ? null
            : allowedModulesStrs.stream().map(Pattern::compile).toList();

    var allowedResourcesStrs = (List<String>) pSettings.get("allowedResources");
    var allowedResources =
        allowedResourcesStrs == null
            ? null
            : allowedResourcesStrs.stream().map(Pattern::compile).toList();

    var modulePathStrs = (List<String>) pSettings.get("modulePath");
    var modulePath =
        modulePathStrs == null ? null : modulePathStrs.stream().map(pathNormalizer).toList();

    var rootDirStr = (String) pSettings.get("rootDir");
    var rootDir = rootDirStr == null ? null : pathNormalizer.apply(rootDirStr);

    return new PklEvaluatorSettings(
        (Map<String, String>) pSettings.get("externalProperties"),
        (Map<String, String>) pSettings.get("env"),
        allowedModules,
        allowedResources,
        (Boolean) pSettings.get("noCache"),
        moduleCacheDir,
        modulePath,
        (Duration) pSettings.get("timeout"),
        rootDir,
        Http.parse((Value) pSettings.get("http")));
  }

  public record Http(@Nullable Proxy proxy) {
    public static final Http DEFAULT = new Http(null);

    public static @Nullable Http parse(@Nullable Value input) {
      if (input == null || input instanceof PNull) {
        return null;
      } else if (input instanceof PObject http) {
        var proxy = Proxy.parse((Value) http.getProperty("proxy"));
        return proxy == null ? DEFAULT : new Http(proxy);
      } else {
        throw PklBugException.unreachableCode();
      }
    }
  }

  public record Proxy(URI address, List<String> noProxy) {

    @SuppressWarnings("unchecked")
    public static @Nullable Proxy parse(Value input) {
      if (input instanceof PNull) {
        return null;
      } else if (input instanceof PObject proxy) {
        var address = (String) proxy.getProperty("address");
        var noProxy = (List<String>) proxy.getProperty("noProxy");
        URI addressUri;
        try {
          addressUri = new URI(address);
        } catch (URISyntaxException e) {
          throw new PklException(ErrorMessages.create("invalidUri", address));
        }
        return new Proxy(addressUri, noProxy);
      } else {
        throw PklBugException.unreachableCode();
      }
    }
  }
}
