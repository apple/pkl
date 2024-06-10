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
import java.util.Objects;
import java.util.function.BiFunction;
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
      Value input, BiFunction<? super String, ? super String, Path> pathNormalizer) {
    if (!(input instanceof PObject pSettings)) {
      throw PklBugException.unreachableCode();
    }

    var moduleCacheDirStr = (String) pSettings.get("moduleCacheDir");
    var moduleCacheDir =
        moduleCacheDirStr == null
            ? null
            : pathNormalizer.apply(moduleCacheDirStr, "moduleCacheDir");

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
        modulePathStrs == null
            ? null
            : modulePathStrs.stream().map(it -> pathNormalizer.apply(it, "modulePath")).toList();

    var rootDirStr = (String) pSettings.get("rootDir");
    var rootDir = rootDirStr == null ? null : pathNormalizer.apply(rootDirStr, "rootDir");

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

  private boolean arePatternsEqual(
      @Nullable List<Pattern> thesePatterns, @Nullable List<Pattern> thosePatterns) {
    if (thesePatterns == null) {
      return thosePatterns == null;
    }
    if (thosePatterns == null || thesePatterns.size() != thosePatterns.size()) {
      return false;
    }
    for (var i = 0; i < thesePatterns.size(); i++) {
      if (!thesePatterns.get(i).pattern().equals(thosePatterns.get(i).pattern())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof PklEvaluatorSettings that)) {
      return false;
    }

    return Objects.equals(externalProperties, that.externalProperties)
        && Objects.equals(env, that.env)
        && arePatternsEqual(allowedModules, that.allowedModules)
        && arePatternsEqual(allowedResources, that.allowedResources)
        && Objects.equals(noCache, that.noCache)
        && Objects.equals(moduleCacheDir, that.moduleCacheDir)
        && Objects.equals(timeout, that.timeout)
        && Objects.equals(rootDir, that.rootDir)
        && Objects.equals(http, that.http);
  }

  private int hashPatterns(@Nullable List<Pattern> patterns) {
    if (patterns == null) {
      return 0;
    }
    var ret = 1;
    for (var pattern : patterns) {
      ret = 31 * ret + pattern.pattern().hashCode();
    }
    return ret;
  }

  @Override
  public int hashCode() {
    var result =
        Objects.hash(externalProperties, env, noCache, moduleCacheDir, timeout, rootDir, http);
    result = 31 * result + hashPatterns(allowedModules);
    result = 31 * result + hashPatterns(allowedResources);
    return result;
  }
}
