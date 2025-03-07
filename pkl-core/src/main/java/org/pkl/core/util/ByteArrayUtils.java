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
package org.pkl.core.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.pkl.core.runtime.VmExceptionBuilder;

public final class ByteArrayUtils {
  private ByteArrayUtils() {}

  public static String base64(byte[] input) {
    return Base64.getEncoder().encodeToString(input);
  }

  public static String md5(byte[] input) {
    return hash(input, "MD5");
  }

  public static String sha1(byte[] input) {
    return hash(input, "SHA-1");
  }

  public static String sha256(byte[] input) {
    return hash(input, "SHA-256");
  }

  public static long sha256Int(byte[] input) {
    return hashInt(input, "SHA-256");
  }

  /**
   * Implemented directly instead of using JRE's `new BigInteger.toString(16)` so we can AOT-compile
   * this and do not need a Truffle boundary.
   */
  public static String toHex(byte[] hash) {
    //    return new BigInteger(hash).toString(16);
    var hexDigitTable =
        new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    var builder = new StringBuilder(hash.length * 2);
    for (var b : hash) {
      builder.append(hexDigitTable[b >> 4 & 0xF]);
      builder.append(hexDigitTable[b & 0xF]);
    }
    return builder.toString();
  }

  private static String hash(byte[] input, String algorithm) {
    try {
      var digest = MessageDigest.getInstance(algorithm);
      var hash = digest.digest(input);
      return toHex(hash);
    } catch (NoSuchAlgorithmException e) {
      // MD5, SHA-1, and SHA-265 are available in all JVM implementations
      throw new VmExceptionBuilder().unreachableCode().withCause(e).build();
    }
  }

  private static long hashInt(byte[] input, String algorithm) {
    try {
      var digest = MessageDigest.getInstance(algorithm);
      var hash = digest.digest(input);

      long hash64 = 0;
      for (var i = 0; i < 8; i++) {
        hash64 |= (hash[i] & 0xFFL) << (i * 8);
      }
      return hash64;
    } catch (NoSuchAlgorithmException e) {
      // MD5, SHA-1, and SHA-265 are available in all JVM implementations
      throw new VmExceptionBuilder().unreachableCode().withCause(e).build();
    }
  }
}
