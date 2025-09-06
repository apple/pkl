/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.concurrent.ThreadSafe;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import org.pkl.core.Pair;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.Exceptions;

/** An {@code HttpClient} implementation backed by {@link java.net.http.HttpClient}. */
@ThreadSafe
final class JdkHttpClient implements HttpClient {
  // non-private for testing
  final java.net.http.HttpClient underlying;
  final Map<URI, List<Pair<String, String>>> headers;

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

  JdkHttpClient(
      List<Path> certificateFiles,
      List<ByteBuffer> certificateBytes,
      Duration connectTimeout,
      java.net.ProxySelector proxySelector,
      Map<URI, List<Pair<String, String>>> headers) {
    underlying =
        java.net.http.HttpClient.newBuilder()
            .sslContext(createSslContext(certificateFiles, certificateBytes))
            .connectTimeout(connectTimeout)
            .proxy(proxySelector)
            .followRedirects(Redirect.NORMAL)
            .build();
    this.headers = headers;
  }

  @Override
  public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> responseBodyHandler)
      throws IOException {
    try {
      var wrappedRequestBuilder = HttpRequest.newBuilder(request, (name, value) -> true);
      for (var entry : headers.entrySet()) {
        if (RequestRewritingClient.matchesRewriteRule(request.uri(), entry.getKey())) {
          for (var value : entry.getValue()) {
            wrappedRequestBuilder.header(value.getFirst(), value.getSecond());
          }
        }
      }
      return underlying.send(wrappedRequestBuilder.build(), responseBodyHandler);
    } catch (ConnectException e) {
      // original exception has no message
      throw new ConnectException(
          ErrorMessages.create("errorConnectingToHost", request.uri().getHost()));
    } catch (SSLHandshakeException e) {
      throw new SSLHandshakeException(
          ErrorMessages.create(
              "errorSslHandshake", request.uri().getHost(), Exceptions.getRootReason(e)));
    } catch (SSLException e) {
      throw new SSLException(Exceptions.getRootReason(e));
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
      List<Path> certificateFiles, List<ByteBuffer> certificateBytes) {
    try {
      if (certificateFiles.isEmpty() && certificateBytes.isEmpty()) {
        // use JVM's built-in CA certificates
        return SSLContext.getDefault();
      }

      var certFactory = CertificateFactory.getInstance("X.509");
      List<Certificate> certs = gatherCertificates(certFactory, certificateFiles, certificateBytes);
      var keystore = KeyStore.getInstance(KeyStore.getDefaultType());
      keystore.load(null);
      for (var i = 0; i < certs.size(); i++) {
        keystore.setCertificateEntry("Certificate" + i, certs.get(i));
      }
      var trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
      trustManagerFactory.init(keystore);

      var sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());

      return sslContext;
    } catch (GeneralSecurityException | IOException e) {
      throw new HttpClientInitException(
          ErrorMessages.create("cannotInitHttpClient", Exceptions.getRootReason(e)), e);
    }
  }

  private static List<Certificate> gatherCertificates(
      CertificateFactory factory, List<Path> certificateFiles, List<ByteBuffer> certificateBytes) {
    var certificates = new ArrayList<Certificate>();
    for (var file : certificateFiles) {
      try (var stream = Files.newInputStream(file)) {
        collectCertificates(certificates, factory, stream, file);
      } catch (NoSuchFileException e) {
        throw new HttpClientInitException(ErrorMessages.create("cannotFindCertFile", file));
      } catch (IOException e) {
        throw new HttpClientInitException(
            ErrorMessages.create("cannotReadCertFile", Exceptions.getRootReason(e)));
      }
    }
    for (var byteBuffer : certificateBytes) {
      var stream = new ByteArrayInputStream(byteBuffer.array());
      collectCertificates(certificates, factory, stream, "<unavailable>");
    }
    return certificates;
  }

  private static void collectCertificates(
      ArrayList<Certificate> anchors,
      CertificateFactory factory,
      InputStream stream,
      Object source) {
    Collection<X509Certificate> certificates;
    try {
      //noinspection unchecked
      certificates = (Collection<X509Certificate>) factory.generateCertificates(stream);
    } catch (CertificateException e) {
      throw new HttpClientInitException(
          ErrorMessages.create("cannotParseCertFile", source, Exceptions.getRootReason(e)));
    }
    if (certificates.isEmpty()) {
      throw new HttpClientInitException(ErrorMessages.create("emptyCertFile", source));
    }
    anchors.addAll(certificates);
  }
}
