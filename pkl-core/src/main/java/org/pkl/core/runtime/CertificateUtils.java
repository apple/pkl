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
package org.pkl.core.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class CertificateUtils {
  public static void setupAllX509CertificatesGlobally(List<Object> certs) {
    try {
      var certificates = new ArrayList<X509Certificate>(certs.size());
      for (var cert : certs) {
        try (var input = toInputStream(cert)) {
          certificates.addAll(generateCertificates(input));
        }
      }
      setupX509CertificatesGlobally(certificates);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static InputStream toInputStream(Object cert) throws IOException {
    if (cert instanceof Path) {
      var pathCert = (Path) cert;
      return Files.newInputStream(pathCert);
    }
    if (cert instanceof InputStream) {
      return (InputStream) cert;
    }
    throw new IllegalArgumentException(
        "Unknown class for certificate: "
            + cert.getClass()
            + ". Valid types: java.nio.Path, java.io.InputStream");
  }

  private static Collection<X509Certificate> generateCertificates(InputStream inputStream)
      throws CertificateException {
    //noinspection unchecked
    return (Collection<X509Certificate>)
        CertificateFactory.getInstance("X.509").generateCertificates(inputStream);
  }

  private static void setupX509CertificatesGlobally(Collection<X509Certificate> certs)
      throws KeyStoreException,
          CertificateException,
          IOException,
          NoSuchAlgorithmException,
          KeyManagementException {
    System.setProperty("com.sun.net.ssl.checkRevocation", "true");
    Security.setProperty("ocsp.enable", "true");
    var keystore = KeyStore.getInstance(KeyStore.getDefaultType());
    keystore.load(null);

    var count = 1;
    for (var cert : certs) {
      keystore.setCertificateEntry("Certificate" + count++, cert);
    }
    var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(keystore);

    var sc = SSLContext.getInstance("SSL");
    sc.init(null, tmf.getTrustManagers(), new SecureRandom());
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
  }
}
