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
package org.pkl.core.evaluatorSettings;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.pkl.core.Duration;
import org.pkl.core.PNull;
import org.pkl.core.PObject;
import org.pkl.core.PklBugException;
import org.pkl.core.PklException;
import org.pkl.core.Value;
import org.pkl.core.util.ErrorMessages;

/** Java version of {@code pkl.EvaluatorSettings}. */
public record PklEvaluatorSettings(
    @Nullable Map<String, String> externalProperties,
    @Nullable Map<String, String> env,
    @Nullable List<Pattern> allowedModules,
    @Nullable List<Pattern> allowedResources,
    @Nullable Color color,
    @Nullable Boolean noCache,
    @Nullable Path moduleCacheDir,
    @Nullable List<Path> modulePath,
    @Nullable Duration timeout,
    @Nullable Path rootDir,
    @Nullable Http http,
    @Nullable Map<String, ExternalReader> externalModuleReaders,
    @Nullable Map<String, ExternalReader> externalResourceReaders,
    @Nullable TraceMode traceMode) {

  /** Initializes a {@link PklEvaluatorSettings} from a raw object representation. */
  @SuppressWarnings("unchecked")
  public static PklEvaluatorSettings parse(Value input) {
    if (!(input instanceof PObject pSettings)) {
      throw PklBugException.unreachableCode();
    }

    var moduleCacheDirStr = (String) pSettings.get("moduleCacheDir");
    var moduleCacheDir = moduleCacheDirStr == null ? null : Path.of(moduleCacheDirStr);

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
    var modulePath = modulePathStrs == null ? null : modulePathStrs.stream().map(Path::of).toList();

    var rootDirStr = (String) pSettings.get("rootDir");
    var rootDir = rootDirStr == null ? null : Path.of(rootDirStr);

    var externalModuleReadersRaw = (Map<String, Value>) pSettings.get("externalModuleReaders");
    var externalModuleReaders =
        externalModuleReadersRaw == null
            ? null
            : externalModuleReadersRaw.entrySet().stream()
                .collect(
                    Collectors.toMap(
                        Entry::getKey, entry -> ExternalReader.parse(entry.getValue())));

    var externalResourceReadersRaw = (Map<String, Value>) pSettings.get("externalResourceReaders");
    var externalResourceReaders =
        externalResourceReadersRaw == null
            ? null
            : externalResourceReadersRaw.entrySet().stream()
                .collect(
                    Collectors.toMap(
                        Entry::getKey, entry -> ExternalReader.parse(entry.getValue())));

    var color = (String) pSettings.get("color");
    var traceMode = (String) pSettings.get("traceMode");

    return new PklEvaluatorSettings(
        (Map<String, String>) pSettings.get("externalProperties"),
        (Map<String, String>) pSettings.get("env"),
        allowedModules,
        allowedResources,
        color == null ? null : Color.valueOf(color.toUpperCase(Locale.ROOT)),
        (Boolean) pSettings.get("noCache"),
        moduleCacheDir,
        modulePath,
        (Duration) pSettings.get("timeout"),
        rootDir,
        Http.parse((Value) pSettings.get("http")),
        externalModuleReaders,
        externalResourceReaders,
        traceMode == null ? null : TraceMode.valueOf(traceMode.toUpperCase(Locale.ROOT)));
  }

  public record Http(
      @Nullable Proxy proxy,
      @Nullable Map<URI, URI> rewrites,
      @Nullable Map<String, Map<String, List<String>>> headers) {
    public static final Http DEFAULT = new Http(null, Collections.emptyMap(), null);

    public static @Nullable Http parse(@Nullable Value input) {
      if (input == null || input instanceof PNull) {
        return null;
      } else if (input instanceof PObject http) {
        var proxy = Proxy.parse((Value) http.getProperty("proxy"));
        var parsedRewrites = parseHttpRewrites(http.getProperty("rewrites"));
        var parsedHeaders = parseHttpHeaders(http.getProperty("headers"));
        return new Http(proxy, parsedRewrites, parsedHeaders);
      } else {
        throw PklBugException.unreachableCode();
      }
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Map<URI, URI> parseHttpRewrites(Object rewrites) {
      if (rewrites instanceof PNull) {
        return null;
      }
      var parsedRewrites = new HashMap<URI, URI>();
      for (var entry : ((Map<String, String>) rewrites).entrySet()) {
        var key = entry.getKey();
        var value = entry.getValue();
        try {
          parsedRewrites.put(new URI(key), new URI(value));
        } catch (URISyntaxException e) {
          throw new PklException(ErrorMessages.create("invalidUri", e.getInput()));
        }
      }
      return parsedRewrites;
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Map<String, Map<String, List<String>>> parseHttpHeaders(
        Object headerDefs) {
      if (headerDefs instanceof PNull) {
        return null;
      }
      var defs = (Map<String, Map<String, Object>>) headerDefs;
      var ret = new LinkedHashMap<String, Map<String, List<String>>>(defs.size());
      for (var entry : defs.entrySet()) {
        var headers = entry.getValue();
        var map = new LinkedHashMap<String, List<String>>(headers.size());
        for (var header : headers.entrySet()) {
          var value = header.getValue();
          var headerValues =
              value instanceof List<?> ? (List<String>) value : List.of((String) value);
          map.put(header.getKey(), headerValues);
        }
        ret.put(entry.getKey(), map);
      }
      return ret;
    }
  }

  public record Proxy(@Nullable URI address, @Nullable List<String> noProxy) {
    @Deprecated(forRemoval = true)
    public static Proxy create(@Nullable String address, @Nullable List<String> noProxy) {
      URI addressUri;
      try {
        addressUri = address == null ? null : new URI(address);
      } catch (URISyntaxException e) {
        throw new PklException(ErrorMessages.create("invalidUri", address));
      }
      return new Proxy(addressUri, noProxy);
    }

    public static @Nullable Proxy parse(Value input) {
      if (input instanceof PNull) {
        return null;
      } else if (input instanceof PObject proxy) {
        var address = (String) proxy.get("address");
        @SuppressWarnings("unchecked")
        var noProxy = (List<String>) proxy.get("noProxy");
        try {
          var addressUri = address == null ? null : new URI(address);
          return new Proxy(addressUri, noProxy);
        } catch (URISyntaxException e) {
          throw new PklException(ErrorMessages.create("invalidUri", address));
        }
      } else {
        throw PklBugException.unreachableCode();
      }
    }
  }

  public record ExternalReader(
      String executable, @Nullable List<String> arguments, @Nullable String workingDir) {
    @SuppressWarnings("unchecked")
    public static ExternalReader parse(Value input) {
      if (input instanceof PObject externalReader) {
        var executable = (String) externalReader.getProperty("executable");
        var arguments = (List<String>) externalReader.get("arguments");
        var workingDir = externalReader.getProperty("workingDir");
        return new ExternalReader(
            executable, arguments, workingDir instanceof PNull ? null : (String) workingDir);
      }
      throw PklBugException.unreachableCode();
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
        && Objects.equals(color, that.color)
        && Objects.equals(noCache, that.noCache)
        && Objects.equals(moduleCacheDir, that.moduleCacheDir)
        && Objects.equals(timeout, that.timeout)
        && Objects.equals(rootDir, that.rootDir)
        && Objects.equals(http, that.http)
        && Objects.equals(traceMode, that.traceMode);
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
        Objects.hash(
            externalProperties,
            env,
            color,
            noCache,
            moduleCacheDir,
            timeout,
            rootDir,
            http,
            traceMode);
    result = 31 * result + hashPatterns(allowedModules);
    result = 31 * result + hashPatterns(allowedResources);
    return result;
  }
}
