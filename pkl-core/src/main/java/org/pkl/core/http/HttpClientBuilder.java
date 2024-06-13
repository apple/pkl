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
package org.pkl.core.http;

import java.net.ProxySelector;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.pkl.core.Release;
import org.pkl.core.http.HttpClient.Builder;

final class HttpClientBuilder implements HttpClient.Builder {
  private String userAgent;
  private Duration connectTimeout = Duration.ofSeconds(60);
  private Duration requestTimeout = Duration.ofSeconds(60);
  private final List<Path> certificateFiles = new ArrayList<>();
  private final List<ByteBuffer> certificateBytes = new ArrayList<>();
  private int testPort = -1;
  private ProxySelector proxySelector;

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
      return new RequestRewritingClient(userAgent, requestTimeout, testPort, jdkClient);
    };
  }
}
