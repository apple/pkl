/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.commons.test

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

// copy of org.pkl.core.runtime.CertificateUtils
object CertificateUtils {
  fun setupAllX509CertificatesGlobally(certs: List<Any>) {
    try {
      val certificates = ArrayList<X509Certificate>(certs.size)
      for (cert in certs) {
        toInputStream(cert).use { input -> certificates.addAll(generateCertificates(input)) }
      }
      setupX509CertificatesGlobally(certificates)
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  private fun toInputStream(cert: Any): InputStream {
    if (cert is Path) {
      return Files.newInputStream(cert)
    }
    if (cert is InputStream) {
      return cert
    }
    throw IllegalArgumentException(
      "Unknown class for certificate: " +
        cert.javaClass +
        ". Valid types: java.nio.Path, java.io.InputStream"
    )
  }

  private fun generateCertificates(inputStream: InputStream): Collection<X509Certificate> {
    @Suppress("UNCHECKED_CAST")
    return CertificateFactory.getInstance("X.509").generateCertificates(inputStream)
      as Collection<X509Certificate>
  }

  private fun setupX509CertificatesGlobally(certs: Collection<X509Certificate>) {
    System.setProperty("com.sun.net.ssl.checkRevocation", "true")
    Security.setProperty("ocsp.enable", "true")
    val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
    keystore.load(null)
    var count = 1
    for (cert: X509Certificate in certs) {
      keystore.setCertificateEntry("Certificate" + count++, cert)
    }
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    tmf.init(keystore)
    val sc = SSLContext.getInstance("SSL")
    sc.init(null, tmf.trustManagers, SecureRandom())
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
  }
}
