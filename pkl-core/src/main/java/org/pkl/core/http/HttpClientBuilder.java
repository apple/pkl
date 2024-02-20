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

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.pkl.core.Release;

final class HttpClientBuilder implements HttpClient.Builder {
  private final Path userHome;
  private String userAgent;
  private Duration connectTimeout = Duration.ofSeconds(60);
  private Duration requestTimeout = Duration.ofSeconds(60);
  private final List<Path> certificateFiles = new ArrayList<>();
  private final List<URL> certificateUrls = new ArrayList<>();

  HttpClientBuilder() {
    this(Path.of(System.getProperty("user.home")));
  }

  // for testing
  HttpClientBuilder(Path userHome) {
    this.userHome = userHome;
    var release = Release.current();
    userAgent = "Pkl/" + release.version() + " (" + release.os() + "; " + release.flavor() + ")";
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
  public HttpClient.Builder addCertificates(Path file) {
    certificateFiles.add(file);
    return this;
  }

  @Override
  public HttpClient.Builder addCertificates(URL url) {
    certificateUrls.add(url);
    return this;
  }

  public HttpClient.Builder addDefaultCliCertificates() throws IOException {
    var directory = userHome.resolve(".pkl").resolve("cacerts");
    var fileCount = certificateFiles.size();
    if (Files.isDirectory(directory)) {
      try (var files = Files.list(directory)) {
        files.filter(Files::isRegularFile).forEach(certificateFiles::add);
      }
    }
    if (certificateFiles.size() == fileCount) {
      addBuiltInCertificates();
    }
    return this;
  }

  @Override
  public HttpClient.Builder addBuiltInCertificates() {
    certificateUrls.add(getBuiltInCertificates());
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
    return () -> {
      var jdkClient =
          certificateFiles.isEmpty() && certificateUrls.isEmpty()
              ? new JdkHttpClient(List.of(), List.of(getBuiltInCertificates()), connectTimeout)
              : new JdkHttpClient(
                  List.copyOf(certificateFiles), List.copyOf(certificateUrls), connectTimeout);
      return new RequestRewritingClient(userAgent, requestTimeout, jdkClient);
    };
  }

  private static URL getBuiltInCertificates() {
    var resource = HttpClientBuilder.class.getResource("IncludedCARoots.pem");
    if (resource == null) {
      throw new AssertionError("Failed to locate built-in CA certificates.");
    }
    return resource;
  }
}
