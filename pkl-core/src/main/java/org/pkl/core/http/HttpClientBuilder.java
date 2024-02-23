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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.pkl.core.Release;
import org.pkl.core.util.ErrorMessages;

final class HttpClientBuilder implements HttpClient.Builder {
  private final Path userHome;
  private String userAgent;
  private Duration connectTimeout = Duration.ofSeconds(60);
  private Duration requestTimeout = Duration.ofSeconds(60);
  private final List<Path> certificateFiles = new ArrayList<>();
  private final List<URI> certificateUris = new ArrayList<>();

  HttpClientBuilder() {
    this(Path.of(System.getProperty("user.home")));
  }

  // only exists for testing
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
  public HttpClient.Builder addCertificates(URI url) {
    var scheme = url.getScheme();
    if (!"jar".equalsIgnoreCase(scheme) && !"file".equalsIgnoreCase(scheme)) {
      throw new HttpClientInitException(ErrorMessages.create("expectedJarOrFileUrl", url));
    }
    certificateUris.add(url);
    return this;
  }

  public HttpClient.Builder addDefaultCliCertificates() {
    var directory = userHome.resolve(".pkl").resolve("cacerts");
    var fileCount = certificateFiles.size();
    if (Files.isDirectory(directory)) {
      try (var files = Files.list(directory)) {
        files.filter(Files::isRegularFile).forEach(certificateFiles::add);
      } catch (IOException e) {
        throw new HttpClientInitException(e);
      }
    }
    if (certificateFiles.size() == fileCount) {
      addBuiltInCertificates();
    }
    return this;
  }

  @Override
  public HttpClient.Builder addBuiltInCertificates() {
    certificateUris.add(getBuiltInCertificates());
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
    // make defensive copies because Supplier may get called after builder was mutated
    var certificateFiles = List.copyOf(this.certificateFiles);
    var certificateUris = List.copyOf(this.certificateUris);
    return () -> {
      var jdkClient = new JdkHttpClient(certificateFiles, certificateUris, connectTimeout);
      return new RequestRewritingClient(userAgent, requestTimeout, jdkClient);
    };
  }

  private static URI getBuiltInCertificates() {
    var resource = HttpClientBuilder.class.getResource("/org/pkl/certs/PklCARoots.pem");
    if (resource == null) {
      throw new HttpClientInitException(ErrorMessages.create("cannotFindBuiltInCertificates"));
    }
    try {
      return resource.toURI();
    } catch (URISyntaxException e) {
      throw new AssertionError("unreachable");
    }
  }
}
