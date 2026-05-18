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
package org.pkl.core.http;

import static org.pkl.core.util.IoUtils.validateRewriteRule;

import java.net.ProxySelector;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.pkl.core.Release;
import org.pkl.core.http.HttpClient.Builder;
import org.pkl.core.util.GlobResolver;
import org.pkl.core.util.GlobResolver.InvalidGlobPatternException;
import org.pkl.core.util.IoUtils;

final class HttpClientBuilder implements HttpClient.Builder {
  private String userAgent;
  private Duration connectTimeout = Duration.ofSeconds(60);
  private Duration requestTimeout = Duration.ofSeconds(60);
  private final List<Path> certificateFiles = new ArrayList<>();
  private final List<ByteBuffer> certificateBytes = new ArrayList<>();
  private int testPort = -1;
  private ProxySelector proxySelector;
  private Map<URI, URI> rewrites = new HashMap<>();
  // okay to use Pattern as a map key here because `GlobResolver.toRegexPattern()` caches and
  // gives the same `Pattern` instance for an existing glob pattern.
  private Map<Pattern, Map<String, List<String>>> headers = new HashMap<>();

  HttpClientBuilder() {
    var release = Release.current();
    this.userAgent =
        "Pkl/" + release.version() + " (" + release.os() + "; " + release.flavor() + ")";
  }

  public HttpClient.Builder setUserAgent(String userAgent) {
    this.userAgent = userAgent;
    return this;
  }

  @Override
  public HttpClient.Builder setConnectTimeout(Duration timeout) {
    this.connectTimeout = timeout;
    return this;
  }

  @Override
  public HttpClient.Builder setRequestTimeout(Duration timeout) {
    this.requestTimeout = timeout;
    return this;
  }

  @Override
  public HttpClient.Builder addCertificates(Path path) {
    certificateFiles.add(path);
    return this;
  }

  @Override
  public Builder addCertificates(byte[] certificateBytes) {
    this.certificateBytes.add(ByteBuffer.wrap(certificateBytes));
    return this;
  }

  @Override
  public HttpClient.Builder setTestPort(int port) {
    testPort = port;
    return this;
  }

  public HttpClient.Builder setProxySelector(ProxySelector proxySelector) {
    this.proxySelector = proxySelector;
    return this;
  }

  @Override
  public Builder setProxy(URI proxyAddress, List<String> noProxy) {
    this.proxySelector = new org.pkl.core.http.ProxySelector(proxyAddress, noProxy);
    return this;
  }

  @Override
  public Builder setRewrites(Map<URI, URI> rewrites) {
    for (var entry : rewrites.entrySet()) {
      validateRewriteRule(entry.getKey());
      validateRewriteRule(entry.getValue());
    }
    this.rewrites = new HashMap<>(rewrites);
    return this;
  }

  @Override
  public Builder addRewrite(URI sourcePrefix, URI targetPrefix) {
    validateRewriteRule(sourcePrefix);
    validateRewriteRule(targetPrefix);
    this.rewrites.put(sourcePrefix, targetPrefix);
    return this;
  }

  @Override
  public Builder setHeaders(Map<String, Map<String, List<String>>> headers) {
    var newHeaders = new HashMap<Pattern, Map<String, List<String>>>(headers.size());
    for (var rule : headers.entrySet()) {
      Pattern pattern;
      try {
        pattern = GlobResolver.toRegexPattern(rule.getKey());
      } catch (InvalidGlobPatternException e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      }
      var map = new HashMap<String, List<String>>();
      for (var entry : rule.getValue().entrySet()) {
        IoUtils.validateHeaderName(entry.getKey());
        for (var value : entry.getValue()) {
          IoUtils.validateHeaderValue(value);
        }
        map.put(entry.getKey(), new ArrayList<>(entry.getValue()));
      }
      newHeaders.put(pattern, map);
    }
    this.headers = newHeaders;
    return this;
  }

  @Override
  public Builder addHeaders(String globPattern, Map<String, List<String>> headers) {
    try {
      var pattern = GlobResolver.toRegexPattern(globPattern);
      var existingHeaders = this.headers.computeIfAbsent(pattern, k -> new HashMap<>());
      for (var entry : headers.entrySet()) {
        var headerName = entry.getKey();
        var headerValues = entry.getValue();

        IoUtils.validateHeaderName(headerName);
        for (var value : headerValues) {
          IoUtils.validateHeaderValue(value);
        }

        var existingList = existingHeaders.putIfAbsent(headerName, new ArrayList<>(headerValues));
        if (existingList != null) {
          existingList.addAll(headerValues);
        }
      }
      return this;
    } catch (InvalidGlobPatternException e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    }
  }

  @Override
  public HttpClient build() {
    return doBuild().get();
  }

  @Override
  public HttpClient buildLazily() {
    return new LazyHttpClient(doBuild());
  }

  private Supplier<HttpClient> doBuild() {
    // make defensive copy because Supplier may get called after builder was mutated
    var certificateFiles = List.copyOf(this.certificateFiles);
    var proxySelector =
        this.proxySelector != null ? this.proxySelector : java.net.ProxySelector.getDefault();
    return () -> {
      var jdkClient =
          new JdkHttpClient(certificateFiles, certificateBytes, connectTimeout, proxySelector);
      return new RequestRewritingClient(
          userAgent, requestTimeout, testPort, jdkClient, rewrites, headers);
    };
  }
}
