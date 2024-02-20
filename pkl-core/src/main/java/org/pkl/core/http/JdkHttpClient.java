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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.ConnectException;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.concurrent.ThreadSafe;
import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.pkl.core.util.ErrorMessages;

@ThreadSafe
final class JdkHttpClient implements HttpClient {
  // non-private for testing
  final java.net.http.HttpClient underlying;

  // call java.net.http.HttpClient.close() if available (JDK 21+)
  private static final MethodHandle closeMethod;

  static {
    var methodType = MethodType.methodType(void.class, java.net.http.HttpClient.class);
    MethodHandle result;
    try {
      //noinspection JavaLangInvokeHandleSignature
      result =
          MethodHandles.publicLookup()
              .findVirtual(java.net.http.HttpClient.class, "close", methodType);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      // use no-op close method
      result = MethodHandles.empty(methodType);
    }
    closeMethod = result;
  }

  JdkHttpClient(List<Path> certificateFiles, List<URL> certificateUrls, Duration connectTimeout) {
    underlying =
        java.net.http.HttpClient.newBuilder()
            .sslContext(createSslContext(certificateFiles, certificateUrls))
            .connectTimeout(connectTimeout)
            .build();
  }

  @Override
  public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> responseBodyHandler)
      throws IOException {
    try {
      return underlying.send(request, responseBodyHandler);
    } catch (ConnectException e) {
      // original exception has no message
      throw new ConnectException(
          ErrorMessages.create("errorConnectingToHost", request.uri().getHost()));
    } catch (InterruptedException e) {
      // next best thing after letting (checked) InterruptedException bubble up
      Thread.currentThread().interrupt();
      throw new IOException(e);
    }
  }

  @Override
  public void close() {
    try {
      closeMethod.invoke(underlying);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  // https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html#security-algorithm-implementation-requirements
  private static SSLContext createSslContext(
      List<Path> certificateFiles, List<URL> certificateUrls) {
    try {
      var certPathBuilder = CertPathBuilder.getInstance("PKIX");
      // create a non-legacy revocation checker that is configured via setOptions() instead of
      // security property "ocsp.enabled"
      var revocationChecker = (PKIXRevocationChecker) certPathBuilder.getRevocationChecker();
      revocationChecker.setOptions(Set.of()); // prefer OCSP, fall back to CRLs

      Set<TrustAnchor> trustAnchors = createTrustAnchors(certificateFiles, certificateUrls);
      var pkixParameters = new PKIXBuilderParameters(trustAnchors, new X509CertSelector());
      // equivalent of "com.sun.net.ssl.checkRevocation=true"
      pkixParameters.setRevocationEnabled(true);
      pkixParameters.addCertPathChecker(revocationChecker);

      var trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
      trustManagerFactory.init(new CertPathTrustManagerParameters(pkixParameters));

      var sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());

      return sslContext;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  private static Set<TrustAnchor> createTrustAnchors(
      List<Path> certificateFiles, List<URL> certificateUrls)
      throws CertificateException, IOException {
    var anchors = new HashSet<TrustAnchor>();

    for (var file : certificateFiles) {
      try (var stream = Files.newInputStream(file)) {
        var certificates = generateCertificates(stream);
        if (certificates.isEmpty()) {
          throw new CertificateException(ErrorMessages.create("emptyCertificateFile", file));
        }
        for (var certificate : certificates) {
          anchors.add(new TrustAnchor(certificate, null));
        }
      }
    }

    for (var url : certificateUrls) {
      try (var stream = url.openStream()) {
        var certificates = generateCertificates(stream);
        if (certificates.isEmpty()) {
          throw new CertificateException(ErrorMessages.create("emptyCertificateFile", url));
        }
        for (var certificate : certificates) {
          anchors.add(new TrustAnchor(certificate, null));
        }
      }
    }

    return anchors;
  }

  private static List<X509Certificate> generateCertificates(InputStream stream)
      throws CertificateException {
    var factory = CertificateFactory.getInstance("X.509");
    //noinspection unchecked
    return (List<X509Certificate>) factory.generateCertificates(stream);
  }
}
